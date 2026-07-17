package com.joegarb.crawler;

import com.joegarb.crawler.report.ReportGenerator;
import com.joegarb.crawler.seed.DockerLabelSeedSource;
import com.joegarb.crawler.seed.SeedSource;
import com.joegarb.crawler.seed.StaticSeedSource;
import com.joegarb.crawler.store.DatabaseManager;
import com.joegarb.crawler.store.FrontierStore;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
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
          ReportGenerator.Report report =
              ReportGenerator.generateAndWrite(conn, Configuration.REPORT_FILE);
          logger.info("{}", ReportGenerator.summarize(report));
          logger.info("Report written to {}", Configuration.REPORT_FILE);
        }
        return;
      }
      List<SeedSource> seedSources = new ArrayList<>();
      seedSources.add(new StaticSeedSource(startUrls));
      if (Configuration.SEED_FROM_DOCKER_LABELS) {
        seedSources.add(new DockerLabelSeedSource(Configuration.DOCKER_SOCKET));
      }
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierStore.resetClaimedUrls(conn);
        List<String> seeds = new ArrayList<>();
        for (SeedSource seedSource : seedSources) {
          seeds.addAll(seedSource.seeds());
        }
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

    logger.info("Max concurrent fetches: {}", Configuration.NUM_THREADS);
    Thread mainThread = Thread.currentThread();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Shutdown requested, finishing in-flight pages...");
                  mainThread.interrupt();
                  try {
                    // Keep the JVM alive while the crawl drains; give up after a bound so a
                    // stuck fetch cannot block shutdown indefinitely
                    mainThread.join(Duration.ofSeconds(15));
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                }));
    new Crawler().crawl();

    logger.info("Crawl complete.");
  }
}
