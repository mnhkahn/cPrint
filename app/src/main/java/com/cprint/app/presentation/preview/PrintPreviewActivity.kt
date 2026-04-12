package com.cprint.app.presentation.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cprint.app.presentation.theme.CPrintTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for print preview
 */
@AndroidEntryPoint
class PrintPreviewActivity : ComponentActivity() {

    private val viewModel: PrintPreviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val documentUri = intent.getStringExtra(EXTRA_DOCUMENT_URI)
        documentUri?.let {
            viewModel.loadDocument(it)
        }

        setContent {
            CPrintTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrintPreviewScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onPrintClick = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_URI = "document_uri"
    }
}
