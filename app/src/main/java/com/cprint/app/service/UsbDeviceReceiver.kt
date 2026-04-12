package com.cprint.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast
import com.cprint.app.data.repository.UsbPrintRepositoryImpl
import com.cprint.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BroadcastReceiver for USB device events
 * Note: This is a Manifest-registered receiver, so we manually get dependencies instead of using Hilt injection
 */
class UsbDeviceReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbDeviceReceiver"

        const val ACTION_USB_PERMISSION = "com.cprint.app.USB_PERMISSION"
        const val ACTION_PRINTER_CONNECTED = "com.cprint.app.PRINTER_CONNECTED"
        const val ACTION_PRINTER_CONNECTION_FAILED = "com.cprint.app.PRINTER_CONNECTION_FAILED"
        const val ACTION_PRINTER_DISCONNECTED = "com.cprint.app.PRINTER_DISCONNECTED"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("UsbDeviceReceiver onReceive: action=${intent.action}")

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                device?.let {
                    Timber.d("USB device attached: ${it.deviceName}")
                    handleDeviceAttached(context, it)
                }
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                device?.let {
                    Timber.d("USB device detached: ${it.deviceName}")
                    handleDeviceDetached(context, it)
                }
            }

            ACTION_USB_PERMISSION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                Timber.d("USB permission callback received: granted=$granted")
                Timber.d("USB permission intent extras: ${intent.extras?.keySet()?.joinToString()}")

                // Get device from system callback - system always provides EXTRA_DEVICE
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                Timber.d("USB permission device from intent: $device")

                device?.let {
                    if (granted) {
                        Timber.d("USB permission granted for: ${it.deviceName}, VID=${it.vendorId}, PID=${it.productId}")
                        Toast.makeText(context, "USB权限已授予，正在连接打印机...", Toast.LENGTH_SHORT).show()
                        connectPrinter(context, it)
                    } else {
                        Timber.w("USB permission denied for: ${it.deviceName}")
                        Toast.makeText(context, "USB权限被拒绝", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Timber.e("USB permission callback: device is null! This should not happen.")
                    // Try to find any connected printer device as fallback
                    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    val devices = usbManager.deviceList.values
                    val printerDevice = devices.firstOrNull { dev ->
                        (0 until dev.interfaceCount).any { i ->
                            dev.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER
                        }
                    }
                    printerDevice?.let { dev ->
                        if (usbManager.hasPermission(dev) && granted) {
                            Timber.d("Found printer device as fallback: ${dev.deviceName}")
                            connectPrinter(context, dev)
                        }
                    }
                }
            }
        }
    }

    private fun handleDeviceAttached(context: Context, device: UsbDevice) {
        Timber.d("Handling device attached: ${device.deviceName} (VID:${device.vendorId}, PID:${device.productId})")

        // Check if it's a printer class device
        var isPrinterClass = false
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                isPrinterClass = true
                break
            }
        }

        if (isPrinterClass || isSupportedPrinter(device)) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            if (usbManager.hasPermission(device)) {
                Timber.d("Already has permission for device: ${device.deviceName}")
                connectPrinter(context, device)
            } else {
                // Request permission
                Timber.d("Requesting permission for device: ${device.deviceName}")

                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    device.vendorId * 10000 + device.productId,
                    Intent(ACTION_USB_PERMISSION).apply {
                        // Note: System will add EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED
                        // Our custom extras won't be preserved in the callback
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        // Set package to ensure the broadcast is delivered to our app
                        setPackage(context.packageName)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                usbManager.requestPermission(device, permissionIntent)
            }
        } else {
            Timber.w("Device not supported: ${device.deviceName} (VID:${device.vendorId}, PID:${device.productId}, interfaces=${device.interfaceCount})")
        }
    }

    private fun handleDeviceDetached(context: Context, device: UsbDevice) {
        Timber.d("Handling device detached: ${device.deviceName} (VID:${device.vendorId}, PID:${device.productId})")

        // Disconnect from USB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val printerDao = database.printerDao()

                // Find the printer with matching VID/PID and READY status
                val connectedPrinter = printerDao.getPrinterByVidPid(device.vendorId, device.productId)

                connectedPrinter?.let { printer ->
                    if (printer.status == com.cprint.app.domain.model.PrinterStatus.READY) {
                        Timber.d("Marking printer as disconnected: ${printer.name} (VID:${device.vendorId}, PID:${device.productId})")
                        printerDao.updatePrinterStatus(printer.id, com.cprint.app.domain.model.PrinterStatus.DISCONNECTED)

                        // Notify UI with dedicated disconnect action
                        val intent = Intent(ACTION_PRINTER_DISCONNECTED).apply {
                            putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                        }
                        context.sendBroadcast(intent)
                        Timber.d("Sent PRINTER_DISCONNECTED broadcast for ${device.deviceName}")
                    }
                } ?: run {
                    Timber.d("No matching printer found in database for detached device")
                }

                // Also disconnect from USB repository if connected
                try {
                    val usbPrintRepository = UsbPrintRepositoryImpl(context)
                    usbPrintRepository.disconnect()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to disconnect USB repository")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update printer status on disconnect")
            }
        }
    }

    private fun connectPrinter(context: Context, device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            Timber.d("Starting printer connection for: ${device.deviceName}")

            // Step 1: Open USB device connection
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = usbManager.openDevice(device)

            if (connection == null) {
                Timber.e("Failed to open USB device: ${device.deviceName}")
                val intent = Intent(ACTION_PRINTER_CONNECTION_FAILED).apply {
                    putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                    putExtra(EXTRA_ERROR_MESSAGE, "无法打开USB设备")
                }
                context.sendBroadcast(intent)
                return@launch
            }

            // Step 2: Connect to USB printer (claim interface, find endpoints)
            val usbPrintRepository = UsbPrintRepositoryImpl(context)
            val usbConnected = usbPrintRepository.connect(device, connection)
            if (!usbConnected) {
                Timber.e("Failed to connect to USB printer: ${device.deviceName}")
                connection.close()
                val intent = Intent(ACTION_PRINTER_CONNECTION_FAILED).apply {
                    putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                    putExtra(EXTRA_ERROR_MESSAGE, "无法连接到USB打印机")
                }
                context.sendBroadcast(intent)
                return@launch
            }

            // Step 3: Save printer info to database
            try {
                val database = AppDatabase.getInstance(context)
                val printerDao = database.printerDao()

                val printerInfo = com.cprint.app.domain.model.KnownPrinters.findPrinter(device.vendorId, device.productId)
                    ?: run {
                        Timber.e("Unsupported printer: VID=${device.vendorId}, PID=${device.productId}")
                        usbPrintRepository.disconnect()
                        val intent = Intent(ACTION_PRINTER_CONNECTION_FAILED).apply {
                            putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                            putExtra(EXTRA_ERROR_MESSAGE, "不支持的打印机型号")
                        }
                        context.sendBroadcast(intent)
                        return@launch
                    }

                val existingPrinter = printerDao.getPrinterByVidPid(device.vendorId, device.productId)

                val printer = if (existingPrinter != null) {
                    // Update printer info with latest name from KnownPrinters
                    existingPrinter.copy(
                        name = "${printerInfo.manufacturer} ${printerInfo.model}",
                        manufacturer = printerInfo.manufacturer,
                        model = printerInfo.model,
                        protocol = printerInfo.protocol,
                        status = com.cprint.app.domain.model.PrinterStatus.READY,
                        lastConnectedAt = java.util.Date(),
                        connectionCount = existingPrinter.connectionCount + 1
                    )
                } else {
                    com.cprint.app.data.model.entity.PrinterEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "${printerInfo.manufacturer} ${printerInfo.model}",
                        manufacturer = printerInfo.manufacturer,
                        model = printerInfo.model,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        serialNumber = device.serialNumber,
                        protocol = printerInfo.protocol,
                        supportedPaperSizes = listOf("A4", "A5", "Letter"),
                        supportsColor = false,
                        supportsDuplex = true,
                        maxResolution = "600x600",
                        status = com.cprint.app.domain.model.PrinterStatus.READY,
                        isDefault = false,
                        lastConnectedAt = java.util.Date(),
                        connectionCount = 1
                    )
                }

                printerDao.insertPrinter(printer)

                Timber.d("Printer connected successfully: ${device.deviceName}")
                val intent = Intent(ACTION_PRINTER_CONNECTED).apply {
                    putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save printer info: ${device.deviceName}")
                usbPrintRepository.disconnect()
                val intent = Intent(ACTION_PRINTER_CONNECTION_FAILED).apply {
                    putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                    putExtra(EXTRA_ERROR_MESSAGE, e.message ?: "Unknown error")
                }
                context.sendBroadcast(intent)
            }
        }
    }

    private fun isSupportedPrinter(device: UsbDevice): Boolean {
        // Check if device is a printer class
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }

        // Check against known printers list
        return com.cprint.app.domain.model.KnownPrinters.findPrinter(device.vendorId, device.productId) != null
    }
}
