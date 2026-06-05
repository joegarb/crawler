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

The crawler can be configured using environment variables or the `application.properties` file, with environment variables taking precedence. They can be prepended to the `./crawl` command inline:

```bash
LOG_LEVEL=debug ./crawl <startUrl>
```

Some of these include:

- `DB_URL` - Database connection URL (default: `jdbc:sqlite:crawler.db`)
- `NUM_THREADS` - Number of worker threads per process in normal mode (default: `4`). Not used in worker mode (runs single worker on main thread).
- `POLITENESS_DELAY_MS` - Minimum delay in milliseconds between requests to the same domain (default: `1000`)
- `RESTRICT_TO_HOST` - Whether to restrict crawling to the same host and its subdomains (default: `false`)
- `LOG_LEVEL` - Log level (default: `INFO`, set to `DEBUG` for verbose output)
