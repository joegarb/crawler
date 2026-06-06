# Crawler

A simple web crawler written in Java.

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

To resume a previously interrupted crawl, omit the URL — the crawler will continue from the existing frontier:

```bash
./crawl
```

## Configuration

The crawler can be configured using environment variables or the `application.properties` file, with environment variables taking precedence. They can be prepended to the `./crawl` command inline:

```bash
LOG_LEVEL=debug ./crawl <startUrl>
```

Some of these include:

- `DB_URL` - Database connection URL (default: `jdbc:sqlite:crawler.db`)
- `NUM_THREADS` - Number of worker threads (default: `4`)
- `POLITENESS_DELAY_MS` - Minimum delay in milliseconds between requests to the same domain (default: `1000`)
- `RESTRICT_TO_HOST` - Whether to restrict crawling to the same host and its subdomains (default: `false`)
- `LOG_LEVEL` - Log level (default: `INFO`, set to `DEBUG` for verbose output)
