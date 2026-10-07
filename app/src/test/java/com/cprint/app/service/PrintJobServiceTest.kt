package com.cprint.app.service

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import com.cprint.app.domain.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PrintJobServiceTest {
    @Test fun `leaving preview does not stop service and preserves all settings`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val settings = PrintSettings(copies = 3, paperSize = PaperSize.A5,
            colorMode = ColorMode.GRAYSCALE, pageRange = PageRange.parse("2-4"),
            duplexMode = DuplexMode.LONG_EDGE, pagesPerSheet = 2)
        val observer = launch {
            PrintJobService.print(app, "sample.pdf", "content://sample", "application/pdf", 6, settings)
        }
        runCurrent()
        val intent = shadowOf(app).nextStartedService
        assertEquals(PrintJobService.ACTION_START_PRINT, intent.action)
        assertEquals("content://sample", intent.clipData!!.getItemAt(0).uri.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(settings, Gson().fromJson(intent.getStringExtra(PrintJobService.EXTRA_SETTINGS), PrintSettings::class.java))
        observer.cancelAndJoin()
        assertNull(shadowOf(app).nextStoppedService)
        assertNull(shadowOf(app).nextStartedService)
        // A service completion delivered after the page is gone is harmless.
        @Suppress("DEPRECATION")
        val receiver = intent.getParcelableExtra<ResultReceiver>(PrintJobService.EXTRA_RESULT)!!
        receiver.send(0, null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `printing holds CPU awake and releases foreground protection`() {
        val service = Robolectric.buildService(TestService::class.java).create().get()
        val lease = PrintExecutionGuard().acquire(service, 1001)
        assertTrue(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock().isHeld)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        lease.close()
        assertFalse(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(shadowOf(service).isForegroundStopped)
        lease.close() // Cleanup may also run during service destruction.
    }

    class TestService : Service() {
        override fun onBind(intent: Intent): IBinder? = null
    }
}
