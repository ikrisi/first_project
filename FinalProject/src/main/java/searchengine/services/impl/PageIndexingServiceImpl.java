package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupConnection;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.BadResponse;
import searchengine.dto.OkResponse;
import searchengine.model.*;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.LemmaService;
import searchengine.services.PageIndexingService;
import searchengine.services.utils.HTMLParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageIndexingServiceImpl implements PageIndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexesRepository indexesRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;
    private final JsoupConnection jsoupConnection;
    private final LemmaService lemmaService;

    @Override
    public ResponseEntity startIndexing(String url) throws IOException {
        Site findSite = new Site();
        for (Site site : sitesList.getSites()) {
            if (url.contains(site.getUrl())) {
                findSite.setName(site.getName());
                findSite.setUrl(site.getUrl());
            }
        }
        if (findSite.getName() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BadResponse(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        }

        try {
            indexPage(url, findSite);
        } catch (IOException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BadResponse(false,
                    "Страницы не существует"));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new OkResponse());
    }


    @Override
    public void indexPage(String url, Site site) throws IOException {
        String mainSiteUrl = site.getUrl();
        String mainSiteName = site.getName();
        WebSite mainSite = saveAndGetMainSite(mainSiteUrl, mainSiteName);

        Document htmlCode = getHtmlCode(url);
        String htmlContent = getHtmlContent(htmlCode);
        String relUrl = getRelUrl(url, mainSiteUrl);
        Page page = pageRepository.findByPathAndSiteId(relUrl, mainSite.getId());
        if (page != null) {
            clearData(page);
        }
        Page updatePage = saveAndGetPage(page, relUrl, mainSite, htmlCode, htmlContent);

        ConcurrentHashMap<String, Integer> mapLemmas = lemmaService.getLemmas(updatePage.getContent());
        for (Map.Entry<String, Integer> element : mapLemmas.entrySet()) {
            String lemma = element.getKey();
            Integer count = element.getValue();
            Lemma lemmaInDB = saveLemma(lemma, updatePage);
            saveIndex(count, updatePage, lemmaInDB);
        }
    }

    public String getRelUrl(String url, String mainSiteUrl) {
        return url.substring(mainSiteUrl.length());
    }

    public Document getHtmlCode(String url) throws IOException {
        HTMLParser htmlParser = new HTMLParser(url, jsoupConnection);
        return htmlParser.getHtmlCode(url);
    }
    public String getHtmlContent(Document html) {
        return html.head() + String.valueOf(html.body());
    }

    public WebSite saveAndGetMainSite(String mainSiteUrl, String mainSiteName) {
        WebSite webSite = siteRepository.findByUrl(mainSiteUrl);
        if (webSite == null) {
            webSite = new WebSite(StatusType.INDEXING, LocalDateTime.now(), null, mainSiteUrl, mainSiteName);
            siteRepository.save(webSite);
        }
        return webSite;
    }

    public Page saveAndGetPage(Page page, String relUrl, WebSite mainSite, Document html, String htmlContent) {
        page = new Page(mainSite, relUrl, html.connection().response().statusCode(), htmlContent);
        pageRepository.save(page);
        return page;
    }

    public void clearData(Page page) {
        updateLemma(page);
        deleteAllIndexes(page);
        deletePage(page);
    }

    public Lemma saveLemma(String lemma, Page page) {
        Lemma lemmaInDB = lemmaRepository.getLemmaInSite(lemma, page.getWebSiteId().getId());
        if (lemmaInDB == null) {
            lemmaInDB = new Lemma(page.getWebSiteId(), lemma, 1);
        } else {
            lemmaInDB.setFrequency(lemmaInDB.getFrequency() + 1);
        }
        lemmaRepository.save(lemmaInDB);
        return lemmaInDB;
    }

    public void saveIndex(float count, Page page, Lemma lemma) {
        Indexes indexes = indexesRepository.findIndex(page.getId(), lemma.getId());
        if (indexes == null) {
            indexes = new Indexes(page, lemma, count);
        } else {
            indexes.setQuantity(indexes.getQuantity() + count);
        }
        indexesRepository.save(indexes);
    }

    public void updateLemma(Page page) {
        for (Indexes indexes : getIndexesByPage(page)) {
            Optional<Lemma> findLemma = lemmaRepository.findById(indexes.getLemma().getId());
            findLemma.ifPresent(lemma -> {
                lemma.setFrequency(lemma.getFrequency() - 1);
                lemmaRepository.saveAndFlush(lemma);
            });
        }
    }

    public List<Indexes> getIndexesByPage(Page page) {
        return indexesRepository.findAllByPageId(page.getId());
    }

    public void deleteAllIndexes(Page page) {
        indexesRepository.deleteAllByPageId(page.getId());
    }

    public void deletePage(Page page) {
        pageRepository.deleteById(page.getId());
    }
}
