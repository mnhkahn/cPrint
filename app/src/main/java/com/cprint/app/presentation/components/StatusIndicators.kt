package com.cprint.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cprint.app.R

/**
 * Connection status indicator with animated dot
 */
@Composable
fun ConnectionStatusIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val color by animateColorAsState(
        targetValue = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
        animationSpec = tween(300),
        label = "connection_color"
    )

    val scale by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.8f,
        animationSpec = tween(300),
        label = "connection_scale"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size((12 * scale).dp)
                .clip(CircleShape)
                .background(color)
        )

        if (showLabel) {
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

/**
 * Printer status indicator with icon and label
 */
@Composable
fun PrinterStatusIndicator(
    status: com.cprint.app.domain.model.PrinterStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (status) {
        com.cprint.app.domain.model.PrinterStatus.READY -> Triple(
            R.drawable.ic_printer,
            Color(0xFF4CAF50),
            "Ready"
        )
        com.cprint.app.domain.model.PrinterStatus.BUSY -> Triple(
            R.drawable.ic_printing,
            Color(0xFF2196F3),
            "Printing..."
        )
        com.cprint.app.domain.model.PrinterStatus.ERROR -> Triple(
            R.drawable.ic_printer_error,
            Color(0xFFF44336),
            "Error"
        )
        com.cprint.app.domain.model.PrinterStatus.OFFLINE -> Triple(
            R.drawable.ic_printer_offline,
            Color(0xFF9E9E9E),
            "Offline"
        )
        com.cprint.app.domain.model.PrinterStatus.CONNECTING -> Triple(
            R.drawable.ic_printer,
            Color(0xFFFF9800),
            "Connecting..."
        )
        com.cprint.app.domain.model.PrinterStatus.DISCONNECTED -> Triple(
            R.drawable.ic_printer_offline,
            Color(0xFF9E9E9E),
            "Disconnected"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

/**
 * Print job progress indicator
 */
@Composable
fun PrintJobProgressIndicator(
    progress: Float,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Printing $currentPage of $totalPages pages",
            style = MaterialTheme.typography.bodyMedium
        )

        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
