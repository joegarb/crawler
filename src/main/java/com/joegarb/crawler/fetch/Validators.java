package com.joegarb.crawler.fetch;

/**
 * Cache validators from a previous HTTP response, used to make the next request conditional. Fields
 * may be null when the response did not supply them.
 */
public record Validators(String etag, String lastModified) {}
