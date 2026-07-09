package com.joegarb.crawler;

import java.util.List;

/** A {@link SeedSource} backed by a fixed list of URLs, e.g. from command-line arguments. */
public class StaticSeedSource implements SeedSource {
  private final List<String> urls;

  public StaticSeedSource(List<String> urls) {
    this.urls = List.copyOf(urls);
  }

  @Override
  public List<String> seeds() {
    return urls;
  }
}
