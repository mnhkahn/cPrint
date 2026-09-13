package com.cprint.app.driver

import android.annotation.SuppressLint
import android.os.ParcelFileDescriptor
import com.nokoprint.core.NativeDrv
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Starts a native print-driver filter embedded in a downloaded .so and exposes
 * its stdin/stdout/stderr as streams.
 *
 * Protocol (reverse engineered from NokoPrint's DrvWrapper):
 *  - System.load(absolute path of the .so)
 *  - instantiate class com.nokoprint.core.DrvWrapper_<name>, where <name> is the
 *    part of the .so file name between "drv" and "JNI" (libdrvescprJNI.so -> escpr)
 *  - doExec(argv, flatEnv 为 K,V,K,V 交替数组, libDir = .so 所在目录, int[3])
 *    returns a session handle and fills [stdinFd, stdoutFd, stderrFd]
 *  - doWait(handle) blocks until the filter exits, returns the exit code
 *  - doDestroy(handle) releases native resources
 */
object NativeDriver {

    class Session(
        private val drv: NativeDrv,
        private val handle: Long,
        val stdin: OutputStream,
        val stdout: InputStream,
        val stderr: InputStream,
        private val pfds: List<ParcelFileDescriptor>
    ) {
        fun waitFor(): Int = drv.doWait(handle)

        /** Closes the filter's stdin — closes both the stream and the owning PFD. */
        fun closeStdin() {
            try { stdin.close() } catch (_: Exception) {}
            try { pfds[0].close() } catch (_: Exception) {}
        }

        fun destroy() {
            try { stdin.close() } catch (_: Exception) {}
            try { stdout.close() } catch (_: Exception) {}
            try { stderr.close() } catch (_: Exception) {}
            pfds.forEach { try { it.close() } catch (_: Exception) {} }
            try { drv.doDestroy(handle) } catch (e: Exception) { Timber.w(e, "doDestroy failed") }
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun start(soFile: File, argv: Array<String>, env: Map<String, String> = emptyMap()): Session {
        val fileName = soFile.name
        val drvIdx = fileName.indexOf("drv")
        val jniIdx = fileName.indexOf("JNI", drvIdx + 1)
        require(drvIdx >= 0 && jniIdx > drvIdx) { "Cannot derive driver name from $fileName" }
        val name = fileName.substring(drvIdx + 3, jniIdx)

        Timber.d("NativeDriver: loading $soFile (driver '$name')")
        System.load(soFile.absolutePath)
        val drv = Class.forName("com.nokoprint.core.DrvWrapper_$name")
            .getDeclaredConstructor().newInstance() as NativeDrv

        val flatEnv = ArrayList<String>(env.size * 2)
        env.forEach { (k, v) -> flatEnv.add(k); flatEnv.add(v) }

        val fds = IntArray(3)
        val handle = drv.doExec(argv, flatEnv.toTypedArray(), soFile.parentFile!!.absolutePath, fds)
        Timber.d("NativeDriver: doExec ok, handle=$handle fds=${fds.joinToString()}")
        require(handle != 0L) { "doExec returned null handle" }

        val pfdIn = ParcelFileDescriptor.adoptFd(fds[0])
        val pfdOut = ParcelFileDescriptor.adoptFd(fds[1])
        val pfdErr = ParcelFileDescriptor.adoptFd(fds[2])
        return Session(
            drv = drv,
            handle = handle,
            stdin = FileOutputStream(pfdIn.fileDescriptor),
            stdout = FileInputStream(pfdOut.fileDescriptor),
            stderr = FileInputStream(pfdErr.fileDescriptor),
            pfds = listOf(pfdIn, pfdOut, pfdErr)
        )
    }
}
