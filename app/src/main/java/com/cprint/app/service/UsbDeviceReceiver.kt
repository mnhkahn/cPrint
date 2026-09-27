package com.cprint.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.cprint.app.data.repository.PrinterRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** USB insertion is handled by MainActivity; detachment must also work in the background. */
@AndroidEntryPoint
class UsbDeviceReceiver : BroadcastReceiver() {
    @Inject lateinit var printerRepository: PrinterRepositoryImpl

    companion object {
        const val ACTION_USB_PERMISSION = "com.cprint.app.USB_PERMISSION"
        const val ACTION_PRINTER_CONNECTED = "com.cprint.app.PRINTER_CONNECTED"
        const val ACTION_PRINTER_CONNECTION_FAILED = "com.cprint.app.PRINTER_CONNECTION_FAILED"
        const val ACTION_PRINTER_DISCONNECTED = "com.cprint.app.PRINTER_DISCONNECTED"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
        } ?: return
        if (!printerRepository.isSupportedPrinter(device)) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                printerRepository.disconnectDevice(device)
                appContext.sendBroadcast(Intent(ACTION_PRINTER_DISCONNECTED).apply {
                    setPackage(appContext.packageName)
                    putExtra(EXTRA_DEVICE_NAME, device.deviceName)
                })
            } catch (error: Exception) {
                Timber.e(error, "Failed to disconnect USB printer")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
