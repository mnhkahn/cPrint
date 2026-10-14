package com.cprint.app.driver

import java.io.InputStream

/** Limits retained diagnostics while draining the entire native stderr pipe. */
internal object DriverDiagnostics {
    fun drain(input: InputStream, log: StringBuilder, limit: Int) {
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            val remaining = limit - log.length
            if (remaining > 0) {
                log.append(String(buffer, 0, minOf(count, remaining), Charsets.UTF_8))
            }
        }
    }
}
