package com.joegarb.crawler.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for ContentStore. */
class ContentStoreTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    ContentStore.createTable(conn);
  }

  @Test
  void firstSightingIsNew() throws SQLException {
    assertEquals(ChangeStatus.NEW, ContentStore.record(conn, "https://a.com", "hash1"));
  }

  @Test
  void sameHashIsUnchanged() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1");
    assertEquals(ChangeStatus.UNCHANGED, ContentStore.record(conn, "https://a.com", "hash1"));
  }

  @Test
  void differentHashIsChanged() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1");
    assertEquals(ChangeStatus.CHANGED, ContentStore.record(conn, "https://a.com", "hash2"));
  }

  @Test
  void keepsOneRowPerUrlAcrossCrawls() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1"); // NEW
    ContentStore.record(conn, "https://a.com", "hash1"); // UNCHANGED
    ContentStore.record(conn, "https://a.com", "hash2"); // CHANGED
    assertEquals(1, rowCount());
  }

  @Test
  void storedStatusReflectsTheLatestCrawl() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1"); // NEW
    ContentStore.record(conn, "https://a.com", "hash1"); // now stable
    assertEquals("UNCHANGED", currentStatus("https://a.com"));
  }

  @Test
  void latestHashReflectsMostRecentContent() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1");
    ContentStore.record(conn, "https://a.com", "hash2");
    assertEquals("hash2", ContentStore.getLatestHash(conn, "https://a.com").orElseThrow());
  }

  @Test
  void changedAtOnlyMovesWhenContentChanges() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1"); // NEW
    setColumn("changed_at", "2000-01-01 00:00:00");
    ContentStore.record(conn, "https://a.com", "hash1"); // UNCHANGED — keeps changed_at
    assertEquals("2000-01-01 00:00:00", getColumn("changed_at"));
    ContentStore.record(conn, "https://a.com", "hash2"); // CHANGED — moves changed_at
    assertNotEquals("2000-01-01 00:00:00", getColumn("changed_at"));
  }

  @Test
  void recordPreservesReportedHash() throws SQLException {
    ContentStore.record(conn, "https://a.com", "hash1");
    setColumn("reported_hash", "hash1");
    ContentStore.record(conn, "https://a.com", "hash2");
    assertEquals("hash1", getColumn("reported_hash"));
  }

  private void setColumn(String column, String value) throws SQLException {
    try (var statement =
        conn.prepareStatement(
            "UPDATE page_content SET " + column + " = ? WHERE url = 'https://a.com'")) {
      statement.setString(1, value);
      statement.executeUpdate();
    }
  }

  private String getColumn(String column) throws SQLException {
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT " + column + " FROM page_content WHERE url = 'https://a.com'")) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }

  private int rowCount() throws SQLException {
    try (Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM page_content")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private String currentStatus(String url) throws SQLException {
    String sql = "SELECT change_status FROM page_content WHERE url = ?";
    try (var statement = conn.prepareStatement(sql)) {
      statement.setString(1, url);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getString(1);
      }
    }
  }
}
