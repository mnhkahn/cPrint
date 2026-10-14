package com.cprint.app.service

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.print.PrintAttributes
import android.print.PrinterInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class PrinterDiscoveryTest {
    private val service = spy(CPrintPrintService())
    private val manager: UsbManager = mock()
    private val device: UsbDevice = mock()

    private fun newSession(): android.printservice.PrinterDiscoverySession =
        CPrintPrintService::class.java.getDeclaredMethod("onCreatePrinterDiscoverySession").apply {
            isAccessible = true
        }.invoke(service) as android.printservice.PrinterDiscoverySession

    @Before fun setUp() {
        doReturn(manager).whenever(service).getSystemService(Context.USB_SERVICE)
        doReturn("com.cprint.app").whenever(service).packageName
        whenever(device.vendorId).thenReturn(1208)
        whenever(device.productId).thenReturn(2217)
        whenever(device.deviceName).thenReturn("/dev/bus/usb/001/002")
        whenever(manager.deviceList).thenReturn(hashMapOf("printer" to device))
    }

    @Test fun `USB reconnect keeps persisted printer identity`() {
        val first = newSession()
        first.onStartPrinterDiscovery(mutableListOf())
        val id = first.printers.single().id
        assertEquals("1208:2217", id.localId)
        whenever(device.deviceName).thenReturn("/dev/bus/usb/001/009")
        val second = newSession()
        second.onStartPrinterStateTracking(id)
        assertEquals(id, second.printers.single().id)
        assertNotNull(second.printers.single().capabilities)
    }

    @Test fun `saved legacy path receives capabilities before discovery starts`() {
        val oldId = service.generatePrinterId("1208:2217:/dev/bus/usb/001/001")
        val session = newSession()
        session.onStartPrinterStateTracking(oldId)
        val printer = session.printers.single()
        assertEquals(oldId, printer.id)
        assertEquals(PrinterInfo.STATUS_IDLE, printer.status)
        val capabilities = checkNotNull(printer.capabilities)
        assertTrue(capabilities.mediaSizes.contains(PrintAttributes.MediaSize.ISO_A4))
    }

    @Test fun `priority discovery lists saved legacy printer once`() {
        val oldId = service.generatePrinterId("1208:2217:/dev/bus/usb/001/001")
        val session = newSession()
        session.onStartPrinterDiscovery(mutableListOf(oldId))
        assertEquals(oldId, session.printers.single().id)
    }

    @Test fun `disconnected favorite reports unavailable rather than waiting for capabilities`() {
        whenever(manager.deviceList).thenReturn(hashMapOf())
        val id = service.generatePrinterId("1208:2217")
        val session = newSession()
        session.onStartPrinterStateTracking(id)
        assertEquals(PrinterInfo.STATUS_UNAVAILABLE, session.printers.single().status)
        assertNotNull(session.printers.single().capabilities)
    }

    @Test fun `validation resolves saved legacy printer after reconnect`() {
        val oldId = service.generatePrinterId("1208:2217:/dev/bus/usb/001/001")
        val session = newSession()
        session.onValidatePrinters(mutableListOf(oldId))
        assertEquals(oldId, session.printers.single().id)
    }

    @Test fun `legacy identity does not select arbitrary device with two matching printers`() {
        val other: UsbDevice = mock()
        whenever(other.vendorId).thenReturn(1208)
        whenever(other.productId).thenReturn(2217)
        whenever(other.deviceName).thenReturn("/dev/bus/usb/001/003")
        whenever(manager.deviceList).thenReturn(hashMapOf("first" to device, "second" to other))
        val oldId = service.generatePrinterId("1208:2217:/dev/bus/usb/001/001")
        val session = newSession()
        session.onStartPrinterStateTracking(oldId)
        assertEquals(PrinterInfo.STATUS_UNAVAILABLE, session.printers.single().status)
    }
}
