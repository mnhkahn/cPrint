package com.cprint.app.presentation.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import com.cprint.app.domain.usecase.print.CancelPrintJobUseCase
import com.cprint.app.domain.usecase.print.GetPrintJobsUseCase
import com.cprint.app.domain.usecase.print.RetryPrintJobUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for PrintQueueScreen
 */
@HiltViewModel
class PrintQueueViewModel @Inject constructor(
    private val getPrintJobsUseCase: GetPrintJobsUseCase,
    private val cancelPrintJobUseCase: CancelPrintJobUseCase,
    private val retryPrintJobUseCase: RetryPrintJobUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrintQueueUiState>(PrintQueueUiState.Loading)
    val uiState: StateFlow<PrintQueueUiState> = _uiState.asStateFlow()

    private val _activeJobs = MutableStateFlow<List<PrintJob>>(emptyList())
    val activeJobs: StateFlow<List<PrintJob>> = _activeJobs.asStateFlow()

    private val _historyJobs = MutableStateFlow<List<PrintJob>>(emptyList())
    val historyJobs: StateFlow<List<PrintJob>> = _historyJobs.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    init {
        observePrintJobs()
    }

    private fun observePrintJobs() {
        // Observe active jobs
        getPrintJobsUseCase.active()
            .catch { error ->
                Timber.e(error, "Error observing active jobs")
            }
            .onEach { jobs ->
                _activeJobs.value = jobs
                updateUiState()
            }
            .launchIn(viewModelScope)

        // Observe history jobs
        getPrintJobsUseCase.history()
            .catch { error ->
                Timber.e(error, "Error observing history jobs")
            }
            .onEach { jobs ->
                _historyJobs.value = jobs
                updateUiState()
            }
            .launchIn(viewModelScope)
    }

    private fun updateUiState() {
        _uiState.value = PrintQueueUiState.Success(
            activeJobCount = _activeJobs.value.size,
            historyJobCount = _historyJobs.value.size
        )
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            try {
                val result = cancelPrintJobUseCase(jobId)
                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Failed to cancel job")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling job")
            }
        }
    }

    fun retryJob(jobId: String) {
        viewModelScope.launch {
            try {
                val result = retryPrintJobUseCase(jobId)
                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "Failed to retry job")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error retrying job")
            }
        }
    }

    fun refresh() {
        // Jobs are automatically refreshed through Flow
    }
}

/**
 * UI State for PrintQueueScreen
 */
sealed class PrintQueueUiState {
    object Loading : PrintQueueUiState()
    data class Success(
        val activeJobCount: Int,
        val historyJobCount: Int
    ) : PrintQueueUiState()
    data class Error(val message: String) : PrintQueueUiState()
}
