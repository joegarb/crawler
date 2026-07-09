package com.joegarb.crawler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds the site-monitor report — content changes and service health — from the database. */
public final class ReportGenerator {

  /** A page whose content is new or has changed since the previous crawl. */
  public record Change(String url, String status, String detectedAt) {}

  /** A URL that did not return a healthy response. */
  public record Problem(String url, Integer statusCode, String error, String checkedAt) {}

  /**
   * Availability summary across all crawled URLs, where {@code total = ok + skipped +
   * problems.size()}. Skipped URLs are those not fetched due to robots.txt.
   */
  public record Health(int total, int ok, int skipped, List<Problem> problems) {
    public Health {
      problems = List.copyOf(problems);
    }
  }

  /** The full monitor report. */
  public record Report(String generatedAt, List<Change> changes, Health health) {
    public Report {
      changes = List.copyOf(changes);
    }
  }

  private ReportGenerator() {}

  /**
   * Builds a report from the current database state.
   *
   * @param conn Database connection
   * @return the report
   * @throws SQLException if a database access error occurs
   */
  public static Report generate(Connection conn) throws SQLException {
    return new Report(Instant.now().toString(), queryChanges(conn), queryHealth(conn));
  }

  /**
   * Serializes the report as pretty-printed JSON to the given file path.
   *
   * @param report the report to write
   * @param path destination file path
   * @throws IOException if the file cannot be written
   */
  public static void writeJson(Report report, String path) throws IOException {
    Gson gson =
        new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    Files.writeString(Path.of(path), gson.toJson(report));
  }

  /**
   * Returns a short human-readable summary line.
   *
   * @param report the report to summarize
   * @return a one-line summary
   */
  public static String summarize(Report report) {
    return String.format(
        "%d page(s) changed, %d/%d URL(s) healthy",
        report.changes().size(), report.health().ok(), report.health().total());
  }

  private static List<Change> queryChanges(Connection conn) throws SQLException {
    // Pages whose current status (as of the latest crawl) is new or changed. Stable pages read as
    // UNCHANGED and are excluded.
    String sql =
        "SELECT url, change_status, fetched_at FROM page_content"
            + " WHERE change_status IN ('NEW', 'CHANGED')"
            + " ORDER BY fetched_at DESC";
    List<Change> changes = new ArrayList<>();
    try (Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        changes.add(
            new Change(
                rs.getString("url"), rs.getString("change_status"), rs.getString("fetched_at")));
      }
    }
    return changes;
  }

  private static Health queryHealth(Connection conn) throws SQLException {
    int total = count(conn, "SELECT COUNT(*) FROM crawled_urls");
    // URLs not fetched because robots.txt disallowed them: reported separately, not health issues.
    int skipped =
        count(
            conn,
            "SELECT COUNT(*) FROM crawled_urls WHERE error_message = 'robots.txt disallowed'");

    // A URL is a problem only if the server errored (5xx) or did not respond at all (a network
    // failure, i.e. no status code). Responses like 401/403/404 mean the service is reachable.
    String sql =
        "SELECT url, http_status_code, error_message, crawled_at FROM crawled_urls"
            + " WHERE (http_status_code >= 500 OR http_status_code IS NULL)"
            + " AND (error_message IS NULL OR error_message <> 'robots.txt disallowed')"
            + " ORDER BY crawled_at DESC";
    List<Problem> problems = new ArrayList<>();
    try (Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        int code = rs.getInt("http_status_code");
        Integer statusCode = rs.wasNull() ? null : code;
        problems.add(
            new Problem(
                rs.getString("url"),
                statusCode,
                rs.getString("error_message"),
                rs.getString("crawled_at")));
      }
    }
    return new Health(total, total - skipped - problems.size(), skipped, problems);
  }

  private static int count(Connection conn, String sql) throws SQLException {
    try (Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }
}
