package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Tests for the content extractors and their chaining. */
class ContentExtractorTest {
  private static Document parse(String html) {
    return Jsoup.parse(html);
  }

  @Test
  void selectorPrefersArticleThenMain() {
    SelectorContentExtractor selector = new SelectorContentExtractor();
    assertEquals(
        Optional.of("In article"),
        selector.extract(parse("<body><article>In article</article><main>In main</main></body>")));
    assertEquals(
        Optional.of("In main"), selector.extract(parse("<body><main>In main</main></body>")));
  }

  @Test
  void selectorEmptyWhenNoLandmark() {
    assertTrue(
        new SelectorContentExtractor().extract(parse("<body><p>Just body</p></body>")).isEmpty());
  }

  @Test
  void bodyAlwaysApplies() {
    assertEquals(
        Optional.of("Just body"),
        new BodyContentExtractor().extract(parse("<body><p>Just body</p></body>")));
  }

  @Test
  void chainReturnsFirstThatApplies() {
    ContentExtractor chain =
        new ContentExtractorChain(new SelectorContentExtractor(), new BodyContentExtractor());
    // Landmark present -> selector wins, chrome excluded.
    assertEquals(
        Optional.of("Content"),
        chain.extract(parse("<body><article>Content</article><footer>Chrome</footer></body>")));
    // No landmark -> falls through to body.
    assertEquals(Optional.of("Only body"), chain.extract(parse("<body><p>Only body</p></body>")));
  }

  @Test
  void hashHonorsInjectedExtractor() {
    String html = "<body><article>same</article><aside>differs a</aside></body>";
    String other = "<body><article>same</article><aside>differs b</aside></body>";
    // Body extractor sees the differing aside; selector extractor does not.
    assertNotEquals(
        ContentHasher.hash(html, new BodyContentExtractor()),
        ContentHasher.hash(other, new BodyContentExtractor()));
    assertEquals(
        ContentHasher.hash(html, new SelectorContentExtractor()),
        ContentHasher.hash(other, new SelectorContentExtractor()));
  }
}
