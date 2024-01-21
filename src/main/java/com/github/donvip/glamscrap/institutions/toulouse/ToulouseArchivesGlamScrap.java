package com.github.donvip.glamscrap.institutions.toulouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.donvip.glamscrap.GlamScrap;
import com.github.donvip.glamscrap.domain.Fonds;
import com.github.donvip.glamscrap.domain.Notice;
import com.github.donvip.glamscrap.wikidata.Author;

public class ToulouseArchivesGlamScrap extends GlamScrap {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BASE_URL = "http://basededonnees.archives.toulouse.fr/4DCGi/";

    private static final Map<String, Album> ALBUMS = new HashMap<>();
    static {
        ALBUMS.put("16Fi", new Album(81, false));
        ALBUMS.put("38Fi", new Album(39, false));
        ALBUMS.put("39Fi", new Album(11, false));
        ALBUMS.put("1Num", new Album(15, true));
    }

    private static final Map<String, Range> ALLOWED_GAPS = new HashMap<>();
    static {
        ALLOWED_GAPS.put("24Fi", new Range(215, 99));
    }

    public ToulouseArchivesGlamScrap() {
        super("toulouse_archives");
    }

    @Override
    public String getInstitution() {
        return "Archives municipales de Toulouse";
    }

    @Override
    protected Album getAlbum(String cote) {
        return ALBUMS.get(cote);
    }

    @Override
    protected String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    protected Range getAllowedGap(String cote) {
        return ALLOWED_GAPS.get(cote);
    }

    @Override
    protected List<Fonds> fetchAllFonds() throws IOException {
        LOGGER.info("Fetching all image fonds from archives website...");
        Element plan = fetch("web_fondsmcadre/34/ILUMP458").select("#planclassement").first();
        if (plan != null) {
            List<Fonds> allFonds = new ArrayList<>();
            Elements links = plan.select("p > a");
            LOGGER.info("Found {} fonds", links.size());
            for (Element e : links) {
                allFonds.add(extractFonds(e));
            }
            return allFonds;
        } else {
            LOGGER.error("Unable to fetch image fonds from archives website");
            return Collections.emptyList();
        }
    }

    private Fonds extractFonds(Element fonds) throws IOException {
        String fondsText = fonds.text();
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+) - (.+)").matcher(fondsText);
        if (m.matches()) {
            return searchFonds(m.group(1));
        } else {
            LOGGER.warn("Unable to parse fonds {}", fondsText);
            return null;
        }
    }

    @Override
    protected Notice searchNotice(Fonds f, int i, int j, boolean fetch) {
        // Check to be sure, we don't have it in database
        StringBuilder sb = new StringBuilder(f.getExpectedNoticeCotes().get(i));
        if (j > -1) {
            sb.append('/').append(j);
        }
        String cote = sb.toString();
        Notice n = session.get(Notice.class, cote);
        if (n == null && fetch) {
            try {
                Document desc = fetch(String.format("Web_VoirLaNotice/34_01/%s/ILUMP21411", cote.replace("/", "xzx")));
                if (desc != null) {
                    n = ToulouseArchivesParser.parseNotice(desc, cote);
                    if (n != null) {
                        session.beginTransaction();
                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.persist(n);
                        session.persist(f);
                        session.getTransaction().commit();
                    } else if (!cote.contains("/")) {
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
        Document doc = fetch(String.format("Web_FondsCClass%s/ILUMP31929", cote));
        return doc != null ? ToulouseArchivesParser.parseFonds(doc, cote) : null;
    }

    @Override
    protected void postScrapFonds(Fonds f) {
        // Do nothing
    }

    @Override
    public String getOtherFields(Notice n) {
        return "";
    }

    @Override
    public List<String> getCategories(Notice n) {
        return List.of();
    }

    @Override
    public Map<String, Author> getPredefinedAuthors() {
        return Map.of();
    }
}
