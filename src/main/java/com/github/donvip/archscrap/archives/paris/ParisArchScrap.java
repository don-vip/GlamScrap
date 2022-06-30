package com.github.donvip.archscrap.archives.paris;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.donvip.archscrap.ArchScrap;
import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;

/**
 * TODO download images
 * vignette : https://archives.paris.fr/_depot_ad75/_depot_arko/basesdoc/1/116617/vign_AD075PH_FORT008.JPG
 * HD sans watermark : https://archives.paris.fr/_depot_ad75/_depot_arko/basesdoc/1/116617/ori_AD075PH_FORT008.JPG
 * https://archives.paris.fr/a/234/catalogues-des-documents-figures/
 */
public class ParisArchScrap extends ArchScrap {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BASE_URL = "https://archives.paris.fr/f/photos/";

    private static final int PAGE = 20;

    public ParisArchScrap() {
        super("paris");
    }

    @Override
    protected Album getAlbum(String cote) {
        return null;
    }

    @Override
    protected Range getAllowedGap(String cote) {
        return null;
    }

    @Override
    protected String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    protected List<Fonds> fetchAllFonds() throws IOException {
        return List.of(searchFonds("PH"));
    }

    @Override
    protected Notice searchNotice(Fonds f, int i, int j, boolean fetch) {
        String[] tab = f.getExpectedNoticeCotes().get(i-1).split(";");
        String cote = tab[0];
        Notice n = session.get(Notice.class, cote);
        if (n == null && fetch) {
            try {
                Document desc = fetch(String.format("%s/f/", tab[1]));
                if (desc != null) {
                    n = ParisParser.parseNotice(desc, cote);
                    if (n != null) {
                        session.beginTransaction();
                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.persist(n);
                        session.persist(f);
                        session.getTransaction().commit();
                    } else {
                        missedNotices.add(cote);
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

    @Override
    protected Fonds createNewFonds(String cote) throws IOException {
        Fonds fonds = new Fonds(cote);
        fonds.setTitle("Collections photographiques");
        Document doc = fetch("tableau/?&debut=0");
        int n = extractNumberOfResults(doc);
        fonds.setExpectedNotices(n);
        List<String> expectedNoticeCotes = extractAllCotes(doc, n);
        fonds.setExpectedNoticeCotes(expectedNoticeCotes);
        if (expectedNoticeCotes.size() != n) {
            LOGGER.error("Inconsistent number of notices for fonds {}: {} != {}", cote, expectedNoticeCotes.size(), n);
            throw new IllegalStateException();
        }
        return fonds;
    }

    private static int extractNumberOfResults(Document doc) {
        String numberOfResults = doc.select("p.nombre_facettes").first().text();
        return Integer.parseInt(numberOfResults.substring(0, numberOfResults.indexOf(' ')));
    }

    private List<String> extractAllCotes(Document doc, int n) throws IOException {
        List<String> cotes = extractCotes(doc);
        for (int i = PAGE; i < n; i += PAGE) {
            cotes.addAll(extractCotes(fetch(String.format("tableau/?&debut=%d", i))));
        }
        return cotes;
    }

    private static List<String> extractCotes(Document doc) {
        List<String> cotes = new ArrayList<>();
        for (Element tr : doc.select("table.tableau_facettes").select("tr[valign=top]")) {
            Elements cells = tr.select("td");
            String href = cells.get(6).select("a").attr("href");
            cotes.add(String.format("%s %s;%d", cells.get(2).text(), cells.get(3).text(),
                    Integer.parseInt(href.substring(10, href.indexOf("/fiche/")))));
        }
        return cotes;
    }

    @Override
    protected void postScrapFonds(Fonds f) throws IOException {
        // Enrich notices with data unavailable in notices themselves but only through search... (sic)
        Document doc = fetch("tableau/?");
        enrichNotices(f, doc, 16, (n, a) -> {
            n.setAuthors(List.of(a));
            persist(n);
        });
        enrichNotices(f, doc, 18, (n, a) -> {
            String obs = n.getObservation();
            n.setObservation((obs == null ? "" : obs + ';') + "Ouvrage="+a);
            persist(n);
        });
    }

    private static Map<Integer, String> extractMap(Document doc, int crit) {
        return doc.select("select#crit_" + crit).select("option[value]").stream()
                .collect(toMap(e -> Integer.parseInt(e.attr("value2")), e -> e.attr("value")));
    }

    private void enrichNotices(Fonds f, Document doc, int crit, BiConsumer<Notice, String> filler) throws IOException {
        for (Entry<Integer, String> e : extractMap(doc, crit).entrySet()) {
            BiConsumer<Document, String> parser = (d, v) ->
                extractCotes(d).stream()
                               .map(s -> s.split(";")[0])
                               .map(cote -> f.getNotices().stream().filter(n -> n.getCote().equals(cote)).findFirst()
                                       .orElseThrow(() -> new IllegalStateException("No notice found for cote " + cote)))
                               .forEach(n -> filler.accept(n, v));
            Document results = fetch(String.format("tableau/?&crit1=%d&v_%d_1=%s&v_%d_2=%d", crit, crit, e.getValue(), crit, e.getKey()));
            String decodedValue = URLDecoder.decode(e.getValue(), StandardCharsets.ISO_8859_1);
            int n = extractNumberOfResults(results);
            parser.accept(results, decodedValue);
            for (int i = PAGE; i < n; i += PAGE) {
                parser.accept(fetch(String.format("tableau/?&debut=%d", i)), decodedValue);
            }
        }
    }
}
