package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupConnection;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.BadResponse;
import searchengine.dto.OkResponse;
import searchengine.model.StatusType;
import searchengine.model.WebSite;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.MapWebSite;
import searchengine.services.utils.HTMLParser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sitesList;

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final JsoupConnection jsoupConnection;
    private AtomicBoolean indexingStatus;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final IndexesRepository indexesRepository;
    private final LemmaRepository lemmaRepository;
    public static int countStart = 0;

    public ResponseEntity startIndexing(AtomicBoolean indexingStatus) throws InterruptedException {
        this.indexingStatus = indexingStatus;
        if (!indexingStatus.get()) {
            executor.submit( () -> {
                        indexingStatus.set(true);
                        try {
                            clearDB();
                            List<WebSite> webSites = getListSitesForAddToDB();
                            indexing(webSites);
                            indexingStatus.set(false);
                            countStart++;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
            return ResponseEntity.status(HttpStatus.OK).body(new OkResponse());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new BadResponse(false, "Индексация уже запущена"));
    }

    public List<WebSite> getListSitesForAddToDB() {
        List<WebSite> webSites = new ArrayList<>();
        for (Site site : sitesList.getSites()) {
            WebSite webSite = new WebSite(StatusType.INDEXING, LocalDateTime.now(), null, site.getUrl(), site.getName());
            webSite.setUrl(site.getUrl());
            webSites.add(webSite);
        }
        return webSites;
    }

    public void indexing(List<WebSite> webSites) throws InterruptedException {
        List<Thread> threadList = new ArrayList<>();
        for (WebSite webSite : webSites) {
            Runnable startIndexing = () -> {
                siteRepository.save(webSite);
                ConcurrentHashMap<String, CopyOnWriteArrayList<String>> sitesMap = new ConcurrentHashMap<>();
                MapWebSite mapWebSite = new MapWebSite(sitesMap, webSite, new HTMLParser(webSite.getUrl(), jsoupConnection),
                        jsoupConnection, pageRepository, siteRepository, indexingStatus, lemmaRepository, indexesRepository);

                try {
                    log.info("Индексация сайта " + webSite.getUrl() + " запущена");
                    new ForkJoinPool().invoke(mapWebSite);
                } catch (SecurityException e) {
                    log.error("Ошибка при индексации сайта {}", webSite.getUrl());
                    webSite.setStatus(StatusType.FAILED);
                    siteRepository.save(webSite);
                }
                updateIndexingStatus(webSite);
            };
            createThreads(startIndexing, threadList);
        }
        joinThreads(threadList);
        indexingStatus.set(false);
    }

    public void createThreads(Runnable start, List<Thread> threadList) {
        Thread thread = new Thread(start);
        threadList.add(thread);
        thread.start();
    }

    public void joinThreads(List<Thread> threadList) throws InterruptedException {
        for (Thread thread : threadList) {
            thread.join();
        }
    }

    public void updateIndexingStatus(WebSite webSite) {
        if (indexingStatus.get()) {
            log.info("Сайт " + webSite.getUrl() + " проиндексирован");
            webSite.setStatus(StatusType.INDEXED);
            siteRepository.save(webSite);
        } else {
            log.info("Индексация остановлена пользователем");
            webSite.setStatus(StatusType.FAILED);
            siteRepository.save(webSite);
        }
    }

    public ResponseEntity stopIndexing() {
        if (!indexingStatus.get()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new BadResponse(false, "Индексация не запущена"));
        }
        indexingStatus.set(false);
        return ResponseEntity.status(HttpStatus.OK).body(new OkResponse());
    }

    public void clearDB() {
        indexesRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }
}
