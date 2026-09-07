# AKB48 Mail Archive v0.3.22 test checklist

## A. 竖屏

1. 在 BlueStacks/Android 设备横屏状态启动 App。
2. 预期：MainActivity 自动切换到 portrait。
3. 打开离线列表与邮件正文，预期也保持 portrait。

## B. 全量分页

1. 在官方 Android App 生成仍有效的引き継ぎID/密码。
2. v0.3.22 输入后点击「引き継ぎして全件バックアップ開始」。
3. 预期登录阶段：TEMP_USER → INHERIT 均为 HTTP 2xx。
4. 之后界面应持续出现：`一覧取得: page=N / XXX通`。
5. 最终必须走到 `has_next_page=false` 后才进入 detail 阶段。

## C. 正文和图片

1. 预期状态变为 `本文取得: X / N`。
2. 之后会出现 `画像取得...`。
3. 完成后首页显示：邮件 / 本文 / 图片数量。
4. 若少数 detail/image 失败，不应导致整个备份丢失；再次运行应重新尝试失败项。

## D. 离线阅读

1. 完成备份后点击「オフライン閲覧」。
2. 可以按年份筛选、关键词搜索。
3. 点击邮件，正文和已缓存图片应显示。
4. 断网后重复打开同一邮件，仍应显示正文和图片。
5. 本地お気に入り与未读点只影响本地 SQLite。

## E. ZIP

1. 首页点击「ZIP書き出し」。
2. 选择 Downloads 等位置。
3. ZIP 预期含：manifest.json、mails.jsonl、images.jsonl、pages/、details/、images/。
4. manifest 中 `credentials_included` 必须为 false。
5. ZIP 中不应出现引き継ぎ密码、User-Id 或 Access-Token。

## 报错时

只需要提供：失败阶段、HTTP status、错误码/错误消息、非敏感 diagnostic。
不要发送真实 password / User-Id / Access-Token。
