package com.joegarb.crawler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/** Computes a content fingerprint for an HTML page based on its main visible text. */
public final class ContentHasher {
  private ContentHasher() {}

  // Ordered from most to least specific; the first that matches scopes the fingerprint.
  private static final List<String> CONTENT_SELECTORS = List.of("article", "main");

  /**
   * Returns a SHA-256 hex digest of the page's main visible text. Scoping to the main content
   * element (and hashing text rather than raw HTML) keeps the fingerprint stable against rotating
   * chrome such as related-post widgets, nav, and markup churn like CSRF tokens.
   *
   * @param html the raw HTML
   * @return lowercase hex SHA-256 of the normalized main visible text
   */
  public static String hash(String html) {
    String text = mainText(Jsoup.parse(html));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed to be available on every Java platform.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static String mainText(Document doc) {
    for (String selector : CONTENT_SELECTORS) {
      Elements elements = doc.select(selector);
      if (!elements.isEmpty()) {
        return elements.text();
      }
    }
    return doc.body() != null ? doc.body().text() : doc.text();
  }
}
