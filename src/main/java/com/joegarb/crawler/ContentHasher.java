package com.joegarb.crawler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jsoup.Jsoup;

/** Computes a content fingerprint for an HTML page based on its visible text. */
public final class ContentHasher {
  private ContentHasher() {}

  /**
   * Returns a SHA-256 hex digest of the page's normalized visible text. Hashing the extracted text
   * rather than raw HTML avoids false positives from markup churn such as CSRF tokens, timestamps,
   * or reordered attributes.
   *
   * @param html the raw HTML
   * @return lowercase hex SHA-256 of the normalized visible text
   */
  public static String hash(String html) {
    String text = Jsoup.parse(html).text();
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
