package com.nomedias.scan.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nomedias.scan.MainActivity

/** 通知管理：扫描进度通知（前台服务常驻） */
object Notifications {

    const val CHANNEL_SCAN = "channel_scan"
    private const val NOTIF_ID_SCAN = 1001

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCAN,
                context.getString(R.string.notif_channel_scan),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scanProgress(context: Context, indexed: Long, total: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("媒体扫描进行中")
            .setContentText(if (total > 0) "已索引 $indexed / $total" else "已索引 $indexed")
            .setOngoing(true)
            .setContentIntent(pendingIntent(context))
            .build()

    fun notifyScan(context: Context, indexed: Long, total: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_SCAN, scanProgress(context, indexed, total))
    }

    fun cancelScan(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_SCAN)
    }
}
