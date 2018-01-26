package com.github.donvip.archscrap;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.PersistenceException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.cas.FSIterator;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector.SelectorParseException;

import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.types.heideltime.Timex3;

public class ArchScrap {
    
    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final String BASE_URL = "http://basededonnees.archives.toulouse.fr/4DCGi/";

    // -- Hibernate
    private final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                                                        .configure() // configures settings from hibernate.cfg.xml
                                                        .build();
    private Session session;

    // -- HeidelTime
    private static final HeidelTimeStandalone timeNarrative = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.NARRATIVES, OutputType.XMI, "./target/classes/config.windows.props");
    /*private static final HeidelTimeStandalone timeScientific = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.SCIENTIFIC, OutputType.XMI, "./target/classes/config.windows.props");*/

    private void run() {
        LOGGER.debug("Initializing Hibernate...");
        try (SessionFactory sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory()) {
            LOGGER.info("Fetching all image fonds from archives website...");
            Element plan = fetch("web_fondsmcadre/34/ILUMP458").select("#planclassement").first();
            if (plan != null) {
                session = sessionFactory.openSession();
                Elements allFonds = plan.select("p > a");
                LOGGER.info("Found {} fonds", allFonds.size());
                for (Element fonds : allFonds) {
                    handleFonds(fonds);
                }
                session.close();
            } else {
                LOGGER.error("Unable to fetch image fonds from archives website");
            }
            LOGGER.info("Bye!");
        } catch (IOException | PersistenceException e) {
            LOGGER.catching(e);
        }
    }

    private void handleFonds(Element fonds) throws IOException {
        String fondsText = fonds.text();
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+) - (.+)").matcher(fondsText);
        if (m.matches()) {
            String cote = m.group(1);
            session.beginTransaction();
            Fonds f = session.get(Fonds.class, cote);
            session.getTransaction().commit();
            if (f == null) {
                // New fonds: fetch metadata
                f = createNewFonds(fondsText, m.group(2), cote);
                session.beginTransaction();
                session.save(f);
                session.getTransaction().commit();
            }
            if (f.getNotices().size() < f.getExpectedNotices()) {
                // We have less notices in database than expected
                // 1. Try to fetch missing notices
                for (int i : f.getMissingNotices(session)) {
                    searchNotice(f, i);
                }
                // 2. Try to search new ones
                int last = f.getNotices().isEmpty() ? 0 : f.getNotices().get(f.getNotices().size() - 1).getId();  
                for (int i = last + 1; i < f.getExpectedNotices(); i++) {
                    searchNotice(f, i);
                }
            }
        } else {
            LOGGER.warn("Unable to parse fonds {}", fondsText);
        }
    }

    private Notice searchNotice(Fonds f, int i) {
        // Check to be sure, we don't have it in database
        String cote = f.getCote()+i;
        Notice n = session.get(Notice.class, cote);
        if (n == null) {
            try {
                Document desc = fetch(String.format("Web_VoirLaNotice/34_01/%s/ILUMP21411", cote));
                if (desc != null) {
                    Element tab = desc.select("#tableau_notice").first();
                    if (tab != null) {
                        session.beginTransaction();
                        n = new Notice(cote);
                        Elements firstRow = tab.select("tbody > tr");
                        // 1. Title
                        Matcher m = Pattern.compile("(.+) - (.+)").matcher(
                                firstRow.select("p[align=left]").first().text());
                        if (m.matches()) {
                            n.setTitle(m.group(2).trim());
                        }
                        // 2. Description
                        n.setDescription(firstRow.select("p[align=justify] > span").first().text().trim());
                        // Extract date from description thanks to HeidelTime
                        extractDate(n.getDescription(), n);
                        // 3. Author(s)
                        // 4. Document type
                        // 5. Technique
                        // 6. Format
                        // 7. Support
                        // 8. Material condition
                        // 9. Producer
                        // 10. Classification
                        // 11. Origin
                        // 12. Entry mode
                        // 13. Year of entry
                        // 14. Rights
                        // 15. Original consultable
                        // 16. Observations
                        // 17. Indexation
                        // 18. Historical period

                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.save(n);
                        session.save(f);
                        session.getTransaction().commit();
                    } else {
                        LOGGER.warn("Couldn't parse notice for: {}", cote);
                    }
                } else {
                    LOGGER.warn("No notice found for: {}", cote);
                }
            } catch (IOException e) {
                LOGGER.catching(e);
            }
        }
        return n;
    }

    static void extractDate(String text, final Notice n) {
        try {
            ResultFormatter resultFormatter = jcas -> {
                FSIterator<?> iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
                while (iterTimex.hasNext()) {
                    // http://www.timeml.org/publications/timeMLdocs/timeml_1.2.1.html#timex3
                    Timex3 t = (Timex3) iterTimex.next();
                    String v = t.getTimexValue();
                    switch (t.getTimexType()) {
                    case "DATE":
                        switch (v.length()) {
                        case 10: // YYYY-MM-DD
                            LocalDate d = LocalDate.parse(v);
                            n.setDate(d);
                            n.setYearMonth(YearMonth.of(d.getYear(), d.getMonth()));
                            n.setYear(Year.of(d.getYear()));
                            return d.toString();
                        case 7: // YYYY-MM
                            YearMonth ym = YearMonth.parse(v);
                            n.setYearMonth(ym);
                            n.setYear(Year.of(ym.getYear()));
                            return ym.toString();
                        case 4: // YYYY
                            n.setYear(Year.parse(v));
                            return n.getYear().toString();
                        case 2: // Century ?
                            continue;
                        default:
                            if ("PRESENT_REF".equals(v)) {
                                continue;
                            }
                            throw new UnsupportedOperationException(v);
                        }
                    case "DURATION":
                        continue;
                    case "TIME":
                        if (v.startsWith("XXXX-XX-XXT")) {
                            continue;
                        }
                    case "SET":
                    default:
                        throw new UnsupportedOperationException(t.getTimexType()+" / "+t.getTimexValue()); 
                    }
                }
                return null;
            };
            if (timeNarrative.process(text, resultFormatter) == null) {
                //timeScientific.process(text, resultFormatter);
            }
        } catch (DocumentCreationTimeMissingException e) {
            LOGGER.catching(e);
        }
    }

    private Fonds createNewFonds(String fondsText, String title, String cote) throws IOException {
        LOGGER.info("New fonds! {}", fondsText);
        Fonds f = new Fonds(cote, title);
        Element fondsDesc = fetch(String.format("Web_FondsCClass%s/ILUMP31929", cote)).select("#notice_sp").first();
        if (fondsDesc != null) {
            // 1. Search for expected number of notices (information always displayed)
            try {
                f.setExpectedNotices(Integer.valueOf(
                        fondsDesc.select("table > tbody > tr").first()
                                 .select("span.titre:containsOwn(Nombre d\\'articles) ~ span.result").first()
                                 .text()));
            } catch (NumberFormatException | NullPointerException | SelectorParseException e) {
                LOGGER.warn("Unable to fetch number of notices for {}", fondsText);
            }
            // 2. Search for "Note" (optional)
            addOptionalField(fondsDesc, "Note", f::setNote);
            // 3. Search for "Summary" (optional)
            addOptionalField(fondsDesc, "Sommaire", f::setSummary);
            // 4. Search for "Access conditions" (optional)
            addOptionalField(fondsDesc, "Conditions d\\'accès", f::setAccessConditions);
            // 5. Search for "Reuse conditions" (optional)
            addOptionalField(fondsDesc, "Conditions d\\'utilisation", f::setReuseConditions);
        } else {
            LOGGER.warn("Unable to fetch fonds description for {}", fondsText);
        }
        return f;
    }

    private void addOptionalField(Element desc, String legend, Consumer<String> consumer) {
        try {
            Element note = desc.select(String.format("legend:containsOwn(%s) ~ div", legend)).first();
            if (note != null) {
                consumer.accept(note.text());
            }
        } catch (SelectorParseException e) {
            LOGGER.catching(e);
        }
    }

    private static Document fetch(String doc) throws IOException {
        LOGGER.info("Fetching {}", BASE_URL + doc);
        return Jsoup.connect(BASE_URL + doc).get();
    }

    public static void main(String[] args) {
        new ArchScrap().run();
    }
}
