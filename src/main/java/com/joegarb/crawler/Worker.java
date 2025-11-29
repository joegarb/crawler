package com.joegarb.crawler;

import static com.joegarb.crawler.FrontierStore.FrontierUrl;

import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Worker thread that performs web crawling tasks. */
public class Worker extends Thread {
  private static final Logger logger = LoggerFactory.getLogger(Worker.class);

  @Override
  public void run() {
    while (!Thread.currentThread().isInterrupted()) {
      try (Connection conn = DatabaseManager.getConnection()) {
        FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
        if (frontierUrl != null) {
          logger.info(
              "Worker {} claimed URL: {}", Thread.currentThread().getName(), frontierUrl.url());

          // TODO: Fetch and process the URL

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
