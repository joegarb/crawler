package com.joegarb.crawler.extract;

import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;

/** Tries each extractor in order and returns the first that applies. */
public class ContentExtractorChain implements ContentExtractor {
  private final List<ContentExtractor> extractors;

  public ContentExtractorChain(ContentExtractor... extractors) {
    this.extractors = List.of(extractors);
  }

  @Override
  public Optional<String> extract(Document doc) {
    for (ContentExtractor extractor : extractors) {
      Optional<String> text = extractor.extract(doc);
      if (text.isPresent()) {
        return text;
      }
    }
    return Optional.empty();
  }
}
