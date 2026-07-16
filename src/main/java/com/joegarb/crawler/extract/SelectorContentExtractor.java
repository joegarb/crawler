package com.joegarb.crawler.extract;

import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/** Scopes the fingerprint to a semantic content landmark, ignoring surrounding page chrome. */
public class SelectorContentExtractor implements ContentExtractor {
  // Ordered from most to least specific; the first that matches wins.
  private static final List<String> SELECTORS = List.of("article", "main");

  @Override
  public Optional<String> extract(Document doc) {
    for (String selector : SELECTORS) {
      Elements elements = doc.select(selector);
      if (!elements.isEmpty()) {
        return Optional.of(elements.text());
      }
    }
    return Optional.empty();
  }
}
