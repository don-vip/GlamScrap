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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.cas.FSIterator;

import com.github.donvip.archscrap.domain.Notice;

import de.unihd.dbs.heideltime.standalone.DocumentType;
import de.unihd.dbs.heideltime.standalone.HeidelTimeStandalone;
import de.unihd.dbs.heideltime.standalone.OutputType;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.types.heideltime.Timex3;

public abstract class Parser {

    private static final Logger LOGGER = LogManager.getLogger();

    static {
        java.util.logging.Logger.getLogger("HeidelTimeStandalone").setLevel(java.util.logging.Level.WARNING);
    }

    // -- HeidelTime
    private static final HeidelTimeStandalone timeNarrative = new HeidelTimeStandalone(
            Language.FRENCH, DocumentType.NARRATIVES, OutputType.XMI,
            System.getenv("CI") != null ? "./target/classes/config.github.props" : "./target/classes/config.windows.props");

    protected Parser() {
        // Hide public constructor
    }

    protected static String extractDate(String text, final Notice n) {
        try {
            ResultFormatter resultFormatter = jcas -> {
                FSIterator<?> iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
                while (iterTimex.hasNext()) {
                    // http://www.timeml.org/publications/timeMLdocs/timeml_1.2.1.html#timex3
                    Timex3 t = (Timex3) iterTimex.next();
                    String v = t.getTimexValue();
                    if (v.startsWith("XXXX-")) {
                        continue;
                    }
                    switch (t.getTimexType()) {
                    case "DATE":
                        if ("XXXX".equals(v) || v.matches(".*_REF") || v.matches("\\d{2}") || v.matches("\\d{3}")) {
                            continue; // XXXX, PAST_REF, PRESENT_REF, FUTURE_REF, Century, decade
                        } else if (v.matches("\\d{4}-\\d{2}-\\d{2}")) { // YYYY-MM-DD
                            return parseLocalDate(n, v);
                        } else if (v.matches("\\d{4}-\\d{2}-\\d{1}")) { // YYYY-MM-D
                            return parseLocalDate(n, new StringBuilder(v).insert(v.length()-1, "0").toString());
                        } else if (v.matches("\\d{4}-\\d{2}")) { // YYYY-MM
                            return parseYearMonth(n, v);
                        } else if (v.matches("(\\d{4})-(WI|SP|SU|AU)")) { // YYYY-Season
                            return parseYear(n, v.substring(0, v.indexOf('-')));
                        } else if (v.matches("\\d{4}")) { // YYYY
                            return parseYear(n, v);
                        } else {
                            throw new UnsupportedOperationException(v);
                        }
                    case "DURATION", "TIME", "SET":
                        continue;
                    default:
                        throw new UnsupportedOperationException(t.getTimexType()+" / "+t.getTimexValue());
                    }
                }
                return null;
            };
            return text.isEmpty() ? null : timeNarrative.process(text, resultFormatter);
        } catch (DocumentCreationTimeMissingException e) {
            LOGGER.catching(e);
            return null;
        }
    }

    private static String parseYear(final Notice n, String v) {
        n.setYear(Year.parse(v));
        return n.getYear().toString();
    }

    private static String parseYearMonth(final Notice n, String v) {
        YearMonth ym = YearMonth.parse(v);
        n.setYearMonth(ym);
        n.setYear(Year.of(ym.getYear()));
        return ym.toString();
    }

    private static String parseLocalDate(final Notice n, String v) {
        LocalDate d = LocalDate.parse(v);
        n.setDate(d);
        n.setYearMonth(YearMonth.of(d.getYear(), d.getMonth()));
        n.setYear(Year.of(d.getYear()));
        return d.toString();
    }
}
