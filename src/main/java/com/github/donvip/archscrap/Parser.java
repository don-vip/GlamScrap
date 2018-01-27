/**
 * This file is part of ArchScrap.
 *
 *  ArchScrap is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ArchScrap is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with ArchScrap. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.donvip.archscrap;

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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector.SelectorParseException;

import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;

import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.types.heideltime.Timex3;

public class Parser {

    private static final Logger LOGGER = LogManager.getLogger();

    // -- HeidelTime
    private static final HeidelTimeStandalone timeNarrative = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.NARRATIVES, OutputType.XMI, "./target/classes/config.windows.props");
    /*private static final HeidelTimeStandalone timeScientific = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.SCIENTIFIC, OutputType.XMI, "./target/classes/config.windows.props");*/

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
}