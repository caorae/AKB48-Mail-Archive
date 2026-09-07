package com.local.akbmailarchive.util;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free extractor for image URLs referenced by mail HTML. */
public final class HtmlAssetExtractor {
    private HtmlAssetExtractor() {}

    private static final Pattern IMG_ATTR = Pattern.compile(
            "<img\\b[^>]*?\\b(?:src|data-src|data-original|data-lazy-src)\\s*=\\s*(['\\\"])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern SOURCE_SRCSET = Pattern.compile(
            "<(?:img|source)\\b[^>]*?\\bsrcset\\s*=\\s*(['\\\"])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CSS_URL = Pattern.compile(
            "url\\(\\s*(['\\\"]?)(.*?)\\1\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public static Set<String> extract(String html, String baseUrl) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (html == null || html.isEmpty()) return out;

        Matcher img = IMG_ATTR.matcher(html);
        while (img.find()) addResolved(out, img.group(2), baseUrl);

        Matcher srcset = SOURCE_SRCSET.matcher(html);
        while (srcset.find()) {
            String value = decodeEntities(srcset.group(2));
            for (String candidate : value.split(",")) {
                String trimmed = candidate.trim();
                if (trimmed.isEmpty()) continue;
                String url = trimmed.split("\\s+", 2)[0];
                addResolved(out, url, baseUrl);
            }
        }

        Matcher css = CSS_URL.matcher(html);
        while (css.find()) addResolved(out, css.group(2), baseUrl);
        return out;
    }

    private static void addResolved(Set<String> out, String raw, String baseUrl) {
        String value = decodeEntities(raw == null ? "" : raw.trim()).trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) return;
        String lower = value.toLowerCase();
        if (lower.startsWith("data:") || lower.startsWith("javascript:")
                || lower.startsWith("blob:") || lower.startsWith("mailto:")
                || value.startsWith("#")) return;
        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, value);
            if ("http".equalsIgnoreCase(resolved.getProtocol())) {
                resolved = new URL("https", resolved.getHost(), -1, resolved.getFile());
            }
            if (!"https".equalsIgnoreCase(resolved.getProtocol())) return;
            out.add(resolved.toString());
        } catch (Exception ignored) {
        }
    }

    private static String decodeEntities(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
