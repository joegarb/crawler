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
   * @param args Command line arguments. In normal mode, requires a start URL as the first argument.
   *     Use --worker flag to run in worker-only mode (no start URL needed).
   */
  public static void main(String[] args) {
    boolean workerMode = false;
    String startUrl = null;

    // Parse command-line arguments
    for (String arg : args) {
      if ("--worker".equals(arg)) {
        workerMode = true;
      } else if (startUrl == null && !arg.startsWith("--")) {
        startUrl = arg;
      }
    }

    // In normal mode, require a start URL
    if (!workerMode && startUrl == null) {
      logger.error("No start URL provided. Usage: java -jar crawler.jar <startUrl> [--worker]");
      System.exit(1);
    }

    try {
      if (!workerMode) {
        // In normal mode, initialize database and add the start URL to the frontier
        DatabaseManager.initializeDatabase();
        try (Connection conn = DatabaseManager.getConnection()) {
          FrontierStore.addUrl(conn, startUrl);
        }
        logger.info("Start URL: {}", startUrl);
      } else {
        logger.info("Running in worker mode");
      }
    } catch (SQLException e) {
      logger.error("Failed to initialize database", e);
      System.exit(1);
    }

    if (workerMode) {
      // In worker mode, run a single worker on the main thread
      logger.info("Running single worker on main thread");
      Worker worker = new Worker();
      worker.doWork();
    } else {
      // In normal mode, run multiple worker threads
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
    }

    logger.info("Crawl complete.");
  }
}
