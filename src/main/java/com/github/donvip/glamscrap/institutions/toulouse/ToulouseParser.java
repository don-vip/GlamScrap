package com.github.donvip.glamscrap.institutions.toulouse;

import java.time.Year;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector.SelectorParseException;

import com.github.donvip.glamscrap.Parser;
import com.github.donvip.glamscrap.domain.Fonds;
import com.github.donvip.glamscrap.domain.Notice;

class ToulouseParser extends Parser {

    private static final Logger LOGGER = LogManager.getLogger();

    public static Fonds parseFonds(Document desc, String cote) {
        Element fondsDesc = desc.select("#notice_sp").first();
        if (fondsDesc != null) {
            final Fonds f = new Fonds(cote);
            // 0. Search for title
            f.setTitle(fondsDesc.select(String.format("table > tbody > tr > td > h2:containsOwn(%s) + h2", cote)).first().text());
            // 1. Search for expected number of notices (information always displayed)
            try {
                f.setExpectedNotices(Integer.valueOf(
                        fondsDesc.select("table > tbody > tr").first()
                                 .select("span.titre:containsOwn(Nombre d\\'articles) + span.result").first()
                                 .text()));
                f.setExpectedNoticeCotes(IntStream.range(1, f.getExpectedNotices()).mapToObj(i -> cote + i).toList());
            } catch (RuntimeException e) {
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
            return f;
        } else {
            LOGGER.warn("Couldn't parse fonds for: {}", cote);
            return null;
        }
    }

    private static void addOptionalField(Element desc, String legend, Consumer<String> consumer) {
        try {
            Element note = desc.select(String.format("legend:containsOwn(%s) + div", legend)).first();
            if (note != null) {
                consumer.accept(note.text());
            }
        } catch (SelectorParseException e) {
            LOGGER.catching(e);
        }
    }

    public static Notice parseNotice(Document desc, String cote) {
        Element tab = desc.select("#tableau_notice").first();
        if (tab != null) {
            final Notice n = new Notice(cote);
            Elements firstRow = tab.select("tbody > tr");
            // 1. Title
            Matcher m = Pattern.compile("(.+) - (.+)").matcher(
                    firstRow.select("p[align=left]").first().text());
            if (m.matches()) {
                n.setTitle(m.group(2).trim());
            } else {
                if (!cote.matches("\\d+Fi\\d+/\\d+")) {
                    LOGGER.error("Empty notice for {}", cote);
                }
                return null;
            }
            // 2. Description
            n.setDescription(firstRow.select("p[align=justify] > span").first().text().trim());
            // Extract date from description (or title) thanks to HeidelTime
            if (extractDate(n.getDescription(), n) == null) {
                extractDate(n.getTitle(), n);
            }
            Element span = tab.select("tbody > tr[align=LEFT] > td.tab_premierecondition > span.loupe").first();
            // 3. Author(s)
            extractLinks(span, "Auteur(s)", t -> n.getAuthors().add(t
                    .replaceAll("(?i) * [ Auteur ]", "")
                    .replaceAll("(?i) * [ Photographe ]", "")));
            // 4. Document type
            extractTextField(span, "Type document", n::setType);
            // 5. Technique
            extractTextField(span, "Technique", n::setTechnique);
            // 6. Format
            extractTextField(span, "Format", t -> n.setFormat(t.replace(" cm", "")));
            // 7. Support
            extractTextField(span, "Support", n::setSupport);
            // 8. Material condition
            extractTextField(span, "Etat matériel", t -> n.setMaterialCondition(t.toUpperCase(Locale.FRANCE)));
            // 9. Producer
            extractLinks(span, "Producteur", n::setProducer);
            // 10. Classification
            extractTextField(span, "Plan de classement", t -> n.setClassification(t.substring(0, t.length() - 1)));
            // 11. Origin
            extractTextField(span, "Origine du document", n::setOrigin);
            // 12. Entry mode
            extractTextField(span, "Mode d\\'entrée", n::setEntryMode);
            // 13. Year of entry
            try {
                extractYearField(span, "Année d\\'entrée", n::setEntryYear);
            } catch (DateTimeParseException ex) {
                LOGGER.error("Unable to parse year for notice {}: {}", cote, ex.getMessage());
                LOGGER.catching(Level.DEBUG, ex);
            }
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
}
