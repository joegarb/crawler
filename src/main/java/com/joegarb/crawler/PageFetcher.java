package com.joegarb.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fetches web pages via HTTP. */
public class PageFetcher {
  private static final Logger logger = LoggerFactory.getLogger(PageFetcher.class);
  private static final String USER_AGENT = getUserAgent();
  private static final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(Configuration.HTTP_TIMEOUT_SECONDS))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  /**
   * Gets the User-Agent string from application metadata.
   *
   * @return User-Agent string in format "ApplicationName/Version"
   */
  private static String getUserAgent() {
    try {
      Package pkg = PageFetcher.class.getPackage();
      String implementationTitle = pkg.getImplementationTitle();
      String implementationVersion = pkg.getImplementationVersion();

      if (implementationTitle != null && implementationVersion != null) {
        return implementationTitle + "/" + implementationVersion;
      }
    } catch (Exception e) {
      logger.debug("Could not read User-Agent from package metadata: {}", e.getMessage());
    }

    // Fallback
    return "crawler/1.0";
  }

  /**
   * Result of a page fetch operation.
   *
   * @param success Whether the fetch was successful (HTTP 2xx status)
   * @param response HTTP response if an HTTP response was received, null for network errors
   * @param errorMessage Error message if failed, null if successful
   */
  public record FetchResult(boolean success, HttpResponse<String> response, String errorMessage) {
    public static FetchResult success(HttpResponse<String> response) {
      return new FetchResult(true, response, null);
    }

    public static FetchResult httpError(HttpResponse<String> response) {
      return new FetchResult(
          false, response, "HTTP error: " + response.statusCode() + " " + response.uri());
    }

    public static FetchResult failure(String errorMessage) {
      return new FetchResult(false, null, errorMessage);
    }

    /**
     * Returns the HTTP status code if a response was received, null otherwise.
     *
     * @return HTTP status code or null
     */
    public Integer httpStatusCode() {
      return response != null ? response.statusCode() : null;
    }

    /**
     * Checks if the response content type is HTML.
     *
     * @return true if the response appears to be HTML, false otherwise
     */
    public boolean isHtml() {
      if (response == null) {
        return false;
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      // Check if Content-Type contains "text/html" (case-insensitive)
      return contentType.toLowerCase().contains("text/html");
    }
  }

  /**
   * Fetches a web page.
   *
   * @param url The URL to fetch
   * @return FetchResult containing the response or error information
   */
  public FetchResult fetch(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(Configuration.HTTP_TIMEOUT_SECONDS))
              .header("User-Agent", USER_AGENT)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      // Consider 2xx status codes as success, everything else as failure
      // Note: With followRedirects enabled, 3xx should be automatically followed.
      // If we see a 3xx here, it indicates a redirect loop or too many redirects.
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return FetchResult.success(response);
      } else {
        // HTTP error response (3xx redirect issues, 4xx, 5xx, etc.)
        return FetchResult.httpError(response);
      }
    } catch (IOException e) {
      logger.warn("Failed to fetch URL: {} - {}", url, e.getMessage());
      return FetchResult.failure("Network error: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while fetching URL: {}", url);
      return FetchResult.failure("Interrupted");
    } catch (IllegalArgumentException e) {
      logger.warn("Invalid URL: {} - {}", url, e.getMessage());
      return FetchResult.failure("Invalid URL: " + e.getMessage());
    }
  }
}
