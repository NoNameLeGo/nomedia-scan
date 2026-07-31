package com.nomedias.scan.core

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MediaScannerConnection 分批封装。
 * 150G 超大目录不能一次性全扫（MediaProvider 队列会爆/被截断），
 * 因此按子目录分批、批间留间隔，逐批回调进度。
 */
object MediaScanner {

    /** 每批扫描的目录数 */
    private const val BATCH_SIZE = 5

    /** 批间间隔 ms */
    private const val BATCH_GAP_MS = 3000L

    /** 单个目录扫描等待上限（超时标记为 TIMEOUT，可续扫） */
    private const val DIR_TIMEOUT_SECONDS = 20L * 60

    /**
     * 分批扫描目录列表。
     * @param onBatchDone (batchIndex, totalBatches, batchDirs, timeoutDirs) 每批结束回调
     */
    suspend fun scanDirs(
        context: Context,
        dirs: List<String>,
        onBatchDone: (batchIndex: Int, totalBatches: Int, batchDirs: List<String>, timeoutDirs: List<String>) -> Unit
    ) {
        if (dirs.isEmpty()) return
        val batches = dirs.chunked(BATCH_SIZE)
        val timeoutDirs = mutableListOf<String>()

        batches.forEachIndexed { index, batch ->
            val latch = CountDownLatch(batch.size)
            MediaScannerConnection.scanFile(
                context,
                batch.toTypedArray(),
                null
            ) { _, _ -> latch.countDown() }

            val finished = latch.await(DIR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                // 超时的目录记下来，供用户后续续扫
                timeoutDirs += batch
            }
            onBatchDone(index, batches.size, batch, timeoutDirs.toList())

            if (index < batches.lastIndex) {
                delay(BATCH_GAP_MS)
            }
        }
    }

    /** 扫描单个目录（用于恢复阶段的索引清理触发） */
    suspend fun scanSingle(context: Context, path: String, timeoutSeconds: Long = 60L) {
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ -> latch.countDown() }
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
    }
}
