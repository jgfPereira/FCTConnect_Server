package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class JsoupUtils {

    private static final String FCT_BASE_URL = "https://www.fct.unl.pt";
    private static final String NEWS_BASE_URL = FCT_BASE_URL + "/noticias?page=%d";
    private static final String NEWS_CONTAINER_CLASS = "div.view.view-noticias.view-id-noticias.view-display-id-page_1.view-dom-id-1";
    private static final String SINGLE_NEWS_CONTAINER_CLASS = "div.views-row.views-row-%d";
    private static final String NEWS_BODY_CLASS = "div.views-field-title";
    private static final String NEWS_SPAN_CLASS = "span.field-content";
    private static final String LINK_TAG = "a";
    private static final String HREF_ATTR = "href";
    private static final String NEWS_IMAGE_CLASS = "div.noticia-imagem";
    private static final String IMAGE_TAG = "img";
    private static final String SRC_ATTR = "src";
    private static final String NEWS_DESCRIPTION_CLASS = "div.views-field-field-resumo-value";
    private static final String NEWS_DATE_CLASS = "div.views-field-created";
    private static final String PARAGRAPH_OR_DIV = "p, div";
    private static final String LAST_PAGE_CLASS = "li.pager-last.last";
    private static final String PAGE_OF_LINK_SPLIT_REGEX = "=";
    private static final String NUM_OF_NEWS_FOR_PAGE_CLASS = "div.noticia-imagem";
    private static final int FIRST_PAGE = 0;
    private final Document doc;
    private final int page;
    private final int numOfNewsForPage;

    private JsoupUtils(int page) throws IOException {
        final int numOfPages = computeNumOfPages();
        if (page < 0 || page >= numOfPages) {
            throw new IllegalArgumentException("Invalid page number");
        }
        this.page = page;
        doc = Jsoup.connect(String.format(NEWS_BASE_URL, page)).get();
        numOfNewsForPage = computeNumOfNewsForPage();
    }

    private static int computeNumOfPages() throws IOException {
        final Document document = Jsoup.connect(String.format(NEWS_BASE_URL, FIRST_PAGE)).get();
        final String linkLastPage = document.select(LAST_PAGE_CLASS).get(0).select(LINK_TAG).get(0).attr(HREF_ATTR);
        return Integer.parseInt(linkLastPage.split(PAGE_OF_LINK_SPLIT_REGEX)[1]) + 1;
    }

    public static List<NewsData> scrape(int page) throws IOException {
        final JsoupUtils jsoup = new JsoupUtils(page);
        final Element newsContainer = jsoup.getNewsContainer();
        return jsoup.parseAllNews(newsContainer);
    }

    public static List<NewsData> scrape() throws IOException {
        final List<NewsData> allNewsOfAllPages = new ArrayList<>();
        final int numOfPages = computeNumOfPages();
        for (int i = 0; i < numOfPages; i++) {
            allNewsOfAllPages.addAll(scrape(i));
        }
        return allNewsOfAllPages;
    }

    private int computeNumOfNewsForPage() {
        return doc.select(NUM_OF_NEWS_FOR_PAGE_CLASS).size();
    }

    private Element getNewsContainer() {
        return doc.select(NEWS_CONTAINER_CLASS).get(0);
    }

    private Element getSingleNews(Element newsContainer, int newsNum) {
        return newsContainer.select(String.format(SINGLE_NEWS_CONTAINER_CLASS, newsNum)).get(0);
    }

    private NewsData parseSingleNews(Element singleNews) {
        final Element newsBody = singleNews.select(NEWS_BODY_CLASS).get(0).select(NEWS_SPAN_CLASS).get(0).select(LINK_TAG).get(0);
        final String newsLink = FCT_BASE_URL + newsBody.attr(HREF_ATTR);
        final String newsTitle = newsBody.text();
        final Element newsImage = singleNews.select(NEWS_IMAGE_CLASS).get(0).select(NEWS_SPAN_CLASS).get(0).select(LINK_TAG).get(0);
        final String newsImageLink = newsImage.select(IMAGE_TAG).get(0).attr(SRC_ATTR);
        final Element newsDescriptionElement = singleNews.select(NEWS_DESCRIPTION_CLASS).get(0).select(NEWS_SPAN_CLASS).get(0).select(PARAGRAPH_OR_DIV).get(0);
        final String newsDescription = newsDescriptionElement.text();
        final Element newsDateElement = singleNews.select(NEWS_DATE_CLASS).get(0).select(NEWS_SPAN_CLASS).get(0);
        final String newsDate = newsDateElement.text();
        return new NewsData(newsTitle, newsLink, newsImageLink, newsDescription, newsDate);
    }

    private List<NewsData> parseAllNews(Element newsContainer) {
        final List<NewsData> allNews = new ArrayList<>();
        for (int i = 1; i <= numOfNewsForPage; i++) {
            allNews.add(parseSingleNews(getSingleNews(newsContainer, i)));
        }
        return allNews;
    }

    @Override
    public String toString() {
        return "JsoupUtils{" +
                "doc=" + doc +
                ", page=" + page +
                ", numOfNewsForPage=" + numOfNewsForPage +
                '}';
    }
}