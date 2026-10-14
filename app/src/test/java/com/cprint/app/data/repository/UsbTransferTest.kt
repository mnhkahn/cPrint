package com.cprint.app.data.repository

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
