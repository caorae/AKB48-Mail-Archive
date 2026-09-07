# AKB48 Mail Archive (Unofficial) — Android v0.3.22

[日本語](#日本語) · [English](#english) · [简体中文](#简体中文)

---

## 日本語

AKB48 Mail のメールを端末内に保存し、サービス終了後もオフラインで閲覧するための**非公式 Android バックアップ／アーカイブアプリ**です。

BlueStacks、root、ADB は不要で、公式 AKB48 Mail アプリのプライベート領域を読み取ることもありません。

### 主な機能

- 引き継ぎID / 引き継ぎパスワードによるログイン
- `/inbox` の全ページを自動取得
- メール一覧・本文HTML・画像を端末内に保存
- SQLite にメール情報、ローカル既読、ローカルお気に入りを保存
- 年別・メンバー別の絞り込み、件名・本文検索
- 完全オフラインのメール閲覧
- 失敗した本文・画像の再取得
- ZIP 書き出し（Portable Archive v1）
- ZIP に引き継ぎパスワード、User-Id、Access-Token を保存しない

### ログインフロー

公式 Android アプリと同じ引き継ぎフローを使用します。

`POST /users` → 一時 User-Id / Access-Token → `POST /data_inherit_execute` → 正式セッション → `GET /inbox`

### ローカル / ZIP 構成

```text
archive_v1/
  manifest.json
  mails.jsonl
  images.jsonl
  pages/
    page_0001.json
    page_0002.json
    ...
  details/
    m12345.html
    ...
  images/
    m12345/
      <url-sha256>.jpg
      ...
```

`manifest.json` にはメール・本文・画像の件数と `credentials_included=false` が記録されます。

### セキュリティ方針

- 引き継ぎパスワードはログイン時のみ使用し、端末内へ保存しません
- User-Id / Access-Token は現在メモリ上のみで保持し、SQLite / archive / ZIP には保存しません
- メール本文・画像の取得は GET リクエストのみで行います
- サーバー側の既読 / スター状態を変更する API は呼びません
- 非 HTTPS リソースは拒否します
- 認証情報は AKB48 Mail の公式ドメイン以外には送信しません
- オフライン閲覧時の WebView はネットワークへアクセスしません

### 現在未実装

- ZIP インポート
- 複数アカウントの分離
- Android Keystore を利用した長期 Token 保存
- UI の追加改善
- 増分バックアップの最適化
- Windows 版との完全なフォーマット統一

### ビルド

1. Android Studio でプロジェクトルートを開く
2. Gradle JDK に JDK 17 を指定
3. Android SDK Platform 35 をインストール
4. `Sync Project with Gradle Files`
5. `Build > Build APK(s)`

Android SDK や署名用 keystore はリポジトリに含まれません。

### 注意事項

本プロジェクトは非公式であり、AKB48 および公式運営・公式アプリ開発元とは関係ありません。

ご自身が正当にアクセスできるアカウントの個人バックアップ用途でのみ使用してください。保存したメール本文・画像についても、公式の利用条件・権利者のルールに従い、個人での閲覧・保管を目的として取り扱ってください。

---

## English

AKB48 Mail Archive is an **unofficial Android backup and offline reader** for saving AKB48 Mail messages locally so they can continue to be viewed offline after the service ends.

It does not require BlueStacks, root access, or ADB, and it does not read the private storage of the official AKB48 Mail app.

### Main features

- Login with transfer ID and transfer password
- Automatic download of every `/inbox` page
- Local backup of message metadata, HTML content, and images
- SQLite storage for mail metadata, local read state, and local favorites
- Filtering by year/member and search across subject/body text
- Fully offline mail reader
- Retry of failed message HTML or image downloads
- ZIP export using Portable Archive v1
- Exported ZIP files never include the transfer password, User-Id, or Access-Token

### Login flow

The app follows the same transfer flow used by the official Android app:

`POST /users` → temporary User-Id / Access-Token → `POST /data_inherit_execute` → authenticated session → `GET /inbox`

### Local / ZIP format

```text
archive_v1/
  manifest.json
  mails.jsonl
  images.jsonl
  pages/
    page_0001.json
    page_0002.json
    ...
  details/
    m12345.html
    ...
  images/
    m12345/
      <url-sha256>.jpg
      ...
```

`manifest.json` records mail/detail/image counts and explicitly sets `credentials_included=false`.

### Security policy

- The transfer password is used only for login and is not written to local storage
- User-Id / Access-Token are currently kept in memory only and are not written to SQLite, archives, or ZIP exports
- Mail content and images are downloaded using GET requests only
- The app does not call APIs that modify server-side read/star state
- Non-HTTPS resources are rejected
- Authentication headers are never sent to non-AKB48 Mail domains
- The offline WebView does not access the network

### Not yet implemented

- ZIP import
- Multi-account separation
- Long-term Token storage with Android Keystore
- Additional UI improvements
- Optimized incremental backup
- Full format unification with the Windows version

### Build

1. Open the project root in Android Studio
2. Set Gradle JDK to JDK 17
3. Install Android SDK Platform 35
4. Run `Sync Project with Gradle Files`
5. Use `Build > Build APK(s)`

Android SDK files and signing keystores are not included in this repository.

### Disclaimer

This is an unofficial project and is not affiliated with AKB48, its official operators, or the developers/operators of the official application.

Use it only to back up an account that you are legitimately authorized to access. Saved mail text and images should be handled for personal viewing and archival purposes in accordance with the applicable terms and rights-holder rules.

---

## 简体中文

AKB48 Mail Archive 是一款**非官方 Android 本地备份 / 离线阅读工具**，用于把 AKB48 Mail 邮件保存到自己的设备中，在服务结束后仍可离线查看。

不需要 BlueStacks、root 或 ADB，也不会读取官方 AKB48 Mail App 的私有目录。

### 主要功能

- 使用引き継ぎID / 引き継ぎ用パスワード登录
- 自动获取 `/inbox` 的全部分页
- 本地保存邮件索引、正文 HTML 和图片
- 使用 SQLite 保存邮件信息、本地已读和本地收藏
- 按年份 / 成员筛选，支持标题 / 正文搜索
- 完全离线的邮件阅读器
- 对失败的正文或图片进行重试
- 通过 Portable Archive v1 格式导出 ZIP
- 导出的 ZIP 中不保存迁移密码、User-Id 或 Access-Token

### 登录流程

应用使用与官方 Android App 相同的迁移流程：

`POST /users` → 临时 User-Id / Access-Token → `POST /data_inherit_execute` → 正式会话 → `GET /inbox`

### 本地 / ZIP 结构

```text
archive_v1/
  manifest.json
  mails.jsonl
  images.jsonl
  pages/
    page_0001.json
    page_0002.json
    ...
  details/
    m12345.html
    ...
  images/
    m12345/
      <url-sha256>.jpg
      ...
```

`manifest.json` 会记录邮件、正文和图片数量，并明确写入 `credentials_included=false`。

### 安全策略

- 迁移密码只用于登录请求，不写入本地存储
- User-Id / Access-Token 当前仅保存在内存中，不写入 SQLite、archive 或 ZIP
- 邮件正文和图片仅通过 GET 请求获取
- 不调用修改服务器端已读 / 收藏状态的 API
- 拒绝非 HTTPS 资源
- 不会把认证 Header 发送给 AKB48 Mail 官方域名之外的服务器
- 离线阅读 WebView 不访问网络

### 当前尚未实现

- ZIP 导入
- 多账号隔离
- 使用 Android Keystore 长期保存 Token
- 更进一步的 UI 优化
- 增量备份策略优化
- 与 Windows 版备份格式完全统一

### 构建

1. 用 Android Studio 打开项目根目录
2. Gradle JDK 选择 JDK 17
3. 安装 Android SDK Platform 35
4. 执行 `Sync Project with Gradle Files`
5. 使用 `Build > Build APK(s)`

本仓库不包含 Android SDK 或 APK 签名 keystore。

### 非官方说明

本项目为非官方项目，与 AKB48、官方运营方以及官方 App 的开发 / 运营方无隶属关系。

请仅用于备份您本人有权访问的账号。保存下来的邮件正文和图片也应遵守相关使用条款和权利方规则，以个人阅读和保存为目的进行使用。