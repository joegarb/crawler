package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the frontier queue of URLs to be crawled.
 *
 * <p>Note: Database migrations have not been implemented, so deleting the database file is
 * necessary to pick up schema changes.
 */
public class FrontierStore {
  private static final Logger logger = LoggerFactory.getLogger(FrontierStore.class);

  public static record FrontierUrl(long id, String url) {}

  /**
   * Creates the frontier_queue table if it doesn't exist.
   *
   * @param conn Database connection
   * @throws SQLException if a database access error occurs
   */
  public static void createTable(Connection conn) throws SQLException {
    String sql =
        "CREATE TABLE IF NOT EXISTS frontier_queue ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "url TEXT NOT NULL UNIQUE,"
            + "added_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "claimed_at TEXT"
            + ")";
    try (Statement statement = conn.createStatement()) {
      statement.execute(sql);
      logger.debug("Frontier queue table created or already exists");
    }
  }

  /**
   * Adds a URL to the frontier queue if it doesn't already exist.
   *
   * @param conn Database connection
   * @param url URL to add
   * @throws SQLException if a database access error occurs
   */
  public static void addUrl(Connection conn, String url) throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize(url);
    String sql = "INSERT OR IGNORE INTO frontier_queue (url) VALUES (?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, normalizedUrl);
      statement.executeUpdate();
    }
  }

  /**
   * Adds multiple URLs to the frontier queue if they don't already exist.
   *
   * @param conn Database connection
   * @param urls List of URLs to add
   * @throws SQLException if a database access error occurs
   */
  public static void addUrls(Connection conn, List<String> urls) throws SQLException {
    if (urls == null || urls.isEmpty()) {
      return;
    }

    String sql = "INSERT OR IGNORE INTO frontier_queue (url) VALUES (?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      for (String url : urls) {
        String normalizedUrl = UrlNormalizer.normalize(url);
        statement.setString(1, normalizedUrl);
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  /**
   * Atomically claims and returns the next pending URL from the frontier queue.
   *
   * @param conn Database connection
   * @return The next URL entry to crawl (with ID and URL), or null if the queue is empty
   * @throws SQLException if a database access error occurs
   */
  public static FrontierUrl getNextUrl(Connection conn) throws SQLException {
    String sql =
        "UPDATE frontier_queue SET claimed_at = datetime('now') WHERE id = ("
            + "SELECT id FROM frontier_queue WHERE claimed_at IS NULL "
            + "ORDER BY added_at ASC LIMIT 1"
            + ") RETURNING id, url";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return new FrontierUrl(resultSet.getLong("id"), resultSet.getString("url"));
        }
      }
    }
    return null;
  }

  /**
   * Checks if there are any URLs currently claimed by workers.
   *
   * @param conn Database connection
   * @return true if there are claimed URLs, false otherwise
   * @throws SQLException if a database access error occurs
   */
  public static boolean hasClaimedUrls(Connection conn) throws SQLException {
    String sql = "SELECT COUNT(*) FROM frontier_queue WHERE claimed_at IS NOT NULL";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return resultSet.getInt(1) > 0;
        }
      }
    }
    return false;
  }

  /**
   * Removes a URL from the frontier queue after it has been processed.
   *
   * @param conn Database connection
   * @param id ID of the URL entry to remove
   * @throws SQLException if a database access error occurs
   */
  public static void removeUrl(Connection conn, long id) throws SQLException {
    String sql = "DELETE FROM frontier_queue WHERE id = ?";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setLong(1, id);
      statement.executeUpdate();
    }
  }
}
