package com.cprint.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cprint.app.R
import com.cprint.app.presentation.queue.PrintQueueActivity
import com.cprint.app.util.UsbUtils
import javax.inject.Inject

/** Keeps USB rendering/transfers alive while the UI is hidden or the screen is off. */
class PrintExecutionGuard @Inject constructor() {
    fun acquire(service: Service, notificationId: Int): AutoCloseable {
        UsbUtils.printerAccessError(service)?.let { throw IllegalStateException(it) }
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
        try {
            service.startForeground(notificationId, notification)
        } catch (error: SecurityException) {
            // The USB connection can disappear between the check and promotion.
            val reason = UsbUtils.printerAccessError(service) ?: throw error
            throw IllegalStateException(reason, error)
        }
        val power = service.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cPrint:print:$notificationId")
        try {
            // Safety bound in case a native driver never returns. Normal completion releases immediately.
            lock.acquire(2 * 60 * 60 * 1000L)
        } catch (error: Exception) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            throw error
        }
        logPowerState(service, power, lock, "print_started")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                logPowerState(context, power, lock, intent.action.orEmpty())
            }
        }
        var observingPower = runCatching {
            val events = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(service, receiver, events, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onFailure { Log.d(TAG, "Unable to observe power state", it) }.isSuccess
        return AutoCloseable {
            try {
                if (observingPower) {
                    observingPower = false
                    service.unregisterReceiver(receiver)
                }
            } finally {
                if (lock.isHeld) lock.release()
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            }
        }
    }

    private fun logPowerState(context: Context, power: PowerManager, lock: PowerManager.WakeLock, event: String) {
        runCatching {
            val standbyEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && power.isLowPowerStandbyEnabled
            Log.i(TAG, "Power: event=$event, elapsedMs=${SystemClock.elapsedRealtime()}, " +
                "uptimeMs=${SystemClock.uptimeMillis()}, held=${lock.isHeld}, interactive=${power.isInteractive}, " +
                "idle=${power.isDeviceIdleMode}, standbyEnabled=$standbyEnabled, " +
                "batteryExempt=${power.isIgnoringBatteryOptimizations(context.packageName)}")
        }.onFailure { Log.d(TAG, "Power diagnostics unavailable", it) }
    }

    private companion object {
        const val CHANNEL_ID = "print_service_channel"
        const val TAG = "PrintExecutionGuard"
    }
}
