@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.cprint.app.presentation.preview

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cprint.app.R
import com.cprint.app.domain.model.ColorMode
import com.cprint.app.domain.model.Orientation
import com.cprint.app.domain.model.PageRange
import com.cprint.app.domain.model.PaperSize
import com.cprint.app.domain.model.PrintSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Print Preview Screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreviewScreen(
    viewModel: PrintPreviewViewModel,
    onBackClick: () -> Unit,
    onPrintClick: () -> Unit,
    onSelectDocument: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pages by viewModel.previewPages.collectAsState()
    val printSettings by viewModel.printSettings.collectAsState()
    val isPrinting by viewModel.isPrinting.collectAsState()
    val printProgress by viewModel.printProgress.collectAsState()
    val printStatusMessage by viewModel.printStatusMessage.collectAsState()
    val documentUri = (uiState as? PrintPreviewUiState.Success)?.documentUri
    val pageIndexes = pages.indexes
    val pagerState = key(documentUri, pageIndexes) {
        rememberPagerState(
            initialPage = pages.currentPage?.let(pageIndexes::indexOf)?.coerceAtLeast(0) ?: 0,
            pageCount = { pageIndexes.size }
        )
    }
    val currentPage = pageIndexes.getOrNull(pagerState.currentPage)
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { position ->
            if (viewModel.previewPages.value.indexes == pageIndexes) {
                pageIndexes.getOrNull(position)?.let(viewModel::goToPage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_preview)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            PreviewBottomBar(
                position = pagerState.currentPage,
                previewPageCount = pageIndexes.size,
                selectedDocumentPage = currentPage?.takeIf { printSettings.pageRange != null },
                onPreviousPage = {
                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                },
                onNextPage = {
                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pageIndexes.lastIndex)) }
                },
                onPrintClick = onPrintClick,
                canPrint = uiState is PrintPreviewUiState.Success && pageIndexes.isNotEmpty() && !isPrinting
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is PrintPreviewUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PrintPreviewUiState.Error -> {
                    ErrorView(message = state.message, onSelectDocument = onSelectDocument)
                }
                is PrintPreviewUiState.Success -> {
                    PreviewContent(
                        documentUri = state.documentUri,
                        totalPages = pages.documentPageCount,
                        pageIndexes = pageIndexes,
                        pagerState = pagerState,
                        currentPage = currentPage,
                        onPageCountReady = { viewModel.updatePageCount(it) },
                        printSettings = printSettings,
                        onPrintPageRangeChange = viewModel::updatePrintPageRange,
                        onSettingsChange = { viewModel.updatePrintSettings(it) }
                    )
                }
            }

            // Printing Progress Dialog
            if (isPrinting) {
                PrintingProgressDialog(
                    progress = printProgress,
                    statusMessage = printStatusMessage,
                    onCancel = { viewModel.cancelPrint() }
                )
            }
        }
    }
}

@Composable
private fun PreviewContent(
    documentUri: String,
    totalPages: Int,
    pageIndexes: List<Int>,
    pagerState: PagerState,
    currentPage: Int?,
    onPageCountReady: (Int) -> Unit,
    printSettings: PrintSettings,
    onPrintPageRangeChange: (PageRange?) -> Unit,
    onSettingsChange: (PrintSettings) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Page preview area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp)
        ) {
            PdfPageViewer(
                documentUri = documentUri,
                pageIndexes = pageIndexes,
                pagerState = pagerState,
                onPageCountReady = onPageCountReady,
                paperSize = printSettings.paperSize,
                orientation = printSettings.orientation
            )
        }

        // Settings panel
        SettingsPanel(
            settings = printSettings,
            currentPage = currentPage,
            totalPages = totalPages,
            onPrintPageRangeChange = onPrintPageRangeChange,
            onSettingsChange = onSettingsChange
        )
    }
}

@Composable
private fun PdfPageViewer(
    documentUri: String,
    pageIndexes: List<Int>,
    pagerState: PagerState,
    onPageCountReady: (Int) -> Unit,
    paperSize: PaperSize,
    orientation: Orientation
) {
    val context = LocalContext.current
    var retryCount by remember(documentUri) { mutableStateOf(0) }
    var document by remember(documentUri, retryCount) { mutableStateOf<PdfPreviewDocument?>(null) }
    var loadError by remember(documentUri, retryCount) { mutableStateOf<String?>(null) }
    LaunchedEffect(documentUri, retryCount) {
        var openedDocument: PdfPreviewDocument? = null
        try {
            withContext(Dispatchers.IO) {
                // Keep ownership even if this effect is cancelled before IO returns.
                openedDocument = PdfPreviewDocument.open(context, Uri.parse(documentUri))
            }
            document = openedDocument
            onPageCountReady(checkNotNull(openedDocument).pageCount)
            awaitCancellation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Unable to open PDF preview")
            loadError = previewErrorMessage(error)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { openedDocument?.close() }
        }
    }

    val aspectRatio = if (orientation == Orientation.LANDSCAPE) {
        paperSize.heightMm / paperSize.widthMm
    } else {
        paperSize.widthMm / paperSize.heightMm
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Fit the entire sheet within both bounds without changing its shape.
        val paperWidth = minOf(maxWidth, maxHeight * aspectRatio)
        Card(
            modifier = Modifier.size(width = paperWidth, height = paperWidth / aspectRatio),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            val loadedDocument = document
            if (loadError != null) {
                PreviewLoadError(message = loadError!!, onRetry = { retryCount++ })
            } else if (loadedDocument != null && pageIndexes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.print_pages_invalid))
                }
            } else if (loadedDocument != null) {
                // A range change replaces both the pager state and its cached items.
                key(pagerState) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { position ->
                        pageIndexes.getOrNull(position)?.let { pageNumber ->
                            PdfPageImage(
                                document = loadedDocument,
                                pageNumber = pageNumber
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(
    document: PdfPreviewDocument,
    pageNumber: Int
) {
    var retryCount by remember(document, pageNumber) { mutableStateOf(0) }
    var bitmap by remember(document, pageNumber, retryCount) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(document, pageNumber, retryCount) { mutableStateOf(false) }
    var scale by remember(document, pageNumber) { mutableFloatStateOf(1f) }
    var offset by remember(document, pageNumber) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(document, pageNumber, retryCount) {
        try {
            bitmap = withContext(Dispatchers.IO) { document.renderPage(pageNumber) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.e(error, "Unable to render preview page %s", pageNumber + 1)
            renderError = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale == 1f) {
                        Offset.Zero
                    } else {
                        Offset(
                            x = (offset.x + pan.x).coerceIn(-1000f, 1000f),
                            y = (offset.y + pan.y).coerceIn(-1000f, 1000f)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (renderError) {
            PreviewLoadError(message = "这一页预览失败，请重试。", onRetry = { retryCount++ })
        } else if (bitmap == null) {
            CircularProgressIndicator()
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageNumber + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

@Composable
private fun PreviewLoadError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color.DarkGray, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun SettingsPanel(
    settings: PrintSettings,
    currentPage: Int?,
    totalPages: Int,
    onPrintPageRangeChange: (PageRange?) -> Unit,
    onSettingsChange: (PrintSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            PrintPageRangeSelector(
                selected = settings.pageRange,
                currentPage = currentPage?.plus(1),
                totalPages = totalPages,
                onSelected = onPrintPageRangeChange
            )

            // Copies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.copies))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (settings.copies > 1) {
                                onSettingsChange(settings.copy(copies = settings.copies - 1))
                            }
                        }
                    ) {
                        Text("-")
                    }
                    Text(
                        text = settings.copies.toString(),
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = {
                            if (settings.copies < 99) {
                                onSettingsChange(settings.copy(copies = settings.copies + 1))
                            }
                        }
                    ) {
                        Text("+")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Paper size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.paper_size))
                DropdownSelector(
                    options = PaperSize.entries,
                    selected = settings.paperSize,
                    optionLabel = { it.name },
                    onSelected = { onSettingsChange(settings.copy(paperSize = it)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.orientation))
                DropdownSelector(
                    options = Orientation.entries,
                    selected = settings.orientation,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                Orientation.PORTRAIT -> R.string.portrait
                                Orientation.LANDSCAPE -> R.string.landscape
                            }
                        )
                    },
                    onSelected = { onSettingsChange(settings.copy(orientation = it)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.color_mode))
                DropdownSelector(
                    options = ColorMode.entries,
                    selected = settings.colorMode,
                    optionLabel = { it.name },
                    onSelected = { onSettingsChange(settings.copy(colorMode = it)) }
                )
            }
        }
    }
}

@Composable
private fun PrintPageRangeSelector(
    selected: PageRange?,
    currentPage: Int?,
    totalPages: Int,
    onSelected: (PageRange?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.print_pages))
        Box {
            TextButton(
                onClick = { expanded = true },
                enabled = totalPages > 1 || selected != null,
                modifier = Modifier.widthIn(max = 200.dp)
            ) {
                Text(
                    text = selected?.let { stringResource(R.string.print_pages_selection, it.toString()) }
                        ?: stringResource(R.string.print_pages_all),
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_pages_all)) },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    }
                )
                if (currentPage != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.print_pages_current, currentPage)) },
                        onClick = {
                            expanded = false
                            onSelected(PageRange(specificPages = listOf(currentPage)))
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_pages_custom)) },
                    onClick = {
                        expanded = false
                        editing = true
                    }
                )
            }
        }
    }
    if (editing) {
        var input by remember { mutableStateOf(selected?.toString().orEmpty()) }
        val range = remember(input, totalPages) { PageRange.parseSelection(input, totalPages) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.print_pages_custom)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.print_pages_bounds, totalPages)) },
                    supportingText = {
                        Text(stringResource(if (input.isNotBlank() && range == null) {
                            R.string.print_pages_invalid
                        } else {
                            R.string.page_range_hint
                        }))
                    },
                    isError = input.isNotBlank() && range == null,
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = range != null, onClick = {
                    range?.let(onSelected)
                    editing = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun <T> DropdownSelector(
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(optionLabel(selected))
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun PreviewBottomBar(
    position: Int,
    previewPageCount: Int,
    selectedDocumentPage: Int?,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPrintClick: () -> Unit,
    canPrint: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page navigation
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPreviousPage,
                    enabled = position > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.previous_page)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (previewPageCount == 0) "–" else "${position + 1} / $previewPageCount")
                    if (selectedDocumentPage != null) {
                        Text(
                            text = stringResource(R.string.print_pages_selection, (selectedDocumentPage + 1).toString()),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                IconButton(
                    onClick = onNextPage,
                    enabled = position < previewPageCount - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.next_page)
                    )
                }
            }

            // Print button
            Button(
                onClick = onPrintClick,
                enabled = canPrint
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.print))
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onSelectDocument: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_loading_document),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSelectDocument) { Text("重新选择文件") }
    }
}

@Composable
private fun PrintingProgressDialog(
    progress: Int,
    statusMessage: String,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "正在打印",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消打印")
                }
            }
        }
    }
}
