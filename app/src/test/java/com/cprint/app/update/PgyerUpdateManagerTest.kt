package com.cprint.app.update

import android.app.Application
import com.cprint.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PgyerUpdateManagerTest {
    @Test fun `download URL matches the published application`() {
        assertEquals("https://www.pgyer.com/dayinxiaobangshou", BuildConfig.PGYER_DOWNLOAD_PAGE)
    }

    @Test fun `parses visible version on PGYER download page`() {
        val release = PgyerUpdateManager.parseRelease(
            "<div>版本：<span>1.0.7</span> (build 2)</div>"
        )!!
        assertEquals("1.0.7", release.version)
        assertTrue(PgyerUpdateManager.isNewerVersion(release.version, "1.0.6"))
    }

    @Test fun `missing version cannot be interpreted as current release`() {
        assertNull(PgyerUpdateManager.parseRelease("<html><body>页面不存在</body></html>"))
    }
    @Test fun `recognizes newer semantic versions`() {
        assertTrue(PgyerUpdateManager.isNewerVersion("v1.2.0", "1.1.9"))
        assertTrue(PgyerUpdateManager.isNewerVersion("1.0.1", "1.0.0"))
        assertFalse(PgyerUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(PgyerUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
    }

    @Test fun `parses PGYER public-page release metadata`() {
        val release = PgyerUpdateManager.parseRelease(
            "<script>{\"buildVersion\":\"0.1.7\",\"buildUpdateDescription\":\"修复打印\\n优化连接\"}</script>"
        )!!
        assertEquals("0.1.7", release.version)
        assertEquals("修复打印\n优化连接", release.notes)
    }
}
