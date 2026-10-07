package com.cprint.app.presentation.main

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cprint.app.domain.model.KnownPrinters
import com.cprint.app.util.UsbUtils
import com.cprint.app.domain.model.PrinterStatus
import com.cprint.app.presentation.preview.PrintPreviewActivity
import com.cprint.app.presentation.queue.PrintQueueActivity
import com.cprint.app.presentation.settings.PrintSettingsActivity
import com.cprint.app.presentation.theme.CPrintTheme
import com.cprint.app.service.UsbDeviceReceiver
import com.cprint.app.update.AppUpdateViewModel
import com.cprint.app.update.AppUpdatePanel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main Activity - Entry point of the cPrint app
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val updater: AppUpdateViewModel by viewModels()
    private val requestedUsbPermissions = mutableSetOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.onPermissionsGranted()
            // Check for already connected USB printers after permissions are granted
            checkAndAutoConnectUsbPrinter()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Take persistable permission to access the file later
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Timber.d("Got persistable permission for: $it")
            } catch (e: Exception) {
                Timber.w("Could not take persistable permission: ${e.message}")
            }
            handleSelectedDocument(it)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Timber.d("MainActivity: Received broadcast - ${intent.action}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    viewModel.onPrinterDisconnected()
                    intent.usbDevice()?.let { requestedUsbPermissions.remove(it.deviceName) }
                }
                UsbDeviceReceiver.ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    Timber.d("MainActivity: USB permission callback - granted=$granted, device=$device")

                    device?.let {
                        if (granted) {
                            Timber.d("MainActivity: USB permission granted for ${it.deviceName}")
                            Toast.makeText(context, "打印机权限已授予，正在连接...", Toast.LENGTH_SHORT).show()
                            viewModel.connectPrinter(it)
                        } else {
                            Timber.w("MainActivity: USB permission denied for ${it.deviceName}")
                            Toast.makeText(context, "打印机权限被拒绝", Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Timber.e("MainActivity: USB permission callback but device is null!")
                    }
                }
                UsbDeviceReceiver.ACTION_PRINTER_CONNECTED -> {
                    val deviceName = intent.getStringExtra(UsbDeviceReceiver.EXTRA_DEVICE_NAME)
                    Timber.d("MainActivity: Printer connected - $deviceName")
                    Toast.makeText(context, "打印机已连接: $deviceName", Toast.LENGTH_LONG).show()
                    viewModel.refreshData()
                }
                UsbDeviceReceiver.ACTION_PRINTER_CONNECTION_FAILED -> {
                    val deviceName = intent.getStringExtra(UsbDeviceReceiver.EXTRA_DEVICE_NAME)
                    val errorMessage = intent.getStringExtra(UsbDeviceReceiver.EXTRA_ERROR_MESSAGE)
                    Timber.e("MainActivity: Printer connection failed - $deviceName, error: $errorMessage")
                    Toast.makeText(context, "打印机连接失败: $errorMessage", Toast.LENGTH_LONG).show()
                }
                UsbDeviceReceiver.ACTION_PRINTER_DISCONNECTED -> {
                    val deviceName = intent.getStringExtra(UsbDeviceReceiver.EXTRA_DEVICE_NAME)
                    Timber.d("MainActivity: Printer disconnected - $deviceName")
                    Toast.makeText(context, "打印机已断开: $deviceName", Toast.LENGTH_SHORT).show()
                    viewModel.refreshData()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register USB permission receiver early in onCreate so it's active during permission dialog
        val filter = IntentFilter().apply {
            addAction(UsbDeviceReceiver.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbDeviceReceiver.ACTION_PRINTER_CONNECTED)
            addAction(UsbDeviceReceiver.ACTION_PRINTER_CONNECTION_FAILED)
            addAction(UsbDeviceReceiver.ACTION_PRINTER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        Timber.d("MainActivity: USB permission receiver registered in onCreate")

        observeViewModel()
        handleIntent(intent)
        checkAndRequestPermissions()

        // Headless trigger for automation: adb shell am start ... --ez drvtest true
        if (intent.getBooleanExtra("drvtest", false)) {
            Timber.d("MainActivity: drvtest extra set, running driver pipeline test")
            viewModel.runDriverPipelineTest()
        }

        setContent {
            CPrintTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        MainScreen(
                            viewModel = viewModel,
                            onSelectDocument = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            onOpenSettings = { openPrintSettings() },
                            onOpenQueue = { openPrintQueue() },
                            onCheckForUpdate = { updater.checkForUpdate(userInitiated = true) },
                            onDocumentSelected = { document ->
                                openPrintPreview(document.uri)
                            }
                        )
                        AppUpdatePanel(updater)
                    }
                }
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
        Timber.d("MainActivity: USB permission receiver unregistered in onDestroy")
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        // Check if printer needs reconnection (e.g., after app restart)
        checkAndAutoConnectUsbPrinter()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.onPermissionsGranted()
            // Permissions already granted, check for USB printers
            checkAndAutoConnectUsbPrinter()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is MainUiState.Error -> {
                            // Show error (handled by UI)
                            Timber.e(state.message)
                        }
                        else -> { /* Other states handled by UI */ }
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                intent.usbDevice()?.let {
                    requestedUsbPermissions.remove(it.deviceName)
                    autoConnectUsbPrinter(it)
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    handleSelectedDocument(uri)
                }
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { handleSelectedDocument(it) }
            }
        }
    }

    private fun handleSelectedDocument(uri: Uri) {
        openPrintPreview(uri.toString())
    }

    private fun openPrintPreview(documentUri: String) {
        startActivity(PrintPreviewActivity.createIntent(this, documentUri))
    }

    private fun openPrintSettings() {
        startActivity(Intent(this, PrintSettingsActivity::class.java))
    }

    private fun openPrintQueue() {
        startActivity(Intent(this, PrintQueueActivity::class.java))
    }

    /** USB access is independent of storage and notification permissions. */
    private fun checkAndAutoConnectUsbPrinter() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { isPrinter(it) }
        if (device == null) viewModel.onPrinterDisconnected() else autoConnectUsbPrinter(device)
    }

    private fun isPrinter(device: UsbDevice): Boolean =
        UsbUtils.isPrinter(device) || KnownPrinters.findPrinter(device.vendorId, device.productId) != null

    private fun autoConnectUsbPrinter(device: UsbDevice) {
        if (!isPrinter(device)) return
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            // Repository checks the actual USB connection, not persisted READY state.
            // Repeated onResume/onNewIntent calls do not reopen the same connection.
            viewModel.connectPrinter(device)
        } else if (requestedUsbPermissions.add(device.deviceName)) {
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                device.deviceId,
                Intent(UsbDeviceReceiver.ACTION_USB_PERMISSION).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
