package searchengine.services.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.JsoupConnection;
import searchengine.model.Page;
import searchengine.model.WebSite;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Slf4j
@RequiredArgsConstructor
public class HTMLParser {

    private final JsoupConnection jsoupConnection;
    private static String regexLink = "/.+[^#]";
    private static String regexpPicture = "(?i)/.+[.]((png)|(svg)|(jpg)|(jpeg)|(pdf))";
    private String currentMainSite;

    public HTMLParser(String currentMainSite, JsoupConnection jsoupConnection) {
        this.currentMainSite = currentMainSite;
        this.jsoupConnection = jsoupConnection;
    }

    public ConcurrentHashMap<String, Page> getSubLinks(String url, WebSite mainWebSite) throws InterruptedException {
        ConcurrentHashMap<String, Page> subLinks = new ConcurrentHashMap<>();
        Thread.sleep(150);

        try {
            Document parentHtmlCode = getHtmlCode(url);
            Elements elements = parentHtmlCode.select("a");
            for (Element element : elements) {
                if (element.hasAttr("href") && element.attribute("href").getValue().matches(regexLink)
                && !element.attribute("href").getValue().matches(regexpPicture)) {
                    String childAbsUrl = element.absUrl("href");
                    String childRelUrl = element.attr("href");
                    Document childHtmlCode = getHtmlCode(childAbsUrl);
                    String contentHtml = childHtmlCode.head() + String.valueOf(childHtmlCode.body());

                    Page page = new Page(mainWebSite,
                                        childRelUrl,
                                        childHtmlCode.connection().response().statusCode(),
                                        contentHtml);

                    subLinks.put(childAbsUrl, page);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return subLinks;
    }

    public Document getHtmlCode(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(jsoupConnection.getUserAgent())
                .referrer(jsoupConnection.getReferrer())
                .timeout(30 * 1000)
                .ignoreContentType(true)
                .get();
    }
}
