# Known API / flow notes — v0.3.22

Base: `https://api.akb48mail-appli.com/v1/`

## Authentication / inheritance

1. `POST /users` creates a temporary user/session.
2. `POST /data_inherit_execute` is `application/x-www-form-urlencoded` with:
   - `user_id`
   - `password`
3. The temporary `User-Id` / `Access-Token` are present in headers for inheritance execution.
4. The returned inherited UserInfo replaces the temporary session.

## Inbox

`GET /inbox`

Confirmed parameters used by the archive:
- is_unread=false
- is_star=false
- page=N
- is_information=false

Pagination is driven by `has_next_page`.

## Mail detail

The official app loads `mail.detail_url` in a WebView while supplying its authenticated header map.
v0.3.22 mirrors this with an authenticated HTTPS GET and stores the HTML locally.

## Legacy headers

- Accept
- Content-Type (ordinary API / form body as appropriate)
- User-Id
- Access-Token
- Os-Type: android
- Os-Version
- User-Agent
- Device-Version
- Application-Version: 1.5.9
- Application-Language: ja
- Terms-Version

## Resource credential boundary

For HTML-referenced resources, v0.3.22 attaches User-Id / Access-Token only when the target host is exactly `akb48mail-appli.com` or a subdomain of it. Third-party hosts never receive those credentials.
