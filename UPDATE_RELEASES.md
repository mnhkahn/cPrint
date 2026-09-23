# 蒲公英发布与自动更新

发布由 GitHub Actions 的 **Android Release Build** 完成。请在仓库 Secrets 配置：

- `RELEASE_KEYSTORE_BASE64`：发布签名 keystore 的 Base64 内容
- `CPRINT_RELEASE_STORE_PASSWORD`：cPrint keystore 与 key 的密码
- `PGYER_API_KEY`：蒲公英 API Key
- `LARK_RELEASE_WEBHOOK`：飞书群机器人 Webhook（发布成功后通知）

推送版本 tag 即可发布，例如：

```sh
git tag 0.1.7
git push origin 0.1.7
```

也可以手动运行 **Android Release Build**，填写 `0.1.7` 这类版本号。工作流会签名 APK、通过 `git-chglog` 生成更新说明、上传并轮询蒲公英发布状态，然后将相同更新说明写入蒲公英和 GitHub Release，并发送飞书机器人通知。蒲公英会为每次发布返回实际下载页。

App 每次启动会读取该公开页的最新版本号和更新说明；如果版本更新，会显示“前往蒲公英更新”。点击后由浏览器打开蒲公英页面，由蒲公英生成短时下载链接并引导安装。App 不再从 GitHub 下载 APK，也不包含蒲公英 API Key。
