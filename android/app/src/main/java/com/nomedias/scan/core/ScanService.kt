package com.nomedias.scan.core

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 前台服务：媒体扫描为长耗时操作，扫描期间保活 + 通知栏显示进度。
 * 检测/移除/添加 .nomedia 都是秒级操作，直接在 Activity 中调用 ScanManager 即可。
 */
class ScanService : Service() {

    companion object {
        private const val ACTION_SCAN = "com.nomedias.scan.action.SCAN"
        private const val NOTIF_ID = 2001

        fun startScan(context: Context) {
            context.startForegroundService(
                Intent(context, ScanService::class.java).apply { action = ACTION_SCAN }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_SCAN) {
            ScanManager.startScan(this)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        Notifications.createChannels(this)
        val notif: Notification = Notifications.scanProgress(this, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}
