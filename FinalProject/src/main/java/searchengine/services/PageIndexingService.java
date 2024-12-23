package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.config.Site;

import java.io.IOException;

public interface PageIndexingService {
    void indexPage(String url, Site site) throws IOException;
    ResponseEntity startIndexing(String url) throws IOException;
}
