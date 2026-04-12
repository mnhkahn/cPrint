package com.cprint.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.*
import com.cprint.app.domain.repository.PrinterCapabilities
import com.cprint.app.domain.usecase.printer.GetConnectedPrinterUseCase
import com.cprint.app.domain.usecase.printer.GetPrinterCapabilitiesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for PrintSettingsScreen
 */
@HiltViewModel
class PrintSettingsViewModel @Inject constructor(
    private val getConnectedPrinterUseCase: GetConnectedPrinterUseCase,
    private val getPrinterCapabilitiesUseCase: GetPrinterCapabilitiesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintSettingsUiState>(PrintSettingsUiState.Loading)
    val uiState: StateFlow<PrintSettingsUiState> = _uiState.asStateFlow()

    private val _printSettings = MutableStateFlow(PrintSettings())
    val printSettings: StateFlow<PrintSettings> = _printSettings.asStateFlow()

    private val _printerCapabilities = MutableStateFlow<PrinterCapabilities?>(null)

    init {
        observePrinter()
    }

    private fun observePrinter() {
        getConnectedPrinterUseCase()
            .onEach { printer ->
                if (printer != null) {
                    loadPrinterCapabilities(printer.id)
                    _uiState.value = PrintSettingsUiState.Success(
                        printerName = printer.getDisplayName(),
                        isPrinterConnected = printer.status == PrinterStatus.READY
                    )
                } else {
                    _uiState.value = PrintSettingsUiState.NoPrinter
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadPrinterCapabilities(printerId: String) {
        viewModelScope.launch {
            try {
                val result = getPrinterCapabilitiesUseCase(printerId)
                if (result.isSuccess) {
                    _printerCapabilities.value = result.getOrThrow()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading printer capabilities")
            }
        }
    }

    fun updateSettings(settings: PrintSettings) {
        _printSettings.value = settings
    }

    fun updateCopies(copies: Int) {
        _printSettings.value = _printSettings.value.copy(copies = copies.coerceIn(1, 99))
    }

    fun updatePaperSize(paperSize: PaperSize) {
        _printSettings.value = _printSettings.value.copy(paperSize = paperSize)
    }

    fun updateOrientation(orientation: Orientation) {
        _printSettings.value = _printSettings.value.copy(orientation = orientation)
    }

    fun updateColorMode(colorMode: ColorMode) {
        _printSettings.value = _printSettings.value.copy(colorMode = colorMode)
    }

    fun updateDuplexMode(duplexMode: DuplexMode) {
        _printSettings.value = _printSettings.value.copy(duplexMode = duplexMode)
    }

    fun updateQuality(quality: PrintQuality) {
        _printSettings.value = _printSettings.value.copy(quality = quality)
    }

    fun updatePagesPerSheet(pagesPerSheet: Int) {
        _printSettings.value = _printSettings.value.copy(pagesPerSheet = pagesPerSheet.coerceIn(1, 16))
    }

    fun updatePageRange(pageRange: PageRange?) {
        _printSettings.value = _printSettings.value.copy(pageRange = pageRange)
    }

    fun saveSettings() {
        // Settings are automatically saved to DataStore or preferences
        // This method can be used to trigger a save operation if needed
    }

    /**
     * Get available paper sizes based on printer capabilities
     */
    fun getAvailablePaperSizes(): List<PaperSize> {
        val capabilities = _printerCapabilities.value ?: return PaperSize.entries
        return PaperSize.entries.filter {
            capabilities.supportedPaperSizes.contains(it.value)
        }
    }

    /**
     * Check if printer supports color
     */
    fun supportsColor(): Boolean {
        return _printerCapabilities.value?.supportsColor ?: false
    }

    /**
     * Check if printer supports duplex
     */
    fun supportsDuplex(): Boolean {
        return _printerCapabilities.value?.supportsDuplex ?: false
    }
}

/**
 * UI State for PrintSettingsScreen
 */
sealed class PrintSettingsUiState {
    object Loading : PrintSettingsUiState()
    object NoPrinter : PrintSettingsUiState()
    data class Success(
        val printerName: String,
        val isPrinterConnected: Boolean
    ) : PrintSettingsUiState()
}
