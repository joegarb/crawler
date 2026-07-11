# Crawler

A web crawler and site monitor written in Java. It crawls politely (respecting robots.txt and per-domain delays), resumes interrupted crawls, and fingerprints page content across crawls so it can report what has changed and which URLs are unhealthy.

## Prerequisites

- Java 21 or later

## Building

```bash
./mvnw clean package
```

On Windows, use `mvnw.cmd` instead.

## Running

Crawl starting from a URL:

```bash
./crawl <startUrl>
```

As it crawls, each page's content is fingerprinted from its visible text and compared against the previous crawl, so re-crawling a site detects new and changed pages. Hashing the visible text (rather than the raw HTML) avoids false positives from markup churn such as CSRF tokens or timestamps.

To resume a previously interrupted crawl, omit the URL — the crawler will continue from the existing frontier:

```bash
./crawl
```

## Monitoring report

Generate a JSON report of what changed and which URLs are unhealthy, then exit without crawling:

```bash
./crawl --report
```

This writes `report.json` (see `REPORT_FILE`) with two sections:

- `changes` — pages that are new or whose content changed since the previous crawl
- `health` — how many URLs were crawled, how many are healthy, and details of any that returned an error or non-success status (URLs skipped for robots.txt are not counted as problems)

Run a crawl first to populate the data, then `--report` to summarize it — for example, on a schedule.

Pages whose content is inherently volatile (index or feed pages that reshuffle on every request) can be excluded from change tracking with `CHANGE_TRACKING_EXCLUDE_URLS` — a comma-separated list of URL glob patterns (`*` wildcard). They are still crawled for link discovery and health, just not reported as changed:

```bash
CHANGE_TRACKING_EXCLUDE_URLS='*/intelligence/,*/feed/' ./crawl <startUrl>
```

## Running with Docker

Images are published to `ghcr.io/joegarb/crawler`. Mount a volume for the database so change detection persists between runs:

```bash
docker run --rm -v crawler-data:/data \
  -e DB_URL=jdbc:sqlite:/data/crawler.db \
  ghcr.io/joegarb/crawler:latest https://example.com
```

Generate the report from the same volume:

```bash
docker run --rm -v crawler-data:/data \
  -e DB_URL=jdbc:sqlite:/data/crawler.db -e REPORT_FILE=/data/report.json \
  ghcr.io/joegarb/crawler:latest --report
```

As a Compose service (it's a batch job, so schedule it rather than running it as a long-lived service):

```yaml
services:
  crawler:
    image: ghcr.io/joegarb/crawler:latest
    volumes:
      - crawler-data:/data
    environment:
      - DB_URL=jdbc:sqlite:/data/crawler.db
      - REPORT_FILE=/data/report.json
    command: ["https://example.com"]

volumes:
  crawler-data:
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
- `REPORT_FILE` - File path the JSON report is written to in `--report` mode (default: `report.json`)
- `CHANGE_TRACKING_EXCLUDE_URLS` - Comma-separated URL glob patterns excluded from change tracking (default: none)
- `LOG_LEVEL` - Log level (default: `INFO`, set to `DEBUG` for verbose output)
