package com.local.akbmailarchive.storage;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File layout for portable archive v1.
 *
 * archive_v1/
 *   manifest.json
 *   mails.jsonl
 *   images.jsonl
 *   pages/page_0001.json
 *   details/m12345.html
 *   images/m12345/<sha256>.<ext>
 */
public final class ArchiveStore {
    public static final int FORMAT_VERSION = 1;

    private final File root;
    private final File pagesDir;
    private final File detailsDir;
    private final File imagesDir;

    public ArchiveStore(Context context) {
        root = new File(context.getApplicationContext().getFilesDir(), "archive_v1");
        pagesDir = new File(root, "pages");
        detailsDir = new File(root, "details");
        imagesDir = new File(root, "images");
        ensureDirs();
    }

    public File getRoot() { return root; }

    public synchronized String savePageJson(int page, String rawJson) throws IOException {
        File file = new File(pagesDir, String.format(Locale.US, "page_%04d.json", page));
        writeUtf8Atomic(file, safe(rawJson));
        return relative(file);
    }

    public synchronized String saveDetailHtml(String mailId, String html) throws IOException {
        File file = new File(detailsDir, safeFileName(mailId) + ".html");
        writeUtf8Atomic(file, safe(html));
        return relative(file);
    }

    public synchronized SavedImage saveImage(
            String mailId,
            String sourceUrl,
            byte[] bytes,
            String mimeType
    ) throws IOException {
        String safeMail = safeFileName(mailId);
        File mailDir = new File(imagesDir, safeMail);
        if (!mailDir.exists() && !mailDir.mkdirs() && !mailDir.isDirectory()) {
            throw new IOException("Cannot create image directory: " + mailDir);
        }

        String hash = sha256Hex(bytes);
        String urlHash = sha256Hex(sourceUrl.getBytes(StandardCharsets.UTF_8));
        String ext = extensionFor(sourceUrl, mimeType);
        File file = new File(mailDir, urlHash + ext);

        if (!file.exists() || file.length() != bytes.length) {
            writeBytesAtomic(file, bytes);
        }
        return new SavedImage(relative(file), normalizedMime(mimeType, ext), hash);
    }

    public synchronized String readUtf8(String relativePath) throws IOException {
        File file = resolve(relativePath);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] bytes = readAllBytes(in);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public synchronized File resolve(String relativePath) throws IOException {
        File file = new File(root, safe(relativePath));
        String rootPath = root.getCanonicalPath() + File.separator;
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(rootPath)) {
            throw new IOException("Path escapes archive root");
        }
        return file;
    }

    public synchronized void writePortableIndexes(ArchiveDatabase db, int pageCount) throws IOException {
        writeDatabaseJsonLines(new File(root, "mails.jsonl"), db, true);
        writeDatabaseJsonLines(new File(root, "images.jsonl"), db, false);

        JSONObject manifest = new JSONObject();
        try {
            manifest.put("format", "AKB48MailArchive");
            manifest.put("format_version", FORMAT_VERSION);
            manifest.put("generated_at", isoNow());
            manifest.put("page_count", pageCount);
            manifest.put("mail_count", db.getMailCount());
            manifest.put("detail_count", db.getDetailCount());
            manifest.put("image_count", db.getImageCount());
            manifest.put("credentials_included", false);
            manifest.put("notes", "Portable archive. User-Id, Access-Token and inheritance password are not exported.");
        } catch (Exception e) {
            throw new IOException("Failed to build manifest", e);
        }
        writeUtf8Atomic(new File(root, "manifest.json"), manifest.toString());
    }

    public synchronized void writeZip(OutputStream output) throws IOException {
        if (output == null) throw new IOException("Output stream is null");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            addDirectoryToZip(zip, root, root);
        }
    }

    private void addDirectoryToZip(ZipOutputStream zip, File base, File current) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        byte[] buffer = new byte[64 * 1024];

        for (File child : children) {
            if (child.getName().endsWith(".tmp")) continue;
            if (child.isDirectory()) {
                addDirectoryToZip(zip, base, child);
                continue;
            }
            String name = base.toURI().relativize(child.toURI()).getPath();
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(child.lastModified());
            zip.putNextEntry(entry);
            try (InputStream in = new BufferedInputStream(new FileInputStream(child))) {
                int n;
                while ((n = in.read(buffer)) >= 0) zip.write(buffer, 0, n);
            }
            zip.closeEntry();
        }
    }

    private void writeDatabaseJsonLines(
            File file, ArchiveDatabase db, boolean mails
    ) throws IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            if (mails) db.writeMailsJsonl(writer);
            else db.writeImagesJsonl(writer);
        }
        replaceFile(tmp, file);
    }

    private void ensureDirs() {
        if (!root.exists()) root.mkdirs();
        if (!pagesDir.exists()) pagesDir.mkdirs();
        if (!detailsDir.exists()) detailsDir.mkdirs();
        if (!imagesDir.exists()) imagesDir.mkdirs();
    }

    private String relative(File file) {
        return root.toURI().relativize(file.toURI()).getPath();
    }

    private static void writeUtf8Atomic(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        replaceFile(tmp, file);
    }

    private static void writeBytesAtomic(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create directory: " + parent);
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
            out.write(bytes);
        }
        replaceFile(tmp, file);
    }

    private static void replaceFile(File tmp, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("Cannot replace " + target);
        }
        if (!tmp.renameTo(target)) {
            try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(target)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            }
            if (!tmp.delete()) tmp.deleteOnExit();
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    public static String sha256Hex(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static String extensionFor(String url, String mime) {
        String m = safe(mime).toLowerCase(Locale.US);
        if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
        if (m.contains("png")) return ".png";
        if (m.contains("gif")) return ".gif";
        if (m.contains("webp")) return ".webp";
        if (m.contains("svg")) return ".svg";

        String lower = safe(url).toLowerCase(Locale.US);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg"}) {
            if (lower.endsWith(ext)) return ext.equals(".jpeg") ? ".jpg" : ext;
        }
        return ".bin";
    }

    private static String normalizedMime(String mime, String ext) {
        String m = safe(mime);
        int semi = m.indexOf(';');
        if (semi >= 0) m = m.substring(0, semi).trim();
        if (!m.isEmpty()) return m;
        switch (ext) {
            case ".jpg": return "image/jpeg";
            case ".png": return "image/png";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".svg": return "image/svg+xml";
            default: return "application/octet-stream";
        }
    }

    private static String safeFileName(String value) {
        String v = safe(value).replaceAll("[^A-Za-z0-9._-]", "_");
        if (v.isEmpty()) v = "mail";
        return v.length() <= 120 ? v : v.substring(0, 120);
    }

    private static String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date());
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static final class SavedImage {
        public final String relativePath;
        public final String mimeType;
        public final String sha256;

        public SavedImage(String relativePath, String mimeType, String sha256) {
            this.relativePath = relativePath;
            this.mimeType = mimeType;
            this.sha256 = sha256;
        }
    }
}
