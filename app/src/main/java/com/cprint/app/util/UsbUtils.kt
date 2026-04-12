package com.cprint.app.util

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import timber.log.Timber

/**
 * Utility class for USB operations
 */
object UsbUtils {

    /**
     * Check if a USB device is a printer
     */
    fun isPrinter(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        return false
    }

    /**
     * Get printer interface from USB device
     */
    fun getPrinterInterface(device: UsbDevice): android.hardware.usb.UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return usbInterface
            }
        }
        return null
    }

    /**
     * Get bulk output endpoint for sending data to printer
     */
    fun getBulkOutEndpoint(usbInterface: android.hardware.usb.UsbInterface): android.hardware.usb.UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                endpoint.direction == UsbConstants.USB_DIR_OUT) {
                return endpoint
            }
        }
        return null
    }

    /**
     * Get bulk input endpoint for receiving data from printer
     */
    fun getBulkInEndpoint(usbInterface: android.hardware.usb.UsbInterface): android.hardware.usb.UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                endpoint.direction == UsbConstants.USB_DIR_IN) {
                return endpoint
            }
        }
        return null
    }

    /**
     * Get all connected USB printers
     */
    fun getConnectedPrinters(context: Context): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.filter { isPrinter(it) }
    }

    /**
     * Check if USB permission is granted for a device
     */
    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(device)
    }

    /**
     * Get device identifier string
     */
    fun getDeviceIdentifier(device: UsbDevice): String {
        return "${device.vendorId}:${device.productId}"
    }

    /**
     * Log USB device information for debugging
     */
    fun logDeviceInfo(device: UsbDevice) {
        Timber.d("USB Device Info:")
        Timber.d("  Device Name: ${device.deviceName}")
        Timber.d("  Vendor ID: ${device.vendorId}")
        Timber.d("  Product ID: ${device.productId}")
        Timber.d("  Manufacturer: ${device.manufacturerName}")
        Timber.d("  Product: ${device.productName}")
        Timber.d("  Serial: ${device.serialNumber}")
        Timber.d("  Interface Count: ${device.interfaceCount}")

        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            Timber.d("  Interface $i:")
            Timber.d("    Class: ${usbInterface.interfaceClass}")
            Timber.d("    Subclass: ${usbInterface.interfaceSubclass}")
            Timber.d("    Protocol: ${usbInterface.interfaceProtocol}")
            Timber.d("    Endpoint Count: ${usbInterface.endpointCount}")
        }
    }
}
