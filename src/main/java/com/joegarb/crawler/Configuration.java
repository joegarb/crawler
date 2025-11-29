package com.joegarb.crawler;

import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the crawler, loaded from properties file with environment variable overrides.
 */
public class Configuration {
  private static final Logger logger = LoggerFactory.getLogger(Configuration.class);
  private static final Properties properties = loadProperties();

  /** Database connection URL. */
  public static final String DB_URL = getProperty("db.url", "DB_URL", "jdbc:sqlite:crawler.db");

  /** Number of worker threads per process. */
  public static final int NUM_THREADS = getIntProperty("num.threads", "NUM_THREADS", 4);

  /** Delay in milliseconds between processing URLs. */
  public static final int DELAY_BETWEEN_REQUESTS_MS =
      getIntProperty("delay.between.requests.ms", "DELAY_BETWEEN_REQUESTS_MS", 1000);

  /** HTTP timeout in seconds. */
  public static final int HTTP_TIMEOUT_SECONDS =
      getIntProperty("http.timeout.seconds", "HTTP_TIMEOUT_SECONDS", 10);

  /** Time in seconds to wait before retrying a failed URL fetch. */
  public static final int FAILED_RETRY_INTERVAL_SECONDS =
      getIntProperty("failed.retry.interval.seconds", "FAILED_RETRY_INTERVAL_SECONDS", 300);

  /** Time in seconds to wait before re-fetching a successfully crawled URL. */
  public static final int SUCCESS_REFRESH_INTERVAL_SECONDS =
      getIntProperty("success.refresh.interval.seconds", "SUCCESS_REFRESH_INTERVAL_SECONDS", 86400);

  /** Whether to restrict crawling to the same host (and its subdomains). */
  public static final boolean RESTRICT_TO_HOST =
      getBooleanProperty("restrict.to.host", "RESTRICT_TO_HOST", true);

  /**
   * Loads properties from application.properties file.
   *
   * @return Properties object, or empty Properties if file not found
   */
  private static Properties loadProperties() {
    Properties props = new Properties();
    try (InputStream inputStream =
        Configuration.class.getClassLoader().getResourceAsStream("application.properties")) {
      if (inputStream != null) {
        props.load(inputStream);
        logger.debug("Loaded configuration from application.properties");
      } else {
        logger.debug("application.properties not found, using defaults");
      }
    } catch (Exception e) {
      logger.warn("Failed to load application.properties, using defaults: {}", e.getMessage());
    }
    return props;
  }

  /**
   * Gets a property value, checking environment variable first, then properties file, then default.
   *
   * @param propertyKey Properties file key
   * @param envKey Environment variable name
   * @param defaultValue Default value if not found
   * @return Property value
   */
  private static String getProperty(String propertyKey, String envKey, String defaultValue) {
    // Environment variables take precedence
    String envValue = System.getenv(envKey);
    if (envValue != null && !envValue.isEmpty()) {
      logger.debug("Using {}={} from environment variable", envKey, envValue);
      return envValue;
    }

    // Then check properties file
    String propValue = properties.getProperty(propertyKey);
    if (propValue != null && !propValue.isEmpty()) {
      logger.debug("Using {}={} from properties file", propertyKey, propValue);
      return propValue;
    }

    // Finally use default
    return defaultValue;
  }

  /**
   * Gets an integer property value.
   *
   * @param propertyKey Properties file key
   * @param envKey Environment variable name
   * @param defaultValue Default value if not found or invalid
   * @return Property value as integer
   */
  private static int getIntProperty(String propertyKey, String envKey, int defaultValue) {
    String stringValue = getProperty(propertyKey, envKey, String.valueOf(defaultValue));
    try {
      return Integer.parseInt(stringValue);
    } catch (NumberFormatException e) {
      logger.warn(
          "Invalid integer value for {}/{}: {}, using default: {}",
          propertyKey,
          envKey,
          stringValue,
          defaultValue);
      return defaultValue;
    }
  }

  /**
   * Gets a boolean property value.
   *
   * @param propertyKey Properties file key
   * @param envKey Environment variable name
   * @param defaultValue Default value if not found or invalid
   * @return Property value as boolean
   */
  private static boolean getBooleanProperty(
      String propertyKey, String envKey, boolean defaultValue) {
    String stringValue = getProperty(propertyKey, envKey, String.valueOf(defaultValue));
    if (stringValue == null) {
      return defaultValue;
    }
    String lowerValue = stringValue.toLowerCase().trim();
    if (lowerValue.equals("true") || lowerValue.equals("1") || lowerValue.equals("yes")) {
      return true;
    }
    if (lowerValue.equals("false") || lowerValue.equals("0") || lowerValue.equals("no")) {
      return false;
    }
    logger.warn(
        "Invalid boolean value for {}/{}: {}, using default: {}",
        propertyKey,
        envKey,
        stringValue,
        defaultValue);
    return defaultValue;
  }
}
