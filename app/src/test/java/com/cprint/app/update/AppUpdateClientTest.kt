package com.cprint.app.update

import com.cprint.app.BuildConfig
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.net.URL
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppUpdateClientTest {
    private val endpoint = URL("https://www.cyeam.com/api/apps/cprint/update")
    private fun response() = JSONObject().put("appId", "cprint").put("hasUpdate", true).put("versionCode", 15).put("versionName", "1.0.12")
        .put("packageName", "com.cprint.app").put("releaseNotes", "测试更新")
        .put("sizeBytes", 90595777).put("expiresAt", Instant.now().plusSeconds(900).toString())
        .put("downloadUrl", "/api/apps/cprint/download?token=test")
    @Test fun relativeDownloadAndAndroidVersionCode() {
        val result = AppUpdateClient.parse(response().toString(), endpoint, 14, "com.cprint.app")!!
        assertEquals("https://www.cyeam.com/api/apps/cprint/download?token=test", result.downloadUrl)
        assertEquals(15L, result.versionCode)
        assertEquals(90595777L, result.sizeBytes)
        assertNull(AppUpdateClient.parse("{\"hasUpdate\":false}", endpoint, 15, "com.cprint.app"))
    }
    // Optional replay of a real gateway response, captured by the local smoke test.
    @Test fun deployedResponseWhenProvided() {
        val path = System.getenv("APP_UPDATE_TEST_RESPONSE")
        org.junit.Assume.assumeTrue(!path.isNullOrBlank())
        val body = java.io.File(path!!).readText()
        val metadata = JSONObject(body)
        val result = AppUpdateClient.parse(body, URL(BuildConfig.APP_UPDATE_URL), 0, BuildConfig.APPLICATION_ID)!!
        assertEquals(metadata.getLong("versionCode"), result.versionCode)
        assertEquals(metadata.getLong("sizeBytes"), result.sizeBytes)
        assertTrue(result.downloadUrl.startsWith("https://www.cyeam.com/api/apps/cprint/download?token="))
    }
    @Test fun rejectsOtherAppsOldVersionsAndUntrustedDownloads() {
        val cases = listOf(response().put("appId", "other"), response().put("packageName", "com.other"), response().put("versionCode", 14),
            response().put("downloadUrl", "https://evil.example/app.apk"),
            response().put("downloadUrl", "http://www.cyeam.com/app.apk"),
            response().put("expiresAt", "2020-01-01T00:00:00Z"), response().put("sizeBytes", 0))
        cases.forEach { json -> assertTrue(runCatching { AppUpdateClient.parse(json.toString(), endpoint, 14, "com.cprint.app") }.isFailure) }
    }
}
