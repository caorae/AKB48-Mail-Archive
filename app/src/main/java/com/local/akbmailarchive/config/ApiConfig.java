package com.local.akbmailarchive.config;

/**
 * Compatibility values observed from the legacy Android application.
 */
public final class ApiConfig {
    private ApiConfig() {}

    public static final String API_BASE = "https://api.akb48mail-appli.com/v1/";
    public static final String TEMP_USER_PATH = "users";
    public static final String LOGIN_PATH = "data_inherit_execute";
    public static final String INBOX_PATH = "inbox";

    // Values confirmed from base.apk v1.5.9.
    public static final String LEGACY_APPLICATION_VERSION = "1.5.9";
    public static final String APPLICATION_LANGUAGE = "ja";

    // A fresh official installation creates a temporary user before inheritance.
    // PreferenceUtil.getTermsVersion() defaults to "0" on that first request.
    public static final String TEMP_USER_TERMS_VERSION = "0";

    // Direct tests show Terms-Version 0 is rejected by data_inherit_execute with 426.
    // The inheritance screen is reached after terms acceptance, so use the accepted version.
    public static final String LOGIN_TERMS_VERSION = "5";
    public static final String AUTHENTICATED_TERMS_VERSION = "5";
}
