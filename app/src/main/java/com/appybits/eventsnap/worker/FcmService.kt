package com.appybits.eventsnap.worker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appybits.eventsnap.MainActivity
import com.appybits.eventsnap.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Receives Firebase Cloud Messaging (push) events:
 *  - onMessageReceived: a push arrived while the app is in the foreground -> show a notification.
 *  - onNewToken: Firebase issued this device a new push token -> save it to Firestore.
 */
@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Support both "notification" pushes and data-only pushes.
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        if (title == null && body == null) return
        showNotification(title ?: "EventSnap", body ?: "")
    }

    override fun onNewToken(token: String) {
        // Uses a process-wide scope: this service is destroyed almost immediately after
        // onNewToken returns, which would cancel a service-scoped coroutine mid-write.
        appScope.launch { authRepository.updateFcmToken(token) }
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled() below
    private fun showNotification(title: String, body: String) {
        val manager = NotificationManagerCompat.from(this)
        // False if the user denied the Android 13+ notification permission or blocked the app.
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // No FLAG_ONE_SHOT: with it, only the first notification's tap would ever work.
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            // TODO: replace with your own monochrome notification icon (R.drawable.ic_notification)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(nextId.incrementAndGet(), notification)
    }

    companion object {
        const val CHANNEL_ID = "eventsnap_notifications"

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val nextId = AtomicInteger(0)

        /**
         * Safe to call repeatedly. Also call this once from your Application.onCreate()
         * so the channel exists before the first background push arrives.
         */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EventSnap Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}