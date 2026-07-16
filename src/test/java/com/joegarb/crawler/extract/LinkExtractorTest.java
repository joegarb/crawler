package com.joegarb.crawler.extract;

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
  void skipsNofollowLinks() {
    String html =
        "<html><body>"
            + "<a href=\"/follow\">Follow</a>"
            + "<a href=\"/nofollow\" rel=\"nofollow\">Nofollow</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/follow"));
    assertFalse(links.contains("https://crawlme.example.com/nofollow"));
  }

  @Test
  void skipsNofollowLinksWithMultipleRelValues() {
    String html =
        "<html><body>"
            + "<a href=\"/nofollow\" rel=\"nofollow noreferrer\">Nofollow</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertTrue(links.isEmpty());
  }

  @Test
  void skipsAllLinksWhenPageMetaRobotsNofollow() {
    String html =
        "<html><head><meta name=\"robots\" content=\"nofollow\"></head>"
            + "<body>"
            + "<a href=\"/page1\">Page 1</a>"
            + "<a href=\"/page2\">Page 2</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertTrue(links.isEmpty());
  }

  @Test
  void skipsAllLinksWhenPageMetaRobotsNofollowWithMultipleDirectives() {
    String html =
        "<html><head><meta name=\"robots\" content=\"noindex, nofollow\"></head>"
            + "<body><a href=\"/page1\">Page 1</a></body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertTrue(links.isEmpty());
  }

  @Test
  void skipsNonHttpLinks() {
    String html =
        "<html><body>"
            + "<a href=\"mailto:founders@crawlme.example.com\">Email</a>"
            + "<a href=\"tel:+15551234567\">Call</a>"
            + "<a href=\"javascript:void(0)\">JS</a>"
            + "<a href=\"/real-page\">Real</a>"
            + "</body></html>";
    List<String> links = LinkExtractor.extractLinks(html, BASE_URL);

    assertEquals(1, links.size());
    assertTrue(links.contains("https://crawlme.example.com/real-page"));
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
