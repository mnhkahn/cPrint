package com.cprint.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cprint.app.data.repository.ImportedDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DocumentStorageCard() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ImportedDocumentStore(context) }
    val scope = rememberCoroutineScope()
    var used by remember { mutableStateOf<Long?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) { used = withContext(Dispatchers.IO) { store.usedBytes() } }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("文档存储", style = MaterialTheme.typography.titleMedium)
            Text("系统文件优先保留长期读取权限；临时分享文件保存副本。")
            Text(used?.let { "分享副本：%.1f MB / 200 MB".format(it / 1048576.0) } ?: "正在统计…")
            Text("清理只删除分享副本，保留原文件和打印历史；再次打开被清理的文件时需重新选择。")
            message?.let { Text(it) }
            TextButton(onClick = { confirming = true }, enabled = !busy && (used ?: 0) > 0) {
                Text(if (busy) "正在清理…" else "清理未使用的分享副本")
            }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("清理分享副本？") },
        text = { Text("打印历史会保留，正在预览或打印的文件会跳过。被清理的文件下次使用时需要重新选择。") },
        confirmButton = {
            TextButton(onClick = {
                confirming = false
                busy = true
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) { store.clearUnused() }
                        used = withContext(Dispatchers.IO) { store.usedBytes() }
                        message = "已清理 $count 个副本；正在使用的文件已保留"
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        message = "清理失败，请重试"
                    } finally { busy = false }
                }
            }) { Text("清理") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } }
    )
}
