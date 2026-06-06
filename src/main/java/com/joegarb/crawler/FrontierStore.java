package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
  private static final DateTimeFormatter SQLITE_DATETIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static record FrontierUrl(long id, String url, String domain) {}

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
            + "domain TEXT,"
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
    String domain = UrlNormalizer.extractDomain(normalizedUrl);
    String sql = "INSERT OR IGNORE INTO frontier_queue (url, domain) VALUES (?, ?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, normalizedUrl);
      statement.setString(2, domain);
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

    String sql = "INSERT OR IGNORE INTO frontier_queue (url, domain) VALUES (?, ?)";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      for (String url : urls) {
        String normalizedUrl = UrlNormalizer.normalize(url);
        String domain = UrlNormalizer.extractDomain(normalizedUrl);
        statement.setString(1, normalizedUrl);
        statement.setString(2, domain);
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  /**
   * Atomically claims and returns the next pending URL whose domain is not within the politeness
   * cooldown window.
   *
   * @param conn Database connection
   * @return The next claimable URL entry, or null if none is available
   * @throws SQLException if a database access error occurs
   */
  public static FrontierUrl getNextUrl(Connection conn) throws SQLException {
    String cutoff =
        LocalDateTime.now(ZoneOffset.UTC)
            .minusNanos((long) Configuration.POLITENESS_DELAY_MS * 1_000_000L)
            .format(SQLITE_DATETIME);

    String sql =
        "UPDATE frontier_queue SET claimed_at = datetime('now') WHERE id = ("
            + "SELECT fq.id FROM frontier_queue fq"
            + " WHERE fq.claimed_at IS NULL"
            + " AND (fq.domain IS NULL OR NOT EXISTS ("
            + "  SELECT 1 FROM domain_access da"
            + "  WHERE da.domain = fq.domain AND da.last_fetched_at > ?"
            + " ))"
            + " ORDER BY fq.added_at ASC LIMIT 1"
            + ") RETURNING id, url, domain";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, cutoff);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return new FrontierUrl(
              resultSet.getLong("id"), resultSet.getString("url"), resultSet.getString("domain"));
        }
      }
    }
    return null;
  }

  /**
   * Checks if there are any unclaimed URLs in the frontier (regardless of cooldown).
   *
   * @param conn Database connection
   * @return true if there are unclaimed URLs waiting, false otherwise
   * @throws SQLException if a database access error occurs
   */
  public static boolean hasQueuedUrls(Connection conn) throws SQLException {
    String sql = "SELECT COUNT(*) FROM frontier_queue WHERE claimed_at IS NULL";
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

  /**
   * Resets all claimed URLs back to unclaimed. Called on startup to recover URLs that were
   * in-flight when the process was last killed.
   *
   * @param conn Database connection
   * @throws SQLException if a database access error occurs
   */
  public static void resetClaimedUrls(Connection conn) throws SQLException {
    String sql = "UPDATE frontier_queue SET claimed_at = NULL WHERE claimed_at IS NOT NULL";
    try (Statement statement = conn.createStatement()) {
      int count = statement.executeUpdate(sql);
      if (count > 0) {
        logger.info("Reset {} claimed URL(s) from previous run", count);
      }
    }
  }
}
