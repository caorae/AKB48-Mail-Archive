package com.local.akbmailarchive.net;

import android.os.Build;

import com.local.akbmailarchive.config.ApiConfig;
import com.local.akbmailarchive.model.DownloadedResource;
import com.local.akbmailarchive.model.InboxPage;
import com.local.akbmailarchive.model.LoginSession;
import com.local.akbmailarchive.model.MailItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal client for the legacy AKB48 Mail service.
 *
 * Authentication flow confirmed from base.apk v1.5.9:
 *  1) POST /users -> temporary User-Id / Access-Token
 *  2) POST /data_inherit_execute with temporary credentials in headers
 *  3) replace session with inherited UserInfo
 *  4) GET-only archive requests
 *
 * Credentials remain in memory only.
 */
public final class AkbApiClient {
    private static final int MAX_DIAGNOSTIC_LINES = 350;
    private static final Pattern CHARSET_PATTERN = Pattern.compile(
            "charset\\s*=\\s*['\\\"]?([^;\\s'\\\"]+)", Pattern.CASE_INSENSITIVE);

    private volatile String userId = "";
    private volatile String accessToken = "";
    private final List<String> diagnostics = new ArrayList<>();

    public LoginSession loginWithInheritance(String inheritanceId, String password)
            throws IOException, JSONException {
        if (inheritanceId == null || inheritanceId.trim().isEmpty()) {
            throw new IllegalArgumentException("引き継ぎID不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("パスワード不能为空");
        }

        clearSession();
        clearDiagnostics();
        addDiagnostic("v" + com.local.akbmailarchive.BuildConfig.VERSION_NAME + " flow: temporary user -> inheritance execute -> GET-only archive");

        LoginSession temporary = createTemporaryUserSession();
        this.userId = temporary.userId;
        this.accessToken = temporary.accessToken;
        addDiagnostic("TEMP_USER session: User-Id=" + presence(temporary.userId)
                + ", Access-Token=" + presence(temporary.accessToken));

        String body = "user_id=" + URLEncoder.encode(inheritanceId.trim(), "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");

        HttpResult result = requestTextApi(
                "INHERIT",
                "POST",
                ApiConfig.API_BASE + ApiConfig.LOGIN_PATH,
                body.getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded",
                this.userId,
                this.accessToken,
                ApiConfig.LOGIN_TERMS_VERSION,
                ApiConfig.APPLICATION_LANGUAGE
        );

        LoginSession inherited = parseUserInfoSession(result.body, "引き継ぎ");
        this.userId = inherited.userId;
        this.accessToken = inherited.accessToken;
        addDiagnostic("INHERIT session: User-Id=" + presence(inherited.userId)
                + ", Access-Token=" + presence(inherited.accessToken));
        return inherited;
    }

    private LoginSession createTemporaryUserSession() throws IOException, JSONException {
        HttpResult result = requestTextApi(
                "TEMP_USER",
                "POST",
                ApiConfig.API_BASE + ApiConfig.TEMP_USER_PATH,
                new byte[0],
                "application/json",
                "",
                "",
                ApiConfig.TEMP_USER_TERMS_VERSION,
                ""
        );
        return parseUserInfoSession(result.body, "一時ユーザー作成");
    }

    private LoginSession parseUserInfoSession(String responseBody, String stage)
            throws JSONException, IOException {
        JSONObject root = new JSONObject(responseBody);
        JSONObject userInfoObject = findObjectContainingKey(root, "access_token");
        if (userInfoObject == null) userInfoObject = findObjectContainingKey(root, "accessToken");
        if (userInfoObject == null) {
            throw new IOException(stage + "返回成功，但没有找到 access_token。响应开头: "
                    + safePrefix(responseBody, 500));
        }

        String token = firstNonEmpty(
                userInfoObject.optString("access_token", ""),
                userInfoObject.optString("accessToken", "")
        );
        String id = firstNonEmpty(
                userInfoObject.optString("id", ""),
                userInfoObject.optString("user_id", ""),
                userInfoObject.optString("userId", "")
        );
        if (id.isEmpty()) id = findStringByKey(root, "user_id");
        if (id.isEmpty() || token.isEmpty()) {
            throw new IOException(stage + "成功响应中缺少 User-Id 或 Access-Token。");
        }

        String nickname = firstNonEmpty(
                userInfoObject.optString("nickname", ""),
                userInfoObject.optString("name", "")
        );
        return new LoginSession(id, token, nickname);
    }

    public InboxPage getInboxPage(int page) throws IOException, JSONException {
        requireSession();
        if (page < 1) throw new IllegalArgumentException("page must be >= 1");

        String url = ApiConfig.API_BASE + ApiConfig.INBOX_PATH
                + "?is_unread=false"
                + "&is_star=false"
                + "&page=" + page
                + "&is_information=false";

        HttpResult result = requestTextApi(
                "INBOX",
                "GET",
                url,
                null,
                "application/json",
                userId,
                accessToken,
                ApiConfig.AUTHENTICATED_TERMS_VERSION,
                ApiConfig.APPLICATION_LANGUAGE
        );

        JSONObject root = new JSONObject(result.body);
        JSONArray mailsJson = root.optJSONArray("mails");
        if (mailsJson == null) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                mailsJson = data.optJSONArray("mails");
                if (mailsJson != null) root = data;
            }
        }

        List<MailItem> mails = new ArrayList<>();
        if (mailsJson != null) {
            for (int i = 0; i < mailsJson.length(); i++) {
                JSONObject m = mailsJson.optJSONObject(i);
                if (m == null) continue;

                String id = valueToString(m.opt("id"));
                if (id.isEmpty()) continue;
                String subject = valueToString(m.opt("subject"));
                String receiveDatetime = firstNonEmpty(
                        valueToString(m.opt("receive_datetime")),
                        valueToString(m.opt("receive_time"))
                );
                String content = valueToString(m.opt("content"));
                String detailUrl = valueToString(m.opt("detail_url"));
                String memberName = extractMemberName(m.opt("member"));

                mails.add(new MailItem(
                        id,
                        memberName,
                        subject,
                        receiveDatetime,
                        content,
                        detailUrl,
                        m.toString()
                ));
            }
        }

        return new InboxPage(
                page,
                root.optInt("mail_count", mails.size()),
                root.optBoolean("has_next_page", false),
                root.optInt("unread_count", 0),
                root.optInt("star_count", 0),
                mails,
                result.body
        );
    }

    /** GET the official mail detail page with the authenticated header map. */
    public String fetchDetailHtml(String detailUrl) throws IOException {
        requireSession();
        URL url = requireHttps(detailUrl);
        // The official WebView calls loadUrl(detailUrl, getHeaderMap()).  Mirror that
        // legacy map here, including its JSON Accept/Content-Type values.
        RawHttpResult result = requestBytes(
                "DETAIL",
                "GET",
                url,
                null,
                "application/json",
                "application/json",
                true,
                null
        );
        return decodeText(result.bytes, result.contentType);
    }

    /**
     * Download an image/resource referenced by mail HTML.
     * Credentials are attached only to the official akb48mail-appli.com host family.
     */
    public DownloadedResource fetchResource(String resourceUrl, String referer) throws IOException {
        requireSession();
        URL url = requireHttps(resourceUrl);
        boolean attachAuth = isTrustedAuthHost(url.getHost());
        RawHttpResult result = requestBytes(
                "RESOURCE",
                "GET",
                url,
                null,
                null,
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                attachAuth,
                referer
        );
        return new DownloadedResource(result.bytes, result.contentType, result.finalUrl);
    }

    public void clearSession() {
        userId = "";
        accessToken = "";
    }

    public synchronized String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        for (String line : diagnostics) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private void requireSession() {
        if (userId.isEmpty() || accessToken.isEmpty()) {
            throw new IllegalStateException("尚未登录");
        }
    }

    private synchronized void clearDiagnostics() {
        diagnostics.clear();
    }

    private synchronized void addDiagnostic(String line) {
        if (diagnostics.size() >= MAX_DIAGNOSTIC_LINES) return;
        diagnostics.add(line);
    }

    private HttpResult requestTextApi(
            String stage,
            String method,
            String urlText,
            byte[] requestBody,
            String contentType,
            String uid,
            String token,
            String termsVersion,
            String applicationLanguage
    ) throws IOException {
        URL url = requireHttps(urlText);
        RawHttpResult raw = requestBytesApi(
                stage, method, url, requestBody, contentType,
                uid, token, termsVersion, applicationLanguage
        );
        return new HttpResult(raw.statusCode, decodeText(raw.bytes, raw.contentType));
    }

    private RawHttpResult requestBytesApi(
            String stage,
            String method,
            URL url,
            byte[] requestBody,
            String contentType,
            String uid,
            String token,
            String termsVersion,
            String applicationLanguage
    ) throws IOException {
        addRequestDiagnostics(stage, method, url, contentType, uid, token, termsVersion,
                applicationLanguage, requestBody == null ? -1 : requestBody.length);

        return performHttp(
                stage,
                method,
                url,
                requestBody,
                contentType,
                "application/json",
                uid,
                token,
                termsVersion,
                applicationLanguage,
                true,
                null
        );
    }

    private RawHttpResult requestBytes(
            String stage,
            String method,
            URL url,
            byte[] requestBody,
            String contentType,
            String accept,
            boolean attachAuth,
            String referer
    ) throws IOException {
        if (stage.equals("DETAIL")) {
            addDiagnostic("DETAIL -> GET " + url.getPath());
        }
        return performHttp(
                stage,
                method,
                url,
                requestBody,
                contentType,
                accept,
                attachAuth ? userId : "",
                attachAuth ? accessToken : "",
                ApiConfig.AUTHENTICATED_TERMS_VERSION,
                ApiConfig.APPLICATION_LANGUAGE,
                attachAuth,
                referer
        );
    }

    private RawHttpResult performHttp(
            String stage,
            String method,
            URL initialUrl,
            byte[] requestBody,
            String contentType,
            String accept,
            String uid,
            String token,
            String termsVersion,
            String applicationLanguage,
            boolean attachLegacyHeaders,
            String referer
    ) throws IOException {
        URL current = initialUrl;
        String currentMethod = method;
        byte[] currentBody = requestBody;

        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())) {
                throw new IOException("Blocked non-HTTPS URL: " + current);
            }

            HttpURLConnection conn = (HttpURLConnection) current.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod(currentMethod);
            conn.setConnectTimeout(20_000);
            conn.setReadTimeout(60_000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", accept == null ? "*/*" : accept);
            conn.setRequestProperty("User-Agent", userAgent());
            if (contentType != null && !contentType.isEmpty()) {
                conn.setRequestProperty("Content-Type", contentType);
            }
            if (referer != null && !referer.isEmpty()) {
                conn.setRequestProperty("Referer", referer);
            }

            boolean sendAuthHere = attachLegacyHeaders && isTrustedAuthHost(current.getHost());
            if (sendAuthHere) {
                setLegacyHeaders(conn, uid, token, termsVersion, applicationLanguage);
            }

            if (currentBody != null && ("POST".equals(currentMethod) || "PUT".equals(currentMethod))) {
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(currentBody.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(currentBody);
                    os.flush();
                }
            }

            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("HTTP " + status + " without Location from " + current);
                }
                URL next = new URL(current, location);
                if (!"https".equalsIgnoreCase(next.getProtocol())) {
                    throw new IOException("Blocked redirect to non-HTTPS URL: " + next);
                }
                // Never forward a non-empty POST/PUT body (which may contain inheritance
                // credentials) to an untrusted host through a 307/308-style redirect.
                if (currentBody != null && currentBody.length > 0
                        && ("POST".equals(currentMethod) || "PUT".equals(currentMethod))
                        && !isTrustedAuthHost(next.getHost())) {
                    throw new SecurityException();
                }
                // For 301/302/303 after POST, browsers typically switch to GET.
                if ((status == 301 || status == 302 || status == 303)
                        && !"GET".equals(currentMethod) && !"HEAD".equals(currentMethod)) {
                    currentMethod = "GET";
                    currentBody = null;
                    contentType = null;
                }
                current = next;
                continue;
            }

            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            byte[] body = readAllBytes(stream);
            String responseType = valueOrEmpty(conn.getHeaderField("Content-Type"));
            String finalUrl = conn.getURL().toString();
            String setCookie = conn.getHeaderField("Set-Cookie");

            if (!"RESOURCE".equals(stage)) {
                addDiagnostic(stage + " <- HTTP " + status
                        + ", Set-Cookie="
                        + (setCookie == null || setCookie.isEmpty() ? "<none>" : "<present>"));
            }
            conn.disconnect();

            if (status < 200 || status >= 300) {
                String bodyText = decodeText(body, responseType);
                if (!"RESOURCE".equals(stage)) {
                    addDiagnostic(stage + " error=" + safePrefix(bodyText, 260));
                }
                throw new AkbApiException(
                        status,
                        "HTTP " + status + " / " + safePrefix(bodyText, 300),
                        bodyText
                );
            }
            return new RawHttpResult(status, body, responseType, finalUrl);
        }

        throw new IOException("Too many redirects: " + initialUrl);
    }

    private void addRequestDiagnostics(
            String stage,
            String method,
            URL url,
            String contentType,
            String uid,
            String token,
            String termsVersion,
            String applicationLanguage,
            int bodyLength
    ) {
        String target = url.getPath() + (url.getQuery() == null ? "" : "?" + url.getQuery());
        addDiagnostic(stage + " -> " + method + " " + target);
        addDiagnostic("  Accept=application/json; Content-Type=" + contentType
                + "; bodyBytes=" + (bodyLength < 0 ? "<none>" : bodyLength));
        addDiagnostic("  User-Id=" + presence(uid) + "; Access-Token=" + presence(token)
                + "; Terms-Version=" + termsVersion);
        addDiagnostic("  Os-Type=android; Os-Version=" + Build.VERSION.RELEASE
                + "; Device-Version=" + Build.PRODUCT);
        addDiagnostic("  Application-Version=" + ApiConfig.LEGACY_APPLICATION_VERSION
                + "; Application-Language="
                + (applicationLanguage == null || applicationLanguage.isEmpty()
                ? "<empty>" : applicationLanguage));
        addDiagnostic("  User-Agent=" + userAgent());
    }

    private void setLegacyHeaders(
            HttpURLConnection conn,
            String uid,
            String token,
            String termsVersion,
            String applicationLanguage
    ) {
        conn.setRequestProperty("User-Id", uid == null ? "" : uid);
        conn.setRequestProperty("Access-Token", token == null ? "" : token);
        conn.setRequestProperty("Os-Type", "android");
        conn.setRequestProperty("Os-Version", Build.VERSION.RELEASE);
        conn.setRequestProperty("User-Agent", userAgent());
        conn.setRequestProperty("Device-Version", Build.PRODUCT);
        conn.setRequestProperty("Application-Version", ApiConfig.LEGACY_APPLICATION_VERSION);
        conn.setRequestProperty("Application-Language", applicationLanguage == null ? "" : applicationLanguage);
        conn.setRequestProperty("Terms-Version", termsVersion == null ? "0" : termsVersion);
    }

    private static URL requireHttps(String urlText) throws IOException {
        if (urlText == null || urlText.trim().isEmpty()) throw new IOException("URL is empty");
        URL url = new URL(urlText.trim());
        if ("https".equalsIgnoreCase(url.getProtocol())) return url;
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            // Never use cleartext. Old archive HTML may contain http:// links, so try
            // the HTTPS equivalent instead of silently losing those resources.
            return new URL("https", url.getHost(), -1, url.getFile());
        }
        throw new IOException("Blocked non-HTTP(S) URL: " + url);
    }

    private static boolean isTrustedAuthHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.US);
        return h.equals("akb48mail-appli.com") || h.endsWith(".akb48mail-appli.com");
    }

    private static String userAgent() {
        String agent = System.getProperty("http.agent");
        return agent == null || agent.trim().isEmpty() ? "unknown" : agent;
    }

    private static String decodeText(byte[] bytes, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        String ct = valueOrEmpty(contentType);
        Matcher m = CHARSET_PATTERN.matcher(ct);
        if (m.find()) {
            try { charset = Charset.forName(m.group(1)); } catch (Exception ignored) {}
        }
        return new String(bytes == null ? new byte[0] : bytes, charset);
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        if (in == null) return new byte[0];
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static JSONObject findObjectContainingKey(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (obj.has(key) && !obj.optString(key, "").isEmpty()) return obj;
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                JSONObject found = findObjectContainingKey(obj.opt(keys.next()), key);
                if (found != null) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findObjectContainingKey(array.opt(i), key);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String findStringByKey(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (obj.has(key)) {
                String value = valueToString(obj.opt(key));
                if (!value.isEmpty()) return value;
            }
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String found = findStringByKey(obj.opt(keys.next()), key);
                if (!found.isEmpty()) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String found = findStringByKey(array.opt(i), key);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static String extractMemberName(Object member) {
        if (member instanceof JSONObject) {
            JSONObject obj = (JSONObject) member;
            return firstNonEmpty(
                    obj.optString("name", ""),
                    obj.optString("mail_name", ""),
                    obj.optString("nickname", ""),
                    obj.optString("display_name", "")
            );
        }
        return valueToString(member);
    }

    private static String valueToString(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static String presence(String value) {
        if (value == null || value.isEmpty()) return "<empty>";
        return "<present,len=" + value.length() + ">";
    }

    private static String safePrefix(String text, int max) {
        if (text == null) return "";
        String normalized = text.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...";
    }

    private static String valueOrEmpty(String value) { return value == null ? "" : value; }

    private static final class HttpResult {
        final int statusCode;
        final String body;
        HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    private static final class RawHttpResult {
        final int statusCode;
        final byte[] bytes;
        final String contentType;
        final String finalUrl;
        RawHttpResult(int statusCode, byte[] bytes, String contentType, String finalUrl) {
            this.statusCode = statusCode;
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.contentType = valueOrEmpty(contentType);
            this.finalUrl = valueOrEmpty(finalUrl);
        }
    }
}
