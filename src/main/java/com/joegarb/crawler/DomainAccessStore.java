package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks when each domain was last fetched to enforce per-domain politeness delays. */
public class DomainAccessStore {
  private static final Logger logger = LoggerFactory.getLogger(DomainAccessStore.class);

  /**
   * Creates the domain_access table if it doesn't exist.
   *
   * @param conn Database connection
   * @throws SQLException if a database access error occurs
   */
  public static void createTable(Connection conn) throws SQLException {
    String sql =
        "CREATE TABLE IF NOT EXISTS domain_access ("
            + "domain TEXT PRIMARY KEY,"
            + "last_fetched_at TEXT NOT NULL"
            + ")";
    try (Statement statement = conn.createStatement()) {
      statement.execute(sql);
      logger.debug("Domain access table created or already exists");
    }
  }

  /**
   * Records that the given domain was just fetched, updating the timestamp if it already exists.
   *
   * @param conn Database connection
   * @param domain The domain that was fetched (e.g. "example.com")
   * @throws SQLException if a database access error occurs
   */
  public static void recordAccess(Connection conn, String domain) throws SQLException {
    if (domain == null) {
      return;
    }
    String sql =
        "INSERT OR REPLACE INTO domain_access (domain, last_fetched_at)"
            + " VALUES (?, datetime('now'))";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, domain);
      statement.executeUpdate();
      logger.debug("Recorded access for domain: {}", domain);
    }
  }
}
