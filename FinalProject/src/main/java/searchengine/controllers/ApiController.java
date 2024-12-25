package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.dto.BadResponse;
import searchengine.dto.OkResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.PageIndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private AtomicBoolean indexingStatus = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final PageIndexingService pageIndexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() throws InterruptedException{
        return indexingService.startIndexing(indexingStatus);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
       return indexingService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) throws IOException {
        return pageIndexingService.startIndexing(url);
    }

    @GetMapping("/search")
    public ResponseEntity search(@RequestParam(required = false) String query,
                                 @RequestParam(required = false, defaultValue = "all") String site,
                                 @RequestParam(required = false, defaultValue = "0") int offset,
                                 @RequestParam(required = false, defaultValue = "20") int limit) throws IOException {
        return searchService.search(query, site, offset, limit);
    }


}
