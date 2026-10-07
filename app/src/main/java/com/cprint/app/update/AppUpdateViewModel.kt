package com.cprint.app.update

import com.cprint.app.BuildConfig

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdateStage { IDLE, CHECKING, DOWNLOADING, READY, FAILED }
data class UpdateState(val stage: UpdateStage = UpdateStage.IDLE, val version: String = "", val notes: String = "", val progress: Float = 0f, val message: String = "", val id: Long = -1)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var running = false
    private val apk: File get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-update.apk")

    init { checkForUpdate() }

    fun checkForUpdate(userInitiated: Boolean = false) {
        if (running) return
        running = true
        viewModelScope.launch {
            try {
                var id = prefs.getLong("id", -1)
                if (id >= 0 && prefs.getLong("versionCode", 0) <= BuildConfig.VERSION_CODE) { clearDownload(); id = -1 }
                if (id < 0 || mutable.value.stage == UpdateStage.FAILED) {
                    clearDownload()
                    mutable.value = UpdateState(UpdateStage.CHECKING)
                    val update = AppUpdateClient.check()
                    if (update == null) {
                        mutable.value = UpdateState(message = if (userInitiated) "已经是最新版本" else "")
                        return@launch
                    }
                    val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                        .setTitle("打印小帮手 ${update.versionName}")
                        .setDescription("正在下载更新，下载完成后可安装")
                        .setMimeType("application/vnd.android.package-archive")
                        .setAllowedOverMetered(false)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "app-update.apk")
                    id = downloads.enqueue(request)
                    prefs.edit().putLong("id", id).putLong("versionCode", update.versionCode)
                        .putString("versionName", update.versionName).putString("notes", update.notes)
                        .putLong("size", update.sizeBytes).apply()
                }
                val current = UpdateState(UpdateStage.DOWNLOADING, prefs.getString("versionName", "")!!, prefs.getString("notes", "")!!, id = id)
                while (true) {
                    val snapshot = withContext(Dispatchers.IO) {
                        downloads.query(DownloadManager.Query().setFilterById(id)).use { c ->
                            check(c.moveToFirst()) { "下载任务已移除，请重试" }
                            Triple(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                        }
                    }
                    when (snapshot.first) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            withContext(Dispatchers.IO) {
                                AppUpdateApkVerifier.verify(context, apk, prefs.getLong("size", -1), prefs.getLong("versionCode", -1))
                            }
                            mutable.value = current.copy(stage = UpdateStage.READY, progress = 1f, message = "下载完成，可以安装")
                            break
                        }
                        DownloadManager.STATUS_FAILED -> error("下载失败或链接已过期，请重试获取新地址")
                        else -> mutable.value = current.copy(progress = (snapshot.second.toFloat() / prefs.getLong("size", 1)).coerceIn(0f, 1f),
                            message = if (snapshot.first == DownloadManager.STATUS_PAUSED) "等待 Wi-Fi 或网络恢复" else "正在后台下载（仅使用非计费网络）")
                    }
                    delay(1000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(stage = UpdateStage.FAILED, message = error.message ?: "更新失败，请重试")
            } finally { running = false }
        }
    }

    fun installIntent(): Intent {
        check(mutable.value.stage == UpdateStage.READY)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private fun clearDownload() {
        val id = prefs.getLong("id", -1)
        if (id >= 0) downloads.remove(id)
        apk.delete()
        prefs.edit().clear().apply()
    }
}
