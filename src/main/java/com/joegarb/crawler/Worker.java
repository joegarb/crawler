package com.joegarb.crawler;

import static com.joegarb.crawler.FrontierStore.FrontierUrl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Worker thread that performs web crawling tasks. */
public class Worker extends Thread {
  private static final Logger logger = LoggerFactory.getLogger(Worker.class);
  private static final PageFetcher pageFetcher = new PageFetcher();
  private static final int POLL_INTERVAL_MS = 500;

  @Override
  public void run() {
    doWork();
  }

  /** Performs the worker's crawling tasks. Can be called directly or from run(). */
  public void doWork() {
    while (!Thread.currentThread().isInterrupted()) {
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
        if (frontierUrl != null) {
          logger.debug(
              "Worker {} claimed URL: {}", Thread.currentThread().getName(), frontierUrl.url());

          PageFetcher.FetchResult result = pageFetcher.fetch(frontierUrl.url());

          if (result.success() && result.isHtml()) {
            List<String> links =
                LinkExtractor.extractLinks(result.response().body(), frontierUrl.url());

            StringBuilder output = new StringBuilder(frontierUrl.url());
            for (String link : links) {
              output.append("\n  ").append(link);
            }
            logger.info("{}", output.toString());

            // Add links that need crawling to the frontier
            List<String> urlsToAdd = new ArrayList<>();
            for (String link : links) {
              if (!MetadataStore.hasBeenCrawled(conn, link)) {
                urlsToAdd.add(link);
              }
            }
            if (!urlsToAdd.isEmpty()) {
              FrontierStore.addUrls(conn, urlsToAdd);
            }

            MetadataStore.markAsCrawled(conn, frontierUrl.url(), result.httpStatusCode(), null);
          } else if (result.success() && !result.isHtml()) {
            // Successfully fetched but not HTML - mark as crawled but don't extract links
            logger.info(frontierUrl.url());
            MetadataStore.markAsCrawled(conn, frontierUrl.url(), result.httpStatusCode(), null);
          } else {
            logger.warn(
                "Worker {} failed to fetch URL: {} - {}",
                Thread.currentThread().getName(),
                frontierUrl.url(),
                result.errorMessage());

            MetadataStore.markAsCrawled(
                conn, frontierUrl.url(), result.httpStatusCode(), result.errorMessage());
          }

          DomainAccessStore.recordAccess(conn, frontierUrl.domain());
          FrontierStore.removeUrl(conn, frontierUrl.id());
        } else {
          // No URL claimable right now — either all domains are cooling down or the queue is empty
          if (FrontierStore.hasQueuedUrls(conn) || FrontierStore.hasClaimedUrls(conn)) {
            try {
              Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              logger.error("Worker {} interrupted during delay", Thread.currentThread().getName());
              break;
            }
          } else {
            break;
          }
        }
      } catch (SQLException e) {
        logger.error("Database error in worker", e);
        break;
      }
    }
    logger.info("Worker {} complete", Thread.currentThread().getName());
  }
}
