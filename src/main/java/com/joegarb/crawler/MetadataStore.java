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
            + "crawled_at TEXT NOT NULL DEFAULT (datetime('now'))"
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
   * @throws SQLException if a database access error occurs
   */
  public static void markAsCrawled(Connection conn, String url) throws SQLException {
    String sql = "INSERT OR REPLACE INTO crawled_urls (url) VALUES (?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, url);
      statement.executeUpdate();
    }
  }

  /**
   * Checks if a URL has already been crawled.
   *
   * @param conn Database connection
   * @param url URL to check
   * @return true if the URL has been crawled, false otherwise
   * @throws SQLException if a database access error occurs
   */
  public static boolean hasBeenCrawled(Connection conn, String url) throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize(url);
    String sql = "SELECT 1 FROM crawled_urls WHERE url = ?";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, normalizedUrl);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
