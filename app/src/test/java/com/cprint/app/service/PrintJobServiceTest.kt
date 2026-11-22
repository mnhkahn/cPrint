package com.cprint.app.service

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import com.cprint.app.R
import com.cprint.app.domain.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PrintJobServiceTest {
    private val manager: UsbManager = mock()
    private val app get() = RuntimeEnvironment.getApplication()
    private val context = object : ContextWrapper(app) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.USB_SERVICE) manager else super.getSystemService(name)
    }

    @Test fun `missing or unauthorized printer fails before starting service`() = runTest {
        whenever(manager.deviceList).thenReturn(hashMapOf())
        val missing = PrintJobService.print(context, "sample.pdf", "content://sample", "application/pdf", 1, PrintSettings())
        assertEquals(app.getString(R.string.usb_printer_missing), missing.exceptionOrNull()?.message)
        assertNull(shadowOf(app).nextStartedService)

        attachPrinter(granted = false)
        val unauthorized = PrintJobService.print(context, "sample.pdf", "content://sample", "application/pdf", 1, PrintSettings())
        assertEquals(app.getString(R.string.usb_permission_message), unauthorized.exceptionOrNull()?.message)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun `leaving preview does not stop service and preserves all settings`() = runTest {
        attachPrinter(granted = true)
        val settings = PrintSettings(copies = 3, paperSize = PaperSize.A5,
            colorMode = ColorMode.GRAYSCALE, pageRange = PageRange.parse("2-4"),
            duplexMode = DuplexMode.LONG_EDGE, pagesPerSheet = 2)
        val observer = launch {
            PrintJobService.print(context, "sample.pdf", "content://sample", "application/pdf", 6, settings)
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
        attachPrinter(granted = true)
        val service = Robolectric.buildService(TestService::class.java).create().get()
        service.usbManager = manager
        val lease = PrintExecutionGuard().acquire(service, 1001)
        assertTrue(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock().isHeld)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        lease.close()
        assertFalse(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock().isHeld)
        assertTrue(shadowOf(service).isForegroundStopped)
        lease.close() // Cleanup may also run during service destruction.
    }

    private fun attachPrinter(granted: Boolean) {
        val printer: UsbDevice = mock()
        whenever(printer.vendorId).thenReturn(1208)
        whenever(printer.productId).thenReturn(2217)
        whenever(manager.deviceList).thenReturn(hashMapOf("printer" to printer))
        whenever(manager.hasPermission(printer)).thenReturn(granted)
    }

    class TestService : Service() {
        var usbManager: UsbManager? = null

        override fun getSystemService(name: String): Any? =
            if (name == Context.USB_SERVICE) usbManager else super.getSystemService(name)

        override fun onBind(intent: Intent): IBinder? = null
    }
}
