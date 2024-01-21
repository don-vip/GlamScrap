package com.github.donvip.glamscrap.institutions.toulouse;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.donvip.glamscrap.GlamScrap;
import com.github.donvip.glamscrap.Gwt;
import com.github.donvip.glamscrap.Gwt.GwtResponse;
import com.github.donvip.glamscrap.Gwt.TypedValue;
import com.github.donvip.glamscrap.domain.Fonds;
import com.github.donvip.glamscrap.domain.Notice;
import com.github.donvip.glamscrap.wikidata.Author;

// https://phototheque.toulouse.fr/api/doc/
public class ToulousePhotothequeGlamScrap extends GlamScrap {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final String BASE_URL = "https://phototheque.toulouse.fr/";

    private static final Map<String, Album> ALBUMS = new HashMap<>();
    static {
        ALBUMS.put("bBxZc08Cb", new Album(1, false));
    }

    public ToulousePhotothequeGlamScrap() {
        super("toulouse_photos");
    }

    @Override
    protected Album getAlbum(String cote) {
        return ALBUMS.get(cote);
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
    public String getInstitution() {
        return "Ville de Toulouse";
    }

    @Override
    protected List<Fonds> fetchAllFonds() throws IOException {
        return List.of();
    }

    @Override
    protected void postScrapFonds(Fonds f) throws IOException {
        // Do nothing
    }

    @Override
    protected Notice searchNotice(Fonds f, int i, int j, boolean fetch) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected Fonds createNewFonds(String cote) throws IOException {
        try {
            GwtResponse basket = Gwt.decodeResponse(fetchPost(shareGetBasketAction(cote)));
            LOGGER.info(basket);
            List<GwtResponse> medias = new ArrayList<>();
            boolean finished = false;
            int index = 0;
            while (!finished) {
                GwtResponse media = Gwt.decodeResponse(fetchPost(shareGetBasketMediasListAction(cote, index)));
                LOGGER.info(media);
                medias.add(media);
                index += 60; // FIXME
                finished = index > 617;//FIXME
            }
            return ToulousePhotosParser.parseFonds(basket, medias, cote);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ScriptException e) {
            throw new IOException(e);
        }
    }

    protected static HttpRequest shareGetBasketAction(String sharedBasketIdentifier) {
        return keepeekAction("ShareGetBasketAction", "com.keepeek.kpk360.shared.dispatch.share.basket.ShareGetBasketAction/3056539714", List.of(
                new TypedValue(Gwt.STRING, null, null, -1, List.of()),
                shareParameters(sharedBasketIdentifier)));
    }

    protected static HttpRequest shareGetBasketMediasListAction(String sharedBasketIdentifier, int index) {
        return keepeekAction("ShareGetBasketMediasListAction", "com.keepeek.kpk360.shared.dispatch.share.basket.ShareGetBasketMediasListAction/840037237", List.of(
                new TypedValue(null, Gwt.INTEGER, null, -1, List.of()),
                new TypedValue(null, Gwt.INTEGER, null, index, List.of()),
                new TypedValue(null, Gwt.BOOLEAN, null, 0, List.of()),
                new TypedValue(Gwt.STRING, null, "DESC", -1, List.of()),
                new TypedValue(Gwt.STRING, null, "", -1, List.of()),
                new TypedValue(Gwt.STRING, null, null, -1, List.of()),
                shareParameters(sharedBasketIdentifier)));
    }

    protected static HttpRequest keepeekAction(String urlAction, String actionQualifiedClass, List<TypedValue> arguments) {
        return Gwt.request(BASE_URL + "dispatch/" + urlAction,
                "https://phototheque.toulouse.fr/keepeek360/", "2ED7D09E9D2EBA03EA03E51C1582B244", "AE4175F0BB42AE91733577263A0417ED",
                "com.gwtplatform.dispatch.rpc.shared.DispatchService", "execute", List.of(
                        new TypedValue(Gwt.STRING, null, null, -1, List.of()),
                        new TypedValue("com.gwtplatform.dispatch.rpc.shared.Action", actionQualifiedClass, null, -1, arguments)));
    }

    private static TypedValue shareParameters(String sharedBasketIdentifier) {
        return new TypedValue(null, "com.keepeek.kpk360.shared.dispatch.common.share.ShareParameters/511658636", null, -1, List.of(
                new TypedValue(Gwt.STRING, null, "fr", -1, List.of()),
                new TypedValue(Gwt.STRING, null, sharedBasketIdentifier, -1, List.of()),
                new TypedValue(Gwt.STRING, null, "https://phototheque.toulouse.fr/", -1, List.of())));
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
