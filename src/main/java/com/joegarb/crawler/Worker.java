package com.joegarb.crawler;

import static com.joegarb.crawler.FrontierStore.FrontierUrl;

import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Worker thread that performs web crawling tasks. */
public class Worker extends Thread {
  private static final Logger logger = LoggerFactory.getLogger(Worker.class);
  private static final PageFetcher pageFetcher = new PageFetcher();

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
        if (frontierUrl != null) {
          logger.info(
              "Worker {} claimed URL: {}", Thread.currentThread().getName(), frontierUrl.url());

          PageFetcher.FetchResult result = pageFetcher.fetch(frontierUrl.url());

          if (result.success()) {
            // TODO: Parse the response for urls to add to the frontier

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

          FrontierStore.removeUrl(conn, frontierUrl.id());
        } else {
          logger.info("Worker {} complete", Thread.currentThread().getName());
          break;
        }
      } catch (SQLException e) {
        logger.error("Database error in worker", e);
        break;
      }
    }
  }
}
