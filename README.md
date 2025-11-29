# Crawler

A simple web crawler written in Java.

By default the crawler is limited to a single host and its subdomains, but this behavior can be changed via configuration.

For simplicity, and because of being intended for a single host, the crawler simply waits X seconds between fetching URLs to be polite to the server and it does not currencly check `robots.txt`.

## Prerequisites

- Java 21 or later

## Building

```bash
./mvnw clean package
```

On Windows, use `mvnw.cmd` instead.

## Running

```bash
./crawl <startUrl>
```

## Configuration

The crawler can be configured using environment variables or the `application.properties` file, with environment variables taking precedence. Some of these include:

- `DB_URL` - Database connection URL (default: `jdbc:sqlite:crawler.db`)
- `NUM_THREADS` - Number of worker threads per process (default: `4`)
- `DELAY_BETWEEN_REQUESTS_MS` - Delay in milliseconds between fetching URLs (default: `1000`)
- `RESTRICT_TO_HOST` - Whether to restrict crawling to the same host and its subdomains (default: `true`)
