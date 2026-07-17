package com.joegarb.crawler.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for DatabaseManager schema initialization and migration. */
class DatabaseManagerTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
  }

  @Test
  void freshDatabaseGetsLatestSchemaAndVersion() throws SQLException {
    DatabaseManager.initializeSchema(conn);
    assertEquals(DatabaseManager.CURRENT_SCHEMA_VERSION, DatabaseManager.getSchemaVersion(conn));
    assertTrue(tableExists("frontier_queue"));
    assertTrue(tableExists("crawled_urls"));
    assertTrue(tableExists("domain_access"));
    assertTrue(tableExists("page_content"));
  }

  @Test
  void initializeSchemaIsIdempotent() throws SQLException {
    DatabaseManager.initializeSchema(conn);
    DatabaseManager.initializeSchema(conn);
    assertEquals(DatabaseManager.CURRENT_SCHEMA_VERSION, DatabaseManager.getSchemaVersion(conn));
  }

  @Test
  void migratesVersion1SchemaToCurrent() throws SQLException {
    // Hand-built version-1 baseline schema (before the etag/last_modified columns existed), as a
    // pre-versioning database (user_version 0)
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "CREATE TABLE frontier_queue (id INTEGER PRIMARY KEY AUTOINCREMENT,"
              + " url TEXT NOT NULL UNIQUE, domain TEXT,"
              + " added_at TEXT NOT NULL DEFAULT (datetime('now')), claimed_at TEXT)");
      statement.execute(
          "CREATE TABLE crawled_urls (url TEXT PRIMARY KEY,"
              + " crawled_at TEXT NOT NULL DEFAULT (datetime('now')),"
              + " http_status_code INTEGER, error_message TEXT)");
      statement.execute(
          "CREATE TABLE domain_access (domain TEXT PRIMARY KEY,"
              + " last_fetched_at TEXT NOT NULL, crawl_delay_ms INTEGER)");
      statement.execute(
          "CREATE TABLE page_content (url TEXT PRIMARY KEY, content_hash TEXT NOT NULL,"
              + " change_status TEXT NOT NULL,"
              + " fetched_at TEXT NOT NULL DEFAULT (datetime('now')),"
              + " changed_at TEXT, reported_hash TEXT)");
    }

    DatabaseManager.initializeSchema(conn);

    assertEquals(DatabaseManager.CURRENT_SCHEMA_VERSION, DatabaseManager.getSchemaVersion(conn));
    // The migrated columns exist and are writable
    try (Statement statement = conn.createStatement()) {
      statement.execute(
          "INSERT INTO crawled_urls (url, etag, last_modified) VALUES ('u', 'e', 'lm')");
    }
  }

  private boolean tableExists(String name) throws SQLException {
    String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + name + "'";
    try (Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    }
  }
}
