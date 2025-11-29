package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages metadata about crawled URLs.
 *
 * <p>Note: Database migrations have not been implemented, so deleting the database file is
 * necessary to pick up schema changes.
 */
public class MetadataStore {
  private static final Logger logger = LoggerFactory.getLogger(MetadataStore.class);

  /** Time in seconds to wait before retrying a failed URL fetch. */
  private static final int FAILED_RETRY_INTERVAL_SECONDS = 300; // 5 minutes

  /** Time in seconds to wait before re-fetching a successfully crawled URL. */
  private static final int SUCCESS_REFRESH_INTERVAL_SECONDS = 86400; // 24 hours

  /**
   * Creates the crawled_urls table if it doesn't exist.
   *
   * @param conn Database connection
   * @throws SQLException if a database access error occurs
   */
  public static void createTable(Connection conn) throws SQLException {
    String sql =
        "CREATE TABLE IF NOT EXISTS crawled_urls ("
            + "url TEXT PRIMARY KEY,"
            + "crawled_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "http_status_code INTEGER,"
            + "error_message TEXT"
            + ")";
    try (Statement statement = conn.createStatement()) {
      statement.execute(sql);
      logger.debug("Crawled URLs table created or already exists");
    }
  }

  /**
   * Records that a URL has been crawled.
   *
   * <p>If the URL has already been crawled, the timestamp is updated to the current time.
   *
   * @param conn Database connection
   * @param url URL that was crawled
   * @param httpStatusCode HTTP status code if an HTTP response was received, null for network
   *     errors
   * @param errorMessage Error message if the crawl failed, null if successful
   * @throws SQLException if a database access error occurs
   */
  public static void markAsCrawled(
      Connection conn, String url, Integer httpStatusCode, String errorMessage)
      throws SQLException {
    String sql =
        "INSERT OR REPLACE INTO crawled_urls (url, http_status_code, error_message) VALUES (?, ?, ?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, url);
      if (httpStatusCode != null) {
        statement.setInt(2, httpStatusCode);
      } else {
        statement.setNull(2, java.sql.Types.INTEGER);
      }
      statement.setString(3, errorMessage);
      statement.executeUpdate();
    }
  }

  /**
   * Checks if a URL has already been crawled and is still fresh (not ready for retry/refresh).
   *
   * <p>Returns false if the URL has never been crawled, or if it was crawled but enough time has
   * passed to retry (for failures) or refresh (for successes).
   *
   * @param conn Database connection
   * @param url URL to check
   * @return true if the URL has been crawled recently and doesn't need retry/refresh, false
   *     otherwise
   * @throws SQLException if a database access error occurs
   */
  public static boolean hasBeenCrawled(Connection conn, String url) throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize(url);
    String sql = "SELECT http_status_code, crawled_at FROM crawled_urls WHERE url = ?";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, normalizedUrl);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          // URL has never been crawled
          return false;
        }

        // URL has been crawled - check if enough time has passed for retry/refresh
        Integer statusCode = null;
        int statusCodeValue = resultSet.getInt("http_status_code");
        if (!resultSet.wasNull()) {
          statusCode = statusCodeValue;
        }
        String crawledAt = resultSet.getString("crawled_at");

        int retryInterval;
        if (statusCode != null && statusCode >= 200 && statusCode < 300) {
          // Successful URL - use refresh interval
          retryInterval = SUCCESS_REFRESH_INTERVAL_SECONDS;
        } else {
          // Failed URL (non-2xx or NULL) - use retry interval
          retryInterval = FAILED_RETRY_INTERVAL_SECONDS;
        }

        // Check if enough time has passed
        String checkSql = "SELECT 1 WHERE datetime(?, '+' || ? || ' seconds') > datetime('now')";
        try (PreparedStatement checkStatement = conn.prepareStatement(checkSql)) {
          checkStatement.setString(1, crawledAt);
          checkStatement.setInt(2, retryInterval);
          try (ResultSet checkResult = checkStatement.executeQuery()) {
            // If the check returns a row, the URL is still fresh (not ready for retry)
            // If it doesn't return a row, enough time has passed (should retry/refresh)
            return checkResult.next();
          }
        }
      }
    }
  }
}
