package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for LinkExtractor. */
class LinkExtractorTest {
  private static final String BASE_URL = "https://crawlme.example.com/page";

  @Test
  void extractsAbsoluteLinksOnSameHost() {
    String html =
        "<html><body><a href=\"https://crawlme.example.com/other\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/other"));
  }

  @Test
  void resolvesRelativeLinks() {
    String html = "<html><body><a href=\"/relative\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/relative"));
  }

  @Test
  void filtersOutExternalLinks() {
    String html =
        "<html><body>"
            + "<a href=\"https://crawlme.example.com/internal\">Internal</a>"
            + "<a href=\"https://facebook.com/external\">External</a>"
            + "<a href=\"https://example.com/other\">Other Domain</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/internal"));
    assertFalse(links.contains("https://facebook.com/external"));
    assertFalse(links.contains("https://example.com/other"));
  }

  @Test
  void extractsMultipleLinks() {
    String html =
        "<html><body>"
            + "<a href=\"/page1\">Page 1</a>"
            + "<a href=\"/page2\">Page 2</a>"
            + "<a href=\"https://crawlme.example.com/page3\">Page 3</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(3, links.size());
    assertTrue(links.contains("https://crawlme.example.com/page1"));
    assertTrue(links.contains("https://crawlme.example.com/page2"));
    assertTrue(links.contains("https://crawlme.example.com/page3"));
  }

  @Test
  void handlesEmptyHtml() {
    String html = "<html><body></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertTrue(links.isEmpty());
  }

  @Test
  void resolvesRelativePaths() {
    // Links without scheme are resolved relative to base URL
    String html = "<html><body><a href=\"not-a-valid-url\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    // Relative link gets resolved to base URL's domain
    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/not-a-valid-url"));
  }

  @Test
  void allowsSubdomainsOfHost() {
    // Should allow subdomains of the target host
    // e.g., if target is "crawlme.example.com", allow "sub.crawlme.example.com"
    String html =
        "<html><body>"
            + "<a href=\"https://crawlme.example.com/exact\">Exact Match</a>"
            + "<a href=\"https://sub.crawlme.example.com/subdomain\">Subdomain of Host</a>"
            + "<a href=\"https://deep.sub.crawlme.example.com/deep\">Deep Subdomain</a>"
            + "<a href=\"https://example.com/parent\">Parent Domain</a>"
            + "<a href=\"https://other.example.com/different\">Different Host</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(3, links.size());
    assertTrue(links.contains("https://crawlme.example.com/exact"));
    assertTrue(links.contains("https://sub.crawlme.example.com/subdomain"));
    assertTrue(links.contains("https://deep.sub.crawlme.example.com/deep"));
    assertFalse(links.contains("https://example.com/parent"));
    assertFalse(links.contains("https://other.example.com/different"));
  }
}
