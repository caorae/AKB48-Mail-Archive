package com.local.akbmailarchive.model;

public final class LoginSession {
    public final String userId;
    public final String accessToken;
    public final String nickname;

    public LoginSession(String userId, String accessToken, String nickname) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.nickname = nickname == null ? "" : nickname;
    }
}
