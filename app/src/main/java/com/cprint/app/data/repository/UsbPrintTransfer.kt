package com.cprint.app.data.repository

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import android.os.SystemClock
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

/** One outstanding USB write at a time, with an idle deadline rather than a job deadline. */
internal class UsbPrintTransfer(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val isConnected: () -> Boolean,
    private val disconnect: () -> Unit,
    private val request: UsbRequest = UsbRequest()
) {
    fun send(
        data: ByteArray,
        copies: Int,
        checkActive: () -> Unit,
        onWaiting: (Boolean) -> Unit,
        onBytesSent: (Int) -> Unit
    ) {
        val buffer = ByteBuffer.allocateDirect(16 * 1024)
        var pending = false
        try {
            check(onConnection { request.initialize(connection, endpoint) }) { "无法初始化 USB 打印写入" }
            repeat(copies) {
                var offset = 0
                while (offset < data.size) {
                    val length = minOf(buffer.capacity(), data.size - offset)
                    buffer.clear()
                    buffer.put(data, offset, length)
                    buffer.flip()
                    while (buffer.hasRemaining()) {
                        checkActive()
                        val start = buffer.position()
                        if (!onConnection { request.queue(buffer) }) throw IOException("USB bulk transfer 无法提交写入")
                        pending = true
                        val started = SystemClock.elapsedRealtime()
                        while (true) {
                            checkActive()
                            val elapsed = SystemClock.elapsedRealtime() - started
                            if (elapsed >= IDLE_TIMEOUT_MS) {
                                throw IOException("USB 写入超时：打印机 3 分钟未接收数据，请检查打印机面板和 USB 连接")
                            }
                            val completed = try {
                                onConnection { connection.requestWait(minOf(POLL_MS, IDLE_TIMEOUT_MS - elapsed)) }
                            } catch (_: TimeoutException) {
                                if (SystemClock.elapsedRealtime() - started >= WAIT_NOTICE_MS) onWaiting(true)
                                // The write is still queued. Wait again without resending any bytes.
                                continue
                            }
                            if (completed !== request) throw IOException("USB bulk transfer 未收到写入结果")
                            pending = false
                            break
                        }
                        val written = buffer.position() - start
                        if (written <= 0) throw IOException("USB bulk transfer 未写入任何数据")
                        offset += written
                        onWaiting(false)
                        onBytesSent(written)
                    }
                }
            }
        } finally {
            try {
                if (pending) {
                    val drained = synchronized(connection) {
                        if (!isConnected()) true else runCatching {
                            request.cancel()
                            connection.requestWait(POLL_MS) === request
                        }.getOrDefault(false)
                    }
                    // Closing the connection discards any completion that could not be reaped.
                    if (!drained) disconnect()
                }
            } finally {
                synchronized(connection) { request.close() }
                onWaiting(false)
            }
        }
    }

    private inline fun <T> onConnection(block: () -> T): T = synchronized(connection) {
        if (!isConnected()) throw IOException("打印机未连接，USB 打印已中断")
        block()
    }

    private companion object {
        const val POLL_MS = 1_000L
        const val WAIT_NOTICE_MS = 5_000L
        const val IDLE_TIMEOUT_MS = 180_000L
    }
}
