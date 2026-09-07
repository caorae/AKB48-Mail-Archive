# AKB48MailArchive Portable Archive v1

The exported ZIP root contains the following portable files. The Android SQLite database is intentionally not exported; another client can rebuild its own local index from JSONL.

## manifest.json

```json
{
  "format": "AKB48MailArchive",
  "format_version": 1,
  "generated_at": "2026-09-08T01:00:00+09:00",
  "page_count": 123,
  "mail_count": 2456,
  "detail_count": 2456,
  "image_count": 3100,
  "credentials_included": false
}
```

## mails.jsonl

One JSON object per line. Fields:

- `id`
- `member_name`
- `subject`
- `receive_datetime`
- `content` — content returned in inbox JSON, if present
- `detail_url` — original official detail URL
- `raw_json` — original per-mail JSON object serialized as text
- `detail_html_path` — relative path under archive root
- `detail_status` — `1` downloaded, `0` pending, `-1` failed
- `local_read` — local-only state
- `local_star` — local-only state

## images.jsonl

One cached resource per line:

- `mail_id`
- `source_url`
- `local_path`
- `mime_type`
- `sha256` — SHA-256 of downloaded bytes

## pages/

Raw `/inbox` response JSON, one file per page: `page_0001.json`, `page_0002.json`, etc.

## details/

Raw detail HTML. File name is based on sanitized mail id.

## images/

Cached HTML-referenced resources, grouped by mail id. File names use SHA-256 of the source URL so duplicate names/query strings cannot collide.

## Credentials

The portable archive MUST NOT contain:

- inheritance password
- User-Id header value
- Access-Token header value

`credentials_included` must remain `false`.
