package com.cprint.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.cprint.app.data.repository.UsbPrintRepositoryImpl
import com.cprint.app.domain.model.PrinterStatus
import com.cprint.app.domain.repository.PrinterRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Service for managing USB printer connection
 */
@AndroidEntryPoint
class UsbConnectionService : Service() {

    @Inject
    lateinit var printerRepository: PrinterRepository

    @Inject
    lateinit var usbPrintRepository: UsbPrintRepositoryImpl

    private val binder = LocalBinder()
    private var connectionJob: Job? = null
    private var currentDevice: UsbDevice? = null

    inner class LocalBinder : Binder() {
        fun getService(): UsbConnectionService = this@UsbConnectionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Timber.d("UsbConnectionService created")
        startConnectionMonitor()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
        disconnectDevice()
        Timber.d("UsbConnectionService destroyed")
    }

    /**
     * Connect to a USB device
     */
    fun connectDevice(device: UsbDevice): Boolean {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(device)) {
            Timber.w("No permission for USB device")
            return false
        }

        val connection = usbManager.openDevice(device)
            ?: return false

        if (usbPrintRepository.connect(device, connection)) {
            currentDevice = device
            Timber.d("Connected to USB printer: ${device.deviceName}")
            return true
        }

        connection.close()
        return false
    }

    /**
     * Disconnect from current device
     */
    fun disconnectDevice() {
        usbPrintRepository.disconnect()
        currentDevice = null
        Timber.d("Disconnected from USB printer")
    }

    /**
     * Check if connected to a printer
     */
    fun isConnected(): Boolean {
        return currentDevice != null
    }

    /**
     * Get current device
     */
    fun getCurrentDevice(): UsbDevice? = currentDevice

    private fun startConnectionMonitor() {
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                checkConnectionStatus()
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private suspend fun checkConnectionStatus() {
        currentDevice?.let { device ->
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val devices = usbManager.deviceList

            if (!devices.containsValue(device)) {
                // Device disconnected
                disconnectDevice()
                printerRepository.disconnectPrinter()
            }
        }
    }
}
