package com.local.akbmailarchive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.local.akbmailarchive.model.ArchivedMail;
import com.local.akbmailarchive.storage.ArchiveDatabase;

import java.util.ArrayList;
import java.util.List;

public final class OfflineListActivity extends Activity {
    private ArchiveDatabase db;
    private EditText searchInput;
    private Spinner yearSpinner;
    private Spinner memberSpinner;
    private TextView summary;
    private ListView listView;
    private MailAdapter adapter;
    private final List<ArchivedMail> current = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ArchiveDatabase(this);
        setContentView(buildUi());
        setupFilters();
        reload();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(10));

        TextView title = new TextView(this);
        title.setText(R.string.offline_title);
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        root.addView(title, fullWidth());

        summary = new TextView(this);
        summary.setTextSize(13);
        summary.setTextColor(Color.DKGRAY);
        summary.setPadding(0, dp(4), 0, dp(8));
        root.addView(summary, fullWidth());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(searchRow, fullWidth());

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint(R.string.search_hint);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchParams.rightMargin = dp(6);
        searchRow.addView(searchInput, searchParams);

        Button searchButton = new Button(this);
        searchButton.setText(R.string.search_button);
        searchRow.addView(searchButton,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams filterParams = fullWidth();
        filterParams.topMargin = dp(6);
        filterParams.bottomMargin = dp(6);
        root.addView(filterRow, filterParams);

        yearSpinner = new Spinner(this);
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        yearParams.rightMargin = dp(4);
        filterRow.addView(yearSpinner, yearParams);

        memberSpinner = new Spinner(this);
        LinearLayout.LayoutParams memberParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        memberParams.leftMargin = dp(4);
        filterRow.addView(memberSpinner, memberParams);

        listView = new ListView(this);
        adapter = new MailAdapter();
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        searchButton.setOnClickListener(v -> reload());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            reload();
            return true;
        });
        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                reload();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        yearSpinner.setOnItemSelectedListener(filterListener);
        memberSpinner.setOnItemSelectedListener(filterListener);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ArchivedMail mail = current.get(position);
            Intent intent = new Intent(this, OfflineMailActivity.class);
            intent.putExtra(OfflineMailActivity.EXTRA_MAIL_ID, mail.id);
            startActivity(intent);
        });

        return root;
    }

    private void setupFilters() {
        List<String> years = new ArrayList<>();
        years.add(getString(R.string.filter_all_years));
        years.addAll(db.listYears());
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);

        List<String> members = new ArrayList<>();
        members.add(getString(R.string.filter_all_members));
        members.addAll(db.listMembers());
        ArrayAdapter<String> memberAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, members);
        memberAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        memberSpinner.setAdapter(memberAdapter);
    }

    private void reload() {
        if (db == null || searchInput == null || yearSpinner == null || memberSpinner == null) return;
        String query = searchInput.getText().toString().trim();
        String year = "";
        Object selectedYear = yearSpinner.getSelectedItem();
        if (selectedYear != null && !getString(R.string.filter_all_years).equals(selectedYear.toString())) {
            year = selectedYear.toString();
        }
        String member = "";
        Object selectedMember = memberSpinner.getSelectedItem();
        if (selectedMember != null && !getString(R.string.filter_all_members).equals(selectedMember.toString())) {
            member = selectedMember.toString();
        }

        current.clear();
        current.addAll(db.searchMails(query, year, member, 3000));
        String summaryText = getString(R.string.offline_summary, current.size(), db.getMailCount());
        if (current.size() >= 3000) {
            summaryText += getString(R.string.offline_summary_limited, 3000);
        }
        summary.setText(summaryText);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (db != null && adapter != null) reload();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private final class MailAdapter extends BaseAdapter {
        @Override public int getCount() { return current.size(); }
        @Override public Object getItem(int position) { return current.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView meta;
            TextView subject;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                meta = (TextView) row.getChildAt(0);
                subject = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(OfflineListActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));

                meta = new TextView(OfflineListActivity.this);
                meta.setTextSize(12);
                meta.setTextColor(Color.DKGRAY);
                row.addView(meta, fullWidth());

                subject = new TextView(OfflineListActivity.this);
                subject.setTextSize(17);
                subject.setTextColor(Color.BLACK);
                subject.setPadding(0, dp(3), 0, dp(1));
                row.addView(subject, fullWidth());
            }

            ArchivedMail item = current.get(position);
            meta.setText((item.localRead ? "" : "● ") + item.receiveDatetime
                    + (item.memberName.isEmpty() ? "" : " / " + item.memberName));
            subject.setText((item.localStar ? "★ " : "")
                    + (item.subject.isEmpty() ? getString(R.string.no_subject) : item.subject));
            row.setBackgroundColor(position % 2 == 0
                    ? Color.rgb(248,248,248) : Color.rgb(238,238,238));
            return row;
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
}
