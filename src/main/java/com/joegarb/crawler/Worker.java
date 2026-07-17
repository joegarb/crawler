package com.joegarb.crawler;

import static com.joegarb.crawler.store.FrontierStore.FrontierUrl;

import com.joegarb.crawler.extract.ContentHasher;
import com.joegarb.crawler.extract.LinkExtractor;
import com.joegarb.crawler.fetch.PageFetcher;
import com.joegarb.crawler.fetch.RobotsCache;
import com.joegarb.crawler.fetch.Validators;
import com.joegarb.crawler.store.ChangeStatus;
import com.joegarb.crawler.store.ContentStore;
import com.joegarb.crawler.store.DatabaseManager;
import com.joegarb.crawler.store.DomainAccessStore;
import com.joegarb.crawler.store.FrontierStore;
import com.joegarb.crawler.store.MetadataStore;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Processes a single claimed frontier URL: fetch, extract, record, and dequeue. */
public class Worker implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(Worker.class);
  private static final PageFetcher pageFetcher = new PageFetcher();

  private final FrontierUrl frontierUrl;

  public Worker(FrontierUrl frontierUrl) {
    this.frontierUrl = frontierUrl;
  }

  @Override
  public void run() {
    try (Connection conn = DatabaseManager.getConnection()) {
      process(conn);
    } catch (SQLException e) {
      logger.error("Database error processing {}", frontierUrl.url(), e);
    }
  }

  private void process(Connection conn) throws SQLException {
    // Check robots.txt — skip disallowed URLs without fetching them
    if (!RobotsCache.isAllowed(frontierUrl.url())) {
      logger.info("Skipping robots.txt-disallowed URL: {}", frontierUrl.url());
      MetadataStore.markAsCrawled(conn, frontierUrl.url(), null, "robots.txt disallowed");
      FrontierStore.removeUrl(conn, frontierUrl.id());
      return;
    }

    // Persist the domain's robots.txt Crawl-delay so the frontier enforces it when
    // claiming subsequent URLs for the domain
    if (frontierUrl.domain() != null) {
      OptionalLong robotsCrawlDelayMs = RobotsCache.getCrawlDelayMs(frontierUrl.domain());
      if (robotsCrawlDelayMs.isPresent()) {
        DomainAccessStore.recordCrawlDelay(
            conn, frontierUrl.domain(), robotsCrawlDelayMs.getAsLong());
      }
    }

    Validators validators = MetadataStore.getValidators(conn, frontierUrl.url());
    PageFetcher.FetchResult result = pageFetcher.fetch(frontierUrl.url(), validators);

    if (result.isNotModified()) {
      // Server confirmed the content is unchanged — no body to hash, no links to extract.
      // Keep the validators that produced the 304 for the next conditional request.
      logger.info("{} not modified (304)", frontierUrl.url());
      MetadataStore.markAsCrawled(
          conn, frontierUrl.url(), result.httpStatusCode(), null, validators);
    } else if (result.success() && result.isHtml()) {
      List<String> links = LinkExtractor.extractLinks(result.response().body(), frontierUrl.url());

      StringBuilder output = new StringBuilder(frontierUrl.url());
      for (String link : links) {
        output.append("\n  ").append(link);
      }
      logger.info("{}", output.toString());

      // Add links that need crawling to the frontier
      List<String> urlsToAdd = new ArrayList<>();
      for (String link : links) {
        if (!MetadataStore.hasBeenCrawled(conn, link)) {
          urlsToAdd.add(link);
        }
      }
      if (!urlsToAdd.isEmpty()) {
        FrontierStore.addUrls(conn, urlsToAdd);
      }

      if (!Configuration.CHANGE_TRACKING_EXCLUDER.isExcluded(frontierUrl.url())) {
        String contentHash = ContentHasher.hash(result.response().body());
        ChangeStatus changeStatus = ContentStore.record(conn, frontierUrl.url(), contentHash);
        if (changeStatus != ChangeStatus.UNCHANGED) {
          logger.info("Content {} for {}", changeStatus, frontierUrl.url());
        }
      }

      MetadataStore.markAsCrawled(
          conn, frontierUrl.url(), result.httpStatusCode(), null, responseValidators(result));
    } else if (result.success() && !result.isHtml()) {
      // Successfully fetched but not HTML - mark as crawled but don't extract links
      logger.info(frontierUrl.url());
      MetadataStore.markAsCrawled(
          conn, frontierUrl.url(), result.httpStatusCode(), null, responseValidators(result));
    } else {
      logger.warn("Failed to fetch URL: {} - {}", frontierUrl.url(), result.errorMessage());

      MetadataStore.markAsCrawled(
          conn, frontierUrl.url(), result.httpStatusCode(), result.errorMessage());
    }

    DomainAccessStore.recordAccess(conn, frontierUrl.domain());
    FrontierStore.removeUrl(conn, frontierUrl.id());
  }

  private static Validators responseValidators(PageFetcher.FetchResult result) {
    String etag = result.response().headers().firstValue("ETag").orElse(null);
    String lastModified = result.response().headers().firstValue("Last-Modified").orElse(null);
    if (etag == null && lastModified == null) {
      return null;
    }
    return new Validators(etag, lastModified);
  }
}
