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
    return DriverManager.getConnection(Configuration.DB_URL);
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
      logger.info("Database initialized successfully");
    }
  }
}
