package com.appybits.eventsnap.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appybits.eventsnap.MainActivity
import kotlin.random.Random

object NotificationHelper {
    private const val CHANNEL_ID = "eventsnap_local_notifications"
    private const val CHANNEL_NAME = "EventSnap Activity"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates on uploads, downloads, and events"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(Random.nextInt(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun showWelcomeNotification(context: Context, role: String) {
        showNotification(context, "Welcome to EventSnap! \uD83C\uDF89", "You are successfully logged in as a $role.")
    }

    fun showUploadCompleteNotification(context: Context, count: Int) {
        showNotification(context, "Upload Complete ✅", "Successfully uploaded $count photo(s) to the event.")
    }

    fun showPrintRequestNotification(context: Context, count: Int) {
        showNotification(context, "Print Request Sent \uD83D\uDDA8\uFE0F", "Your request for $count photo print(s) has been submitted to the studio.")
    }
    
    fun showSelfieMatchedNotification(context: Context) {
        showNotification(context, "Selfie Uploaded! \uD83D\uDCF8", "We'll start matching your face to event photos. We'll notify you when new photos arrive.")
    }
}
