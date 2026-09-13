package com.cprint.app.presentation.main

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.Printer
import com.cprint.app.domain.model.RecentDocument
import com.cprint.app.domain.repository.PrinterRepository
import com.cprint.app.domain.usecase.document.GetRecentDocumentsUseCase
import com.cprint.app.domain.usecase.document.OpenDocumentUseCase
import com.cprint.app.domain.usecase.printer.GetConnectedPrinterUseCase
import com.cprint.app.driver.PrintDriverEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for MainScreen
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getConnectedPrinterUseCase: GetConnectedPrinterUseCase,
    private val getRecentDocumentsUseCase: GetRecentDocumentsUseCase,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val printerRepository: PrinterRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)

    private val _driverTestStatus = MutableStateFlow<String?>(null)
    val driverTestStatus: StateFlow<String?> = _driverTestStatus.asStateFlow()

    init {
        observeData()
    }

    fun runDriverPipelineTest() {
        if (_driverTestStatus.value != null) return // already running / showing result
        viewModelScope.launch {
            _driverTestStatus.value = "驱动管线测试运行中（首次需下载驱动包）…"
            val result = PrintDriverEngine.runEscprPipelineTest(appContext)
            _driverTestStatus.value = buildString {
                append(if (result.success) "✅ " else "❌ ").append(result.message).append('\n')
                append("输出: ${result.outBytes} 字节 → ${result.outFile?.absolutePath ?: "-"}\n")
                if (result.headHex.isNotEmpty()) append("头16字节: ${result.headHex}\n")
                append("exit=${result.exitCode}")
                if (result.stderrLog.isNotEmpty()) append("\nstderr: ${result.stderrLog.take(500)}")
            }
        }
    }

    fun clearDriverTestStatus() {
        _driverTestStatus.value = null
    }

    private fun observeData() {
        combine(
            getConnectedPrinterUseCase(),
            getRecentDocumentsUseCase.withLimit(10),
            _permissionsGranted
        ) { printer, documents, permissionsGranted ->
            if (permissionsGranted) {
                MainUiState.Success(
                    printer = printer,
                    recentDocuments = documents,
                    isPrinterConnected = printer?.status == com.cprint.app.domain.model.PrinterStatus.READY
                )
            } else {
                MainUiState.PermissionsRequired
            }
        }
            .catch { error ->
                Timber.e(error, "Error observing main data")
                _uiState.value = MainUiState.Error(error.message ?: "Unknown error")
            }
            .onEach { state ->
                _uiState.value = state
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionsGranted() {
        _permissionsGranted.value = true
    }

    fun onPermissionsDenied() {
        _permissionsGranted.value = false
        _uiState.value = MainUiState.PermissionsRequired
    }

    fun openDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                openDocumentUseCase(uri)
            } catch (e: Exception) {
                Timber.e(e, "Error opening document")
            }
        }
    }

    fun refreshData() {
        // Data is automatically refreshed through Flow
    }

    fun connectPrinter(device: UsbDevice) {
        viewModelScope.launch {
            try {
                Timber.d("MainViewModel: Connecting to printer ${device.deviceName}")
                val result = printerRepository.connectPrinter(device)
                if (result.isSuccess) {
                    Timber.d("MainViewModel: Printer connected successfully")
                    _uiState.update { currentState ->
                        if (currentState is MainUiState.Success) {
                            currentState.copy(isPrinterConnected = true)
                        } else currentState
                    }
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "MainViewModel: Failed to connect printer")
                    _uiState.value = MainUiState.Error(error?.message ?: "连接打印机失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "MainViewModel: Exception connecting to printer")
                _uiState.value = MainUiState.Error(e.message ?: "连接打印机时发生错误")
            }
        }
    }
}

/**
 * UI State for MainScreen
 */
sealed class MainUiState {
    object Loading : MainUiState()
    object PermissionsRequired : MainUiState()
    data class Success(
        val printer: Printer?,
        val recentDocuments: List<RecentDocument>,
        val isPrinterConnected: Boolean
    ) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
