# Crawler

A simple web crawler written in Java.

The crawler is limited to crawling a single subdomain. For example, when starting with
`https://crawlme.example.com/`, it will crawl all pages on `crawlme.example.com` but will not
follow external links to other domains or subdomains (e.g., `facebook.com`, `example.com`, or
`community.example.com`).

## Notes

- The crawler waits 1 second between fetching URLs to be polite to the server.
- It does not currently check `robots.txt` files, since the crawler is designed for crawling a single subdomain.

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
