package com.joegarb.crawler;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Normalizes URLs to prevent duplicate entries for effectively identical URLs. */
public class UrlNormalizer {
  private static final Logger logger = LoggerFactory.getLogger(UrlNormalizer.class);

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

  /**
   * Extracts the subdomain (host) from a URL (e.g., "crawlme.example.com" from
   * "https://crawlme.example.com/page").
   *
   * @param url The URL to extract subdomain from
   * @return The subdomain (host), or null if extraction fails
   */
  public static String extractSubdomain(String url) {
    if (url == null || url.trim().isEmpty()) {
      return null;
    }

    String urlToParse = url.trim();

    if (!urlToParse.contains("://")) {
      urlToParse = "http://" + urlToParse;
    }

    try {
      URL parsedUrl = URL.parse(urlToParse);
      io.mola.galimatias.Host host = parsedUrl.host();
      if (host != null) {
        return host.toString().toLowerCase();
      }
    } catch (GalimatiasParseException e) {
      logger.debug("Could not parse URL for subdomain extraction: {}", url);
    }
    return null;
  }
}
