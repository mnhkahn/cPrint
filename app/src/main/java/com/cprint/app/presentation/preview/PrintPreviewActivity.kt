package com.cprint.app.presentation.preview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cprint.app.presentation.theme.CPrintTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Activity for print preview
 */
@AndroidEntryPoint
class PrintPreviewActivity : ComponentActivity() {

    private val viewModel: PrintPreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val documentUri = intent.getStringExtra(EXTRA_DOCUMENT_URI)
        documentUri?.let { uriString ->
            try {
                // Try to take persistable permission if available
                val uri = Uri.parse(uriString)
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Timber.d("Got persistable permission in PrintPreviewActivity for: $uriString")
            } catch (e: Exception) {
                // Permission might not be grantable, log but continue
                Timber.w("Could not take persistable permission in PrintPreviewActivity: ${e.message}")
            }
            viewModel.loadDocument(uriString)
        }

        // Observe for print completion
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PrintPreviewUiState.Success -> {
                            // Check if we just finished printing (you might want to add a specific state for this)
                        }
                        is PrintPreviewUiState.Error -> {
                            Timber.e("Print preview error: ${state.message}")
                        }
                        else -> {}
                    }
                }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            CPrintTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrintPreviewScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onPrintClick = {
                            val state = uiState
                            if (state is PrintPreviewUiState.Success) {
                                viewModel.print(
                                    documentName = state.documentName,
                                    documentUri = state.documentUri,
                                    documentType = state.documentType,
                                    pageCount = state.pageCount
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
    }
}
