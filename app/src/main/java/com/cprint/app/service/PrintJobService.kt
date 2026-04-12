package com.cprint.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cprint.app.R
import com.cprint.app.domain.model.PrintJob
import com.cprint.app.domain.model.PrintSettings
import com.cprint.app.domain.usecase.print.CancelPrintJobUseCase
import com.cprint.app.domain.usecase.print.CreatePrintJobUseCase
import com.cprint.app.presentation.queue.PrintQueueActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service for handling print jobs
 */
@AndroidEntryPoint
class PrintJobService : Service() {

    @Inject
    lateinit var createPrintJobUseCase: CreatePrintJobUseCase

    @Inject
    lateinit var cancelPrintJobUseCase: CancelPrintJobUseCase

    private val binder = LocalBinder()
    private var currentPrintJob: PrintJob? = null
    private var printJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): PrintJobService = this@PrintJobService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("PrintJobService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PRINT -> {
                val documentName = intent.getStringExtra(EXTRA_DOCUMENT_NAME) ?: return START_NOT_STICKY
                val documentUri = intent.getStringExtra(EXTRA_DOCUMENT_URI) ?: return START_NOT_STICKY
                val documentType = intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: return START_NOT_STICKY
                val totalPages = intent.getIntExtra(EXTRA_TOTAL_PAGES, 1)
                val copies = intent.getIntExtra(EXTRA_COPIES, 1)

                startPrintJob(documentName, documentUri, documentType, totalPages, copies)
            }
            ACTION_CANCEL_PRINT -> {
                cancelCurrentPrint()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        printJob?.cancel()
        Timber.d("PrintJobService destroyed")
    }

    private fun startPrintJob(
        documentName: String,
        documentUri: String,
        documentType: String,
        totalPages: Int,
        copies: Int
    ) {
        val settings = PrintSettings(copies = copies)

        startForeground(NOTIFICATION_ID, createNotification(documentName, 0))

        printJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = createPrintJobUseCase(
                    documentName = documentName,
                    documentUri = documentUri,
                    documentType = documentType,
                    totalPages = totalPages,
                    settings = settings
                )

                if (result.isSuccess) {
                    Timber.d("Print job completed successfully")
                    updateNotification(documentName, 100, true)
                } else {
                    Timber.e(result.exceptionOrNull(), "Print job failed")
                    updateNotification(documentName, 0, false, result.exceptionOrNull()?.message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Print job error")
                updateNotification(documentName, 0, false, e.message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelCurrentPrint() {
        currentPrintJob?.let { job ->
            CoroutineScope(Dispatchers.IO).launch {
                cancelPrintJobUseCase(job.id)
            }
        }
        printJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.print_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.print_service_channel_description)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(documentName: String, progress: Int): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PrintQueueActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PrintJobService::class.java).apply {
                action = ACTION_CANCEL_PRINT
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.printing_document, documentName))
            .setContentText(getString(R.string.print_in_progress))
            .setSmallIcon(R.drawable.ic_print)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_cancel, getString(R.string.cancel), cancelIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        documentName: String,
        progress: Int,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(documentName)
            .setSmallIcon(R.drawable.ic_print)
            .setProgress(0, 0, false)

        if (success) {
            builder.setContentText(getString(R.string.print_completed))
                .setOngoing(false)
        } else {
            builder.setContentText(errorMessage ?: getString(R.string.print_failed))
                .setOngoing(false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val CHANNEL_ID = "print_service_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_PRINT = "com.cprint.app.ACTION_START_PRINT"
        const val ACTION_CANCEL_PRINT = "com.cprint.app.ACTION_CANCEL_PRINT"

        const val EXTRA_DOCUMENT_NAME = "document_name"
        const val EXTRA_DOCUMENT_URI = "document_uri"
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_TOTAL_PAGES = "total_pages"
        const val EXTRA_COPIES = "copies"

        fun startPrint(
            context: Context,
            documentName: String,
            documentUri: String,
            documentType: String,
            totalPages: Int,
            copies: Int = 1
        ) {
            val intent = Intent(context, PrintJobService::class.java).apply {
                action = ACTION_START_PRINT
                putExtra(EXTRA_DOCUMENT_NAME, documentName)
                putExtra(EXTRA_DOCUMENT_URI, documentUri)
                putExtra(EXTRA_DOCUMENT_TYPE, documentType)
                putExtra(EXTRA_TOTAL_PAGES, totalPages)
                putExtra(EXTRA_COPIES, copies)
            }
            context.startForegroundService(intent)
        }
    }
}
