package com.local.akbmailarchive.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.local.akbmailarchive.model.ArchivedMail;
import com.local.akbmailarchive.model.MailItem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedWriter;
import java.io.IOException;

/** Local-only archive database. No server credentials are stored here. */
public final class ArchiveDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "akb48_mail_archive.db";
    private static final int DB_VERSION = 1;

    public ArchiveDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE mails ("
                + "id TEXT PRIMARY KEY,"
                + "member_name TEXT NOT NULL DEFAULT '',"
                + "subject TEXT NOT NULL DEFAULT '',"
                + "receive_datetime TEXT NOT NULL DEFAULT '',"
                + "content TEXT NOT NULL DEFAULT '',"
                + "detail_url TEXT NOT NULL DEFAULT '',"
                + "raw_json TEXT NOT NULL DEFAULT '',"
                + "detail_html_path TEXT NOT NULL DEFAULT '',"
                + "detail_status INTEGER NOT NULL DEFAULT 0,"
                + "detail_error TEXT NOT NULL DEFAULT '',"
                + "local_read INTEGER NOT NULL DEFAULT 0,"
                + "local_star INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX idx_mails_datetime ON mails(receive_datetime DESC)");
        db.execSQL("CREATE INDEX idx_mails_member ON mails(member_name)");
        db.execSQL("CREATE TABLE images ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "mail_id TEXT NOT NULL,"
                + "source_url TEXT NOT NULL,"
                + "local_path TEXT NOT NULL DEFAULT '',"
                + "mime_type TEXT NOT NULL DEFAULT '',"
                + "sha256 TEXT NOT NULL DEFAULT '',"
                + "updated_at INTEGER NOT NULL DEFAULT 0,"
                + "UNIQUE(mail_id, source_url)"
                + ")");
        db.execSQL("CREATE INDEX idx_images_mail ON images(mail_id)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Unsupported archive DB upgrade " + oldVersion + " -> " + newVersion);
        }
    }

    public synchronized void upsertMail(MailItem item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("member_name", item.memberName);
        values.put("subject", item.subject);
        values.put("receive_datetime", item.receiveDatetime);
        values.put("content", item.content);
        values.put("detail_url", item.detailUrl);
        values.put("raw_json", item.rawJson);
        values.put("updated_at", System.currentTimeMillis());

        int rows = db.update("mails", values, "id=?", new String[]{item.id});
        if (rows == 0) {
            values.put("id", item.id);
            db.insertOrThrow("mails", null, values);
        }
    }

    public synchronized void markDetailSuccess(String mailId, String relativePath) {
        ContentValues v = new ContentValues();
        v.put("detail_html_path", safe(relativePath));
        v.put("detail_status", 1);
        v.put("detail_error", "");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("mails", v, "id=?", new String[]{mailId});
    }

    public synchronized void markDetailFailure(String mailId, String message) {
        ContentValues v = new ContentValues();
        v.put("detail_status", -1);
        v.put("detail_error", truncate(message, 1000));
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("mails", v, "id=?", new String[]{mailId});
    }

    public synchronized void upsertImage(
            String mailId,
            String sourceUrl,
            String localPath,
            String mimeType,
            String sha256
    ) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("local_path", safe(localPath));
        values.put("mime_type", safe(mimeType));
        values.put("sha256", safe(sha256));
        values.put("updated_at", System.currentTimeMillis());
        int rows = db.update(
                "images",
                values,
                "mail_id=? AND source_url=?",
                new String[]{mailId, sourceUrl}
        );
        if (rows == 0) {
            values.put("mail_id", mailId);
            values.put("source_url", sourceUrl);
            db.insertOrThrow("images", null, values);
        }
    }

    public synchronized long getMailCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM mails", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public synchronized long getDetailCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM mails WHERE detail_status=1", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public synchronized long getImageCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM images", null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public synchronized List<ArchivedMail> listForDetailDownload(boolean retryFailures) {
        String where = retryFailures
                ? "detail_url<>'' AND detail_status<>1"
                : "detail_url<>'' AND detail_status=0";
        List<ArchivedMail> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "mails",
                mailColumns(),
                where,
                null,
                null,
                null,
                "receive_datetime DESC"
        )) {
            while (c.moveToNext()) out.add(readArchivedMail(c));
        }
        return out;
    }

    public synchronized List<ArchivedMail> listWithStoredDetail() {
        List<ArchivedMail> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "mails",
                mailColumns(),
                "detail_status=1 AND detail_html_path<>''",
                null,
                null,
                null,
                "receive_datetime DESC"
        )) {
            while (c.moveToNext()) out.add(readArchivedMail(c));
        }
        return out;
    }

    public synchronized List<ArchivedMail> searchMails(String query, String year, int limit) {
        return searchMails(query, year, "", limit);
    }

    public synchronized List<ArchivedMail> searchMails(
            String query, String year, String member, int limit
    ) {
        String q = safe(query).trim();
        String y = safe(year).trim();
        String m = safe(member).trim();
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();

        if (!q.isEmpty()) {
            where.append(" AND (member_name LIKE ? OR subject LIKE ? OR content LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (!y.isEmpty()) {
            where.append(" AND receive_datetime LIKE ?");
            args.add(y + "%");
        }
        if (!m.isEmpty()) {
            where.append(" AND member_name=?");
            args.add(m);
        }

        List<ArchivedMail> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "mails",
                mailColumns(),
                where.toString(),
                args.toArray(new String[0]),
                null,
                null,
                "receive_datetime DESC",
                String.valueOf(Math.max(1, limit))
        )) {
            while (c.moveToNext()) out.add(readArchivedMail(c));
        }
        return out;
    }

    public synchronized List<String> listYears() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT substr(receive_datetime,1,4) y FROM mails "
                        + "WHERE length(receive_datetime)>=4 ORDER BY y DESC",
                null
        )) {
            while (c.moveToNext()) {
                String y = c.getString(0);
                if (y != null && y.matches("\\d{4}")) out.add(y);
            }
        }
        return out;
    }

    public synchronized List<String> listMembers() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT member_name FROM mails WHERE member_name<>'' ORDER BY member_name",
                null
        )) {
            while (c.moveToNext()) {
                String name = nz(c.getString(0));
                if (!name.isEmpty()) out.add(name);
            }
        }
        return out;
    }

    public synchronized ArchivedMail getMail(String mailId) {
        try (Cursor c = getReadableDatabase().query(
                "mails",
                mailColumns(),
                "id=?",
                new String[]{mailId},
                null,
                null,
                null,
                "1"
        )) {
            return c.moveToFirst() ? readArchivedMail(c) : null;
        }
    }

    public synchronized Map<String, CachedImage> getImageMap(String mailId) {
        Map<String, CachedImage> out = new LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().query(
                "images",
                new String[]{"source_url", "local_path", "mime_type"},
                "mail_id=?",
                new String[]{mailId},
                null,
                null,
                null
        )) {
            while (c.moveToNext()) {
                String source = nz(c.getString(0));
                out.put(source, new CachedImage(source, nz(c.getString(1)), nz(c.getString(2))));
            }
        }
        return out;
    }

    public synchronized void setLocalRead(String mailId, boolean read) {
        ContentValues v = new ContentValues();
        v.put("local_read", read ? 1 : 0);
        getWritableDatabase().update("mails", v, "id=?", new String[]{mailId});
    }

    public synchronized void setLocalStar(String mailId, boolean star) {
        ContentValues v = new ContentValues();
        v.put("local_star", star ? 1 : 0);
        getWritableDatabase().update("mails", v, "id=?", new String[]{mailId});
    }

    public synchronized void putMeta(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", safe(value));
        getWritableDatabase().insertWithOnConflict(
                "meta", null, v, SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public synchronized String getMeta(String key) {
        try (Cursor c = getReadableDatabase().query(
                "meta", new String[]{"value"}, "key=?", new String[]{key},
                null, null, null, "1")) {
            return c.moveToFirst() ? nz(c.getString(0)) : "";
        }
    }

    public synchronized void writeMailsJsonl(BufferedWriter writer) throws IOException {
        try (Cursor c = getReadableDatabase().query(
                "mails",
                new String[]{
                        "id", "member_name", "subject", "receive_datetime", "content",
                        "detail_url", "raw_json", "detail_html_path", "detail_status",
                        "local_read", "local_star"
                },
                null, null, null, null, "receive_datetime DESC"
        )) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("id", nz(c.getString(0)));
                    o.put("member_name", nz(c.getString(1)));
                    o.put("subject", nz(c.getString(2)));
                    o.put("receive_datetime", nz(c.getString(3)));
                    o.put("content", nz(c.getString(4)));
                    o.put("detail_url", nz(c.getString(5)));
                    o.put("raw_json", nz(c.getString(6)));
                    o.put("detail_html_path", nz(c.getString(7)));
                    o.put("detail_status", c.getInt(8));
                    o.put("local_read", c.getInt(9) != 0);
                    o.put("local_star", c.getInt(10) != 0);
                    writer.write(o.toString());
                    writer.newLine();
                } catch (Exception e) {
                    throw new IOException("Failed to export mail JSONL", e);
                }
            }
        }
    }

    public synchronized void writeImagesJsonl(BufferedWriter writer) throws IOException {
        try (Cursor c = getReadableDatabase().query(
                "images",
                new String[]{"mail_id", "source_url", "local_path", "mime_type", "sha256"},
                null, null, null, null, "mail_id, id"
        )) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                try {
                    o.put("mail_id", nz(c.getString(0)));
                    o.put("source_url", nz(c.getString(1)));
                    o.put("local_path", nz(c.getString(2)));
                    o.put("mime_type", nz(c.getString(3)));
                    o.put("sha256", nz(c.getString(4)));
                    writer.write(o.toString());
                    writer.newLine();
                } catch (Exception e) {
                    throw new IOException("Failed to export image JSONL", e);
                }
            }
        }
    }

    private static String[] mailColumns() {
        return new String[]{
                "id", "member_name", "subject", "receive_datetime", "content",
                "detail_url", "detail_html_path", "detail_status", "local_read", "local_star"
        };
    }

    private static ArchivedMail readArchivedMail(Cursor c) {
        return new ArchivedMail(
                nz(c.getString(0)),
                nz(c.getString(1)),
                nz(c.getString(2)),
                nz(c.getString(3)),
                nz(c.getString(4)),
                nz(c.getString(5)),
                nz(c.getString(6)),
                c.getInt(7),
                c.getInt(8) != 0,
                c.getInt(9) != 0
        );
    }

    public static final class CachedImage {
        public final String sourceUrl;
        public final String localPath;
        public final String mimeType;

        public CachedImage(String sourceUrl, String localPath, String mimeType) {
            this.sourceUrl = sourceUrl;
            this.localPath = localPath;
            this.mimeType = mimeType;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        String v = safe(s);
        return v.length() <= max ? v : v.substring(0, max);
    }
}
