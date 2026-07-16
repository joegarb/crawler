package com.joegarb.crawler.url;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Normalizes URLs to prevent duplicate entries for effectively identical URLs. */
public class UrlNormalizer {
  private static final Logger logger = LoggerFactory.getLogger(UrlNormalizer.class);

  /**
   * Extracts the hostname from a URL (e.g. "example.com" from "https://example.com/page").
   *
   * @param url The URL to extract the host from
   * @return The lowercase host, or null if extraction fails
   */
  public static String extractDomain(String url) {
    if (url == null) {
      return null;
    }
    String urlToParse = url.trim();
    if (urlToParse.isEmpty()) {
      return null;
    }
    if (!urlToParse.contains("://")) {
      urlToParse = "http://" + urlToParse;
    }
    try {
      io.mola.galimatias.Host host = URL.parse(urlToParse).host();
      return host != null ? host.toString().toLowerCase() : null;
    } catch (GalimatiasParseException e) {
      logger.debug("Could not extract domain from URL: {}", url);
      return null;
    }
  }

  /**
   * Normalizes a URL to a canonical form.
   *
   * @param url The URL to normalize
   * @return The normalized URL, or the original URL if normalization fails
   */
  public static String normalize(String url) {
    if (url == null) {
      return null;
    }

    String urlToParse = url.trim();
    if (urlToParse.isEmpty()) {
      return url;
    }

    if (!urlToParse.contains("://")) {
      urlToParse = "http://" + urlToParse;
    }

    try {
      return URL.parse(urlToParse).withFragment(null).toString();
    } catch (GalimatiasParseException e) {
      logger.warn("Failed to normalize URL: {}, using original", url, e);
      return url;
    }
  }
}
