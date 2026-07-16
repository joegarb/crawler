package com.joegarb.crawler.store;

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
            + "last_fetched_at TEXT NOT NULL,"
            + "crawl_delay_ms INTEGER"
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
        "INSERT INTO domain_access (domain, last_fetched_at) VALUES (?, datetime('now'))"
            + " ON CONFLICT(domain) DO UPDATE SET last_fetched_at = excluded.last_fetched_at";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, domain);
      statement.executeUpdate();
      logger.debug("Recorded access for domain: {}", domain);
    }
  }

  /**
   * Records the robots.txt Crawl-delay for the given domain so the frontier can enforce it when
   * selecting URLs to claim.
   *
   * @param conn Database connection
   * @param domain The domain the delay applies to
   * @param crawlDelayMs The Crawl-delay in milliseconds
   * @throws SQLException if a database access error occurs
   */
  public static void recordCrawlDelay(Connection conn, String domain, long crawlDelayMs)
      throws SQLException {
    if (domain == null) {
      return;
    }
    String sql =
        "INSERT INTO domain_access (domain, last_fetched_at, crawl_delay_ms)"
            + " VALUES (?, datetime('now'), ?)"
            + " ON CONFLICT(domain) DO UPDATE SET crawl_delay_ms = excluded.crawl_delay_ms";
    try (PreparedStatement statement = conn.prepareStatement(sql)) {
      statement.setString(1, domain);
      statement.setLong(2, crawlDelayMs);
      statement.executeUpdate();
      logger.debug("Recorded crawl delay {} ms for domain: {}", crawlDelayMs, domain);
    }
  }
}
