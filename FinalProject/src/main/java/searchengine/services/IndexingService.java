package searchengine.services;

import org.springframework.http.ResponseEntity;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    ResponseEntity startIndexing(AtomicBoolean isIndexing) throws InterruptedException;

    ResponseEntity stopIndexing();
}
