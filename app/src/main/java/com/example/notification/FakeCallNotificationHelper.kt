package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.MainActivity
import com.example.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Smartwatch Fake Incoming Call Notification Helper
 *
 * Dispatches an ongoing silent CallStyle incoming call notification.
 * Non-Wear OS smartwatches (Garmin, Amazfit, Huawei, Fitbit, etc.) monitor
 * system incoming calls via their companion apps over Bluetooth and immediately
 * buzz/vibrate the user's wrist continuously while the call notification is ongoing.
 */
object FakeCallNotificationHelper {

    private const val CHANNEL_ID = "smartwatch_silent_call_channel"
    private const val CHANNEL_NAME = "Smartwatch Relay Incoming Call"
    private const val NOTIFICATION_ID = 7771

    private val isCallActive = AtomicBoolean(false)

    /**
     * Creates a high-importance but completely silent notification channel.
     * Sound is strictly set to null so the phone speaker NEVER rings aloud.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            // Avoid recreating if already configured
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Silent incoming call trigger for smartwatch vibration"
                // Silent sound prevents audible phone ringtone
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Starts the fake incoming call notification.
     * Idempotent: Only posts if not already active so repeated sensor loop events
     * will not restart or lag the notification.
     */
    @Synchronized
    fun startFakeCall(context: Context, callerName: String = "Magnetic Strike") {
        if (isCallActive.get()) {
            return // Idempotent: Already active
        }

        createNotificationChannel(context)

        // Caller identity for CallStyle
        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        // Fullscreen / Activity intent
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Dummy broadcast intents for Decline and Answer actions required by CallStyle
        val dummyIntent = Intent("com.example.FAKE_CALL_ACTION")
        val dummyPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            dummyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Construct high-priority, silent CallStyle notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon_eye)
            .setContentTitle(callerName)
            .setContentText("Magnetic detection trigger")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setVibrate(null)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    dummyPendingIntent, // Decline intent
                    fullScreenPendingIntent // Answer intent
                )
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            isCallActive.set(true)
        } catch (e: SecurityException) {
            // Handled if POST_NOTIFICATIONS is not granted
        } catch (e: Exception) {
            // Failsafe catch
        }
    }

    /**
     * Instantly cancels the notification, signaling the smartwatch companion app
     * to immediately stop wrist vibration.
     */
    @Synchronized
    fun stopFakeCall(context: Context) {
        if (!isCallActive.get()) {
            return
        }
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        } finally {
            isCallActive.set(false)
        }
    }

    /**
     * Returns true if a fake incoming call notification is currently active.
     */
    fun isFakeCallActive(): Boolean = isCallActive.get()
}
