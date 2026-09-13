package com.nokoprint.core

/** JNI shim for libdrvhplipJNI.so — class name must match the native exports. */
class DrvWrapper_hplip : NativeDrv {
    external override fun doExec(argv: Array<String>, env: Array<String>, libDir: String, outFds: IntArray): Long
    external override fun doWait(handle: Long): Int
    external override fun doDestroy(handle: Long)
}
