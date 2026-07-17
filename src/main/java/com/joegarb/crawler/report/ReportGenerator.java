package com.joegarb.crawler.report;

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
import java.time.temporal.ChronoUnit;
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
   * Builds a report from the current database state. Changes are relative to the last written
   * report; generating alone does not advance that baseline.
   *
   * @param conn Database connection
   * @return the report
   * @throws SQLException if a database access error occurs
   */
  public static Report generate(Connection conn) throws SQLException {
    return new Report(
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        queryChanges(conn),
        queryHealth(conn));
  }

  /**
   * Builds the report, writes it as JSON and as human-readable markdown, and advances the
   * reported-content baseline so the next report only includes changes detected after this one. The
   * baseline advances only if the report files are written successfully.
   *
   * @param conn Database connection
   * @param jsonPath destination path for the JSON report
   * @param markdownPath destination path for the markdown report
   * @return the written report
   * @throws SQLException if a database access error occurs
   * @throws IOException if a file cannot be written
   */
  public static Report generateAndWrite(Connection conn, String jsonPath, String markdownPath)
      throws SQLException, IOException {
    boolean originalAutoCommit = conn.getAutoCommit();
    conn.setAutoCommit(false);
    try {
      Report report = generate(conn);
      writeJson(report, jsonPath);
      Files.writeString(Path.of(markdownPath), toMarkdown(report));
      markReported(conn);
      conn.commit();
      return report;
    } catch (SQLException | IOException e) {
      conn.rollback();
      throw e;
    } finally {
      conn.setAutoCommit(originalAutoCommit);
    }
  }

  /**
   * Renders the report as self-describing markdown prose, suitable for direct inclusion in an email
   * or digest without knowledge of the crawler.
   *
   * @param report the report to render
   * @return the markdown text
   */
  public static String toMarkdown(Report report) {
    StringBuilder md = new StringBuilder();
    md.append("# Site monitor report\n\n");
    md.append(
        "Website change and availability report for the monitored sites, generated "
            + report.generatedAt()
            + ". Content changes are those detected since the previous report.\n");
    md.append("\n**Summary: " + summarize(report) + "**\n");

    md.append("\n## Content changes since the last report\n\n");
    if (report.changes().isEmpty()) {
      md.append("No content changes since the last report.\n");
    } else {
      for (Change change : report.changes()) {
        md.append(
            "- "
                + change.url()
                + " — "
                + change.status()
                + " (detected "
                + change.detectedAt()
                + " UTC)\n");
      }
    }

    md.append("\n## Availability (current)\n\n");
    Health health = report.health();
    md.append(
        health.ok()
            + " of "
            + health.total()
            + " monitored URLs are reachable and healthy."
            + " Auth-protected responses (401/403) count as reachable.\n");
    if (health.skipped() > 0) {
      md.append(health.skipped() + " URL(s) not fetched because robots.txt disallows them.\n");
    }
    for (Problem problem : health.problems()) {
      String cause = problem.error() != null ? problem.error() : "HTTP " + problem.statusCode();
      md.append(
          "- PROBLEM: "
              + problem.url()
              + " — "
              + cause
              + " (as of "
              + problem.checkedAt()
              + " UTC)\n");
    }
    return md.toString();
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
    // Pages whose current content differs from what the last report saw (reported_hash). Pages
    // never reported before are NEW; pages that changed and reverted read as equal and drop out.
    String sql =
        "SELECT url,"
            + " CASE WHEN reported_hash IS NULL THEN 'NEW' ELSE 'CHANGED' END AS change_status,"
            + " changed_at FROM page_content"
            + " WHERE reported_hash IS NULL OR reported_hash <> content_hash"
            + " ORDER BY changed_at DESC";
    List<Change> changes = new ArrayList<>();
    try (Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        changes.add(
            new Change(
                rs.getString("url"), rs.getString("change_status"), rs.getString("changed_at")));
      }
    }
    return changes;
  }

  private static void markReported(Connection conn) throws SQLException {
    String sql =
        "UPDATE page_content SET reported_hash = content_hash"
            + " WHERE reported_hash IS NULL OR reported_hash <> content_hash";
    try (Statement statement = conn.createStatement()) {
      statement.executeUpdate(sql);
    }
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
