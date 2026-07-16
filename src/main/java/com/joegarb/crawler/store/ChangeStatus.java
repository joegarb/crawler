package com.joegarb.crawler.store;

/** Whether a page's content is new, changed, or unchanged since the previous crawl. */
public enum ChangeStatus {
  NEW,
  CHANGED,
  UNCHANGED
}
