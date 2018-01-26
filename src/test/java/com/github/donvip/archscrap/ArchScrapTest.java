package com.github.donvip.archscrap;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;

import org.junit.Test;

import com.github.donvip.archscrap.ArchScrap;
import com.github.donvip.archscrap.Notice;

/**
 * Unit tests for App.
 */
public class ArchScrapTest {

    @Test
    public void testExtractDate() {
        doTestDate("2 mai 1937. Photographie d'un groupe de six personnes",
                LocalDate.of(1937, Month.MAY, 2));
        doTestDate("1987. Laissez-passer voiture presse délivré à M. André Cros par le Comité des Pyrénées de la Fédération française de rugby, pour la saison 1987 - 1988 de la section rugby du Stade toulousain",
                Year.of(1987));
        doTestDate("Tour de Contrôle (Blagnac). 28 octobre 1972. Vue d'ensemble d'un homme de dos, d'une jeune femme et d'une petite fille (épouse et fille de Bernard Ziegler, un des pilotes de l'équipage du vol d'essai) sortant de la tour de contrôle. ",
                LocalDate.of(1972, Month.OCTOBER, 28));
        doTestDate("Rue du Midi. Années 1950. Vue perspective descendante de la rue du Midi au niveau des n°20 et 13.",
                Year.of(1950));
        doTestDate("Rue Matabiau, place Roquelaine. Août 1944. Vue d'ensemble d'une barricade ;",
                YearMonth.of(1944, Month.AUGUST));
        /*doTestDate("28.10.72 1er Vol d'Airbus",
                LocalDate.of(1972, Month.OCTOBER, 28));
        doTestDate("28.10.72 1er Vol d'Airbus (1972)",
                LocalDate.of(1972, Month.OCTOBER, 28));*/
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
        ArchScrap.extractDate(text, n);
        assertEquals(year, n.getYear());
        assertEquals(yearMonth, n.getYearMonth());
        assertEquals(date, n.getDate());
    }
}
