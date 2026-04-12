package com.cprint.app.presentation.queue

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cprint.app.R
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintJobStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Print Queue Screen composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintQueueScreen(
    viewModel: PrintQueueViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()
    val historyJobs by viewModel.historyJobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_queue)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text(stringResource(R.string.active_jobs)) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeJobs.isNotEmpty()) {
                                    Badge { Text(activeJobs.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text(stringResource(R.string.history)) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) }
                )
            }

            // Content
            when (selectedTab) {
                0 -> ActiveJobsList(
                    jobs = activeJobs,
                    onCancelJob = { viewModel.cancelJob(it) }
                )
                1 -> HistoryJobsList(
                    jobs = historyJobs,
                    onRetryJob = { viewModel.retryJob(it) }
                )
            }
        }
    }
}

@Composable
private fun ActiveJobsList(
    jobs: List<PrintJob>,
    onCancelJob: (String) -> Unit
) {
    if (jobs.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Print,
            title = stringResource(R.string.no_active_jobs),
            message = stringResource(R.string.no_active_jobs_message)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(jobs, key = { it.id }) { job ->
                ActiveJobItem(
                    job = job,
                    onCancel = { onCancelJob(job.id) }
                )
            }
        }
    }
}

@Composable
private fun HistoryJobsList(
    jobs: List<PrintJob>,
    onRetryJob: (String) -> Unit
) {
    if (jobs.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.History,
            title = stringResource(R.string.no_history),
            message = stringResource(R.string.no_history_message)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(jobs, key = { it.id }) { job ->
                HistoryJobItem(
                    job = job,
                    onRetry = { onRetryJob(job.id) }
                )
            }
        }
    }
}

@Composable
private fun ActiveJobItem(
    job: PrintJob,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.documentName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${job.totalPages} ${stringResource(R.string.pages)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusChip(status = job.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress indicator
            if (job.status == PrintJobStatus.PRINTING || job.status == PrintJobStatus.PREPARING) {
                LinearProgressIndicator(
                    progress = { job.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${job.progress}%",
                    style = MaterialTheme.typography.bodySmall
                )

                if (job.canCancel()) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryJobItem(
    job: PrintJob,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            val (icon, iconColor) = when (job.status) {
                PrintJobStatus.COMPLETED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                PrintJobStatus.FAILED -> Icons.Default.Error to Color(0xFFF44336)
                PrintJobStatus.CANCELLED -> Icons.Default.Cancel to Color(0xFFFF9800)
                else -> Icons.Default.Print to MaterialTheme.colorScheme.primary
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = iconColor
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.documentName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDate(job.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.errorMessage != null) {
                    Text(
                        text = job.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (job.status == PrintJobStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: PrintJobStatus) {
    val (text, color) = when (status) {
        PrintJobStatus.PENDING -> stringResource(R.string.pending) to MaterialTheme.colorScheme.secondary
        PrintJobStatus.PREPARING -> stringResource(R.string.preparing) to MaterialTheme.colorScheme.tertiary
        PrintJobStatus.PRINTING -> stringResource(R.string.printing) to MaterialTheme.colorScheme.primary
        PrintJobStatus.PAUSED -> stringResource(R.string.paused) to Color(0xFFFF9800)
        PrintJobStatus.COMPLETED -> stringResource(R.string.completed) to Color(0xFF4CAF50)
        PrintJobStatus.FAILED -> stringResource(R.string.failed) to Color(0xFFF44336)
        PrintJobStatus.CANCELLED -> stringResource(R.string.cancelled) to Color(0xFF9E9E9E)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatDate(date: java.util.Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}
