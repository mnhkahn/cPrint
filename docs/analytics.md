# cPrint Analytics 事件

使用现有 Firebase Analytics，保留自动采集的 `first_open`、`session_start` 等事件。

| 事件 | 触发位置 / 口径 | 专有参数 |
| --- | --- | --- |
| `document_import_result` | OpenDocumentUseCase 完成导入或打开最近文档；协程取消不算导入失败 | `source`、`file_type`、`page_count`、`result`、`error_code`、`duration_ms` |
| `printer_connect_result` | 新的连接尝试结束，或用户拒绝 USB 授权；复用现有连接不重复记录 | `vendor_id`、`product_id`、`result`、`error_code`、`duration_ms` |
| `print_requested` | CreatePrintJobUseCase 接收一个打印请求 | 打印参数 |
| `print_started` | 任务入库，开始执行连接检查、渲染和传输 | 打印参数 |
| `print_finished` | 每个请求一次终态，包括创建任务失败、执行失败、取消、传输完成 | 打印参数、`result`、`stage`、`error_code`、`duration_ms` |
| `print_retry` | 重试操作创建待处理任务的结果；不代表已经执行或完成打印 | `result`、`error_code` |

打印参数：`source`（`app` / `system`）、`file_type`（`pdf` / `image` / `other`）、
`page_count`（文档总页数，不是已打印页数）、`copies`、`paper_size`、`color_mode`、`duplex_mode`、`pages_per_sheet`。
导入来源：`file_picker` / `share` / `open_with` / `recent` / `unknown`。

`print_finished.result`：`transfer_success` / `failure` / `cancelled`。
成功仅表示数据发送完成，不表示打印机确认纸张出纸。
`stage`：`create_job` / `processing`（包含未带细分阶段的异常、协程取消）/
`connection` / `render` / `transfer`。
其他结果事件使用 `success` / `failure` / `cancelled`。

错误只上传固定分类：`none`、`cancelled`、`permission_denied`、`usb_permission_denied`、
`file_not_found`、`unsupported`、`printer_disconnected`、`timeout`、`usb_transfer_failed`、
`document_too_large`、`io_error`、`invalid_input`、`invalid_state`、`unknown`。
不上传文件名、URI、文档内容、打印机序列号、任务 ID 或原始异常文本。
Analytics SDK 异常不会中断业务。

## GA 查看

安装包含此改动的 APK 后执行相应操作。事件会进入现有“打印小帮手”数据流。
本次代码改动不创建 GA 控制台配置。
如需按参数筛选正式报表，在 GA 管理 → 自定义设置中注册事件范围的维度：
`source`、`file_type`、`result`、`stage`、`error_code`、`paper_size`、`color_mode`、`duplex_mode`。
按需注册 `vendor_id`、`product_id` 用于分析兼容性。
`duration_ms` 可注册为毫秒单位的自定义指标，`page_count`、`copies` 可注册为标准单位指标。

核心指标：尝试打印用户数、首次传输成功用户数、传输成功率、失败阶段和错误码分布。
成功率应明确分母是 `print_requested`（含创建失败）或 `print_started`（仅开始执行的任务）；取消单列。

## 真机验证

```sh
adb shell setprop debug.firebase.analytics.app com.cprint.app
adb shell setprop log.tag.FA VERBOSE
adb shell setprop log.tag.FA-SVC VERBOSE
adb logcat -v time -s FA FA-SVC
```

在 Firebase / GA DebugView 中验证导入、连接、App 内打印、系统打印、取消及重试。
每个请求应只有一个 `print_finished`；失败创建不应出现 `print_started`。
验证结束关闭调试模式：

```sh
adb shell setprop debug.firebase.analytics.app .none.
adb shell setprop log.tag.FA INFO
adb shell setprop log.tag.FA-SVC INFO
```
