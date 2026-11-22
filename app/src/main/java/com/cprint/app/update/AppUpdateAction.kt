package com.cprint.app.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cprint.app.R

@Composable
fun AppUpdateAction(updater: AppUpdateViewModel) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var lifecycle by remember(owner) { mutableStateOf(owner.lifecycle.currentState) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> lifecycle = owner.lifecycle.currentState }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var hidden by rememberSaveable(state.id, state.stage) { mutableStateOf(false) }
    var prompted by rememberSaveable { mutableStateOf(-1L) }
    var installError by remember { mutableStateOf<String?>(null) }
    fun install() {
        runCatching { context.startActivity(updater.installIntent()) }
            .onFailure { installError = "无法打开系统安装器，请稍后重试" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls() && state.stage == UpdateStage.READY) install()
    }
    LaunchedEffect(state.stage, state.id, lifecycle) {
        if (state.stage == UpdateStage.READY && lifecycle == Lifecycle.State.RESUMED && prompted != state.id) {
            hidden = false
            prompted = state.id
            if (context.packageManager.canRequestPackageInstalls()) install()
        }
    }
    LaunchedEffect(state.message) {
        if (state.stage == UpdateStage.IDLE && state.message.isNotBlank()) {
            android.widget.Toast.makeText(context, state.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    UpdateToolbarButton(state) {
        if (state.stage == UpdateStage.IDLE) updater.checkForUpdate(userInitiated = true)
        else hidden = false
    }
    if (hidden || state.stage == UpdateStage.IDLE || state.stage == UpdateStage.CHECKING) return
    AlertDialog(
        onDismissRequest = { hidden = true },
        title = { Text(if (state.version.isBlank()) "应用更新" else "更新至 ${state.version}") },
        text = { Column {
            Text(state.notes)
            Text(installError ?: state.message)
            if (state.stage == UpdateStage.DOWNLOADING) LinearProgressIndicator(progress = { state.progress })
            if (state.stage == UpdateStage.READY) Text("安装由系统确认。首次使用需允许本应用安装更新。")
        } },
        confirmButton = {
            when (state.stage) {
                UpdateStage.READY -> TextButton(onClick = {
                    if (context.packageManager.canRequestPackageInstalls()) install()
                    else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }) { Text("安装更新") }
                UpdateStage.FAILED -> TextButton(onClick = { installError = null; updater.checkForUpdate() }) { Text("重试") }
                else -> TextButton(onClick = { hidden = true }) { Text("后台下载") }
            }
        },
        dismissButton = { TextButton(onClick = { hidden = true }) { Text("稍后") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateToolbarButton(state: UpdateState, onClick: () -> Unit) {
    val label = stringResource(when (state.stage) {
        UpdateStage.IDLE -> R.string.check_app_update
        UpdateStage.CHECKING -> R.string.checking_app_update
        UpdateStage.DOWNLOADING -> R.string.downloading_app_update
        UpdateStage.READY -> R.string.app_update_ready
        UpdateStage.FAILED -> R.string.app_update_failed
    })
    val busy = state.stage == UpdateStage.CHECKING || state.stage == UpdateStage.DOWNLOADING
    val tint = MaterialTheme.colorScheme.onPrimary
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = state.stage != UpdateStage.CHECKING) {
            BadgedBox(badge = { if (state.stage == UpdateStage.READY) Badge() }) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    if (state.stage == UpdateStage.CHECKING) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = tint, strokeWidth = 2.dp)
                    } else if (state.stage == UpdateStage.DOWNLOADING) {
                        CircularProgressIndicator(
                            progress = { state.progress }, modifier = Modifier.size(28.dp),
                            color = tint, trackColor = tint.copy(alpha = 0.24f), strokeWidth = 2.dp
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Update,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(if (busy) 16.dp else 24.dp)
                    )
                }
            }
        }
    }
}
