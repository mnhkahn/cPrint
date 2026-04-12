package com.cprint.app.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cprint.app.R

/**
 * Error dialog with confirm and dismiss buttons
 */
@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmText: String = stringResource(R.string.ok),
    dismissText: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = {
                onConfirm?.invoke()
                onDismiss()
            }) {
                Text(confirmText)
            }
        },
        dismissButton = dismissText?.let {
            {
                OutlinedButton(onClick = onDismiss) {
                    Text(it)
                }
            }
        }
    )
}

/**
 * USB connection error dialog
 */
@Composable
fun UsbConnectionErrorDialog(
    errorType: UsbErrorType,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val (title, message) = when (errorType) {
        UsbErrorType.PERMISSION_DENIED ->
            stringResource(R.string.usb_permission_title) to
            stringResource(R.string.usb_permission_message)
        UsbErrorType.DEVICE_NOT_FOUND ->
            stringResource(R.string.printer_not_found_title) to
            stringResource(R.string.printer_not_found_message)
        UsbErrorType.CONNECTION_FAILED ->
            stringResource(R.string.connection_failed_title) to
            stringResource(R.string.connection_failed_message)
        UsbErrorType.TRANSFER_ERROR ->
            stringResource(R.string.transfer_error_title) to
            stringResource(R.string.transfer_error_message)
        UsbErrorType.UNKNOWN ->
            stringResource(R.string.unknown_error_title) to
            stringResource(R.string.unknown_error_message)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Printer error dialog for printer-specific errors
 */
@Composable
fun PrinterErrorDialog(
    error: PrinterError,
    onDismiss: () -> Unit,
    onResolve: (() -> Unit)? = null
) {
    val (titleRes, messageRes, actionTextRes) = when (error) {
        PrinterError.OUT_OF_PAPER ->
            Triple(R.string.out_of_paper_title, R.string.out_of_paper_message, R.string.ok)
        PrinterError.PAPER_JAM ->
            Triple(R.string.paper_jam_title, R.string.paper_jam_message, R.string.ok)
        PrinterError.LOW_INK ->
            Triple(R.string.low_ink_title, R.string.low_ink_message, R.string.ok)
        PrinterError.DOOR_OPEN ->
            Triple(R.string.door_open_title, R.string.door_open_message, R.string.ok)
        PrinterError.OFFLINE ->
            Triple(R.string.printer_offline_title, R.string.printer_offline_message, R.string.retry)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(messageRes)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_printer_error),
                contentDescription = null,
                tint = Color(0xFFFF9800)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onResolve?.invoke()
                    onDismiss()
                }
            ) {
                Text(stringResource(actionTextRes))
            }
        }
    )
}

/**
 * USB error types
 */
enum class UsbErrorType {
    PERMISSION_DENIED,
    DEVICE_NOT_FOUND,
    CONNECTION_FAILED,
    TRANSFER_ERROR,
    UNKNOWN
}

/**
 * Printer error types
 */
enum class PrinterError {
    OUT_OF_PAPER,
    PAPER_JAM,
    LOW_INK,
    DOOR_OPEN,
    OFFLINE
}
