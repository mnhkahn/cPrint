package com.nokoprint.core

/**
 * JNI shim for libdrvescprJNI.so.
 * The native side exports Java_com_nokoprint_core_DrvWrapper_1escpr_*,
 * so the package and class name must match exactly.
 */
class DrvWrapper_escpr : NativeDrv {
    external override fun doExec(argv: Array<String>, env: Array<String>, libDir: String, outFds: IntArray): Long
    external override fun doWait(handle: Long): Int
    external override fun doDestroy(handle: Long)
}
