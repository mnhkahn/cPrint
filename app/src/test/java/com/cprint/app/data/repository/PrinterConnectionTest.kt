package com.cprint.app.data.repository

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.cprint.app.data.local.PrinterDao
import com.cprint.app.data.model.entity.PrinterEntity
import com.cprint.app.domain.model.PrinterStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class PrinterConnectionTest {
    private val context: Context = mock()
    private val manager: UsbManager = mock()
    private val device: UsbDevice = mock()
    private val connection: UsbDeviceConnection = mock()
    private val dao: PrinterDao = mock()
    private val usb: UsbPrintRepositoryImpl = mock()
    private lateinit var repository: PrinterRepositoryImpl

    @Before fun setUp() {
        whenever(context.getSystemService(Context.USB_SERVICE)).thenReturn(manager)
        whenever(device.vendorId).thenReturn(1208)
        whenever(device.productId).thenReturn(2217)
        whenever(manager.hasPermission(device)).thenReturn(true)
        whenever(manager.openDevice(device)).thenReturn(connection)
        whenever(usb.connect(device, connection)).thenReturn(true)
        repository = PrinterRepositoryImpl(context, dao, usb)
    }

    @Test fun `initializes shared USB connection before marking printer ready`() = runBlocking<Unit> {
        assertTrue(repository.connectPrinter(device).isSuccess)

        val saved = argumentCaptor<PrinterEntity>()
        inOrder(usb, dao) {
            verify(usb).connect(device, connection)
            verify(dao).insertPrinter(saved.capture())
        }
        assertEquals(PrinterStatus.READY, saved.firstValue.status)
    }

    @Test fun `denied USB permission never opens device or marks it ready`() = runBlocking<Unit> {
        whenever(manager.hasPermission(device)).thenReturn(false)
        assertTrue(repository.connectPrinter(device).isFailure)

        verify(manager, never()).openDevice(any())
        verify(dao, never()).insertPrinter(any())
        verifyNoInteractions(usb)
    }

    @Test fun `failed initialization cleans up shared connection without marking ready`() = runBlocking<Unit> {
        whenever(usb.connect(device, connection)).thenReturn(false)
        assertTrue(repository.connectPrinter(device).isFailure)

        verify(usb, times(2)).disconnect()
        verify(dao, never()).insertPrinter(any())
    }

    @Test fun `repeated lifecycle callbacks reuse live connection`() = runBlocking<Unit> {
        repository.connectPrinter(device)
        val saved = argumentCaptor<PrinterEntity>()
        verify(dao).insertPrinter(saved.capture())
        whenever(dao.getPrinterByVidPid(1208, 2217)).thenReturn(saved.firstValue)
        whenever(usb.isConnectedTo(device)).thenReturn(true)

        assertTrue(repository.connectPrinter(device).isSuccess)

        verify(manager, times(1)).openDevice(device)
        verify(usb, times(1)).connect(device, connection)
        verify(dao, times(1)).insertPrinter(any())
    }

    @Test fun `persisted ready state does not skip USB initialization after process restart`() = runBlocking<Unit> {
        repository.connectPrinter(device)
        val saved = argumentCaptor<PrinterEntity>()
        verify(dao).insertPrinter(saved.capture())
        whenever(dao.getPrinterByVidPid(1208, 2217)).thenReturn(saved.firstValue)
        whenever(usb.isConnectedTo(device)).thenReturn(false)

        assertTrue(repository.connectPrinter(device).isSuccess)

        verify(manager, times(2)).openDevice(device)
        verify(usb, times(2)).connect(device, connection)
    }

    @Test fun `detachment closes shared connection and clears ready state`() = runBlocking<Unit> {
        repository.connectPrinter(device)
        val saved = argumentCaptor<PrinterEntity>()
        verify(dao).insertPrinter(saved.capture())
        whenever(dao.getPrinterByVidPid(1208, 2217)).thenReturn(saved.firstValue)
        whenever(usb.isConnectedTo(device)).thenReturn(true)
        clearInvocations(usb)

        repository.disconnectDevice(device)

        verify(usb).disconnect()
        verify(dao).updatePrinterStatus(saved.firstValue.id, PrinterStatus.DISCONNECTED)
    }
}
