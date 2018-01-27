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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.github.donvip.archscrap.domain.Fonds;
import com.github.donvip.archscrap.domain.Notice;

public class ArchScrap implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();
    
    private static final String BASE_URL = "http://basededonnees.archives.toulouse.fr/4DCGi/";

    // -- Hibernate
    private final StandardServiceRegistry registry;
    private final SessionFactory sessionFactory;
    private final Session session;

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
            }
        } catch (IOException e) {
            LOGGER.catching(e);
        }
        LOGGER.info("Bye!");
    }

    public void doScrap(String[] args) throws IOException {
        LOGGER.info("Fetching all image fonds from archives website...");
        Element plan = fetch("web_fondsmcadre/34/ILUMP458").select("#planclassement").first();
        if (plan != null) {
            Elements allFonds = plan.select("p > a");
            LOGGER.info("Found {} fonds", allFonds.size());
            for (Element fonds : allFonds) {
                handleFonds(fonds);
            }
        } else {
            LOGGER.error("Unable to fetch image fonds from archives website");
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

    private void handleFonds(Element fonds) throws IOException {
        String fondsText = fonds.text();
        Matcher m = Pattern.compile("(\\d+[A-Z][a-z]+) - (.+)").matcher(fondsText);
        if (m.matches()) {
            String cote = m.group(1);
            Fonds f = searchFonds(cote);
            if (f.getNotices().size() < f.getExpectedNotices()) {
                // We have less notices in database than expected
                // 1. Try to fetch missing notices
                for (int i : f.getMissingNotices(session)) {
                    searchNotice(f, i);
                }
                // 2. Try to search new ones
                int last = f.getNotices().isEmpty() ? 0 : f.getNotices().get(f.getNotices().size() - 1).getId();  
                for (int i = last + 1; i < f.getExpectedNotices(); i++) {
                    searchNotice(f, i);
                }
            }
        } else {
            LOGGER.warn("Unable to parse fonds {}", fondsText);
        }
    }

    private Fonds searchFonds(String cote) throws IOException {
        // Check to be sure, we don't have it in database
        session.beginTransaction();
        Fonds f = session.get(Fonds.class, cote);
        session.getTransaction().commit();
        if (f == null) {
            f = createNewFonds(cote);
            session.beginTransaction();
            session.save(f);
            session.getTransaction().commit();
        }
        return f;
    }

    private Notice searchNotice(Fonds f, int i) {
        // Check to be sure, we don't have it in database
        String cote = f.getCote()+i;
        session.beginTransaction();
        Notice n = session.get(Notice.class, cote);
        session.getTransaction().commit();
        if (n == null) {
            try {
                Document desc = fetch(String.format("Web_VoirLaNotice/34_01/%s/ILUMP21411", cote));
                if (desc != null) {
                    n = Parser.parseNotice(desc, cote);
                    if (n != null) {
                        session.beginTransaction();
                        f.getNotices().add(n);
                        n.setFonds(f);
                        session.save(n);
                        session.save(f);
                        session.getTransaction().commit();
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
