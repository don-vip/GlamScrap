/**
 * This file is part of GlamScrap.
 *
 *  GlamScrap is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  GlamScrap is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with GlamScrap. If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.donvip.glamscrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.github.donvip.glamscrap.domain.Notice;

/**
 * Unit tests for Parser.
 */
class ParserTest {

    @Test
    void testExtractDate1() {
        doTestDate("2 mai 1937. Photographie d'un groupe de six personnes",
                LocalDate.of(1937, Month.MAY, 2));
    }

    @Test
    void testExtractDate2() {
        doTestDate("1987. Laissez-passer voiture presse délivré à M. André Cros par le Comité des Pyrénées de la Fédération française de rugby, pour la saison 1987 - 1988 de la section rugby du Stade toulousain",
                Year.of(1987));
    }

    @Test
    void testExtractDate3() {
        doTestDate("Tour de Contrôle (Blagnac). 28 octobre 1972. Vue d'ensemble d'un homme de dos, d'une jeune femme et d'une petite fille (épouse et fille de Bernard Ziegler, un des pilotes de l'équipage du vol d'essai) sortant de la tour de contrôle. ",
                LocalDate.of(1972, Month.OCTOBER, 28));
    }

    @Test
    void testExtractDate4() {
        doTestDate("Rue du Midi. Années 1950. Vue perspective descendante de la rue du Midi au niveau des n°20 et 13.",
                Year.of(1950));
    }

    @Test
    void testExtractDate5() {
        doTestDate("Rue Matabiau, place Roquelaine. Août 1944. Vue d'ensemble d'une barricade ;",
                YearMonth.of(1944, Month.AUGUST));
    }

    @Test
    void testExtractDate6() {
        doTestDate("Esquisse représentant les Cathares. Peinture de Raymond Moretti pour illustrer le plafond des arcades de la Place du Capitole. Version reproduite à la Galerue des Arcades. Après dix mois de siège, la citadelle est vaincue à l'hiver 1244.",
                Year.of(1244));
    }

    @Test
    void testExtractDate7() {
        doTestDate("Vallée de Héas, commune de Gèdre (Haute-Pyrenées). Gravure de Mellin ( début XIX° ) rééditée dans les années 1980. Vue d'ensemble de la vallée d'Héas.",
                (Year) null);
    }

    @Test
    @Disabled
    void testExtractDate8() {
        doTestDate("28.10.72 1er Vol d'Airbus",
                LocalDate.of(1972, Month.OCTOBER, 28));
    }

    @Test
    @Disabled
    void testExtractDate9() {
        doTestDate("28.10.72 1er Vol d'Airbus (1972)",
                LocalDate.of(1972, Month.OCTOBER, 28));
    }

    @Test
    void testExtractDate10() {
        doTestDate("15 place du Président-Wilson. Carte photographique de la terrasse du grand café restaurant Lafayette avec le personnel posant devant. Mention sur l'image: \"Baron E. Duquesne, 3-2-1913\". Au verso, mention manuscrite: \"Café - Rest Lafayette Toulouse 3 février 1913\".\"",
                LocalDate.of(1913, Month.FEBRUARY, 3));
    }

    private static void doTestDate(String text, LocalDate date) {
        doTestDate(text, Year.of(date.getYear()), YearMonth.of(date.getYear(), date.getMonth()), date);
    }

    private static void doTestDate(String text, YearMonth ym) {
        doTestDate(text, Year.of(ym.getYear()), ym, null);
    }

    private static void doTestDate(String text, Year year) {
        doTestDate(text, year, null, null);
    }

    private static void doTestDate(String text, Year year, YearMonth yearMonth, LocalDate date) {
        Notice n = new Notice();
        Parser.extractDate(text, n);
        assertEquals(year, n.getYear());
        assertEquals(yearMonth, n.getYearMonth());
        assertEquals(date, n.getDate());
    }
}
