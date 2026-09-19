# 自动更新发布说明

“打印小帮手”会在启动时检查 GitHub 仓库的 Latest Release，并在用户确认后下载其中第一个 `.apk` 资源，交给 Android 系统安装器完成更新。

## 发布一个可更新版本

1. 在 `app/build.gradle.kts` 同时提高 `versionCode` 和 `versionName`；`versionName` 必须与标签对应，例如 `1.1.0` 对应 `v1.1.0`。
2. 将用于首次安装应用的同一把签名密钥配置为仓库 Secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
3. 推送标签：`git tag v1.1.0 && git push origin v1.1.0`。

工作流会构建签名 APK 并作为 GitHub Release 资源上传。Android 不能让普通应用静默安装 APK；用户首次更新时需允许“打印小帮手”安装未知应用，随后仍会由系统显示安装确认。
