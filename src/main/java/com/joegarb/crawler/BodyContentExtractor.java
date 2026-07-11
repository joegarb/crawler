package com.joegarb.crawler;

import java.util.Optional;
import org.jsoup.nodes.Document;

/** Falls back to the whole page's visible text. Always applies, so it anchors a chain. */
public class BodyContentExtractor implements ContentExtractor {
  @Override
  public Optional<String> extract(Document doc) {
    return Optional.of(doc.body() != null ? doc.body().text() : doc.text());
  }
}
