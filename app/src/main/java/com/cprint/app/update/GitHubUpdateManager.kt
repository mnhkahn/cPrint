package com.cprint.app.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cprint.app.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Retrieves the latest APK published on this app's GitHub Releases page. */
class GitHubUpdateManager(private val context: Context) {

    data class Release(
        val version: String,
        val notes: String,
        val downloadUrl: String
    )

    suspend fun checkForUpdate(): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = "https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest"
            val connection = openConnection(endpoint)
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                check(connection.responseCode in 200..299) { "GitHub returned HTTP ${connection.responseCode}" }
                val release = JsonParser.parseReader(connection.inputStream.reader()).asJsonObject
                val version = release.get("tag_name")?.asString.orEmpty().removePrefix("v")
                require(version.isNotBlank()) { "Latest release has no version tag" }
                if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@runCatching null

                val asset = release.getAsJsonArray("assets")
                    ?.mapNotNull { it.asJsonObject }
                    ?.firstOrNull { it.get("name")?.asString?.endsWith(".apk", ignoreCase = true) == true }
                    ?: error("Latest release has no APK asset")
                Release(
                    version = version,
                    notes = release.get("body")?.asString.orEmpty(),
                    downloadUrl = asset.get("browser_download_url").asString
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun downloadAndInstall(release: Release): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(targetDirectory, "update.apk")
            val connection = openConnection(release.downloadUrl)
            try {
                check(connection.responseCode in 200..299) { "APK download returned HTTP ${connection.responseCode}" }
                connection.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
            } finally {
                connection.disconnect()
            }
            require(apk.length() > 0) { "Downloaded APK is empty" }
            verifyApk(apk)
            install(apk)
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun install(apk: File) {
        val apkUri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("APK", apkUri)
            }
        )
    }

    private fun verifyApk(apk: File) {
        val packageManager = context.packageManager
        val archiveInfo = packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: error("Downloaded file is not a valid APK")
        require(archiveInfo.packageName == context.packageName) { "APK package does not match this app" }

        val installedInfo = packageManager.getPackageInfo(context.packageName, 0)
        val archiveVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") archiveInfo.versionCode.toLong()
        }
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installedInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") installedInfo.versionCode.toLong()
        }
        require(archiveVersion > installedVersion) { "APK is not newer than the installed app" }
    }

    private fun openConnection(url: String): HttpURLConnection {
        require(URL(url).protocol == "https") { "Only HTTPS update URLs are allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "cPrint-Android-Updater")
        }
    }

    companion object {
        internal fun isNewerVersion(candidate: String, current: String): Boolean {
            fun parts(value: String) = value.removePrefix("v").substringBefore('-')
                .split('.').map { it.toIntOrNull() ?: 0 }
            val candidateParts = parts(candidate)
            val currentParts = parts(current)
            for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
                val difference = candidateParts.getOrElse(index) { 0 } - currentParts.getOrElse(index) { 0 }
                if (difference != 0) return difference > 0
            }
            return false
        }
    }
}
