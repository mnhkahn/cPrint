package com.nokoprint.core

/**
 * Common interface implemented by the JNI shim classes below.
 * The native libraries bind to the concrete class names (DrvWrapper_<name>),
 * this interface only exists so cPrint code can use them polymorphically.
 */
interface NativeDrv {
    fun doExec(argv: Array<String>, env: Array<String>, libDir: String, outFds: IntArray): Long
    fun doWait(handle: Long): Int
    fun doDestroy(handle: Long)
}
