package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.IndexingResponse;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    void startIndexing(AtomicBoolean isIndexing) throws InterruptedException;

    //ResponseEntity stopIndexing();
}
