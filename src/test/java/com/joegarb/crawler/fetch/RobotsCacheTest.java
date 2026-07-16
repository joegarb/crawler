package com.joegarb.crawler.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for RobotsCache parsing and path-matching logic. */
class RobotsCacheTest {

  @Test
  void allowsAllWhenNoRules() {
    RobotsCache.RobotsRules rules = RobotsCache.parse("", "testbot");
    assertTrue(RobotsCache.isAllowedPath("/anything", rules));
  }

  @Test
  void disallowsPathMatchingDisallowRule() {
    String robotsTxt = "User-agent: *\nDisallow: /private/\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertFalse(RobotsCache.isAllowedPath("/private/page", rules));
  }

  @Test
  void allowsPathNotMatchingDisallowRule() {
    String robotsTxt = "User-agent: *\nDisallow: /private/\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertTrue(RobotsCache.isAllowedPath("/public/page", rules));
  }

  @Test
  void allowBeatsDisallowWhenSameLength() {
    String robotsTxt = "User-agent: *\nDisallow: /p\nAllow: /p\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertTrue(RobotsCache.isAllowedPath("/page", rules));
  }

  @Test
  void longerPatternWinsOverShorter() {
    String robotsTxt = "User-agent: *\nAllow: /private/public\nDisallow: /private/\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertFalse(RobotsCache.isAllowedPath("/private/secret", rules));
    assertTrue(RobotsCache.isAllowedPath("/private/public/page", rules));
  }

  @Test
  void emptyDisallowMeansAllowAll() {
    String robotsTxt = "User-agent: *\nDisallow:\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertTrue(RobotsCache.isAllowedPath("/anything", rules));
  }

  @Test
  void parsesCrawlDelayInMilliseconds() {
    String robotsTxt = "User-agent: *\nCrawl-delay: 5\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "testbot");
    assertTrue(rules.crawlDelayMs().isPresent());
    assertEquals(5000L, rules.crawlDelayMs().getAsLong());
  }

  @Test
  void prefersSpecificUserAgentOverWildcard() {
    String robotsTxt =
        "User-agent: crawler\nDisallow: /crawleronly/\n\nUser-agent: *\nDisallow: /everyone/\n";
    RobotsCache.RobotsRules rules = RobotsCache.parse(robotsTxt, "crawler");
    assertFalse(RobotsCache.isAllowedPath("/crawleronly/page", rules));
    assertTrue(RobotsCache.isAllowedPath("/everyone/page", rules));
  }
}
