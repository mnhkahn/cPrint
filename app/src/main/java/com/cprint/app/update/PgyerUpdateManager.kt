package com.cprint.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import com.cprint.app.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Checks the public PGYER download page; credentials and APK download stay outside the app. */
class PgyerUpdateManager(private val context: Context) {

    data class Release(val version: String, val notes: String)

    suspend fun checkForUpdate(): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(BuildConfig.PGYER_DOWNLOAD_PAGE)
            val page = try {
                check(connection.responseCode in 200..299) { "PGYER returned HTTP ${connection.responseCode}" }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val release = parseRelease(page) ?: return@runCatching null
            if (isNewerVersion(release.version, BuildConfig.VERSION_NAME)) release else null
        }
    }

    fun openDownloadPage() {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PGYER_DOWNLOAD_PAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openConnection(url: String): HttpURLConnection {
        require(URL(url).protocol == "https") { "Only HTTPS update URLs are allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("User-Agent", "cPrint-Android-Updater")
        }
    }

    companion object {
        private val versionPatterns = listOf(
            Regex("\\\"(?:buildVersion|versionName)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
            Regex("(?:版本号|版本)\\s*[：:]\\s*([0-9][0-9A-Za-z._+-]*)")
        )
        private val notesPattern = Regex("\\\"buildUpdateDescription\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")

        internal fun parseRelease(page: String): Release? {
            val decodedPage = Html.fromHtml(page, Html.FROM_HTML_MODE_LEGACY).toString()
            val version = versionPatterns.firstNotNullOfOrNull { it.find(decodedPage)?.groupValues?.get(1) }
                ?.trim()
                ?.removePrefix("v")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val notes = notesPattern.find(decodedPage)?.groupValues?.get(1)
                ?.let { runCatching { JsonParser.parseString("\"$it\"").asString }.getOrDefault(it) }
                ?.trim()
                .orEmpty()
            return Release(version, notes)
        }

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
