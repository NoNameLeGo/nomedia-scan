package com.nomedias.scan.core

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.delay

/**
 * MediaScannerConnection 封装：
 * 只负责把目录「入队」触发系统扫描（分批+间隔，避免 MediaProvider 队列被打爆），
 * 不等待回调——扫描进度由 ScanManager 轮询 MediaStore 计数展示，永不阻塞。
 */
object MediaScanner {

    /** 分批触发扫描；每批 5 个目录，批间隔 2.5s */
    suspend fun scanDirs(context: Context, dirs: List<String>) {
        for (batch in dirs.chunked(5)) {
            runCatching {
                MediaScannerConnection.scanFile(context, batch.toTypedArray(), null, null)
            }
            delay(2500)
        }
    }
}
