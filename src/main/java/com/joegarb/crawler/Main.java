package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main entry point for the web crawler. */
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /**
   * Main method that starts the web crawler.
   *
   * @param args Command line arguments. Pass a start URL to seed the frontier, or omit to resume
   *     from an existing queue.
   */
  public static void main(String[] args) {
    String startUrl = null;

    for (String arg : args) {
      if (startUrl == null && !arg.startsWith("--")) {
        startUrl = arg;
      }
    }

    try {
      DatabaseManager.initializeDatabase();
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierStore.resetClaimedUrls(conn);
        if (startUrl != null) {
          FrontierStore.addUrl(conn, startUrl);
          logger.info("Start URL: {}", startUrl);
        } else if (!FrontierStore.hasQueuedUrls(conn) && !FrontierStore.hasClaimedUrls(conn)) {
          logger.error("No start URL provided and frontier is empty.");
          System.exit(1);
        } else {
          logger.info("Resuming from existing frontier");
        }
      }
    } catch (SQLException e) {
      logger.error("Failed to initialize database", e);
      System.exit(1);
    }

    logger.info("Worker threads: {}", Configuration.NUM_THREADS);
    Worker[] workers = new Worker[Configuration.NUM_THREADS];
    for (int i = 0; i < Configuration.NUM_THREADS; i++) {
      workers[i] = new Worker();
      workers[i].start();
    }

    for (Worker worker : workers) {
      try {
        worker.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("Interrupted while waiting for workers to complete");
        System.exit(1);
      }
    }

    logger.info("Crawl complete.");
  }
}
