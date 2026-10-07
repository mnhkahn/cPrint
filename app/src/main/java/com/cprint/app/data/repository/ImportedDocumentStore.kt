package com.cprint.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private durable copies; temporary browser grants are not a document store. */
internal class ImportedDocumentStore(private val context: Context, private val limitBytes: Long = MAX_BYTES) {
    private val directory get() = File(context.filesDir, "imported-documents")

    fun hasPersistentReadAccess(uri: Uri): Boolean = context.contentResolver.persistedUriPermissions
        .any { it.uri == uri && it.isReadPermission }

    fun usedBytes(): Long = synchronized(lock) { files().sumOf { it.length() } }

    private fun files(): List<File> = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()

    /** Keeps history records; missing copies can be selected again when needed. */
    fun clearUnused(): Int = synchronized(lock) {
        files().count { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
            !leases.containsKey(uri) && file.delete()
        }
    }
    fun isImported(uri: Uri): Boolean = uri.authority == "${context.packageName}.fileprovider" &&
        uri.path?.startsWith("/internal_files/imported-documents/") == true

    private fun destination(source: Uri): File {
        val key = MessageDigest.getInstance("SHA-256").digest(source.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "imported-documents/$key")
    }

    fun storedUri(source: Uri): Uri = FileProvider.getUriForFile(context,
        "${context.packageName}.fileprovider", destination(source))

    fun import(source: Uri): Uri = synchronized(importLock) {
        // The import lock guarantees these are leftovers from interrupted imports.
        directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        val target = destination(source)
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建文档存储目录" }
        val available = synchronized(lock) {
            check(!leases.containsKey(storedUri(source).toString())) { "此文档正在使用，请稍后重新导入" }
            limitBytes - files().filter { it != target }.sumOf { it.length() }
        }
        val temporary = File.createTempFile("import-", ".tmp", target.parentFile)
        try {
            checkNotNull(context.contentResolver.openInputStream(source)) { "无法读取文件，请重新选择" }.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= available) { "分享文件副本空间不足，请到设置中清理副本（上限 200 MB）" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(temporary.length() > 0) { "文件为空，请重新选择" }
            synchronized(lock) {
                check(!leases.containsKey(storedUri(source).toString())) { "此文档正在使用，请稍后重新导入" }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            storedUri(source)
        } finally {
            temporary.delete()
        }
    }
    companion object {
        const val MAX_BYTES = 200L * 1024 * 1024
        private val lock = Any()
        private val importLock = Any()
        private val leases = mutableMapOf<String, Int>()

        fun retain(uri: String): AutoCloseable = synchronized(lock) {
            leases[uri] = (leases[uri] ?: 0) + 1
            var closed = false
            AutoCloseable {
                synchronized(lock) {
                    if (!closed) {
                        closed = true
                        val count = (leases[uri] ?: 1) - 1
                        if (count == 0) leases.remove(uri) else leases[uri] = count
                    }
                }
            }
        }
    }
}
