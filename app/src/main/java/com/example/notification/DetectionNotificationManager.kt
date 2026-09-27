package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.domain.model.MagneticDirection
import java.util.Locale

class DetectionNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "magnetic_strike_channel_v2"
        const val CHANNEL_NAME = "Immediate Magnetic Alerts"
        const val NOTIFICATION_ID = 7007
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Immediate heads-up alerts when magnetic movement occurs across the room"
                enableVibration(true)
                // 2 strong distinct pulses (160ms on, 110ms gap, 200ms on)
                vibrationPattern = longArrayOf(0, 160, 110, 200)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            val sysManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            sysManager?.createNotificationChannel(channel)
        }
    }

    fun sendImmediateDetectionNotification(
        deltaMagnitude: Float,
        dominantDirection: MagneticDirection,
        isRoomWide: Boolean = true
    ) {
        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Magnetic Movement Detected!"
        val rangeNote = if (isRoomWide) "across the room" else "nearby"
        val body = String.format(
            Locale.US,
            "Faint magnetic movement %s: Δ %.2f µT (%s)",
            rangeNote,
            deltaMagnitude,
            dominantDirection.label
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setVibrate(longArrayOf(0, 160, 110, 200)) // 2 strong pulses
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Handled safely
        }
    }
}
