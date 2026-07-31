package com.nomedias.scan.core

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder

/**
 * 前台服务：长耗时扫描期间保活 + 通知展示。
 * 实际逻辑在 ScanManager（object），Service 只负责前台化与传参。
 */
class ScanService : Service() {

    companion object {
        private const val ACTION_OPEN = "com.nomedias.scan.action.OPEN"
        private const val ACTION_RESTORE = "com.nomedias.scan.action.RESTORE"
        private const val EXTRA_ROOT = "root"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_MODE = "mode"
        private const val NOTIF_ID = 2001

        fun startOpen(context: Context, rootPath: String, treeUri: Uri?, mode: String) {
            val i = Intent(context, ScanService::class.java).apply {
                action = ACTION_OPEN
                putExtra(EXTRA_ROOT, rootPath)
                putExtra(EXTRA_URI, treeUri?.toString())
                putExtra(EXTRA_MODE, mode)
            }
            context.startForegroundService(i)
        }

        fun startRestore(context: Context) {
            val i = Intent(context, ScanService::class.java).apply { action = ACTION_RESTORE }
            context.startForegroundService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_OPEN -> {
                val root = intent.getStringExtra(EXTRA_ROOT) ?: run { stopSelf(); return START_NOT_STICKY }
                val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
                val mode = runCatching { Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "SAFE") }
                    .getOrDefault(Mode.SAFE)
                ScanManager.startOpen(this, root, uri, mode)
            }
            ACTION_RESTORE -> ScanManager.startRestore(this)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        Notifications.createChannels(this)
        val notif: Notification = Notifications.scanProgress(this, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}
