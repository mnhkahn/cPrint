package com.cprint.app.util

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.robolectric.annotation.Config

/**
 * Unit tests for UsbUtils
 */
@Config(manifest = Config.NONE)
class UsbUtilsTest {

    @Test
    fun `getDeviceIdentifier returns correct format`() {
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x03F0)
        `when`(device.productId).thenReturn(0x2B17)

        val identifier = UsbUtils.getDeviceIdentifier(device)
        assertEquals("1008:11031", identifier) // 0x03F0 = 1008, 0x2B17 = 11031
    }

    @Test
    fun `isPrinter returns true for printer class device`() {
        val device = mock(UsbDevice::class.java)
        val usbInterface = mock(UsbInterface::class.java)

        `when`(device.interfaceCount).thenReturn(1)
        `when`(device.getInterface(0)).thenReturn(usbInterface)
        `when`(usbInterface.interfaceClass).thenReturn(UsbConstants.USB_CLASS_PRINTER)

        assertTrue(UsbUtils.isPrinter(device))
    }

    @Test
    fun `isPrinter returns false for non-printer device`() {
        val device = mock(UsbDevice::class.java)
        val usbInterface = mock(UsbInterface::class.java)

        `when`(device.interfaceCount).thenReturn(1)
        `when`(device.getInterface(0)).thenReturn(usbInterface)
        `when`(usbInterface.interfaceClass).thenReturn(UsbConstants.USB_CLASS_MASS_STORAGE)

        assertFalse(UsbUtils.isPrinter(device))
    }

    @Test
    fun `isPrinter returns true when any interface is printer class`() {
        val device = mock(UsbDevice::class.java)
        val interface1 = mock(UsbInterface::class.java)
        val interface2 = mock(UsbInterface::class.java)

        `when`(device.interfaceCount).thenReturn(2)
        `when`(device.getInterface(0)).thenReturn(interface1)
        `when`(device.getInterface(1)).thenReturn(interface2)
        `when`(interface1.interfaceClass).thenReturn(UsbConstants.USB_CLASS_COMM)
        `when`(interface2.interfaceClass).thenReturn(UsbConstants.USB_CLASS_PRINTER)

        assertTrue(UsbUtils.isPrinter(device))
    }

    @Test
    fun `getPrinterInterface returns printer interface`() {
        val device = mock(UsbDevice::class.java)
        val printerInterface = mock(UsbInterface::class.java)

        `when`(device.interfaceCount).thenReturn(2)
        `when`(device.getInterface(0)).thenReturn(printerInterface)
        `when`(printerInterface.interfaceClass).thenReturn(UsbConstants.USB_CLASS_PRINTER)

        val result = UsbUtils.getPrinterInterface(device)
        assertNotNull(result)
        assertEquals(printerInterface, result)
    }

    @Test
    fun `getPrinterInterface returns null when no printer interface`() {
        val device = mock(UsbDevice::class.java)
        val usbInterface = mock(UsbInterface::class.java)

        `when`(device.interfaceCount).thenReturn(1)
        `when`(device.getInterface(0)).thenReturn(usbInterface)
        `when`(usbInterface.interfaceClass).thenReturn(UsbConstants.USB_CLASS_MASS_STORAGE)

        val result = UsbUtils.getPrinterInterface(device)
        assertNull(result)
    }
}
