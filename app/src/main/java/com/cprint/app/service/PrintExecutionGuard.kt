package com.cprint.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.cprint.app.R
import com.cprint.app.presentation.queue.PrintQueueActivity
import javax.inject.Inject

/** Keeps USB rendering/transfers alive while the UI is hidden or the screen is off. */
class PrintExecutionGuard @Inject constructor() {
    fun acquire(service: Service, notificationId: Int): AutoCloseable {
        val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, service.getString(R.string.print_service_channel_name), NotificationManager.IMPORTANCE_LOW
        ))
        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_print)
            .setContentTitle(service.getString(R.string.print_in_progress))
            .setContentText("切换应用或关闭屏幕后仍会继续打印")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(service, 0,
                Intent(service, PrintQueueActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        service.startForeground(notificationId, notification)
        val lock = (service.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cPrint:print:$notificationId")
        try {
            // Safety bound in case a native driver never returns. Normal completion releases immediately.
            lock.acquire(2 * 60 * 60 * 1000L)
        } catch (error: Exception) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            throw error
        }
        return AutoCloseable {
            if (lock.isHeld) lock.release()
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        }
    }

    private companion object { const val CHANNEL_ID = "print_service_channel" }
}
