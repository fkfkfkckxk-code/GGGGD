package com.svyaz.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.svyaz.app.MainActivity
import com.svyaz.app.R
import com.svyaz.app.appContainer

/**
 * Служба, которая работает во время звонка и показывает уведомление «Идёт звонок».
 * Без неё Android отключил бы микрофон, как только приложение свернули.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Нажата кнопка «Завершить» в уведомлении
            ACTION_HANG_UP -> appContainer.callManager.hangUp()

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> showCallNotification()
        }
        return START_NOT_STICKY
    }

    private fun showCallNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Звонки", NotificationManager.IMPORTANCE_LOW),
        )

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val hangUp = PendingIntent.getService(
            this, 1,
            Intent(this, CallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle("Идёт звонок")
            .setContentText("Нажмите, чтобы вернуться к разговору")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(0, "Завершить", hangUp)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось запустить службу звонка", e)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "CallService"
        private const val CHANNEL_ID = "calls"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_HANG_UP = "com.svyaz.app.call.HANG_UP"
        private const val ACTION_STOP = "com.svyaz.app.call.STOP"

        private var started = false

        fun start(context: Context) {
            if (started) return
            started = true
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
        }

        fun stop(context: Context) {
            if (!started) return
            started = false
            val intent = Intent(context, CallService::class.java)
            try {
                // Просим службу остановиться самой: так она успевает показать уведомление,
                // даже если звонок оборвался сразу после начала (этого требует Android).
                context.startService(Intent(intent).setAction(ACTION_STOP))
            } catch (e: Exception) {
                context.stopService(intent)
            }
        }
    }
}
