package com.joegarb.crawler;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extracts links from HTML content. */
public class LinkExtractor {
  private static final Logger logger = LoggerFactory.getLogger(LinkExtractor.class);

  /**
   * Extracts all links from HTML content that could be crawled.
   *
   * @param htmlContent The HTML content to parse
   * @param baseUrl The base URL used to resolve relative links (also used to determine target host)
   * @return List of normalized URLs
   */
  public static List<String> extractLinks(String htmlContent, String baseUrl) {
    List<String> links = new ArrayList<>();

    String targetHost = extractHost(baseUrl);

    try {
      Document doc = Jsoup.parse(htmlContent, baseUrl);
      Elements linkElements = doc.select("a[href]");

      for (Element element : linkElements) {
        String href = element.attr("href");
        if (href == null || href.isEmpty()) {
          continue;
        }

        try {
          // Resolve relative URLs against the base URL
          URL absoluteUrl = URL.parse(baseUrl).resolve(href);
          String normalizedUrl = UrlNormalizer.normalize(absoluteUrl.toString());

          // Check if link should be included based on host restriction
          if (shouldIncludeLink(normalizedUrl, targetHost)) {
            links.add(normalizedUrl);
          }
        } catch (GalimatiasParseException e) {
          logger.debug("Skipping invalid link: {}", href);
        }
      }
    } catch (Exception e) {
      logger.warn("Error parsing HTML for links: {}", e.getMessage());
    }

    return links;
  }

  /**
   * Extracts the host from a URL (e.g., "crawlme.example.com" from
   * "https://crawlme.example.com/page").
   *
   * @param url The URL to extract host from
   * @return The host (e.g., "crawlme.example.com"), or null if extraction fails
   */
  private static String extractHost(String url) {
    if (url == null || url.trim().isEmpty()) {
      return null;
    }

    String urlToParse = url.trim();

    // Assume http:// if not specified
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
      logger.debug("Could not parse URL for host extraction: {}", url);
    }
    return null;
  }

  /**
   * Determines if a link should be included based on host restriction configuration.
   *
   * @param normalizedUrl The normalized URL to check
   * @param targetHost The target host (e.g., "crawlme.example.com")
   * @return true if the link should be included, false otherwise
   */
  private static boolean shouldIncludeLink(String normalizedUrl, String targetHost) {
    if (!Configuration.RESTRICT_TO_HOST) {
      return true;
    }

    if (targetHost == null) {
      logger.warn("Target host not set, cannot filter links");
      return false;
    }

    String linkHost = extractHost(normalizedUrl);
    if (linkHost == null) {
      return false;
    }

    return linkHost.equals(targetHost) || linkHost.endsWith("." + targetHost);
  }
}
