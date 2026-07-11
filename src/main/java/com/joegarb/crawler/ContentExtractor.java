package com.joegarb.crawler;

import java.util.Optional;
import org.jsoup.nodes.Document;

/** Selects the text of a page to fingerprint for change detection. */
public interface ContentExtractor {
  /**
   * Returns the text to fingerprint, or empty if this extractor does not apply to the page so the
   * next extractor in a chain can try.
   *
   * @param doc the parsed page
   * @return the text to fingerprint, or empty if this extractor does not apply
   */
  Optional<String> extract(Document doc);
}
