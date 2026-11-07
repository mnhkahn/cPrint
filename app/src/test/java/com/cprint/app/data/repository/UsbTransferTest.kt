package com.cprint.app.data.repository

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class UsbTransferTest {
    private val connection: UsbDeviceConnection = mock()
    private val endpoint: UsbEndpoint = mock()
    private val repository = UsbPrintRepositoryImpl(mock<Context>()).apply {
        for ((name, value) in listOf("currentConnection" to connection, "bulkOutEndpoint" to endpoint)) {
            UsbPrintRepositoryImpl::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
        }
    }

    @Test fun `partial transfers send every remaining byte in order`() = runBlocking {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        whenever(connection.bulkTransfer(endpoint, data, 0, 6, 5000)).thenReturn(2)
        whenever(connection.bulkTransfer(endpoint, data, 2, 4, 5000)).thenReturn(1)
        whenever(connection.bulkTransfer(endpoint, data, 3, 3, 5000)).thenReturn(3)

        assertTrue(repository.sendRawData(data).isSuccess)
        inOrder(connection) {
            verify(connection).bulkTransfer(endpoint, data, 0, 6, 5000)
            verify(connection).bulkTransfer(endpoint, data, 2, 4, 5000)
            verify(connection).bulkTransfer(endpoint, data, 3, 3, 5000)
        }
        verifyNoMoreInteractions(connection)
    }

    @Test fun `failed transfer stops without replaying consumed bytes`() = runBlocking {
        val data = ByteArray(6)
        whenever(connection.bulkTransfer(endpoint, data, 0, 6, 5000)).thenReturn(2)
        whenever(connection.bulkTransfer(endpoint, data, 2, 4, 5000)).thenReturn(-1)

        assertTrue(repository.sendRawData(data).isFailure)
        verify(connection).bulkTransfer(endpoint, data, 0, 6, 5000)
        verify(connection).bulkTransfer(endpoint, data, 2, 4, 5000)
        verifyNoMoreInteractions(connection)
    }

    @Test fun `zero byte transfer fails rather than looping`() = runBlocking {
        val data = ByteArray(6)
        whenever(connection.bulkTransfer(endpoint, data, 0, 6, 5000)).thenReturn(0)
        assertTrue(repository.sendRawData(data).isFailure)
        verify(connection, times(1)).bulkTransfer(endpoint, data, 0, 6, 5000)
        verifyNoMoreInteractions(connection)
    }

    @Test fun `printer resumes the pending write after a pause without replaying bytes`() {
        val request = mock<UsbRequest>()
        whenever(request.initialize(connection, endpoint)).thenReturn(true)
        val queued = mutableListOf<List<Byte>>()
        lateinit var buffer: ByteBuffer
        whenever(request.queue(any<ByteBuffer>())).thenAnswer {
            buffer = it.getArgument(0)
            val view = buffer.duplicate()
            queued.add(List(view.remaining()) { view.get() })
            true
        }
        var polls = 0
        whenever(connection.requestWait(1000L)).thenAnswer {
            when (++polls) {
                1 -> buffer.position(2)
                in 2..10 -> {
                    ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
                    throw TimeoutException()
                }
                else -> buffer.position(buffer.limit())
            }
            request
        }

        val waiting = mutableListOf<Boolean>()
        val confirmed = mutableListOf<Int>()
        UsbPrintTransfer(connection, endpoint, { true }, { fail("unexpected disconnect") }, request)
            .send(byteArrayOf(1, 2, 3, 4), 1, {}, waiting::add, confirmed::add)

        assertEquals(listOf(listOf<Byte>(1, 2, 3, 4), listOf<Byte>(3, 4)), queued)
        assertEquals(listOf(2, 2), confirmed)
        assertTrue(waiting.contains(true))
        assertFalse(waiting.last())
        verify(request, never()).cancel()
        verify(request).close()
    }

    @Test fun `idle deadline cancels and drains the outstanding write without requeueing it`() {
        val request = mock<UsbRequest>()
        whenever(request.initialize(connection, endpoint)).thenReturn(true)
        whenever(request.queue(any<ByteBuffer>())).thenReturn(true)
        whenever(connection.requestWait(1000L)).thenAnswer {
            ShadowSystemClock.advanceBy(Duration.ofMinutes(3))
            throw TimeoutException()
        }.thenReturn(request)

        val error = assertThrows(IOException::class.java) {
            UsbPrintTransfer(connection, endpoint, { true }, { fail("unexpected disconnect") }, request)
                .send(byteArrayOf(1), 1, {}, {}, { fail("unexpected progress") })
        }
        assertTrue(error.message.orEmpty().contains("3 分钟"))
        verify(request, times(1)).queue(any<ByteBuffer>())
        inOrder(request, connection) {
            verify(request).cancel()
            verify(connection).requestWait(1000L)
            verify(request).close()
        }
    }
}
