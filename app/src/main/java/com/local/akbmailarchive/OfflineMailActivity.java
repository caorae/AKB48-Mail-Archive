package com.local.akbmailarchive;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.local.akbmailarchive.model.ArchivedMail;
import com.local.akbmailarchive.storage.ArchiveDatabase;
import com.local.akbmailarchive.storage.ArchiveStore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

public final class OfflineMailActivity extends Activity {
    public static final String EXTRA_MAIL_ID = "mail_id";

    private ArchiveDatabase db;
    private ArchiveStore store;
    private ArchivedMail mail;
    private Map<String, ArchiveDatabase.CachedImage> imageMap;
    private WebView webView;
    private Button starButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ArchiveDatabase(this);
        store = new ArchiveStore(this);

        String mailId = getIntent().getStringExtra(EXTRA_MAIL_ID);
        mail = db.getMail(mailId == null ? "" : mailId);
        if (mail == null) {
            TextView error = new TextView(this);
            error.setText(R.string.mail_not_found);
            error.setPadding(dp(20), dp(20), dp(20), dp(20));
            setContentView(error);
            return;
        }

        imageMap = db.getImageMap(mail.id);
        db.setLocalRead(mail.id, true);
        setContentView(buildUi());
        loadOfflineHtml();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), 0);

        TextView meta = new TextView(this);
        meta.setText(mail.receiveDatetime + (mail.memberName.isEmpty() ? "" : " / " + mail.memberName));
        meta.setTextSize(13);
        meta.setTextColor(Color.DKGRAY);
        root.addView(meta, fullWidth());

        TextView subject = new TextView(this);
        subject.setText(mail.subject.isEmpty() ? getString(R.string.no_subject) : mail.subject);
        subject.setTextSize(22);
        subject.setTextColor(Color.BLACK);
        subject.setPadding(0, dp(4), 0, dp(8));
        root.addView(subject, fullWidth());

        starButton = new Button(this);
        updateStarButton(mail.localStar);
        starButton.setOnClickListener(v -> {
            ArchivedMail latest = db.getMail(mail.id);
            boolean newState = latest == null || !latest.localStar;
            db.setLocalStar(mail.id, newState);
            updateStarButton(newState);
        });
        root.addView(starButton, fullWidth());

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // All http/https subrequests are intercepted below. Nothing is fetched from the network.
        settings.setBlockNetworkLoads(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localResponse(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return localResponse(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void loadOfflineHtml() {
        String html = "";
        try {
            if (!mail.detailHtmlPath.isEmpty()) html = store.readUtf8(mail.detailHtmlPath);
        } catch (Exception ignored) {}

        if (html.isEmpty()) {
            if (mail.content.trim().startsWith("<")) {
                html = mail.content;
            } else {
                html = "<html><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                        + "<body style=\"font-family:sans-serif;white-space:pre-wrap\">"
                        + TextUtils.htmlEncode(mail.content)
                        + "</body></html>";
            }
        }
        if (html.isEmpty()) {
            html = "<html><body><p>"
                    + TextUtils.htmlEncode(getString(R.string.mail_body_not_saved))
                    + "</p></body></html>";
        }

        String base = mail.detailUrl.isEmpty()
                ? "https://api.akb48mail-appli.com/"
                : mail.detailUrl;
        webView.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null);
    }

    private WebResourceResponse localResponse(String url) {
        if (url == null || imageMap == null) return null;
        ArchiveDatabase.CachedImage cached = imageMap.get(url);
        if (cached != null) {
            try {
                File file = store.resolve(cached.localPath);
                if (file.isFile()) {
                    String mime = cached.mimeType.isEmpty() ? "application/octet-stream" : cached.mimeType;
                    return new WebResourceResponse(mime, null, new FileInputStream(file));
                }
            } catch (Exception ignored) {}
        }
        // Never fall through to the real network while reading the local archive.
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return new WebResourceResponse(
                    "text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    private void updateStarButton(boolean starred) {
        if (starButton != null) {
            starButton.setText(starred ? R.string.favorite_remove : R.string.favorite_add);
        }
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        if (db != null) db.close();
        super.onDestroy();
    }
}
