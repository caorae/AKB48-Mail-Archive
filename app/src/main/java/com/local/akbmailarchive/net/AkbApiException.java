package com.local.akbmailarchive.net;

import java.io.IOException;

public final class AkbApiException extends IOException {
    private final int statusCode;
    private final String responseBody;

    public AkbApiException(int statusCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
