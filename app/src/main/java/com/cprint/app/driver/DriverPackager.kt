package com.cprint.app.driver

import android.content.Context
import android.os.Build
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Downloads and installs printer driver packages.
 *
 * Prototype note: packages are fetched from NokoPrint's server to validate the
 * whole pipeline. Before any release these must be replaced by self-compiled
 * builds hosted on our own infrastructure (the package format can stay).
 */
object DriverPackager {

    private const val BASE_URL = "https://www.nokoprint.com/android_packs"

    data class DriverPack(
        val packName: String,   // server directory, e.g. "pack_drivers"
        val libId: String,      // e.g. "drv_escpr" — also the install dir name
        val version: String,    // e.g. "1.8.5"
        val soName: String      // e.g. "libdrvescprJNI.so"
    )

    val DRV_ESCPR = DriverPack("pack_drivers", "drv_escpr", "1.8.5", "libdrvescprJNI.so")
    val DRV_GUTENPRINT = DriverPack("pack_drivers", "drv_gutenprint", "5.2.8-pre1s", "libdrvgutenprintJNI.so")
    val DRV_HPLIP = DriverPack("pack_drivers", "drv_hplip", "3.25.6", "libdrvhplipJNI.so")
    val DRV_SPLIX = DriverPack("pack_drivers", "drv_splix", "2.0.0s", "libdrvsplixJNI.so")

    fun installDir(context: Context, pack: DriverPack): File =
        File(context.filesDir, pack.libId)

    fun installedSo(context: Context, pack: DriverPack): File =
        File(installDir(context, pack), pack.soName)

    fun isInstalled(context: Context, pack: DriverPack): Boolean =
        installedSo(context, pack).let { it.exists() && it.length() > 0 }

    /**
     * Downloads (if needed) and installs the driver package.
     * @return the installed .so file
     */
    fun ensureInstalled(context: Context, pack: DriverPack): File {
        val so = installedSo(context, pack)
        if (so.exists() && so.length() > 0) {
            Timber.d("DriverPackager: ${pack.libId} already installed")
            return so
        }

        val dir = installDir(context, pack)
        dir.mkdirs()

        val zipName = "pkg_${pack.libId}_${pack.version.replace('.', '_').replace('-', '_')}.zip"
        val zipFile = File(context.cacheDir, zipName)
        download("$BASE_URL/${pack.packName}/$zipName", zipFile)

        Timber.d("DriverPackager: installing ${pack.libId} from $zipName (${zipFile.length()} bytes)")
        val wantedExecEntries = abiExecEntries()
        var execInstalled = false

        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val bytes = zis.readBytes()
                when {
                    name in wantedExecEntries -> {
                        extractTarXz(bytes, dir) { fileName -> fileName.endsWith(".so") }
                        execInstalled = true
                        Timber.d("DriverPackager: extracted $name")
                    }
                    name == "data.txz" || name == "data.tgz" -> {
                        extractTarXz(bytes, dir) { true }
                        Timber.d("DriverPackager: extracted $name")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        zipFile.delete()

        if (!execInstalled || !so.exists()) {
            throw IllegalStateException("Driver pack ${pack.libId} has no binary for ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        }
        so.setReadable(true)
        Timber.d("DriverPackager: ${pack.libId} installed, so=${so.length()} bytes")
        return so
    }

    /** exec_<abi>.txz entry names, most preferred ABI first. */
    private fun abiExecEntries(): List<String> {
        val map = mapOf(
            "arm64-v8a" to "exec_arm_64",
            "armeabi-v7a" to "exec_arm",
            "x86_64" to "exec_x86_64",
            "x86" to "exec_x86"
        )
        return Build.SUPPORTED_ABIS.mapNotNull { map[it] }.map { "$it.txz" }
    }

    private fun download(url: String, dest: File) {
        Timber.d("DriverPackager: downloading $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Connection", "close")
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code for $url")
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Decompresses a .txz/.tgz payload and extracts entries accepted by [filter]
     * into [destDir], stripping leading "./" and keeping only the base name
     * for top-level files (driver packages are flat).
     */
    private fun extractTarXz(bytes: ByteArray, destDir: File, filter: (String) -> Boolean) {
        if (bytes.isEmpty()) return
        val raw = ByteArrayInputStream(bytes)
        val decompressed = when {
            // xz magic: FD 37 7A 58 5A 00
            bytes.size > 6 && bytes[0] == 0xFD.toByte() && bytes[1] == 0x37.toByte() ->
                XZCompressorInputStream(BufferedInputStream(raw))
            // gzip magic: 1F 8B
            bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() ->
                GzipCompressorInputStream(BufferedInputStream(raw))
            else -> throw IllegalArgumentException("Unknown archive compression format")
        }
        TarArchiveInputStream(BufferedInputStream(decompressed)).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val cleanName = entry.name.removePrefix("./")
                    if (filter(cleanName)) {
                        val outFile = File(destDir, cleanName)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var n = tar.read(buf)
                            while (n >= 0) {
                                out.write(buf, 0, n)
                                n = tar.read(buf)
                            }
                        }
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }
}
