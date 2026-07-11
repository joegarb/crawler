package com.joegarb.crawler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jsoup.Jsoup;

/** Computes a content fingerprint for an HTML page based on its main visible text. */
public final class ContentHasher {
  private ContentHasher() {}

  // Landmark first, whole-body fallback. A richer extractor (e.g. Readability) can be prepended.
  private static final ContentExtractor DEFAULT_EXTRACTOR =
      new ContentExtractorChain(new SelectorContentExtractor(), new BodyContentExtractor());

  /**
   * Returns a SHA-256 hex digest of the page's main visible text using the default extractor.
   *
   * @param html the raw HTML
   * @return lowercase hex SHA-256 of the extracted text
   */
  public static String hash(String html) {
    return hash(html, DEFAULT_EXTRACTOR);
  }

  /**
   * Returns a SHA-256 hex digest of the page text selected by the given extractor. Hashing
   * extracted text rather than raw HTML keeps the fingerprint stable against markup churn and
   * rotating chrome.
   *
   * @param html the raw HTML
   * @param extractor selects which text to fingerprint
   * @return lowercase hex SHA-256 of the extracted text
   */
  public static String hash(String html, ContentExtractor extractor) {
    String text = extractor.extract(Jsoup.parse(html)).orElse("");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed to be available on every Java platform.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
