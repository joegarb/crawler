package com.joegarb.crawler;

import io.mola.galimatias.GalimatiasParseException;
import io.mola.galimatias.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fetches, parses, and caches robots.txt rules per domain for the lifetime of the process. */
public class RobotsCache {
  private static final Logger logger = LoggerFactory.getLogger(RobotsCache.class);
  private static final PageFetcher pageFetcher = new PageFetcher();
  private static final ConcurrentHashMap<String, RobotsRules> cache = new ConcurrentHashMap<>();

  static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), OptionalLong.empty());

  public record RobotsRule(boolean allow, String path) {}

  public record RobotsRules(List<RobotsRule> rules, OptionalLong crawlDelayMs) {
    public RobotsRules {
      rules = List.copyOf(rules);
    }
  }

  /**
   * Returns whether the given URL is allowed by the cached robots.txt for its domain.
   *
   * @param url The URL to check
   * @return true if allowed (including when robots.txt is missing or unreachable)
   */
  public static boolean isAllowed(String url) {
    String domain = UrlNormalizer.extractDomain(url);
    if (domain == null) {
      return true;
    }
    String scheme = extractScheme(url);
    RobotsRules rules = getRules(domain, scheme);
    String path = extractPath(url);
    return isAllowedPath(path, rules);
  }

  /**
   * Returns the Crawl-delay in milliseconds from the cached robots.txt for the given domain, if
   * present.
   *
   * @param domain The domain to check
   * @return The Crawl-delay in milliseconds, or empty if not specified or not yet cached
   */
  public static OptionalLong getCrawlDelayMs(String domain) {
    if (domain == null) {
      return OptionalLong.empty();
    }
    RobotsRules rules = cache.get(domain);
    return rules != null ? rules.crawlDelayMs() : OptionalLong.empty();
  }

  static RobotsRules getRules(String domain, String scheme) {
    return cache.computeIfAbsent(domain, d -> fetchAndParse(d, scheme));
  }

  private static RobotsRules fetchAndParse(String domain, String scheme) {
    String robotsUrl = scheme + "://" + domain + "/robots.txt";
    logger.debug("Fetching robots.txt for domain: {}", domain);
    PageFetcher.FetchResult result = pageFetcher.fetch(robotsUrl);
    if (result.success()) {
      return parse(result.response().body(), userAgentProduct());
    }
    Integer status = result.httpStatusCode();
    if (status != null && status >= 500) {
      logger.warn("Failed to fetch robots.txt for {} (status {}), failing open", domain, status);
    }
    return ALLOW_ALL;
  }

  private static String userAgentProduct() {
    try {
      String title = RobotsCache.class.getPackage().getImplementationTitle();
      if (title != null && !title.isBlank()) {
        return title;
      }
    } catch (Exception ignored) {
    }
    return "crawler";
  }

  private static String extractScheme(String url) {
    int idx = url.indexOf("://");
    return idx >= 0 ? url.substring(0, idx).toLowerCase() : "https";
  }

  private static String extractPath(String url) {
    try {
      URL parsed = URL.parse(url);
      String path = parsed.path();
      String query = parsed.query();
      if (query != null && !query.isEmpty()) {
        return path + "?" + query;
      }
      return path != null ? path : "/";
    } catch (GalimatiasParseException e) {
      return "/";
    }
  }

  /**
   * Returns whether the given path is allowed by the given rules. Package-private for testing.
   *
   * <p>Among matching rules (prefix match), the longest pattern wins. If two patterns are the same
   * length, Allow beats Disallow.
   *
   * @param path URL path (and optional query) to check
   * @param rules Parsed robots.txt rules
   * @return true if the path is allowed
   */
  static boolean isAllowedPath(String path, RobotsRules rules) {
    RobotsRule best = null;
    for (RobotsRule rule : rules.rules()) {
      if (path.startsWith(rule.path())) {
        if (best == null
            || rule.path().length() > best.path().length()
            || (rule.path().length() == best.path().length() && rule.allow())) {
          best = rule;
        }
      }
    }
    return best == null || best.allow();
  }

  /**
   * Parses a robots.txt file and returns the rules for the given user-agent. Package-private for
   * testing.
   *
   * @param content The full robots.txt content
   * @param userAgentProduct The product name to match (e.g. "crawler"), case-insensitive
   * @return Parsed rules for the best matching user-agent block
   */
  static RobotsRules parse(String content, String userAgentProduct) {
    if (content == null || content.isBlank()) {
      return ALLOW_ALL;
    }

    String[] lines = content.split("\\r?\\n");
    List<String> currentAgents = new ArrayList<>();
    List<String[]> currentDirectives = new ArrayList<>();
    List<String[]> bestDirectives = null;
    boolean exactMatch = false;

    // Iterate one past the end so the last group is evaluated even without a trailing blank line
    for (int i = 0; i <= lines.length; i++) {
      String line = i < lines.length ? lines[i].trim() : "";
      int hash = line.indexOf('#');
      if (hash >= 0) {
        line = line.substring(0, hash).trim();
      }

      if (line.isEmpty()) {
        if (!currentAgents.isEmpty()) {
          boolean isExact = false;
          boolean isWild = false;
          for (String agent : currentAgents) {
            if (agent.equalsIgnoreCase(userAgentProduct)) {
              isExact = true;
              break;
            } else if (agent.equals("*")) {
              isWild = true;
            }
          }
          if (isExact && !exactMatch) {
            bestDirectives = new ArrayList<>(currentDirectives);
            exactMatch = true;
          } else if (isWild && !exactMatch && bestDirectives == null) {
            bestDirectives = new ArrayList<>(currentDirectives);
          }
        }
        currentAgents.clear();
        currentDirectives.clear();
      } else if (line.toLowerCase().startsWith("user-agent:")) {
        currentAgents.add(line.substring("user-agent:".length()).trim());
      } else if (line.toLowerCase().startsWith("allow:")) {
        currentDirectives.add(new String[] {"Allow", line.substring("allow:".length()).trim()});
      } else if (line.toLowerCase().startsWith("disallow:")) {
        currentDirectives.add(
            new String[] {"Disallow", line.substring("disallow:".length()).trim()});
      } else if (line.toLowerCase().startsWith("crawl-delay:")) {
        currentDirectives.add(
            new String[] {"Crawl-delay", line.substring("crawl-delay:".length()).trim()});
      }
    }

    if (bestDirectives == null) {
      return ALLOW_ALL;
    }

    List<RobotsRule> rules = new ArrayList<>();
    OptionalLong crawlDelayMs = OptionalLong.empty();
    for (String[] directive : bestDirectives) {
      switch (directive[0]) {
        case "Allow" -> rules.add(new RobotsRule(true, directive[1]));
        case "Disallow" -> {
          if (!directive[1].isEmpty()) {
            rules.add(new RobotsRule(false, directive[1]));
          }
        }
        case "Crawl-delay" -> {
          try {
            crawlDelayMs = OptionalLong.of((long) (Double.parseDouble(directive[1]) * 1000));
          } catch (NumberFormatException ignored) {
          }
        }
        default -> {}
      }
    }

    return new RobotsRules(Collections.unmodifiableList(rules), crawlDelayMs);
  }
}
