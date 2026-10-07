package com.cprint.app.service

import android.app.Application
import android.os.Looper
import android.print.PrintJobId
import android.print.PrintJobInfo
import android.printservice.PrintDocument
import android.printservice.PrintJob
import com.cprint.app.domain.repository.UsbPrintRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class CPrintPrintServiceTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: CPrintPrintService
    private lateinit var job: PrintJob
    private lateinit var usb: UsbPrintRepository
    private lateinit var protection: AutoCloseable

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = CPrintPrintService()
        usb = mock()
        service.usbPrintRepository = usb
        protection = mock()
        service.executionGuard = mock { on { acquire(any(), any()) } doReturn protection }
        // Reproduce the framework's thread checks on every PrintJob accessor,
        // including the failure-reporting path that previously also crashed.
        job = mock(defaultAnswer = { invocation ->
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        val id: PrintJobId = mock()
        whenever(job.id).thenReturn(id)
        whenever(job.isQueued).thenReturn(true)
        whenever(job.start()).thenReturn(true)
        whenever(job.info).thenReturn(mock<PrintJobInfo>())
        val document: PrintDocument = mock(defaultAnswer = { invocation ->
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            Answers.RETURNS_DEFAULTS.answer(invocation)
        })
        whenever(job.document).thenReturn(document)
    }

    private fun dispatchCallback(name: String) {
        CPrintPrintService::class.java.getDeclaredMethod(name, PrintJob::class.java).apply {
            isAccessible = true
        }.invoke(service, job)
    }

    @After fun tearDown() {
        service.onDestroy()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test fun `missing spool document is reported on main without crashing`() {
        dispatchCallback("onPrintJobQueued")
        dispatcher.scheduler.advanceUntilIdle()

        verify(job).start()
        verify(job).fail("The system print document is unavailable")
        verify(service.executionGuard).acquire(service, 1002)
        verify(protection).close()
        verify(job, never()).complete()
        verifyNoInteractions(usb)
    }

    @Test fun `rejected start does not consume document`() {
        whenever(job.start()).thenReturn(false)
        dispatchCallback("onPrintJobQueued")
        dispatcher.scheduler.advanceUntilIdle()

        verifyNoInteractions(service.executionGuard)
        verify(job, never()).document
        verify(job, never()).fail(any())
    }

    @Test fun `duplicate callback only starts one job`() {
        dispatchCallback("onPrintJobQueued")
        dispatchCallback("onPrintJobQueued")
        dispatcher.scheduler.advanceUntilIdle()

        verify(job, times(1)).start()
        verify(job, times(1)).fail(any())
    }

    @Test fun `cancelling waiting job updates spooler without cancelling USB`() {
        dispatchCallback("onPrintJobQueued")
        dispatchCallback("onRequestCancelPrintJob")
        dispatcher.scheduler.advanceUntilIdle()

        verify(job).cancel()
        verify(job, never()).start()
        verifyNoInteractions(usb)
    }
}
