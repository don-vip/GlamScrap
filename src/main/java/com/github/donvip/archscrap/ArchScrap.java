package com.github.donvip.archscrap;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class ArchScrap implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final String BASE_URL = "http://basededonnees.archives.toulouse.fr/4DCGi/";

    // -- Hibernate
    private final StandardServiceRegistry registry;
    private final SessionFactory sessionFactory;
    private final Session session;

    // -- HeidelTime
    private static final HeidelTimeStandalone timeNarrative = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.NARRATIVES, OutputType.XMI, "./target/classes/config.windows.props");
    /*private static final HeidelTimeStandalone timeScientific = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.SCIENTIFIC, OutputType.XMI, "./target/classes/config.windows.props");*/

    private ArchScrap() {
        LOGGER.debug("Initializing Hibernate...");
        registry = new StandardServiceRegistryBuilder()
                    .configure() // configures settings from hibernate.cfg.xml
                    .build();
        sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
        session = sessionFactory.openSession();
    }

    @Override
    public void close() throws IOException {
        try {
            session.close();
        } finally {
            sessionFactory.close();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            LOGGER.info("Usage: ArchScrap scrap [<fonds>[,<fonds>]*] | fetch [<notice>]");
            return;
        }
        try (ArchScrap app = new ArchScrap()) {
            switch (args[0]) {
                case "scrap":
                    app.doScrap(args);
                    break;
                case "fetch":
                    app.doFetch(args);
                    break;
            }
        } catch (IOException e) {
            LOGGER.catching(e);
        }
        LOGGER.info("Bye!");
    }

    public void doScrap(String[] args) throws IOException {
        LOGGER.info("Fetching all image fonds from archives website...");
        Element plan = fetch("web_fondsmcadre/34/ILUMP458").select("#planclassement").first();
        if (plan != null) {
            Elements allFonds = plan.select("p > a");
            LOGGER.info("Found {} fonds", allFonds.size());
            for (Element fonds : allFonds) {
                handleFonds(fonds);
            }
        } else {
            LOGGER.error("Unable to fetch image fonds from archives website");
        }
    }

    public void doFetch(String[] args) throws IOException {
        String cote = args[1];
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+)(\\d+)").matcher(cote);
        if (m.matches()) {
            LOGGER.info(searchNotice(
                    searchFonds(m.group(1)),
                    Integer.valueOf(m.group(2))));
        } else {
            LOGGER.error("Unrecognized cote: {}", cote);
        }
    }

    private void handleFonds(Element fonds) throws IOException {
        String fondsText = fonds.text();
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+) - (.+)").matcher(fondsText);
        if (m.matches()) {
            String cote = m.group(1);
            Fonds f = searchFonds(cote);
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

    private Fonds searchFonds(String cote) throws IOException {
        // Check to be sure, we don't have it in database
        session.beginTransaction();
        Fonds f = session.get(Fonds.class, cote);
        session.getTransaction().commit();
        if (f == null) {
            f = createNewFonds(cote);
            session.beginTransaction();
            session.save(f);
            session.getTransaction().commit();
        }
        return f;
    }

    private Notice searchNotice(Fonds f, int i) {
        // Check to be sure, we don't have it in database
        String cote = f.getCote()+i;
        session.beginTransaction();
        Notice n = session.get(Notice.class, cote);
        session.getTransaction().commit();
        if (n == null) {
            try {
                Document desc = fetch(String.format("Web_VoirLaNotice/34_01/%s/ILUMP21411", cote));
                if (desc != null) {
                    n = parseNotice(desc, cote);
                    if (n != null) {
                        session.beginTransaction();
                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.save(n);
                        session.save(f);
                        session.getTransaction().commit();
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

    private Notice parseNotice(Document desc, String cote) {
        Element tab = desc.select("#tableau_notice").first();
        if (tab != null) {
            final Notice n = new Notice(cote);
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
            Element span = tab.select("tbody > tr[align=LEFT] > td.tab_premierecondition > span.loupe").first();
            // 3. Author(s)
            extractLinks(span, "Auteur(s)", t -> n.getAuthors().add(t.replace(" * [ Auteur ]", "")));
            // 4. Document type
            extractTextField(span, "Type document", n::setType);
            // 5. Technique
            extractTextField(span, "Technique", n::setTechnique);
            // 6. Format
            extractTextField(span, "Format", n::setFormat);
            // 7. Support
            extractTextField(span, "Support", n::setSupport);
            // 8. Material condition
            extractTextField(span, "Etat matériel", n::setMaterialCondition);
            // 9. Producer
            extractLinks(span, "Producteur", n::setProducer);
            // 10. Classification
            extractTextField(span, "Plan de classement", t -> n.setClassification(t.substring(0, t.length() - 1)));
            // 11. Origin
            extractTextField(span, "Origine du document", n::setOrigin);
            // 12. Entry mode
            extractTextField(span, "Mode d\\'entrée", n::setEntryMode);
            // 13. Year of entry
            extractYearField(span, "Année d\\'entrée", n::setEntryYear);
            // 14. Rights
            extractTextField(span, "Droits", n::setRights);
            // 15. Original consultable
            extractBoolField(span, "Original Consultable", n::setOriginalConsultable);
            // 16. Observations
            extractTextField(span, "Observation", n::setObservation);
            // 17. Indexation
            extractLinks(span, "Termes d\\'indexation", t -> n.getIndexation().add(t.replace(" *", "")));
            // 18. Historical period
            extractTextField(span, "Période historique", n::setHistoricalPeriod);
            return n;
        } else {
            LOGGER.warn("Couldn't parse notice for: {}", cote);
            return null;
        }
    }

    private static void extractLinks(Element span, String title, Consumer<String> consumer) {
        for (Element e : span.select("span.titre:contains("+title+") + span.lien > a")) {
            consumer.accept(e.text().trim());
        }
    }

    private static <T> void extractField(Element span, String title, Consumer<T> consumer, Function<String, T> parser) {
        Element e = span.select("span.titre:contains("+title+") + span.result").first();
        if (e != null) {
            consumer.accept(parser.apply(e.text().trim()));
        }
    }

    private static void extractTextField(Element span, String title, Consumer<String> consumer) {
        extractField(span, title, consumer, s -> s);
    }

    private static void extractBoolField(Element span, String title, Consumer<Boolean> consumer) {
        extractField(span, title, consumer, "OUI"::equalsIgnoreCase);
    }

    private static void extractYearField(Element span, String title, Consumer<Year> consumer) {
        extractField(span, title, consumer, Year::parse);
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

    private Fonds createNewFonds(String cote) throws IOException {
        LOGGER.info("New fonds! {}", cote);
        Fonds f = new Fonds(cote);
        Element fondsDesc = fetch(String.format("Web_FondsCClass%s/ILUMP31929", cote)).select("#notice_sp").first();
        if (fondsDesc != null) {
            // 0. Search for title
            f.setTitle(fondsDesc.select(String.format("table > tbody > tr > td > h2:containsOwn(%s) + h2", cote)).first().text());
            // 1. Search for expected number of notices (information always displayed)
            try {
                f.setExpectedNotices(Integer.valueOf(
                        fondsDesc.select("table > tbody > tr").first()
                                 .select("span.titre:containsOwn(Nombre d\\'articles) + span.result").first()
                                 .text()));
            } catch (NumberFormatException | NullPointerException | SelectorParseException e) {
                LOGGER.warn("Unable to fetch number of notices for {}", cote);
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
            LOGGER.warn("Unable to fetch fonds description for {}", cote);
        }
        return f;
    }

    private void addOptionalField(Element desc, String legend, Consumer<String> consumer) {
        try {
            Element note = desc.select(String.format("legend:containsOwn(%s) + div", legend)).first();
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
}
