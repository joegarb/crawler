package com.joegarb.crawler.url;

import java.util.List;
import java.util.regex.Pattern;

/** Matches URLs against glob patterns (with {@code *} wildcards) to exclude them from tracking. */
public class UrlExcluder {
  private final List<Pattern> patterns;

  public UrlExcluder(List<String> globs) {
    this.patterns =
        globs.stream()
            .map(String::trim)
            .filter(glob -> !glob.isEmpty())
            .map(UrlExcluder::globToPattern)
            .toList();
  }

  /**
   * @param url the URL to test
   * @return true if the URL matches any configured glob pattern
   */
  public boolean isExcluded(String url) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(url).matches());
  }

  private static Pattern globToPattern(String glob) {
    String[] literals = glob.split("\\*", -1);
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < literals.length; i++) {
      if (i > 0) {
        regex.append(".*");
      }
      if (!literals[i].isEmpty()) {
        regex.append(Pattern.quote(literals[i]));
      }
    }
    return Pattern.compile(regex.toString());
  }
}
