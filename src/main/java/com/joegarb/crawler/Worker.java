package com.joegarb.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Worker thread that performs web crawling tasks. */
public class Worker extends Thread {
  private static final Logger logger = LoggerFactory.getLogger(Worker.class);

  @Override
  public void run() {
    logger.info("Worker running");
  }
}
