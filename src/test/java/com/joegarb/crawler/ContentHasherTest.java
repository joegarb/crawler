package com.joegarb.crawler;

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
}
