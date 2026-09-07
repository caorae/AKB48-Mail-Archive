package com.local.akbmailarchive.model;

import java.util.Collections;
import java.util.List;

public final class InboxPage {
    public final int page;
    public final int mailCount;
    public final boolean hasNextPage;
    public final int unreadCount;
    public final int starCount;
    public final List<MailItem> mails;
    public final String rawJson;

    public InboxPage(
            int page,
            int mailCount,
            boolean hasNextPage,
            int unreadCount,
            int starCount,
            List<MailItem> mails,
            String rawJson
    ) {
        this.page = page;
        this.mailCount = mailCount;
        this.hasNextPage = hasNextPage;
        this.unreadCount = unreadCount;
        this.starCount = starCount;
        this.mails = mails == null ? Collections.emptyList() : mails;
        this.rawJson = rawJson == null ? "" : rawJson;
    }
}
