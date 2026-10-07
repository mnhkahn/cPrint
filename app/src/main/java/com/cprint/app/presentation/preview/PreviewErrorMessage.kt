package com.cprint.app.presentation.preview

import java.io.FileNotFoundException
import java.io.IOException

internal fun previewErrorMessage(error: Throwable): String = when (error) {
    is SecurityException -> "没有读取此文件的权限，请从文件管理器重新选择或分享文件"
    is FileNotFoundException -> "找不到文件或文件暂时不可用，请先下载到本机后重新选择"
    is IOException -> "读取文件失败，请确认文件已下载完成后重试"
    else -> error.message?.takeIf { it.isNotBlank() } ?: "无法生成预览，请重新选择文件"
}
