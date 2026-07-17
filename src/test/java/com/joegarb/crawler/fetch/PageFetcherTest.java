package com.joegarb.crawler.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for PageFetcher conditional requests. */
class PageFetcherTest {
  private HttpServer server;
  private String url;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          if ("\"v1\"".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
          } else {
            byte[] body = "<html><body>hi</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.getResponseHeaders().set("ETag", "\"v1\"");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void unconditionalFetchReturnsFullResponse() {
    PageFetcher.FetchResult result = new PageFetcher().fetch(url);
    assertTrue(result.success());
    assertFalse(result.isNotModified());
    assertTrue(result.isHtml());
    assertEquals("\"v1\"", result.response().headers().firstValue("ETag").orElseThrow());
  }

  @Test
  void matchingEtagYieldsNotModified() {
    PageFetcher.FetchResult result = new PageFetcher().fetch(url, new Validators("\"v1\"", null));
    assertTrue(result.success());
    assertTrue(result.isNotModified());
    assertEquals(304, result.httpStatusCode());
  }

  @Test
  void staleEtagYieldsFullResponse() {
    PageFetcher.FetchResult result = new PageFetcher().fetch(url, new Validators("\"v0\"", null));
    assertTrue(result.success());
    assertFalse(result.isNotModified());
    assertTrue(result.isHtml());
  }
}
