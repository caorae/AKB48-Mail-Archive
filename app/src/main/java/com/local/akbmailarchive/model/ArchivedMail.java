package com.local.akbmailarchive.model;

/** Mail loaded from the local SQLite archive. */
public final class ArchivedMail {
    public final String id;
    public final String memberName;
    public final String subject;
    public final String receiveDatetime;
    public final String content;
    public final String detailUrl;
    public final String detailHtmlPath;
    public final int detailStatus;
    public final boolean localRead;
    public final boolean localStar;

    public ArchivedMail(
            String id,
            String memberName,
            String subject,
            String receiveDatetime,
            String content,
            String detailUrl,
            String detailHtmlPath,
            int detailStatus,
            boolean localRead,
            boolean localStar
    ) {
        this.id = safe(id);
        this.memberName = safe(memberName);
        this.subject = safe(subject);
        this.receiveDatetime = safe(receiveDatetime);
        this.content = safe(content);
        this.detailUrl = safe(detailUrl);
        this.detailHtmlPath = safe(detailHtmlPath);
        this.detailStatus = detailStatus;
        this.localRead = localRead;
        this.localStar = localStar;
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }
}
