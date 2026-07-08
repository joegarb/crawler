package com.joegarb.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for StaticSeedSource. */
class StaticSeedSourceTest {
  @Test
  void returnsProvidedUrls() {
    List<String> urls = List.of("https://a.com", "https://b.com");
    assertEquals(urls, new StaticSeedSource(urls).seeds());
  }

  @Test
  void returnsEmptyWhenNoUrls() {
    assertTrue(new StaticSeedSource(List.of()).seeds().isEmpty());
  }

  @Test
  void isNotAffectedByLaterMutationOfTheInputList() {
    List<String> input = new ArrayList<>();
    input.add("https://a.com");
    StaticSeedSource source = new StaticSeedSource(input);
    input.add("https://b.com");
    assertEquals(1, source.seeds().size());
  }
}
