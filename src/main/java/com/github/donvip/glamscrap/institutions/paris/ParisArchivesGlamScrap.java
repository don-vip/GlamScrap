package com.github.donvip.glamscrap.institutions.paris;

import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.donvip.glamscrap.GlamScrap;
import com.github.donvip.glamscrap.domain.Fonds;
import com.github.donvip.glamscrap.domain.Notice;
import com.github.donvip.glamscrap.wikidata.Author;

/**
 * https://archives.paris.fr/a/234/catalogues-des-documents-figures/
 */
public class ParisArchivesGlamScrap extends GlamScrap {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BASE_URL = "https://archives.paris.fr/f/photos/";

    private static final int PAGE = 20;

    private static final Map<String, Author> PREDEFINED_AUTHORS = new HashMap<>();
    static {
        PREDEFINED_AUTHORS.put("Durandelle, Louis Emile (photographe)", new Author("Durandelle", "Louis", "photographe"));
        PREDEFINED_AUTHORS.put("Barry (photographe)", new Author("Barry", "Jean", "photographe"));
        PREDEFINED_AUTHORS.put("Petit, Pierre et fils (photographe)", new Author("Petit", "Pierre", "photographe"));
        PREDEFINED_AUTHORS.put("S.I.P. (photographe)", new Author("Société industrielle de photographie", null, "studio photographique"));
        PREDEFINED_AUTHORS.put("Vizzanova,  F. (photographe)", new Author("Vizzavona", "François", "photographe"));
        PREDEFINED_AUTHORS.put("Szepessy, V. de (photographe)", new Author("Szepessy", "Victor", "photographe"));
        PREDEFINED_AUTHORS.put("Chevojon (studio photographique)", new Author("Studio Chevojon", null, "agence photographique"));
        PREDEFINED_AUTHORS.put("Blanc, Geo (photographe)", new Author("Blanc", "Georges", "photographe"));
        PREDEFINED_AUTHORS.put("Nobécourt, F. (photographe)", new Author("Nobécourt", "Fernand", "photographe"));
        PREDEFINED_AUTHORS.put("Bernès, Marouteau et Cie (studio photographique)", new Author("Bernès, Marouteau & Cie", null, "agence photographique"));
        PREDEFINED_AUTHORS.put("Cade, Paul (photographe)", new Author("Cadé", "Paul", "photographe"));
        PREDEFINED_AUTHORS.put("Kollar (photographe)", new Author("Kollar", "François", "photographe"));
        PREDEFINED_AUTHORS.put("Gerschel, Aaron (photographe)", new Author("Gerschel", "Aron", "photographe"));
        PREDEFINED_AUTHORS.put("Delbo (photographe)", new Author("Bouillot", "Pierre", "photographe"));
        PREDEFINED_AUTHORS.put("Harand, F. (photographe)", new Author("Harand", "François", "photographe"));
    }

    public ParisArchivesGlamScrap() {
        super("paris");
    }

    @Override
    public String getInstitution() {
        return "Archives de Paris";
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
        String[] tab = f.getExpectedNoticeCotes().get(i - 1).split(";");
        String cote = tab[0];
        Notice n = session.get(Notice.class, cote);
        if (n == null && fetch) {
            try {
                String path = String.format("%s/f/", tab[1]);
                Document desc = fetch(path);
                if (desc != null) {
                    n = ParisParser.parseNotice(desc, cote);
                    if (n != null) {
                        n.setUrl(new URL(getBaseUrl() + path));
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
        fonds.setTemplate("photograph");
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
        // Enrich notices with data available only through search... (sic)
        Document doc = fetch("tableau/?");
        enrichNotices(f, doc, 16, (n, a) -> {
            n.setAuthors(List.of(a));
            persist(n);
        });
        enrichNotices(f, doc, 18, (n, a) -> {
            String obs = n.getObservation();
            n.setObservation((obs == null ? "" : obs + ';') + "Ouvrage=" + a);
            persist(n);
        });
    }

    private static Map<Integer, String> extractMap(Document doc, int crit) {
        return doc.select("select#crit_" + crit).select("option[value]").stream()
                .collect(toMap(e -> Integer.parseInt(e.attr("value2")), e -> e.attr("value")));
    }

    private void enrichNotices(Fonds f, Document doc, int crit, BiConsumer<Notice, String> filler) throws IOException {
        for (Entry<Integer, String> e : extractMap(doc, crit).entrySet()) {
            BiConsumer<Document, String> parser = (d, v) -> extractCotes(d).stream().map(s -> s.split(";")[0])
                    .map(cote -> f.getNotices().stream().filter(n -> n.getCote().equals(cote)).findFirst()
                            .orElseThrow(() -> new IllegalStateException("No notice found for cote " + cote)))
                    .forEach(n -> filler.accept(n, v));
            Document results = fetch(String.format("tableau/?&crit1=%d&v_%d_1=%s&v_%d_2=%d", crit, crit, e.getValue(),
                    crit, e.getKey()));
            String decodedValue = URLDecoder.decode(e.getValue(), StandardCharsets.ISO_8859_1);
            int n = extractNumberOfResults(results);
            parser.accept(results, decodedValue);
            for (int i = PAGE; i < n; i += PAGE) {
                parser.accept(fetch(String.format("tableau/?&debut=%d", i)), decodedValue);
            }
        }
    }

    @Override
    public String getOtherFields(Notice n) {
        StringBuilder sb = new StringBuilder();
        String observation = n.getObservation();
        if (observation != null) {
            boolean first = true;
            for (String skv : observation.split(";")) {
                String[] kv = skv.split("=");
                if (!first) {
                    sb.append('\n');
                }
                first = false;
                sb.append("{{Information field|name=").append(kv[0]).append("|value=").append(kv[1]).append("}}");
            }
        }
        return sb.toString();
    }

    @Override
    public List<String> getCategories(Notice n) {
        List<String> result = new ArrayList<>();
        result.add(switch (n.getClassification()) {
        case "Architecture":
            yield "Architecture - Archives de Paris";
        case "Expositions internationales":
            yield "Expositions internationales - Archives de Paris";
        case "Fortifications":
            yield "Fortifications - Archives de Paris";
        case "Métro parisien":
            yield "Chantier du métro parisien - Archives de Paris";
        case "Mobilier urbain":
            yield "Mobilier urbain - Archives de Paris";
        case "Rues UPF":
            yield "Photographies de rues par l’Union Photographique Française - Archives de Paris";
        default:
            throw new IllegalArgumentException("Unexpected value: " + n.getClassification());
        });
        String observation = n.getObservation();
        if (observation != null) {
            for (String skv : observation.split(";")) {
                String[] kv = skv.split("=");
                result.add(switch (kv[0]) {
                case "Arrondissement":
                    yield String.format("Historical images of Paris %s arrondissement", kv[1]);
                case "Ouvrage":
                    yield switch (kv[1]) {
                    case "Lampadaire public":
                        yield "Historical images of street lights in Paris";
                    case "Toilettes publiques":
                        yield "Historical images of public toilets in Paris";
                    case "Locomotive à vapeur":
                        yield "Historical images of trains in Paris";
                    case "Kiosque":
                        yield "Historical images of kiosks in Paris";
                    case "Débit de boissons":
                        yield "Historical images of cafés in Paris";
                    case "Passerelle", "Pont":
                        yield "Historical images of bridges in Paris";
                    case "Immeuble":
                        yield "Historical images of buildings in Paris";
                    case "Immeuble à logements":
                        yield "Historical images of residential buildings in Paris";
                    case "Magasin de commerce", "Devanture de boutique":
                        yield "Historical images of commerce buildings in Paris";
                    case "Pavillon d'exposition":
                        yield "Historical images of cultural buildings in Paris";
                    case "Fontaine":
                        yield "Fountains in Paris";
                    case "Monument":
                        yield "Monuments in Paris";
                    case "Sculpture":
                        yield "Sculptures in Paris";
                    case "Statue":
                        yield "Statues in Paris";
                    case "Hôtel de Ville":
                        yield "Historical images of Hôtel de Ville de Paris";
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + kv[1]);
                    };
                case "Quartier":
                    yield null;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + kv[0]);
                });
            }
        }
        return result;
    }

    @Override
    public Map<String, Author> getPredefinedAuthors() {
        return PREDEFINED_AUTHORS;
    }
}
