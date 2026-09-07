package com.local.akbmailarchive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.azudaisuki.akbmailarchive.BuildConfig;
import com.azudaisuki.akbmailarchive.R;
import com.local.akbmailarchive.backup.BackupManager;
import com.local.akbmailarchive.backup.BackupResult;
import com.local.akbmailarchive.model.ArchivedMail;
import com.local.akbmailarchive.model.LoginSession;
import com.local.akbmailarchive.net.AkbApiClient;
import com.local.akbmailarchive.net.AkbApiException;
import com.local.akbmailarchive.storage.ArchiveDatabase;
import com.local.akbmailarchive.storage.ArchiveStore;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_EXPORT_ZIP = 1001;

    private final AkbApiClient api = new AkbApiClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ArchiveDatabase db;
    private ArchiveStore store;

    private EditText inheritanceIdInput;
    private EditText passwordInput;
    private Button backupButton;
    private Button openArchiveButton;
    private Button exportButton;
    private ProgressBar progress;
    private TextView status;
    private TextView localSummary;
    private LinearLayout previewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ArchiveDatabase(this);
        store = new ArchiveStore(this);
        setContentView(buildUi());
        refreshLocalArchive();
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.main_title);
        title.setTextSize(26);
        title.setTextColor(Color.rgb(25, 25, 25));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.main_subtitle, BuildConfig.VERSION_NAME));
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        localSummary = new TextView(this);
        localSummary.setTextSize(14);
        localSummary.setPadding(dp(10), dp(10), dp(10), dp(10));
        localSummary.setBackgroundColor(Color.rgb(242, 242, 242));
        root.addView(localSummary, fullWidth());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(10), 0, dp(12));
        root.addView(actionRow, fullWidth());

        openArchiveButton = new Button(this);
        openArchiveButton.setText(R.string.button_offline_view);
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half1.rightMargin = dp(5);
        actionRow.addView(openArchiveButton, half1);

        exportButton = new Button(this);
        exportButton.setText(R.string.button_export_zip);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(5);
        actionRow.addView(exportButton, half2);

        root.addView(label(R.string.label_inheritance_id));
        inheritanceIdInput = new EditText(this);
        inheritanceIdInput.setSingleLine(true);
        inheritanceIdInput.setHint(R.string.hint_inheritance_id);
        inheritanceIdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(inheritanceIdInput, fullWidth());

        TextView passLabel = label(R.string.label_inheritance_password);
        passLabel.setPadding(0, dp(12), 0, 0);
        root.addView(passLabel);

        passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint(R.string.hint_inheritance_password);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordInput, fullWidth());

        backupButton = new Button(this);
        backupButton.setText(R.string.button_backup_all);
        LinearLayout.LayoutParams buttonParams = fullWidth();
        buttonParams.topMargin = dp(18);
        root.addView(backupButton, buttonParams);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = fullWidth();
        progressParams.topMargin = dp(12);
        root.addView(progress, progressParams);

        status = new TextView(this);
        status.setText(R.string.status_ready);
        status.setTextSize(14);
        status.setPadding(0, dp(12), 0, dp(12));
        root.addView(status);

        TextView privacy = new TextView(this);
        privacy.setText(R.string.privacy_notice);
        privacy.setTextSize(12);
        privacy.setTextColor(Color.GRAY);
        privacy.setPadding(0, 0, 0, dp(14));
        root.addView(privacy);

        TextView previewTitle = new TextView(this);
        previewTitle.setText(R.string.preview_title_latest);
        previewTitle.setTextSize(19);
        previewTitle.setPadding(0, dp(8), 0, dp(8));
        root.addView(previewTitle);

        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(previewContainer, fullWidth());

        backupButton.setOnClickListener(v -> startFullBackup());
        openArchiveButton.setOnClickListener(v -> openArchive());
        exportButton.setOnClickListener(v -> chooseExportDestination());
        return scroll;
    }

    private void startFullBackup() {
        String inheritanceId = inheritanceIdInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (inheritanceId.isEmpty()) {
            inheritanceIdInput.setError(getString(R.string.error_inheritance_id_required));
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError(getString(R.string.error_password_required));
            return;
        }

        setBusy(true, getString(R.string.status_inheritance_running));
        executor.submit(() -> {
            try {
                LoginSession session = api.loginWithInheritance(inheritanceId, password);
                runOnUiThread(() -> {
                    passwordInput.setText("");
                    status.setText(session.nickname.isEmpty()
                            ? getString(R.string.status_login_success)
                            : getString(R.string.status_login_success_with_nickname, session.nickname));
                });

                BackupManager manager = new BackupManager(this, api, db, store);
                BackupResult result = manager.runFullBackup((stage, current, total, savedCount, message) ->
                        runOnUiThread(() -> updateProgress(stage, current, total, message)));

                runOnUiThread(() -> {
                    String completed = getString(
                            R.string.status_backup_complete,
                            result.pageCount, result.mailCount, result.detailCount, result.imageCount
                    );
                    if (result.detailFailures > 0) {
                        completed += getString(R.string.status_detail_failures, result.detailFailures);
                    }
                    if (result.imageFailures > 0) {
                        completed += getString(R.string.status_image_failures, result.imageFailures);
                    }
                    setBusy(false, completed);
                    refreshLocalArchive();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String message = explainError(e);
                    String diag = api.getDiagnostics();
                    if (!diag.isEmpty()) {
                        message += getString(R.string.diagnostic_header) + diag;
                    }
                    setBusy(false, message);
                    refreshLocalArchive();
                });
            }
        });
    }

    private void updateProgress(String stage, int current, int total, String message) {
        if (total > 0) {
            progress.setIndeterminate(false);
            progress.setMax(total);
            progress.setProgress(Math.min(current, total));
        } else {
            progress.setIndeterminate(true);
        }
        status.setText(message);
    }

    private void refreshLocalArchive() {
        long mails = db.getMailCount();
        long details = db.getDetailCount();
        long images = db.getImageCount();
        localSummary.setText(getString(R.string.local_summary, mails, details, images));
        openArchiveButton.setEnabled(mails > 0);
        exportButton.setEnabled(mails > 0);
        renderPreview(db.searchMails("", "", 20));
    }

    private void renderPreview(List<ArchivedMail> mails) {
        previewContainer.removeAllViews();
        if (mails.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.local_archive_empty);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            previewContainer.addView(empty);
            return;
        }

        int n = 0;
        for (ArchivedMail item : mails) {
            n++;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(n % 2 == 0 ? Color.rgb(248,248,248) : Color.rgb(238,238,238));

            TextView meta = new TextView(this);
            meta.setText(item.receiveDatetime + (item.memberName.isEmpty() ? "" : " / " + item.memberName));
            meta.setTextSize(12);
            meta.setTextColor(Color.DKGRAY);
            card.addView(meta);

            TextView subject = new TextView(this);
            subject.setText((item.localStar ? "★ " : "")
                    + (item.subject.isEmpty() ? getString(R.string.no_subject) : item.subject));
            subject.setTextSize(16);
            subject.setTextColor(Color.BLACK);
            card.addView(subject);
            card.setOnClickListener(v -> openMail(item.id));

            LinearLayout.LayoutParams p = fullWidth();
            p.bottomMargin = dp(5);
            previewContainer.addView(card, p);
        }
    }

    private void openArchive() {
        startActivity(new Intent(this, OfflineListActivity.class));
    }

    private void openMail(String mailId) {
        Intent intent = new Intent(this, OfflineMailActivity.class);
        intent.putExtra(OfflineMailActivity.EXTRA_MAIL_ID, mailId);
        startActivity(intent);
    }

    private void chooseExportDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "AKB48MailArchive_" + stamp + ".zip");
        startActivityForResult(intent, REQ_EXPORT_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT_ZIP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        setBusy(true, getString(R.string.status_zip_exporting));
        executor.submit(() -> {
            try {
                int pageCount = parseInt(db.getMeta("last_page_count"), 0);
                store.writePortableIndexes(db, pageCount);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException(getString(R.string.error_export_destination_unavailable));
                    store.writeZip(out);
                }
                runOnUiThread(() -> setBusy(false, getString(R.string.status_zip_export_complete)));
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false, getString(R.string.status_zip_export_failed, messageOf(e))));
            }
        });
    }

    private String explainError(Exception e) {
        if (e instanceof SecurityException) {
            return getString(R.string.error_sensitive_redirect_blocked);
        }
        if (e instanceof AkbApiException) {
            AkbApiException apiError = (AkbApiException) e;
            if (apiError.getStatusCode() == 401) {
                return getString(R.string.error_auth_401, apiError.getMessage());
            }
            if (apiError.getStatusCode() == 426) {
                return getString(R.string.error_terms_426, apiError.getMessage());
            }
            return getString(R.string.error_api_http, apiError.getStatusCode(), apiError.getMessage());
        }
        return getString(R.string.error_generic, messageOf(e));
    }

    private void setBusy(boolean busy, String message) {
        backupButton.setEnabled(!busy);
        inheritanceIdInput.setEnabled(!busy);
        passwordInput.setEnabled(!busy);
        openArchiveButton.setEnabled(!busy && db.getMailCount() > 0);
        exportButton.setEnabled(!busy && db.getMailCount() > 0);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (!busy) progress.setIndeterminate(true);
        status.setText(message);
    }

    private TextView label(int textResId) {
        TextView view = new TextView(this);
        view.setText(textResId);
        view.setTextSize(14);
        view.setTextColor(Color.DKGRAY);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }

    private String messageOf(Throwable t) {
        if (t == null) return getString(R.string.unknown_error);
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (db != null && localSummary != null) refreshLocalArchive();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        api.clearSession();
        if (db != null) db.close();
        super.onDestroy();
    }
}
