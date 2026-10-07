package com.cprint.app.service

import android.app.Service
import android.content.ClipData
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.repository.UsbPrintRepository
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/** Owns preview print jobs independently of the Activity/ViewModel lifecycle. */
@AndroidEntryPoint
class PrintJobService : Service() {
    @Inject lateinit var createPrintJobUseCase: CreatePrintJobUseCase
    @Inject lateinit var usbPrintRepository: UsbPrintRepository
    @Inject lateinit var executionGuard: PrintExecutionGuard

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var printJob: Job? = null
    private var protection: AutoCloseable? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_PRINT) {
            if (printJob != null) {
                scope.launch { usbPrintRepository.cancelPrint() }
                printJob?.cancel()
            } else {
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra<ResultReceiver>(EXTRA_RESULT)
        if (printJob != null) {
            receiver?.send(1, Bundle().apply { putString("error", "已有打印任务正在执行") })
            return START_NOT_STICKY
        }
        try {
            protection = executionGuard.acquire(this, 1001)
            requireNotNull(intent)
            val name = requireNotNull(intent.getStringExtra(EXTRA_DOCUMENT_NAME))
            val uri = requireNotNull(intent.getStringExtra(EXTRA_DOCUMENT_URI))
            val type = requireNotNull(intent.getStringExtra(EXTRA_DOCUMENT_TYPE))
            val settings = intent.getStringExtra(EXTRA_SETTINGS)?.let {
                Gson().fromJson(it, PrintSettings::class.java)
            } ?: PrintSettings(copies = intent.getIntExtra(EXTRA_COPIES, 1))
            val pages = intent.getIntExtra(EXTRA_TOTAL_PAGES, 1)
            printJob = scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        createPrintJobUseCase(name, uri, type, pages, settings)
                    }
                    receiver?.send(if (result.isSuccess) 0 else 1, Bundle().apply {
                        putString("error", result.exceptionOrNull()?.message)
                    })
                } catch (error: CancellationException) {
                    receiver?.send(1, Bundle().apply { putString("error", "打印已取消") })
                    throw error
                } catch (error: Exception) {
                    receiver?.send(1, Bundle().apply { putString("error", error.message) })
                } finally {
                    protection?.close()
                    protection = null
                    printJob = null
                    stopSelf()
                }
            }
        } catch (error: Exception) {
            receiver?.send(1, Bundle().apply { putString("error", error.message) })
            protection?.close()
            protection = null
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        protection?.close()
        protection = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_PRINT = "com.cprint.app.ACTION_START_PRINT"
        const val ACTION_CANCEL_PRINT = "com.cprint.app.ACTION_CANCEL_PRINT"
        const val EXTRA_DOCUMENT_NAME = "document_name"
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_TOTAL_PAGES = "total_pages"
        const val EXTRA_COPIES = "copies"
        const val EXTRA_SETTINGS = "settings"
        const val EXTRA_RESULT = "result"

        suspend fun print(context: Context, documentName: String, documentUri: String,
                          documentType: String, totalPages: Int, settings: PrintSettings): Result<Unit> =
            suspendCancellableCoroutine { continuation ->
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(
                            if (resultCode == 0) Result.success(Unit)
                            else Result.failure(IllegalStateException(resultData?.getString("error") ?: "打印失败"))
                        ))
                    }
                }
                try {
                    context.startForegroundService(Intent(context, PrintJobService::class.java).apply {
                        action = ACTION_START_PRINT
                        clipData = ClipData.newRawUri(documentName, Uri.parse(documentUri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(EXTRA_DOCUMENT_NAME, documentName)
                        putExtra(EXTRA_DOCUMENT_URI, documentUri)
                        putExtra(EXTRA_DOCUMENT_TYPE, documentType)
                        putExtra(EXTRA_TOTAL_PAGES, totalPages)
                        putExtra(EXTRA_SETTINGS, Gson().toJson(settings))
                        putExtra(EXTRA_RESULT, receiver)
                    })
                } catch (error: Exception) {
                    continuation.resumeWith(Result.success(Result.failure(error)))
                }
                // Losing a UI observer must not cancel the service-owned print job.
            }

        fun cancel(context: Context) {
            context.startService(Intent(context, PrintJobService::class.java).apply {
                action = ACTION_CANCEL_PRINT
            })
        }
    }
}
