package com.joegarb.crawler.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Tests for ContentHasher. */
class ContentHasherTest {
  @Test
  void sameVisibleTextProducesSameHashDespiteWhitespace() {
    String a = "<html><body><h1>Hello world</h1></body></html>";
    String b = "<html>  <body>\n    <h1>Hello   world</h1>  </body>\n</html>";
    assertEquals(ContentHasher.hash(a), ContentHasher.hash(b));
  }

  @Test
  void differentVisibleTextProducesDifferentHash() {
    assertNotEquals(ContentHasher.hash("<p>Hello</p>"), ContentHasher.hash("<p>Goodbye</p>"));
  }

  @Test
  void ignoresMarkupChangesThatDoNotAlterVisibleText() {
    // Same visible text, but a changing hidden token — should NOT count as a change.
    String a = "<body><input type=\"hidden\" name=\"csrf\" value=\"aaa\">Welcome</body>";
    String b = "<body><input type=\"hidden\" name=\"csrf\" value=\"zzz\">Welcome</body>";
    assertEquals(ContentHasher.hash(a), ContentHasher.hash(b));
  }

  @Test
  void ignoresChangesOutsideMainContentWhenArticlePresent() {
    // The <article> body is identical; only a sibling widget changes — should NOT count.
    String a =
        "<body><article>Real content</article>" + "<aside>You May Also Like: Post A</aside></body>";
    String b =
        "<body><article>Real content</article>" + "<aside>You May Also Like: Post Z</aside></body>";
    assertEquals(ContentHasher.hash(a), ContentHasher.hash(b));
  }

  @Test
  void detectsChangesWithinMainContent() {
    String a = "<body><article>First version</article><aside>sidebar</aside></body>";
    String b = "<body><article>Second version</article><aside>sidebar</aside></body>";
    assertNotEquals(ContentHasher.hash(a), ContentHasher.hash(b));
  }

  @Test
  void fallsBackToBodyWhenNoMainContentElement() {
    // No <article>/<main>: a page with no such landmark still hashes its body text.
    assertEquals(
        ContentHasher.hash("<body><p>Hello</p></body>"), ContentHasher.hash("<p>Hello</p>"));
    assertNotEquals(
        ContentHasher.hash("<body><p>Hello</p></body>"),
        ContentHasher.hash("<body><p>Different</p></body>"));
  }
}
