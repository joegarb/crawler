package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for MetadataStore. */
class MetadataStoreTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    // In-memory database
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    MetadataStore.createTable(conn);
  }

  @Test
  void hasBeenCrawledReturnsFalseForNewUrl() throws SQLException {
    assertFalse(MetadataStore.hasBeenCrawled(conn, "https://example.com"));
  }

  @Test
  void hasBeenCrawledReturnsTrueAfterMarking() throws SQLException {
    // URLs from frontier are already normalized before being crawled and recorded, so replicate
    // that behavior
    String normalizedUrl = UrlNormalizer.normalize("https://example.com");
    MetadataStore.markAsCrawled(conn, normalizedUrl, 200, null);
    assertTrue(MetadataStore.hasBeenCrawled(conn, normalizedUrl));
  }

  @Test
  void markAsCrawledUpdatesTimestamp() throws SQLException, InterruptedException {
    // URLs from frontier are already normalized before being crawled and recorded, so replicate
    // that behavior
    String normalizedUrl = UrlNormalizer.normalize("https://example.com");
    MetadataStore.markAsCrawled(conn, normalizedUrl, 200, null);

    // Get original timestamp
    String originalTimestamp;
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT crawled_at FROM crawled_urls WHERE url = '" + normalizedUrl + "'")) {
      assertTrue(resultSet.next());
      originalTimestamp = resultSet.getString("crawled_at");
    }

    Thread.sleep(1100); // Ensure timestamp will be different

    MetadataStore.markAsCrawled(conn, normalizedUrl, 200, null);
    // Verify timestamp was updated
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT crawled_at FROM crawled_urls WHERE url = '" + normalizedUrl + "'")) {
      assertTrue(resultSet.next());
      String updatedTimestamp = resultSet.getString("crawled_at");
      assertTrue(updatedTimestamp.compareTo(originalTimestamp) > 0);
    }
  }

  @Test
  void hasBeenCrawledReturnsFalseForFailedUrlAfterRetryInterval() throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize("https://example.com");
    MetadataStore.markAsCrawled(conn, normalizedUrl, null, "Network error");

    // Set crawled_at to be older than the retry interval (5 minutes)
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "UPDATE crawled_urls SET crawled_at = datetime('now', '-6 minutes') WHERE url = '"
              + normalizedUrl
              + "'");
    }

    // Should return false (ready for retry)
    assertFalse(MetadataStore.hasBeenCrawled(conn, normalizedUrl));
  }

  @Test
  void hasBeenCrawledReturnsFalseForSuccessfulUrlAfterRefreshInterval() throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize("https://example.com");
    MetadataStore.markAsCrawled(conn, normalizedUrl, 200, null);

    // Set crawled_at to be older than the refresh interval (24 hours)
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "UPDATE crawled_urls SET crawled_at = datetime('now', '-25 hours') WHERE url = '"
              + normalizedUrl
              + "'");
    }

    // Should return false (ready for refresh)
    assertFalse(MetadataStore.hasBeenCrawled(conn, normalizedUrl));
  }

  @Test
  void hasBeenCrawledReturnsTrueForFailedUrlBeforeRetryInterval() throws SQLException {
    String normalizedUrl = UrlNormalizer.normalize("https://example.com");
    MetadataStore.markAsCrawled(conn, normalizedUrl, 500, "Server error");

    // crawled_at is recent (just set), so should return true
    assertTrue(MetadataStore.hasBeenCrawled(conn, normalizedUrl));
  }
}
