package com.example.localagent.safety

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.localagent.MainActivity

object ServiceProtector {

    const val CHANNEL_ID = "localagent_core"
    const val NOTIFICATION_ID = 1001

    fun startForegroundProtection(service: Service) {
        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LocalAgent Core Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps LocalAgent active in background on HiOS and low-RAM devices"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("LocalAgent Active")
            .setContentText("Autonomous UI Agent listening and protected")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        service.startForeground(NOTIFICATION_ID, notification)
    }
}
