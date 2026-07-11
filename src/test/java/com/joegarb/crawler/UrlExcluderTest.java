package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for UrlExcluder. */
class UrlExcluderTest {
  @Test
  void emptyListExcludesNothing() {
    UrlExcluder excluder = new UrlExcluder(List.of());
    assertFalse(excluder.isExcluded("https://example.com/anything"));
  }

  @Test
  void trailingWildcardMatchesPathRegardlessOfScheme() {
    UrlExcluder excluder = new UrlExcluder(List.of("*/intelligence/"));
    assertTrue(excluder.isExcluded("https://bite.engineering/intelligence/"));
    assertTrue(excluder.isExcluded("http://other.com/intelligence/"));
    assertFalse(excluder.isExcluded("https://bite.engineering/about/"));
    assertFalse(excluder.isExcluded("https://bite.engineering/intelligence/post/"));
  }

  @Test
  void substringWildcardMatchesAnywhere() {
    UrlExcluder excluder = new UrlExcluder(List.of("*feed*"));
    assertTrue(excluder.isExcluded("https://example.com/blog/feed/"));
    assertTrue(excluder.isExcluded("https://example.com/feed"));
    assertFalse(excluder.isExcluded("https://example.com/articles/"));
  }

  @Test
  void exactPatternRequiresFullMatch() {
    UrlExcluder excluder = new UrlExcluder(List.of("https://example.com/x"));
    assertTrue(excluder.isExcluded("https://example.com/x"));
    assertFalse(excluder.isExcluded("https://example.com/x/y"));
  }

  @Test
  void matchesAnyOfSeveralPatternsAndIgnoresBlanks() {
    UrlExcluder excluder = new UrlExcluder(List.of("*/intelligence/", "", "  ", "*/tags/*"));
    assertTrue(excluder.isExcluded("https://example.com/intelligence/"));
    assertTrue(excluder.isExcluded("https://example.com/tags/java/"));
    assertFalse(excluder.isExcluded("https://example.com/home/"));
  }
}
