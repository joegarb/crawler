package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the current content fingerprint of each page so changes can be detected across crawls.
 *
 * <p>One row per URL holds its latest fingerprint and how it compared to the previous crawl, so the
 * stored change status always reflects the most recent crawl: a page that was new and has since
 * been stable reads as UNCHANGED rather than remaining NEW.
 *
 * <p>Note: Database migrations have not been implemented, so deleting the database file is
 * necessary to pick up schema changes.
 */
public class ContentStore {
  private static final Logger logger = LoggerFactory.getLogger(ContentStore.class);

  /**
   * Creates the page_content table if it doesn't exist.
   *
   * @param conn Database connection
   * @throws SQLException if a database access error occurs
   */
  public static void createTable(Connection conn) throws SQLException {
    String sql =
        "CREATE TABLE IF NOT EXISTS page_content ("
            + "url TEXT PRIMARY KEY,"
            + "content_hash TEXT NOT NULL,"
            + "change_status TEXT NOT NULL,"
            + "fetched_at TEXT NOT NULL DEFAULT (datetime('now'))"
            + ")";
    try (Statement statement = conn.createStatement()) {
      statement.execute(sql);
      logger.debug("Page content table created or already exists");
    }
  }

  /**
   * Returns the currently recorded content hash for the URL, or empty if it has none.
   *
   * @param conn Database connection
   * @param url The URL to look up
   * @return The content hash, or empty if the URL has no recorded content
   * @throws SQLException if a database access error occurs
   */
  public static Optional<String> getLatestHash(Connection conn, String url) throws SQLException {
    String sql = "SELECT content_hash FROM page_content WHERE url = ?";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, url);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return Optional.of(resultSet.getString("content_hash"));
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Compares the given hash to the one recorded for the URL, upserts the URL's current fingerprint
   * and status (every crawl, including unchanged), and returns the change status relative to the
   * previous crawl.
   *
   * @param conn Database connection
   * @param url The URL whose content was fetched
   * @param contentHash The fingerprint of the just-fetched content
   * @return The change status relative to the previously recorded content
   * @throws SQLException if a database access error occurs
   */
  public static ChangeStatus record(Connection conn, String url, String contentHash)
      throws SQLException {
    Optional<String> previous = getLatestHash(conn, url);
    ChangeStatus status;
    if (previous.isEmpty()) {
      status = ChangeStatus.NEW;
    } else if (previous.get().equals(contentHash)) {
      status = ChangeStatus.UNCHANGED;
    } else {
      status = ChangeStatus.CHANGED;
    }

    // Upsert every crawl (including UNCHANGED) so the stored status reflects the latest crawl.
    String sql =
        "INSERT OR REPLACE INTO page_content (url, content_hash, change_status, fetched_at)"
            + " VALUES (?, ?, ?, datetime('now'))";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, url);
      statement.setString(2, contentHash);
      statement.setString(3, status.name());
      statement.executeUpdate();
    }
    return status;
  }
}
