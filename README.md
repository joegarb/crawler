# Crawler

A simple web crawler written in Java.

By default the crawler is limited to a single host and its subdomains, but this behavior can be changed via configuration.

For simplicity, and because of being intended for a single host, the crawler simply waits X seconds between fetching URLs to be polite to the server and it does not currently check `robots.txt`.

## Prerequisites

- Java 21 or later

## Building

```bash
./mvnw clean package
```

On Windows, use `mvnw.cmd` instead.

## Running

### Normal Mode

Run the crawler with a start URL. This will initialize the database, add the start URL to the frontier, and start worker threads:

```bash
./crawl <startUrl>
```

### Worker Mode

Run in worker-only mode using the `--worker` flag. In worker mode, the process runs a single worker on the main thread and does not add a start URL (the database should already be initialized by a main process):

```bash
./crawl --worker
```

## Configuration

The crawler can be configured using environment variables or the `application.properties` file, with environment variables taking precedence. Some of these include:

- `DB_URL` - Database connection URL (default: `jdbc:sqlite:crawler.db`)
- `NUM_THREADS` - Number of worker threads per process in normal mode (default: `4`). Not used in worker mode (runs single worker on main thread).
- `DELAY_BETWEEN_REQUESTS_MS` - Delay in milliseconds between fetching URLs (default: `1000`)
- `RESTRICT_TO_HOST` - Whether to restrict crawling to the same host and its subdomains (default: `true`)
