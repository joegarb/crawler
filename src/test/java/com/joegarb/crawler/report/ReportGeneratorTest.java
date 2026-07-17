package com.joegarb.crawler.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.joegarb.crawler.store.ContentStore;
import com.joegarb.crawler.store.MetadataStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for ReportGenerator. */
class ReportGeneratorTest {
  private Connection conn;

  @BeforeEach
  void setUp() throws SQLException {
    conn = DriverManager.getConnection("jdbc:sqlite::memory:");
    MetadataStore.createTable(conn);
    ContentStore.createTable(conn);
  }

  @Test
  void reportsNewAndChangedPages() throws SQLException {
    ContentStore.record(conn, "https://a.com", "h1"); // NEW
    ContentStore.record(conn, "https://a.com", "h2"); // CHANGED
    ContentStore.record(conn, "https://b.com", "hx"); // NEW
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertEquals(2, report.changes().size());
  }

  @Test
  void stablePagesAreNotReportedAgainAfterBeingReported(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1"); // NEW
    ReportGenerator.generateAndWrite(conn, dir.resolve("r1.json").toString());
    ContentStore.record(conn, "https://a.com", "h1"); // stable re-crawl
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertTrue(report.changes().isEmpty());
  }

  @Test
  void writingAReportAdvancesTheBaseline(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1");
    ReportGenerator.Report report =
        ReportGenerator.generateAndWrite(conn, dir.resolve("r1.json").toString());
    assertEquals(1, report.changes().size());
    assertEquals("NEW", report.changes().get(0).status());
    // Nothing changed since — next report is empty
    assertTrue(ReportGenerator.generate(conn).changes().isEmpty());
  }

  @Test
  void changeAfterReportIsReportedAsChanged(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1");
    ReportGenerator.generateAndWrite(conn, dir.resolve("r1.json").toString());
    ContentStore.record(conn, "https://a.com", "h2");
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertEquals(1, report.changes().size());
    assertEquals("CHANGED", report.changes().get(0).status());
  }

  @Test
  void changeThatWentStableBeforeTheReportIsStillReported(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1");
    ReportGenerator.generateAndWrite(conn, dir.resolve("r1.json").toString());
    ContentStore.record(conn, "https://a.com", "h2"); // CHANGED
    ContentStore.record(conn, "https://a.com", "h2"); // stable on later crawls
    assertEquals(1, ReportGenerator.generate(conn).changes().size());
  }

  @Test
  void changeThatRevertedBeforeTheReportIsNotReported(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1");
    ReportGenerator.generateAndWrite(conn, dir.resolve("r1.json").toString());
    ContentStore.record(conn, "https://a.com", "h2");
    ContentStore.record(conn, "https://a.com", "h1"); // back to the reported content
    assertTrue(ReportGenerator.generate(conn).changes().isEmpty());
  }

  @Test
  void generateAloneDoesNotAdvanceTheBaseline() throws SQLException {
    ContentStore.record(conn, "https://a.com", "h1");
    assertEquals(1, ReportGenerator.generate(conn).changes().size());
    assertEquals(1, ReportGenerator.generate(conn).changes().size());
  }

  @Test
  void flagsServerErrorsAndUnreachableUrlsAsProblems() throws SQLException {
    MetadataStore.markAsCrawled(conn, "https://ok.com", 200, null); // reachable
    MetadataStore.markAsCrawled(conn, "https://gated.com", 401, "HTTP error: 401"); // reachable
    MetadataStore.markAsCrawled(conn, "https://broken.com", 500, "HTTP error: 500"); // problem
    MetadataStore.markAsCrawled(
        conn, "https://down.com", null, "Network error: refused"); // problem
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertEquals(4, report.health().total());
    assertEquals(2, report.health().ok());
    assertEquals(2, report.health().problems().size());
  }

  @Test
  void treatsAuthAndNotFoundResponsesAsReachable() throws SQLException {
    MetadataStore.markAsCrawled(conn, "https://auth.com", 401, "HTTP error: 401");
    MetadataStore.markAsCrawled(conn, "https://forbidden.com", 403, "HTTP error: 403");
    MetadataStore.markAsCrawled(conn, "https://missing.com", 404, "HTTP error: 404");
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertTrue(report.health().problems().isEmpty());
    assertEquals(3, report.health().ok());
  }

  @Test
  void countsRobotsDisallowedUrlsAsSkipped() throws SQLException {
    MetadataStore.markAsCrawled(conn, "https://ok.com", 200, null);
    MetadataStore.markAsCrawled(conn, "https://blocked.com", null, "robots.txt disallowed");
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    assertEquals(2, report.health().total());
    assertEquals(1, report.health().ok());
    assertEquals(1, report.health().skipped());
    assertTrue(report.health().problems().isEmpty());
    // total decomposes into ok + skipped + problems
    assertEquals(
        report.health().total(),
        report.health().ok() + report.health().skipped() + report.health().problems().size());
  }

  @Test
  void writesJsonWithExpectedSections(@TempDir Path dir) throws Exception {
    ContentStore.record(conn, "https://a.com", "h1");
    MetadataStore.markAsCrawled(conn, "https://a.com", 200, null);
    ReportGenerator.Report report = ReportGenerator.generate(conn);
    Path out = dir.resolve("report.json");
    ReportGenerator.writeJson(report, out.toString());
    String json = Files.readString(out);
    assertTrue(json.contains("\"changes\""));
    assertTrue(json.contains("\"health\""));
    assertTrue(json.contains("\"generated_at\""));
    assertTrue(json.contains("\"skipped\""));
  }
}
