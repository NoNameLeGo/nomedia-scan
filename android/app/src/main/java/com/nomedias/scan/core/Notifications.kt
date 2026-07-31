package com.nomedias.scan.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.nomedias.scan.MainActivity
import com.nomedias.scan.R

/** 通知管理：进度通知 + 备份窗口提醒 */
object Notifications {

    const val CHANNEL_SCAN = "channel_scan"
    const val CHANNEL_REMIND = "channel_remind"
    private const val NOTIF_ID_SCAN = 1001
    private const val NOTIF_ID_REMIND = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val scan = NotificationChannel(
            CHANNEL_SCAN,
            context.getString(R.string.notif_channel_scan),
            NotificationManager.IMPORTANCE_LOW
        )
        val remind = NotificationChannel(
            CHANNEL_REMIND,
            context.getString(R.string.notif_channel_remind),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(scan)
        nm.createNotificationChannel(remind)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun scanProgress(context: Context, progress: Int, indexed: Long, total: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在扫描媒体库")
            .setContentText("已索引 $indexed / $total")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent(context))
            .build()

    fun backupReminder(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("备份窗口期 · 记得恢复隐藏")
            .setContentText("去网盘备份相册，完成后回到本应用点「恢复隐藏」")
            .setContentIntent(pendingIntent(context))
            .build()

    fun restoreDone(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_REMIND)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("已恢复隐藏")
            .setContentText(".nomedia 已放回，图库索引已清理")
            .setContentIntent(pendingIntent(context))
            .build()

    fun notifyScan(context: Context, progress: Int, indexed: Long, total: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_SCAN, scanProgress(context, progress, indexed, total))
    }

    fun notifyBackupReminder(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_REMIND, backupReminder(context))
    }

    fun notifyRestoreDone(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_REMIND, restoreDone(context))
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_SCAN)
        nm.cancel(NOTIF_ID_REMIND)
    }
}
