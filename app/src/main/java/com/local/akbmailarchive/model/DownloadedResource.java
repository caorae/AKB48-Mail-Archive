package com.local.akbmailarchive.model;

/** Raw HTTP resource downloaded for offline caching. */
public final class DownloadedResource {
    public final byte[] bytes;
    public final String contentType;
    public final String finalUrl;

    public DownloadedResource(byte[] bytes, String contentType, String finalUrl) {
        this.bytes = bytes == null ? new byte[0] : bytes;
        this.contentType = contentType == null ? "" : contentType;
        this.finalUrl = finalUrl == null ? "" : finalUrl;
    }
}
