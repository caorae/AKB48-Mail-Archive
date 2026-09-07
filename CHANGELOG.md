# Changelog

## v0.3.22

- First public release version.
- Release version is shown from `BuildConfig.VERSION_NAME` to avoid UI/version drift.
- Added redirect hardening so a non-empty authentication POST/PUT body is never forwarded to an untrusted HTTPS host.
- Repository package excludes local Android Studio/Gradle/build artifacts.

## v0.2.1
- Moved all normal user-facing UI copy out of Java and into `app/src/main/res/values/strings.xml`.
- Main screen, backup progress, ZIP export, offline list, and offline reader now resolve text from Android string resources.
- No backup/API behavior changes from v0.2.0.

## 0.2.0

- Confirmed v0.1.3 inheritance flow works in testing.
- Force portrait orientation for all activities.
- Fetch every inbox page until `has_next_page=false`.
- Add SQLite local archive.
- Save raw inbox page JSON.
- Download and persist detail HTML.
- Extract/download referenced images/resources.
- Do not send credentials to third-party resource hosts.
- Add offline list, year filter, keyword search.
- Add offline WebView reader with local image interception.
- Add local-only read/star state.
- Add portable archive manifest/JSONL layout.
- Add ZIP export via Android Storage Access Framework.
