package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
