package com.github.donvip.glamscrap.institutions.paris;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.github.donvip.glamscrap.Parser;
import com.github.donvip.glamscrap.domain.Notice;

class ParisParser extends Parser {

    private static final Logger LOGGER = LogManager.getLogger();

    public static Notice parseNotice(Document desc, String cote) throws MalformedURLException {
        Element div = desc.select("div#facettes_conteneur_detail").first();
        if (div != null) {
            final Notice n = new Notice(cote);
            n.setTitle(div.select("h2").text());
            n.setFilename(parseTableRow(div, 2) + ".jpg"); // Nom de l'image
            n.setClassification(parseTableRow(div, 3)); // Collection
            n.setDescription(parseTableRow(div, 6));
            extractDate(parseTableRow(div, 7), n);
            StringBuilder sb = new StringBuilder();
            String arrondissement = parseTableRow(div, 8);
            if (!arrondissement.isEmpty()) {
                sb.append("Arrondissement=").append(arrondissement);
            }
            String quartier = parseTableRow(div, 9);
            if (!quartier.isEmpty()) {
                if (!arrondissement.isEmpty()) {
                    sb.append(';');
                }
                sb.append("Quartier=").append(quartier);
            }
            String observation = sb.toString();
            if (!observation.isEmpty()) {
                n.setObservation(observation);
            }
            n.setDownloadUrl(new URL(div.select("img.vignette").attr("src").replace("/vign_", "/ori_")));
            return n;
        } else {
            LOGGER.warn("Couldn't parse notice for: {}", cote);
            return null;
        }
    }

    private static String parseTableRow(Element div, int i) {
        return div.select("div.champ_formulaire.detail" + i).select("span.post_label").text();
    }
}
