package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import searchengine.config.JsoupConnection;
import searchengine.model.Indexes;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.WebSite;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.utils.HTMLParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MapWebSite extends RecursiveAction {

    private static CopyOnWriteArrayList<String> pagesList = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<String> linkChildren = new CopyOnWriteArrayList<>();
    private static int count = 0;

    private static ConcurrentHashMap<String, CopyOnWriteArrayList<String>> mapSublinks;
    private final WebSite mainSite;
    private HTMLParser htmlParser;
    private final JsoupConnection jsoupConnection;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private AtomicBoolean indexingStatus;
    private final LemmaService lemmaService = new LemmaService();
    private final LemmaRepository lemmaRepository;
    private final IndexesRepository indexesRepository;

    public MapWebSite(ConcurrentHashMap<String, CopyOnWriteArrayList<String>> mapSublinks, WebSite mainSite,
                      HTMLParser htmlParser, JsoupConnection jsoupConnection, PageRepository pageRepository,
                      SiteRepository siteRepository, AtomicBoolean indexingStatus, LemmaRepository lemmaRepository,
                      IndexesRepository indexesRepository) {
        this.mapSublinks = mapSublinks;
        this.mainSite = mainSite;
        this.htmlParser = htmlParser;
        this.jsoupConnection = jsoupConnection;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.indexingStatus = indexingStatus;
        this.lemmaRepository = lemmaRepository;
        this.indexesRepository = indexesRepository;
    }

    @Override
    protected void compute() {
        try {
            if (count != 0 && pageRepository.getCountPages() == 0) {
                pagesList.clear();
                linkChildren.clear();
                mapSublinks.clear();
            }
            if (!indexingStatus.get()) {
                return;
            }
            if (!pagesList.contains(htmlParser.getCurrentMainSite())) {
                pagesList.add(htmlParser.getCurrentMainSite());
            }
            ConcurrentHashMap<String, Page> currentSubLinks = htmlParser.getSubLinks(htmlParser.getCurrentMainSite(), mainSite);

            for (Map.Entry<String, Page> page : currentSubLinks.entrySet()) {
                if (!indexingStatus.get()) {
                    return;
                }
                String linkCurrentPage = page.getKey();
                Page currentPage = page.getValue();

                if(!pagesList.contains(linkCurrentPage)) {
                    pagesList.add(linkCurrentPage);
                    linkChildren.add(linkCurrentPage);
                    pageRepository.save(currentPage);
                    if (currentPage.getCode() < 300) {
                        saveLemmaAndIndex(currentPage);
                    }
                    mainSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(mainSite);
                }

                mapSublinks.put(htmlParser.getCurrentMainSite(), linkChildren);
            }
            List<MapWebSite> taskList = new ArrayList<>();
            for (String child : linkChildren) {
                HTMLParser newParser = new HTMLParser(child, jsoupConnection);
                MapWebSite task = new MapWebSite(mapSublinks, mainSite, newParser, jsoupConnection, pageRepository,
                        siteRepository, indexingStatus, lemmaRepository, indexesRepository);
                task.fork();
                taskList.add(task);
            }

            for (MapWebSite task : taskList) {
                if (!indexingStatus.get()) {
                    return;
                }
                task.join();
            }
            count++;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveLemmaAndIndex(Page page) throws IOException {
        ConcurrentHashMap<String, Integer> lemmas = lemmaService.getLemmas(page.getContent());
        for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
            Lemma lemmaInDB = saveAndGetLemma(lemma.getKey(), page);
            saveIndex(lemma.getValue(), lemmaInDB, page);
        }
    }

    public Lemma saveAndGetLemma(String lemma, Page page) {
        Lemma lemmaInDB = lemmaRepository.getLemmaInSite(lemma, page.getWebSiteId().getId());
        if (lemmaInDB == null) {
            lemmaInDB = new Lemma(page.getWebSiteId(), lemma, 1);
        } else {
            lemmaInDB.setFrequency(lemmaInDB.getFrequency() + 1);
        }
        lemmaRepository.save(lemmaInDB);
        return lemmaInDB;
    }

    public void saveIndex(float quantity, Lemma lemma, Page page) {
        Indexes indexesInDB = indexesRepository.findIndex(page.getId(), lemma.getId());
        if (indexesInDB == null) {
            indexesInDB = new Indexes(page, lemma, quantity);
        } else {
            indexesInDB.setQuantity(indexesInDB.getQuantity() + 1);
        }
        indexesRepository.save(indexesInDB);
    }
}
