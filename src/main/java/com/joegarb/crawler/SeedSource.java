package com.joegarb.crawler;

import java.util.List;

/** Supplies the seed URLs used to populate the frontier at startup. */
public interface SeedSource {
  /**
   * Returns the seed URLs to add to the frontier.
   *
   * @return the seed URLs, possibly empty
   */
  List<String> seeds();
}
