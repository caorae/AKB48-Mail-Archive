package com.local.akbmailarchive.backup;

import android.content.Context;

import com.azudaisuki.akbmailarchive.R;
import com.local.akbmailarchive.model.ArchivedMail;
import com.local.akbmailarchive.model.DownloadedResource;
import com.local.akbmailarchive.model.InboxPage;
import com.local.akbmailarchive.model.MailItem;
import com.local.akbmailarchive.net.AkbApiClient;
import com.local.akbmailarchive.storage.ArchiveDatabase;
import com.local.akbmailarchive.storage.ArchiveStore;
import com.local.akbmailarchive.util.HtmlAssetExtractor;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GET-only backup pipeline: inbox pages -> detail HTML -> referenced images. */
public final class BackupManager {
    private static final int MAX_PAGES = 20_000;
    private static final int RETRIES = 2;

    private final Context context;
    private final AkbApiClient api;
    private final ArchiveDatabase db;
    private final ArchiveStore store;

    public BackupManager(Context context, AkbApiClient api, ArchiveDatabase db, ArchiveStore store) {
        this.context = context.getApplicationContext();
        this.api = api;
        this.db = db;
        this.store = store;
    }

    public BackupResult runFullBackup(ProgressListener listener) throws Exception {
        int page = 1;
        int fetchedMailCount = 0;
        boolean hasNext;

        do {
            ensureNotInterrupted();
            notify(listener, "INBOX", page, 0, fetchedMailCount,
                    context.getString(R.string.progress_inbox_fetching, page));

            InboxPage inbox = api.getInboxPage(page);
            store.savePageJson(page, inbox.rawJson);
            for (MailItem item : inbox.mails) db.upsertMail(item);
            fetchedMailCount += inbox.mails.size();

            notify(listener, "INBOX", page, 0, fetchedMailCount,
                    context.getString(R.string.progress_inbox_saved, page, fetchedMailCount));

            hasNext = inbox.hasNextPage;
            if (hasNext) {
                page++;
                if (page > MAX_PAGES) throw new IllegalStateException(context.getString(R.string.error_inbox_page_limit));
                sleepQuietly(40);
            }
        } while (hasNext);

        final int pageCount = page;
        db.putMeta("last_page_count", String.valueOf(pageCount));
        db.putMeta("last_backup_started_or_updated_at", String.valueOf(System.currentTimeMillis()));

        int detailFailures = downloadMissingDetails(listener);
        int imageFailures = downloadMissingImages(listener);

        store.writePortableIndexes(db, pageCount);
        db.putMeta("last_backup_completed_at", String.valueOf(System.currentTimeMillis()));

        return new BackupResult(
                pageCount,
                db.getMailCount(),
                db.getDetailCount(),
                db.getImageCount(),
                detailFailures,
                imageFailures
        );
    }

    private int downloadMissingDetails(ProgressListener listener) throws Exception {
        List<ArchivedMail> pending = db.listForDetailDownload(true);
        int failures = 0;
        int index = 0;
        int total = pending.size();

        for (ArchivedMail mail : pending) {
            ensureNotInterrupted();
            index++;
            notify(listener, "DETAIL", index, total, db.getDetailCount(),
                    context.getString(R.string.progress_detail_fetching, index, total, displayName(mail)));

            try {
                String html = retryDetail(mail.detailUrl);
                String path = store.saveDetailHtml(mail.id, html);
                db.markDetailSuccess(mail.id, path);
            } catch (Exception e) {
                failures++;
                db.markDetailFailure(mail.id, messageOf(e));
            }
            sleepQuietly(35);
        }
        return failures;
    }

    private int downloadMissingImages(ProgressListener listener) throws Exception {
        List<ArchivedMail> mails = db.listWithStoredDetail();
        int failures = 0;
        int mailIndex = 0;
        int totalMails = mails.size();

        for (ArchivedMail mail : mails) {
            ensureNotInterrupted();
            mailIndex++;
            String html;
            try {
                html = store.readUtf8(mail.detailHtmlPath);
            } catch (Exception e) {
                continue;
            }

            Set<String> urls = HtmlAssetExtractor.extract(html, mail.detailUrl);
            if (urls.isEmpty()) continue;
            Map<String, ArchiveDatabase.CachedImage> existing = db.getImageMap(mail.id);

            int assetIndex = 0;
            for (String url : urls) {
                ensureNotInterrupted();
                assetIndex++;
                ArchiveDatabase.CachedImage cached = existing.get(url);
                if (cached != null && fileExists(cached.localPath)) continue;

                notify(listener, "IMAGE", mailIndex, totalMails, db.getImageCount(),
                        context.getString(
                                R.string.progress_image_fetching,
                                mailIndex, totalMails, assetIndex, urls.size()
                        ));
                try {
                    DownloadedResource resource = retryResource(url, mail.detailUrl);
                    if (resource.bytes.length == 0) throw new IllegalStateException(context.getString(R.string.error_empty_resource));
                    ArchiveStore.SavedImage saved = store.saveImage(
                            mail.id, url, resource.bytes, resource.contentType
                    );
                    db.upsertImage(mail.id, url, saved.relativePath, saved.mimeType, saved.sha256);
                } catch (Exception e) {
                    failures++;
                }
                sleepQuietly(25);
            }
        }
        return failures;
    }

    private String retryDetail(String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                return api.fetchDetailHtml(url);
            } catch (Exception e) {
                last = e;
                if (attempt < RETRIES) sleepQuietly(250L * attempt);
            }
        }
        throw last == null ? new IllegalStateException(context.getString(R.string.error_detail_failed)) : last;
    }

    private DownloadedResource retryResource(String url, String referer) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= RETRIES; attempt++) {
            try {
                return api.fetchResource(url, referer);
            } catch (Exception e) {
                last = e;
                if (attempt < RETRIES) sleepQuietly(250L * attempt);
            }
        }
        throw last == null ? new IllegalStateException(context.getString(R.string.error_resource_failed)) : last;
    }

    private boolean fileExists(String relativePath) {
        try {
            File f = store.resolve(relativePath);
            return f.isFile() && f.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String displayName(ArchivedMail mail) {
        if (!mail.subject.isEmpty()) return mail.subject;
        if (!mail.memberName.isEmpty()) return mail.memberName;
        return mail.id;
    }

    private static void notify(
            ProgressListener listener,
            String stage,
            int current,
            int total,
            long savedCount,
            String message
    ) {
        if (listener != null) listener.onProgress(stage, current, total, savedCount, message);
    }

    private void ensureNotInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException(context.getString(R.string.error_backup_cancelled));
        }
    }

    private static void sleepQuietly(long ms) throws InterruptedException {
        if (ms <= 0) return;
        Thread.sleep(ms);
    }

    private String messageOf(Throwable t) {
        if (t == null) return context.getString(R.string.unknown_error);
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }

    public interface ProgressListener {
        void onProgress(String stage, int current, int total, long savedCount, String message);
    }
}
