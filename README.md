# AKB48 Mail Archive (Unofficial) — Android v0.3.22

这是一个独立 Android 本地备份/离线阅读原型。不需要 BlueStacks、root、ADB，也不读取原 AKB48 Mail App 的私有目录。

## 已经打通的登录流程

v0.1.3 已实测成功。官方 Android App 的继承顺序是：

`POST /users` → 临时 User-Id / Access-Token → `POST /data_inherit_execute` → 正式会话 → `GET /inbox`

v0.3.22 保留这一流程，并开始进入真正的全量备份阶段。

## v0.3.22 新功能

- MainActivity、离线列表、邮件阅读器都强制竖屏
- 自动遍历 `/inbox` 的全部分页，直到 `has_next_page=false`
- 每页原始 JSON 保存到本地：`pages/page_XXXX.json`
- SQLite 保存邮件索引、正文、detail URL、本地已读、本地收藏
- 自动 GET 每封邮件的 `detail_url` 并保存 HTML
- 从 HTML 中提取 `<img src>` / `srcset` / CSS `url(...)` 图片并下载
- 资源下载仅在官方 `*.akb48mail-appli.com` 域名上携带 User-Id / Access-Token；第三方/CDN资源不会收到凭证
- 离线邮件列表：按年份筛选、成员/件名/正文搜索
- 离线 WebView 阅读器：不访问网络，只读取缓存 HTML + 图片
- 本地已读、本地收藏，不调用官方 read/star 更新 API
- 备份可重复执行；缺失/失败的 detail 与图片会再次尝试
- 通过 Android Storage Access Framework 导出 ZIP
- ZIP 中不包含引き継ぎ密码、User-Id 或 Access-Token

## 本地/ZIP 结构（Portable Archive v1）

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

`manifest.json` 会记录邮件、detail、图片数量，并明确 `credentials_included=false`。
这个结构将作为后续 Windows / Android 共用格式的基础。

## 当前安全策略

- 引き継ぎ密码：仅用于本次登录请求，不落盘
- User-Id / Access-Token：当前仅保存在内存中，不写 SQLite、不写 archive、不写 ZIP
- 全量备份阶段只发送 GET 请求
- 不 PATCH 官方已读/收藏状态
- 非 HTTPS 资源直接拒绝
- 离线阅读时 WebView 的 http/https 请求全部被本地拦截，不回源

## 当前尚未完成

v0.3.22 还没有：

- ZIP 导入
- 多账号隔离
- Android Keystore 的长期 Token 保存
- 更漂亮的 Material UI / RecyclerView
- 按成员的独立筛选菜单
- 增量更新策略优化
- 与 Windows v0.1 工具逐字段完全统一（当前先确定 Portable Archive v1）

## 构建

1. 用 Android Studio 打开项目根目录
2. Gradle JDK 选择 JDK 17
3. 安装 Android SDK Platform 35
4. Sync Project with Gradle Files
5. Build > Build APK(s)

项目不附带 Android SDK 或 Gradle 二进制。

## 非官方说明

本项目只用于用户保存自己有权访问的历史邮件，与 AKB48 官方及原 App 开发/运营方无隶属关系。
