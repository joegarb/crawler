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
   * @param baseUrl The base URL used to resolve relative links
   * @param targetSubdomain The subdomain to filter links by (e.g., "crawlme.example.com")
   * @return List of normalized URLs
   */
  public static List<String> extractLinks(
      String htmlContent, String baseUrl, String targetSubdomain) {
    List<String> links = new ArrayList<>();

    if (targetSubdomain == null) {
      logger.warn("Target subdomain not set, cannot filter links");
      return links;
    }

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

          // Check if on same subdomain as target
          String linkSubdomain = UrlNormalizer.extractSubdomain(normalizedUrl);
          if (linkSubdomain != null && linkSubdomain.equals(targetSubdomain)) {
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
}
