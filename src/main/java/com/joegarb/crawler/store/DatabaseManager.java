package com.joegarb.crawler.store;

import com.joegarb.crawler.Configuration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
   * Ordered, append-only migrations. Entry N upgrades the schema from version N+1 to N+2; the
   * stores' createTable methods always define the latest schema, so fresh databases skip this list
   * entirely. To change the schema: update the relevant createTable, then append the equivalent
   * ALTER statements here.
   */
  private static final List<List<String>> MIGRATIONS = List.of();

  static final int CURRENT_SCHEMA_VERSION = MIGRATIONS.size() + 1;

  /**
   * Initializes the database by creating all necessary tables and applying any pending schema
   * migrations.
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
      initializeSchema(connection);
      logger.info("Database initialized successfully");
    }
  }

  /**
   * Creates the schema on a fresh database, or migrates an existing one to the current version. The
   * schema version is tracked with SQLite's user_version pragma; databases created before
   * versioning existed are assumed to match the version-1 baseline.
   *
   * @param connection Database connection
   * @throws SQLException if a database access error occurs
   */
  static void initializeSchema(Connection connection) throws SQLException {
    if (!hasTables(connection)) {
      FrontierStore.createTable(connection);
      MetadataStore.createTable(connection);
      DomainAccessStore.createTable(connection);
      ContentStore.createTable(connection);
      setSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
      return;
    }

    int version = Math.max(getSchemaVersion(connection), 1);
    while (version < CURRENT_SCHEMA_VERSION) {
      for (String sql : MIGRATIONS.get(version - 1)) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(sql);
        }
      }
      version++;
      setSchemaVersion(connection, version);
      logger.info("Migrated database schema to version {}", version);
    }
    // Stamp pre-versioning databases even when no migrations were pending
    setSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
  }

  static int getSchemaVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private static void setSchemaVersion(Connection connection, int version) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA user_version = " + version);
    }
  }

  private static boolean hasTables(Connection connection) throws SQLException {
    String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'frontier_queue'";
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    }
  }
}
