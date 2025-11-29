package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for LinkExtractor. */
class LinkExtractorTest {
  private static final String BASE_URL = "https://crawlme.example.com/page";
  private static final String TARGET_SUBDOMAIN = "crawlme.example.com";

  @Test
  void extractsAbsoluteLinksOnSameSubdomain() {
    String html =
        "<html><body><a href=\"https://crawlme.example.com/other\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/other"));
  }

  @Test
  void resolvesRelativeLinks() {
    String html = "<html><body><a href=\"/relative\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

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
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

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
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

    assertEquals(3, links.size());
    assertTrue(links.contains("https://crawlme.example.com/page1"));
    assertTrue(links.contains("https://crawlme.example.com/page2"));
    assertTrue(links.contains("https://crawlme.example.com/page3"));
  }

  @Test
  void handlesEmptyHtml() {
    String html = "<html><body></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

    assertTrue(links.isEmpty());
  }

  @Test
  void resolvesRelativePaths() {
    // Links without scheme are resolved relative to base URL
    String html = "<html><body><a href=\"not-a-valid-url\">Link</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL, TARGET_SUBDOMAIN);

    // Relative link gets resolved to base URL's domain
    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/not-a-valid-url"));
  }
}
