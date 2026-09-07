package com.local.akbmailarchive.model;

/** One mail entry returned by /inbox. */
public final class MailItem {
    public final String id;
    public final String memberName;
    public final String subject;
    public final String receiveDatetime;
    public final String content;
    public final String detailUrl;
    public final String rawJson;

    public MailItem(
            String id,
            String memberName,
            String subject,
            String receiveDatetime,
            String content,
            String detailUrl,
            String rawJson
    ) {
        this.id = safe(id);
        this.memberName = safe(memberName);
        this.subject = safe(subject);
        this.receiveDatetime = safe(receiveDatetime);
        this.content = safe(content);
        this.detailUrl = safe(detailUrl);
        this.rawJson = safe(rawJson);
    }

    public MailItem(
            String id,
            String memberName,
            String subject,
            String receiveDatetime,
            String detailUrl
    ) {
        this(id, memberName, subject, receiveDatetime, "", detailUrl, "");
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
