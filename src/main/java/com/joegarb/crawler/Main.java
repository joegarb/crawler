package com.joegarb.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Main entry point for the web crawler. */
public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final int NUM_THREADS = 4;

  /**
   * Main method that starts the web crawler.
   *
   * @param args Command line arguments. Requires a start URL as the first argument.
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      logger.error("No start URL provided");
      System.exit(1);
    }

    String startUrl = args[0];

    logger.info("Start URL: {}", startUrl);
    logger.info("Worker threads: {}", NUM_THREADS);

    Worker[] workers = new Worker[NUM_THREADS];
    for (int i = 0; i < NUM_THREADS; i++) {
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
