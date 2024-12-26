package searchengine.controllers;
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
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private AtomicBoolean indexingStatus = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SitesList sitesList;
    private final PageIndexingService pageIndexingService;
    private final SearchService searchService;
    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SitesList sitesList,
                         PageIndexingService pageIndexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.sitesList = sitesList;
        this.pageIndexingService = pageIndexingService;
        this.searchService = searchService;
    }
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() throws InterruptedException{
        if (!indexingStatus.get()) {
            executor.submit( () -> {
                        indexingStatus.set(true);
                        try {
                            indexingService.startIndexing(indexingStatus);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
            return ResponseEntity.status(HttpStatus.OK).body(new OkResponse());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new BadResponse(false, "Индексация уже запущена"));
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        if (!indexingStatus.get()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new BadResponse(false, "Индексация не запущена"));
        }
        indexingStatus.set(false);
        return ResponseEntity.status(HttpStatus.OK).body(new OkResponse());
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