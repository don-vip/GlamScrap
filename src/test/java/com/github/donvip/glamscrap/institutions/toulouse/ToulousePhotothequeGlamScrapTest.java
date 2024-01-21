package com.github.donvip.glamscrap.institutions.toulouse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.github.donvip.glamscrap.HttpRequestBodyTestUtility;

class ToulousePhotothequeGlamScrapTest {

    @Test
    void testShareGetBasketAction() {
        assertEquals("7|0|11|https://phototheque.toulouse.fr/keepeek360/|2ED7D09E9D2EBA03EA03E51C1582B244|com.gwtplatform.dispatch.rpc.shared.DispatchService|execute|java.lang.String/2004016611|com.gwtplatform.dispatch.rpc.shared.Action|com.keepeek.kpk360.shared.dispatch.share.basket.ShareGetBasketAction/3056539714|com.keepeek.kpk360.shared.dispatch.common.share.ShareParameters/511658636|fr|bBxZc08Cb|https://phototheque.toulouse.fr/|1|2|3|4|2|5|6|0|7|0|8|9|10|11|",
                HttpRequestBodyTestUtility.extractBody(ToulousePhotothequeGlamScrap.shareGetBasketAction("bBxZc08Cb")));
    }

    @Test
    void testShareGetBasketMediasListAction() {
        assertEquals("7|0|15|https://phototheque.toulouse.fr/keepeek360/|2ED7D09E9D2EBA03EA03E51C1582B244|com.gwtplatform.dispatch.rpc.shared.DispatchService|execute|java.lang.String/2004016611|com.gwtplatform.dispatch.rpc.shared.Action|com.keepeek.kpk360.shared.dispatch.share.basket.ShareGetBasketMediasListAction/840037237|java.lang.Integer/3438268394|java.lang.Boolean/476441737|DESC||com.keepeek.kpk360.shared.dispatch.common.share.ShareParameters/511658636|fr|bBxZc08Cb|https://phototheque.toulouse.fr/|1|2|3|4|2|5|6|0|7|8|-1|8|60|9|0|10|11|0|12|13|14|15|",
                HttpRequestBodyTestUtility.extractBody(ToulousePhotothequeGlamScrap.shareGetBasketMediasListAction("bBxZc08Cb", 60)));
    }
}
