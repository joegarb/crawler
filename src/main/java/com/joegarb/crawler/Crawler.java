package com.joegarb.crawler;

import static com.joegarb.crawler.store.FrontierStore.FrontierUrl;

import com.joegarb.crawler.store.DatabaseManager;
import com.joegarb.crawler.store.FrontierStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates a crawl: claims frontier URLs and processes each on its own virtual thread, with at
 * most {@link Configuration#NUM_THREADS} pages in flight. Per-domain politeness is enforced by the
 * frontier claim query, so concurrency scales with the number of distinct domains being crawled.
 */
public class Crawler {
  private static final Logger logger = LoggerFactory.getLogger(Crawler.class);
  private static final int POLL_INTERVAL_MS = 500;

  /** Runs the crawl until the frontier is empty and all in-flight pages have been processed. */
  public void crawl() {
    Semaphore permits = new Semaphore(Configuration.NUM_THREADS);
    AtomicInteger inFlight = new AtomicInteger();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          permits.acquire();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }

        FrontierUrl frontierUrl;
        try (Connection conn = DatabaseManager.getConnection()) {
          frontierUrl = FrontierStore.getNextUrl(conn);
          if (frontierUrl == null) {
            permits.release();
            // Nothing claimable right now — either domains are cooling down, pages are still in
            // flight (and may add links), or the crawl is done
            if (!FrontierStore.hasQueuedUrls(conn) && inFlight.get() == 0) {
              break;
            }
            try {
              Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
            continue;
          }
        } catch (SQLException e) {
          permits.release();
          logger.error("Database error claiming next URL", e);
          break;
        }

        logger.debug("Claimed URL: {}", frontierUrl.url());
        inFlight.incrementAndGet();
        Worker worker = new Worker(frontierUrl);
        executor.submit(
            () -> {
              try {
                worker.run();
              } finally {
                inFlight.decrementAndGet();
                permits.release();
              }
            });
      }
    }
  }
}
