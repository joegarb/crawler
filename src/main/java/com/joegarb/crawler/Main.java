package com.joegarb.crawler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
    List<String> startUrls = new ArrayList<>();
    boolean reportMode = false;
    for (String arg : args) {
      if (arg.equals("--report")) {
        reportMode = true;
      } else if (!arg.startsWith("--")) {
        startUrls.add(arg);
      }
    }

    try {
      DatabaseManager.initializeDatabase();
      if (reportMode) {
        try (Connection conn = DatabaseManager.getConnection()) {
          ReportGenerator.Report report = ReportGenerator.generate(conn);
          ReportGenerator.writeJson(report, Configuration.REPORT_FILE);
          logger.info("{}", ReportGenerator.summarize(report));
          logger.info("Report written to {}", Configuration.REPORT_FILE);
        }
        return;
      }
      SeedSource seedSource = new StaticSeedSource(startUrls);
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierStore.resetClaimedUrls(conn);
        List<String> seeds = seedSource.seeds();
        if (!seeds.isEmpty()) {
          FrontierStore.addUrls(conn, seeds);
          logger.info("Seed URLs: {}", seeds);
        } else if (!FrontierStore.hasQueuedUrls(conn) && !FrontierStore.hasClaimedUrls(conn)) {
          logger.error("No seed URLs provided and frontier is empty.");
          System.exit(1);
        } else {
          logger.info("Resuming from existing frontier");
        }
      }
    } catch (SQLException e) {
      logger.error("Failed to initialize database", e);
      System.exit(1);
    } catch (IOException e) {
      logger.error("Failed to write report", e);
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
