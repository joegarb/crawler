package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for FrontierStore. */
class FrontierStoreTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    // In-memory database
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    FrontierStore.createTable(conn);
    DomainAccessStore.createTable(conn);
  }

  @Test
  void addUrl() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    FrontierStore.addUrl(conn, "https://example.com"); // Try to add duplicate
    // Verify only one URL exists
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT COUNT(*) as count FROM frontier_queue")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt("count"));
    }
  }

  @Test
  void addUrlStoresDomain() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com/page");
    try (Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT domain FROM frontier_queue")) {
      assertTrue(resultSet.next());
      assertEquals("example.com", resultSet.getString("domain"));
    }
  }

  @Test
  void getNextUrlReturnsNullWhenEmpty() throws SQLException {
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertNull(frontierUrl);
  }

  @Test
  void getNextUrlClaimsAndReturnsUrl() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertTrue(frontierUrl != null);
    assertEquals("https://example.com/", frontierUrl.url());
    assertEquals("example.com", frontierUrl.domain());
    assertTrue(frontierUrl.id() > 0);
    // Verify URL is now claimed
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT claimed_at FROM frontier_queue WHERE url = 'https://example.com/'")) {
      assertTrue(resultSet.next());
      String claimedAt = resultSet.getString("claimed_at");
      assertTrue(claimedAt != null && !claimedAt.isEmpty());
      // Verify it's a valid ISO 8601 format
      assertTrue(claimedAt.contains("T") || claimedAt.contains(" "));
    }
  }

  @Test
  void getNextUrlOnlyReturnsPendingUrls() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    FrontierStore.getNextUrl(conn);
    // Next URL should return null since the only URL is claimed
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertNull(frontierUrl);
  }

  @Test
  void getNextUrlReturnsOldestFirst() throws SQLException, InterruptedException {
    FrontierStore.addUrl(conn, "https://example.com/1");
    Thread.sleep(10);
    FrontierStore.addUrl(conn, "https://example.com/2");
    // Should get the first one added
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertEquals("https://example.com/1", frontierUrl.url());
  }

  @Test
  void getNextUrlSkipsDomainInCooldown() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com/page");
    // Record a very recent access for example.com
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "INSERT INTO domain_access (domain, last_fetched_at) VALUES ('example.com', datetime('now'))");
    }
    // Domain is cooling — should not be claimable
    assertNull(FrontierStore.getNextUrl(conn));
  }

  @Test
  void getNextUrlReturnsUrlWhenCooldownExpired() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com/page");
    // Record an old access — well beyond the 1-second default delay
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "INSERT INTO domain_access (domain, last_fetched_at) VALUES ('example.com', datetime('now', '-10 seconds'))");
    }
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertTrue(frontierUrl != null);
    assertEquals("https://example.com/page", frontierUrl.url());
  }

  @Test
  void getNextUrlSkipsOneDomainButReturnsAnother() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com/page");
    FrontierStore.addUrl(conn, "https://other.com/page");
    // Put example.com in cooldown
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "INSERT INTO domain_access (domain, last_fetched_at) VALUES ('example.com', datetime('now'))");
    }
    // Should return other.com instead
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertTrue(frontierUrl != null);
    assertEquals("https://other.com/page", frontierUrl.url());
  }

  @Test
  void hasQueuedUrlsReturnsFalseWhenEmpty() throws SQLException {
    assertFalse(FrontierStore.hasQueuedUrls(conn));
  }

  @Test
  void hasQueuedUrlsReturnsTrueWhenUnclaimed() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    assertTrue(FrontierStore.hasQueuedUrls(conn));
  }

  @Test
  void hasQueuedUrlsReturnsFalseWhenAllClaimed() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    FrontierStore.getNextUrl(conn);
    assertFalse(FrontierStore.hasQueuedUrls(conn));
  }

  @Test
  void removeUrl() throws SQLException {
    FrontierStore.addUrl(conn, "https://example.com");
    FrontierStore.FrontierUrl frontierUrl = FrontierStore.getNextUrl(conn);
    assertTrue(frontierUrl != null);
    FrontierStore.removeUrl(conn, frontierUrl.id());
    // Verify URL was removed
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT COUNT(*) as count FROM frontier_queue")) {
      assertTrue(resultSet.next());
      assertEquals(0, resultSet.getInt("count"));
    }
  }
}
