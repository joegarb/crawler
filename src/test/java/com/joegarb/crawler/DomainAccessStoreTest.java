package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for DomainAccessStore. */
class DomainAccessStoreTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    DomainAccessStore.createTable(conn);
  }

  @Test
  void recordsAccessForDomain() throws SQLException {
    DomainAccessStore.recordAccess(conn, "example.com");

    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) as count FROM domain_access WHERE domain = 'example.com'")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt("count"));
    }
  }

  @Test
  void recordAccessOverwritesPreviousEntry() throws SQLException, InterruptedException {
    DomainAccessStore.recordAccess(conn, "example.com");

    String firstTimestamp;
    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT last_fetched_at FROM domain_access WHERE domain = 'example.com'")) {
      assertTrue(resultSet.next());
      firstTimestamp = resultSet.getString("last_fetched_at");
    }

    Thread.sleep(1100);
    DomainAccessStore.recordAccess(conn, "example.com");

    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT last_fetched_at FROM domain_access WHERE domain = 'example.com'")) {
      assertTrue(resultSet.next());
      assertTrue(resultSet.getString("last_fetched_at").compareTo(firstTimestamp) > 0);
    }
  }

  @Test
  void recordAccessIgnoresNullDomain() throws SQLException {
    DomainAccessStore.recordAccess(conn, null);

    try (Statement statement = conn.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT COUNT(*) as count FROM domain_access")) {
      assertTrue(resultSet.next());
      assertEquals(0, resultSet.getInt("count"));
    }
  }
}
