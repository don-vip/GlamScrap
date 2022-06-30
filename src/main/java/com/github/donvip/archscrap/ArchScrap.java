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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

import com.github.donvip.archscrap.archives.paris.ParisArchScrap;
import com.github.donvip.archscrap.archives.toulouse.ToulouseArchScrap;
import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;

public abstract class ArchScrap implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    protected static final class Album {
        final int numberOfAlbums;
        final boolean allowEmptyAlbumNotices;

        public Album(int numberOfAlbums, boolean albumNotices) {
            this.numberOfAlbums = numberOfAlbums;
            this.allowEmptyAlbumNotices = albumNotices;
        }
    }

    protected static final class Range {
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

    // -- Hibernate
    private final StandardServiceRegistry registry;
    private final SessionFactory sessionFactory;
    protected final Session session;

    protected final Set<String> missedNotices = new TreeSet<>();

    protected ArchScrap(String city) {
        LOGGER.debug("Initializing Hibernate...");
        System.setProperty("city", city);
        registry = new StandardServiceRegistryBuilder()
                    .configure() // configures settings from hibernate.cfg.xml
                    .build();
        sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
        session = sessionFactory.openSession();
    }

    protected abstract Album getAlbum(String cote);

    protected abstract Range getAllowedGap(String cote);

    protected abstract String getBaseUrl();

    @Override
    public final void close() throws IOException {
        try {
            session.close();
        } finally {
            sessionFactory.close();
        }
    }

    public static void usage() {
        LOGGER.info("Usage: ArchScrap [paris|toulouse] scrap [<fonds>[,<fonds>]*] | check [<fonds>[,<fonds>]*] | download [<fonds>] | wikicode [<fonds>]| gui");
    }

    public static void main(String[] args) {
        if (args.length < 2) {
        usage();
            return;
        }
        try (ArchScrap app = buildApp(args[0])) {
            switch (args[1]) {
                case "scrap":
                    app.doScrap(args);
                    break;
                case "check":
                    app.doCheck(args);
                    break;
                case "download":
                    app.doDownload(args);
                    break;
                case "wikicode":
                    app.doWikicode(args);
                    break;
                case "gui":
                    app.launchGui();
                    break;
                default:
                    LOGGER.info("Unsupported operation: {}", args[1]);
            }
        } catch (IOException e) {
            LOGGER.catching(e);
        }
        LOGGER.info("Bye!");
    }

    private static ArchScrap buildApp(String city) {
        switch (city) {
            case "paris": return new ParisArchScrap();
            case "toulouse": return new ToulouseArchScrap();
            default: throw new IllegalArgumentException("Unsupported city: " + city);
        }
    }

    private void launchGui() {
        try {
            DatabaseManagerSwing.main(new String[] {
                    "--url", ((SessionImpl) session).getJdbcConnectionAccess().obtainConnection().getMetaData().getURL()});
        } catch (HibernateException | SQLException e) {
            LOGGER.catching(e);
        }
    }

    public final void doCheck(String[] args) throws IOException {
        if (args.length <= 2) {
            // Check all fonds
            for (Fonds f : fetchAllFonds()) {
                checkFonds(f);
            }
        } else {
            for (String cote : args[2].split(",")) {
                checkFonds(searchFonds(cote));
            }
        }
    }

    public final void doDownload(String[] args) throws IOException {
        // TODO download files
    }

    public final void doWikicode(String[] args) throws IOException {
        // TODO download files
    }

    private void checkFonds(Fonds f) {
        if (f != null) {
            int expected = f.getExpectedNotices();
            int got = f.getFetchedNotices(session);
            if (got >= expected) {
                LOGGER.info("{}: : OK", f.getCote());
            } else {
                List<Range> missing = searchNotices(f, expected);
                LOGGER.warn("{}: : KO (expected: {}; got: {}; missing: {})", f.getCote(), expected, got, missing);
            }
        }
    }

    private List<Range> searchNotices(Fonds f, int expected) {
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
        return missing;
    }

    public final void doScrap(String[] args) throws IOException {
        missedNotices.clear();
        if (args.length <= 2) {
            // Scrap all fonds
            for (Fonds f : fetchAllFonds()) {
                scrapFonds(f);
            }
        } else {
            for (String cote : args[2].split(",")) {
                scrapFonds(cote);
            }
        }
        if (!missedNotices.isEmpty()) {
            LOGGER.error("Missed {} notices: {}", missedNotices.size(), missedNotices);
        }
    }

    protected abstract List<Fonds> fetchAllFonds() throws IOException;

    private void scrapFonds(String cote) throws IOException {
        scrapFonds(searchFonds(cote));
    }

    private void scrapFonds(Fonds f) throws IOException {
        if (f != null) {
            // Do we have less notices in database than expected?
            int expected = f.getExpectedNotices();
            if (f.getFetchedNotices(session) < expected) {
                // Special handling of albums collections
                Album album = getAlbum(f.getCote());
                if (album != null) {
                    scrapAlbums(f, album);
                } else {
                    // base search
                    scrapFondsNotices(f, expected, 1, expected);
                    // extend search by number of missing notices
                    scrapFondsNotices(f, expected, expected + 1, expected + missedNotices.size());
                    // post scrapping
                    postScrapFonds(f);
                }
            }
        }
    }

    private void scrapAlbums(Fonds f, Album album) {
        for (int i = 1; i <= album.numberOfAlbums; i++) {
            if (searchNotice(f, i) != null || album.allowEmptyAlbumNotices) {
                scrapAlbum(f, i, 1);
            }
        }
    }

    private void scrapFondsNotices(Fonds f, int expected, int start, int end) {
        Range allowedGap = getAllowedGap(f.getCote());
        for (int i = start; i <= end && f.getFetchedNotices(session) < expected; i++) {
            if ((allowedGap == null || !allowedGap.contains(i)) && (searchNotice(f, i) == null && searchNotice(f, i, 1, true) != null)) {
                // search like albums, some fonds are inconsistent
                scrapAlbum(f, i, 2);
            }
        }
    }

    private void scrapAlbum(Fonds f, int i, int start) {
        for (int j = start; searchNotice(f, i, j, true) != null; j++) {
            LOGGER.trace(j);
        }
    }

    protected abstract void postScrapFonds(Fonds f) throws IOException;

    protected final Fonds searchFonds(String cote) throws IOException {
        // Check to be sure, we don't have it in database
        Fonds f = session.get(Fonds.class, cote);
        if (f == null) {
            LOGGER.info("New fonds! {}", cote);
            f = createNewFonds(cote);
            if (f != null) {
                persist(f);
            } else {
                LOGGER.warn("Unable to fetch fonds description for {}", cote);
            }
        }
        return f;
    }

    protected final void persist(Object o) {
        session.beginTransaction();
        session.persist(o);
        session.getTransaction().commit();
    }

    protected final Notice searchNotice(Fonds f, int i) {
        return searchNotice(f, i, -1, true);
    }

    protected abstract Notice searchNotice(Fonds f, int i, int j, boolean fetch);

    protected abstract Fonds createNewFonds(String cote) throws IOException;

    protected final Document fetch(String doc) throws IOException {
        LOGGER.info("Fetching {}{}", getBaseUrl(), doc);
        return Jsoup.connect(getBaseUrl() + doc).get();
    }
}
