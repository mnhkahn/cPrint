@file:OptIn(ExperimentalMaterial3Api::class)

package com.cprint.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cprint.app.R
import com.cprint.app.domain.model.*

/**
 * Print Settings Screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSettingsScreen(
    viewModel: PrintSettingsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val printSettings by viewModel.printSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_settings)) },
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is PrintSettingsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is PrintSettingsUiState.NoPrinter -> {
                    NoPrinterView()
                }
                is PrintSettingsUiState.Success -> {
                    SettingsContent(
                        printerName = state.printerName,
                        isPrinterConnected = state.isPrinterConnected,
                        settings = printSettings,
                        viewModel = viewModel,
                        onSettingsChange = { viewModel.updateSettings(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    printerName: String,
    isPrinterConnected: Boolean,
    settings: PrintSettings,
    viewModel: PrintSettingsViewModel,
    onSettingsChange: (PrintSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Printer info card
        PrinterInfoCard(
            printerName = printerName,
            isConnected = isPrinterConnected
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Copies setting
        CopiesSetting(
            copies = settings.copies,
            onCopiesChange = { viewModel.updateCopies(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Paper size setting
        PaperSizeSetting(
            selectedPaperSize = settings.paperSize,
            availableSizes = viewModel.getAvailablePaperSizes(),
            onPaperSizeChange = { viewModel.updatePaperSize(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Orientation setting
        OrientationSetting(
            selectedOrientation = settings.orientation,
            onOrientationChange = { viewModel.updateOrientation(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Color mode setting
        ColorModeSetting(
            selectedColorMode = settings.colorMode,
            supportsColor = viewModel.supportsColor(),
            onColorModeChange = { viewModel.updateColorMode(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Duplex setting
        DuplexSetting(
            selectedDuplexMode = settings.duplexMode,
            supportsDuplex = viewModel.supportsDuplex(),
            onDuplexModeChange = { viewModel.updateDuplexMode(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quality setting
        QualitySetting(
            selectedQuality = settings.quality,
            onQualityChange = { viewModel.updateQuality(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pages per sheet setting
        PagesPerSheetSetting(
            pagesPerSheet = settings.pagesPerSheet,
            onPagesPerSheetChange = { viewModel.updatePagesPerSheet(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Page range setting
        PageRangeSetting(
            pageRange = settings.pageRange,
            onPageRangeChange = { viewModel.updatePageRange(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PrinterInfoCard(
    printerName: String,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = printerName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isConnected)
                        stringResource(R.string.printer_ready)
                    else
                        stringResource(R.string.printer_not_connected),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CopiesSetting(
    copies: Int,
    onCopiesChange: (Int) -> Unit
) {
    SettingItem(title = stringResource(R.string.copies)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onCopiesChange(copies - 1) },
                enabled = copies > 1
            ) {
                Text("-", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                text = copies.toString(),
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )

            IconButton(
                onClick = { onCopiesChange(copies + 1) },
                enabled = copies < 99
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun PaperSizeSetting(
    selectedPaperSize: PaperSize,
    availableSizes: List<PaperSize>,
    onPaperSizeChange: (PaperSize) -> Unit
) {
    SettingItem(title = stringResource(R.string.paper_size)) {
        SingleChoiceSegmentedButtonRow {
            availableSizes.forEach { size ->
                SegmentedButton(
                    selected = size == selectedPaperSize,
                    onClick = { onPaperSizeChange(size) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = availableSizes.indexOf(size),
                        count = availableSizes.size
                    )
                ) {
                    Text(size.name)
                }
            }
        }
    }
}

@Composable
private fun OrientationSetting(
    selectedOrientation: Orientation,
    onOrientationChange: (Orientation) -> Unit
) {
    SettingItem(title = stringResource(R.string.orientation)) {
        SingleChoiceSegmentedButtonRow {
            Orientation.entries.forEach { orientation ->
                SegmentedButton(
                    selected = orientation == selectedOrientation,
                    onClick = { onOrientationChange(orientation) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = Orientation.entries.indexOf(orientation),
                        count = Orientation.entries.size
                    )
                ) {
                    Text(
                        when (orientation) {
                            Orientation.PORTRAIT -> stringResource(R.string.portrait)
                            Orientation.LANDSCAPE -> stringResource(R.string.landscape)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorModeSetting(
    selectedColorMode: ColorMode,
    supportsColor: Boolean,
    onColorModeChange: (ColorMode) -> Unit
) {
    SettingItem(title = stringResource(R.string.color_mode)) {
        Column(modifier = Modifier.selectableGroup()) {
            ColorMode.entries.forEach { mode ->
                val enabled = mode != ColorMode.COLOR || supportsColor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = mode == selectedColorMode,
                            onClick = { if (enabled) onColorModeChange(mode) },
                            role = Role.RadioButton,
                            enabled = enabled
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == selectedColorMode,
                        onClick = null,
                        enabled = enabled
                    )
                    Text(
                        text = when (mode) {
                            ColorMode.COLOR -> stringResource(R.string.color)
                            ColorMode.GRAYSCALE -> stringResource(R.string.grayscale)
                            ColorMode.BLACK_WHITE -> stringResource(R.string.black_white)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                        color = if (enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplexSetting(
    selectedDuplexMode: DuplexMode,
    supportsDuplex: Boolean,
    onDuplexModeChange: (DuplexMode) -> Unit
) {
    SettingItem(title = stringResource(R.string.duplex_printing)) {
        Column(modifier = Modifier.selectableGroup()) {
            DuplexMode.entries.forEach { mode ->
                val enabled = mode == DuplexMode.SINGLE || supportsDuplex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = mode == selectedDuplexMode,
                            onClick = { if (enabled) onDuplexModeChange(mode) },
                            role = Role.RadioButton,
                            enabled = enabled
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == selectedDuplexMode,
                        onClick = null,
                        enabled = enabled
                    )
                    Text(
                        text = when (mode) {
                            DuplexMode.SINGLE -> stringResource(R.string.single_sided)
                            DuplexMode.LONG_EDGE -> stringResource(R.string.double_sided_long)
                            DuplexMode.SHORT_EDGE -> stringResource(R.string.double_sided_short)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                        color = if (enabled)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySetting(
    selectedQuality: PrintQuality,
    onQualityChange: (PrintQuality) -> Unit
) {
    SettingItem(title = stringResource(R.string.print_quality)) {
        SingleChoiceSegmentedButtonRow {
            PrintQuality.entries.forEach { quality ->
                SegmentedButton(
                    selected = quality == selectedQuality,
                    onClick = { onQualityChange(quality) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = PrintQuality.entries.indexOf(quality),
                        count = PrintQuality.entries.size
                    )
                ) {
                    Text(
                        when (quality) {
                            PrintQuality.DRAFT -> stringResource(R.string.draft)
                            PrintQuality.NORMAL -> stringResource(R.string.normal)
                            PrintQuality.HIGH -> stringResource(R.string.high)
                            PrintQuality.BEST -> stringResource(R.string.best)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PagesPerSheetSetting(
    pagesPerSheet: Int,
    onPagesPerSheetChange: (Int) -> Unit
) {
    SettingItem(title = stringResource(R.string.pages_per_sheet)) {
        SingleChoiceSegmentedButtonRow {
            listOf(1, 2, 4, 6, 9, 16).forEach { count ->
                SegmentedButton(
                    selected = count == pagesPerSheet,
                    onClick = { onPagesPerSheetChange(count) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = listOf(1, 2, 4, 6, 9, 16).indexOf(count),
                        count = 6
                    )
                ) {
                    Text(count.toString())
                }
            }
        }
    }
}

@Composable
private fun PageRangeSetting(
    pageRange: PageRange?,
    onPageRangeChange: (PageRange?) -> Unit
) {
    var textValue by remember(pageRange) {
        mutableStateOf(pageRange?.toString() ?: "")
    }

    SettingItem(title = stringResource(R.string.page_range)) {
        Column {
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onPageRangeChange(PageRange.parse(it))
                },
                placeholder = { Text(stringResource(R.string.page_range_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = stringResource(R.string.page_range_example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        content()
    }
}

@Composable
private fun NoPrinterView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Print,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.no_printer_connected),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.connect_printer_to_configure),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
