package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.logging.Logger;

public final class JsoupUtils {

    private static final Logger LOG = Logger.getLogger(JsoupUtils.class.getName());
    private static final String NEWS_BASE_URL = "https://www.fct.unl.pt/noticias";
    private static final String NEWS_CONTAINER_CLASS = "div.view.view-noticias.view-id-noticias.view-display-id-page_1.view-dom-id-1";
    private final Document doc;

    private JsoupUtils() {
        try {
            doc = Jsoup.connect(NEWS_BASE_URL).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String scrape() {
        return new JsoupUtils().getNewsContainer();
    }

    private String getNewsContainer() {
        try {
            final Elements newsContainer = doc.select(NEWS_CONTAINER_CLASS);
            return newsContainer.get(0).html();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}