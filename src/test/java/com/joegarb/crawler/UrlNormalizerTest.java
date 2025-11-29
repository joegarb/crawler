package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for UrlNormalizer. */
class UrlNormalizerTest {

  @Test
  void normalizesComplexUrl() {
    String url1 = "HTTPS://EXAMPLE.COM:443/path/to/page?param1=value1&param2=value2#fragment";
    String url2 = "https://example.com/path/to/page?param1=value1&param2=value2";
    assertEquals(url2, UrlNormalizer.normalize(url1));
    assertEquals(url2, UrlNormalizer.normalize(url2));
  }

  @Test
  void assumesHttpSchemeWhenMissing() {
    assertEquals("http://joe.org/", UrlNormalizer.normalize("joe.org"));
    assertEquals("http://example.com/path", UrlNormalizer.normalize("example.com/path"));
  }

  @Test
  void removesDefaultHttpPort() {
    assertEquals("http://example.com/", UrlNormalizer.normalize("http://example.com:80"));
    assertEquals("http://example.com/path", UrlNormalizer.normalize("http://example.com:80/path"));
  }

  @Test
  void preservesNonDefaultPorts() {
    assertEquals("http://example.com:8080/", UrlNormalizer.normalize("http://example.com:8080"));
    assertEquals("https://example.com:8443/", UrlNormalizer.normalize("https://example.com:8443"));
  }

  @Test
  void handlesNullAndEmpty() {
    assertEquals(null, UrlNormalizer.normalize(null));
    assertEquals("", UrlNormalizer.normalize(""));
  }

  @Test
  void trimsWhitespace() {
    assertEquals("https://example.com/", UrlNormalizer.normalize("  https://example.com  "));
    assertEquals("http://example.com/", UrlNormalizer.normalize("  example.com  "));
  }

  @Test
  void handlesInvalidUrl() {
    String invalid = "not a valid url";
    assertEquals(invalid, UrlNormalizer.normalize(invalid));
  }
}
