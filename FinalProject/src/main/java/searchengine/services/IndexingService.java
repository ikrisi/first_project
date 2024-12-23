package searchengine.services;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    void startIndexing(AtomicBoolean isIndexing) throws InterruptedException;
}
