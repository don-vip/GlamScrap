package com.github.donvip.glamscrap.institutions.toulouse;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.donvip.glamscrap.Gwt.GwtResponse;
import com.github.donvip.glamscrap.Parser;
import com.github.donvip.glamscrap.domain.Fonds;

public class ToulousePhotosParser extends Parser {

    private static final Logger LOGGER = LogManager.getLogger();

    public static Fonds parseFonds(GwtResponse basket, List<GwtResponse> medias, String cote) {
        if (basket != null && medias != null) {
            final Fonds f = new Fonds(cote);
            // 0. Search for title
            //int idx = basket.indexOf("\"com.keepeek.kpk360.shared.transport.UserLightTransport/");
            //idx = basket.lastIndexOf("\"", idx - 3);
            //f.setTitle(basket.substring(idx + 1, basket.indexOf("\"", idx + 1)));
            // 1. Search for expected number of notices (information always displayed)
            try {
                f.setExpectedNotices(Integer.valueOf("617")); // TODO
            } catch (RuntimeException e) {
                LOGGER.warn("Unable to fetch number of notices for {}", cote);
            }
            return f;
        } else {
            LOGGER.warn("Couldn't parse fonds for: {}", cote);
            return null;
        }
    }
}
