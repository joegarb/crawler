package com.joegarb.crawler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages database connections and initialization. */
public class DatabaseManager {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

  /**
   * Gets a database connection.
   *
   * @return A connection to the database
   * @throws SQLException if a database access error occurs
   */
  public static Connection getConnection() throws SQLException {
    Connection connection = DriverManager.getConnection(Configuration.DB_URL);
    if (Configuration.DB_URL.startsWith("jdbc:sqlite:")) {
      try (var statement = connection.createStatement()) {
        // Wait for a contended write lock rather than failing immediately with SQLITE_BUSY. The
        // claim transaction in FrontierStore.getNextUrl holds the write lock across two statements,
        // so concurrent workers must briefly serialize on it.
        statement.execute("PRAGMA busy_timeout=5000");
      }
    }
    return connection;
  }

  /**
   * Initializes the database by creating all necessary tables.
   *
   * @throws SQLException if a database access error occurs
   */
  public static void initializeDatabase() throws SQLException {
    logger.info("Initializing database...");
    try (Connection connection = DriverManager.getConnection(Configuration.DB_URL)) {
      // Enable WAL (Write-Ahead Logging) mode for SQLite to improve concurrency.
      if (Configuration.DB_URL.startsWith("jdbc:sqlite:")) {
        try (var statement = connection.createStatement()) {
          statement.execute("PRAGMA journal_mode=WAL");
        }
      }
      FrontierStore.createTable(connection);
      MetadataStore.createTable(connection);
      DomainAccessStore.createTable(connection);
      logger.info("Database initialized successfully");
    }
  }
}
