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
  void preVersioningDatabaseIsBroughtToCurrentVersion() throws SQLException {
    // A database created before versioning existed: baseline tables, user_version 0.
    FrontierStore.createTable(conn);
    MetadataStore.createTable(conn);
    DomainAccessStore.createTable(conn);
    ContentStore.createTable(conn);
    assertEquals(0, DatabaseManager.getSchemaVersion(conn));

    DatabaseManager.initializeSchema(conn);
    assertEquals(DatabaseManager.CURRENT_SCHEMA_VERSION, DatabaseManager.getSchemaVersion(conn));
  }

  private boolean tableExists(String name) throws SQLException {
    String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + name + "'";
    try (Statement statement = conn.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    }
  }
}
