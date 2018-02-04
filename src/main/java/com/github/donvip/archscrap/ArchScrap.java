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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.internal.SessionImpl;
import org.hsqldb.util.DatabaseManagerSwing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;

public class ArchScrap implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final String BASE_URL = "http://basededonnees.archives.toulouse.fr/4DCGi/";

    private static class Album {
        final int numberOfAlbums;
        final boolean allowEmptyAlbumNotices;

        public Album(int numberOfAlbums, boolean albumNotices) {
            this.numberOfAlbums = numberOfAlbums;
            this.allowEmptyAlbumNotices = albumNotices;
        }
    }

    private static class Range {
        final int min;
        int max;

        public Range(int val) {
            this(val, val);
        }

        public Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return min != max ? min + "-" + max : Integer.toString(min);
        }

        public boolean contains(int i) {
            return min <= i && i <= max;
        }
    }

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

    // -- Hibernate
    private final StandardServiceRegistry registry;
    private final SessionFactory sessionFactory;
    private final Session session;

    private final Set<String> missedNotices = new TreeSet<>();

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
                case "check":
                    app.doCheck(args);
                    break;
                case "gui":
                    app.launchGui(args);
                    break;
            }
        } catch (IOException e) {
            LOGGER.catching(e);
        }
        LOGGER.info("Bye!");
    }

    private void launchGui(String[] args) {
        try {
            DatabaseManagerSwing.main(new String[] {
                    "--url", ((SessionImpl) session).connection().getMetaData().getURL()});
        } catch (HibernateException | SQLException e) {
            LOGGER.catching(e);
        }
    }

    public void doCheck(String[] args) throws IOException {
        if (args.length <= 1) {
            // Check all fonds
            for (Fonds f : fetchAllFonds()) {
                checkFonds(f);
            }
        } else {
            for (String cote : args[1].split(",")) {
                checkFonds(searchFonds(cote));
            }
        }
    }

    private void checkFonds(Fonds f) {
        if (f != null) {
            int expected = f.getExpectedNotices();
            int got = f.getFetchedNotices(session);
            if (got >= expected) {
                LOGGER.info("{}: : OK", f.getCote());
            } else {
                LinkedList<Range> missing = new LinkedList<>();
                for (int i = 1; i <= expected; i++) {
                    if (searchNotice(f, i, -1, false) == null && searchNotice(f, i, 1, false) == null) {
                        if (!missing.isEmpty() && missing.getLast().max == i-1) {
                            missing.getLast().max = i;
                        } else {
                            missing.add(new Range(i));
                        }
                    }
                }
                LOGGER.warn("{}: : KO (expected: {}; got: {}; missing: {})", f.getCote(), expected, got, missing);
            }
        }
    }

    public void doScrap(String[] args) throws IOException {
        missedNotices.clear();
        if (args.length <= 1) {
            // Scrap all fonds
            for (Fonds f : fetchAllFonds()) {
                scrapFonds(f);
            }
        } else {
            for (String cote : args[1].split(",")) {
                scrapFonds(cote);
            }
        }
        if (!missedNotices.isEmpty()) {
            LOGGER.error("Missed {} notices: {}", missedNotices.size(), missedNotices);
        }
    }

    private List<Fonds> fetchAllFonds() throws IOException {
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

    private void scrapFonds(String cote) throws IOException {
        scrapFonds(searchFonds(cote));
    }

    private void scrapFonds(Fonds f) throws IOException {
        // Do we have less notices in database than expected?
        int expected = f.getExpectedNotices();
        if (f != null && f.getFetchedNotices(session) < expected) {
            // Special handling of albums collections
            if (ALBUMS.containsKey(f.getCote())) {
                Album album = ALBUMS.get(f.getCote());
                for (int i = 1; i <= album.numberOfAlbums; i++) {
                    if (searchNotice(f, i) != null || album.allowEmptyAlbumNotices) {
                        scrapAlbum(f, i, 1);
                    }
                }
            } else {
                // base search
                scrapFondsNotices(f, expected, 1, expected);
                // extend search by number of missing notices
                scrapFondsNotices(f, expected, expected + 1, expected + missedNotices.size());
            }
        }
    }

    private void scrapFondsNotices(Fonds f, int expected, int start, int end) {
        Range allowedGap = ALLOWED_GAPS.get(f.getCote());
        for (int i = start; i <= end && f.getFetchedNotices(session) < expected; i++) {
            if (allowedGap == null || !allowedGap.contains(i)) {
                if (searchNotice(f, i) == null) {
                    // search like albums, some fonds are inconsistent
                    if (searchNotice(f, i, 1, true) != null) {
                        scrapAlbum(f, i, 2);
                    }
                }
            }
        }
    }

    private void scrapAlbum(Fonds f, int i, int start) {
        for (int j = start; searchNotice(f, i, j, true) != null; j++) {
            LOGGER.trace(j);
        }
    }

    private Fonds searchFonds(String cote) throws IOException {
        // Check to be sure, we don't have it in database
        Fonds f = session.get(Fonds.class, cote);
        if (f == null) {
            f = createNewFonds(cote);
            if (f != null) {
                session.beginTransaction();
                session.save(f);
                session.getTransaction().commit();
            }
        }
        return f;
    }

    private Notice searchNotice(Fonds f, int i) {
        return searchNotice(f, i, -1, true);
    }

    private Notice searchNotice(Fonds f, int i, int j, boolean fetch) {
        // Check to be sure, we don't have it in database
        StringBuilder sb = new StringBuilder(f.getCote()).append(i);
        if (j > -1) {
            sb.append('/').append(j);
        }
        String cote = sb.toString();
        Notice n = session.get(Notice.class, cote);
        if (n == null && fetch) {
            try {
                Document desc = fetch(String.format("Web_VoirLaNotice/34_01/%s/ILUMP21411", cote.replace("/", "xzx")));
                if (desc != null) {
                    n = Parser.parseNotice(desc, cote);
                    if (n != null) {
                        session.beginTransaction();
                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.save(n);
                        session.save(f);
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

    private Fonds createNewFonds(String cote) throws IOException {
        LOGGER.info("New fonds! {}", cote);
        Document doc = fetch(String.format("Web_FondsCClass%s/ILUMP31929", cote));
        if (doc != null) {
            return Parser.parseFonds(doc, cote);
        } else {
            LOGGER.warn("Unable to fetch fonds description for {}", cote);
            return null;
        }
    }

    private static Document fetch(String doc) throws IOException {
        LOGGER.info("Fetching {}", BASE_URL + doc);
        return Jsoup.connect(BASE_URL + doc).get();
    }
}
