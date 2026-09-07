package com.local.akbmailarchive.backup;

public final class BackupResult {
    public final int pageCount;
    public final long mailCount;
    public final long detailCount;
    public final long imageCount;
    public final int detailFailures;
    public final int imageFailures;

    public BackupResult(
            int pageCount,
            long mailCount,
            long detailCount,
            long imageCount,
            int detailFailures,
            int imageFailures
    ) {
        this.pageCount = pageCount;
        this.mailCount = mailCount;
        this.detailCount = detailCount;
        this.imageCount = imageCount;
        this.detailFailures = detailFailures;
        this.imageFailures = imageFailures;
    }
}
