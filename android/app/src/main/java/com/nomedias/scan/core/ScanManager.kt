package com.nomedias.scan.core

import android.content.Context
import android.net.Uri
import com.nomedias.scan.fileops.FileOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局调度器：编排「临时打开」与「恢复隐藏」两大流程。
 * 由前台服务驱动，UI 通过 state 订阅进度。
 */
object ScanManager {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    // ---- 打开流程 ----

    fun startOpen(context: Context, rootPath: String, treeUri: Uri?, mode: Mode) {
        if (job?.isActive == true) return
        _state.value = ScanState(mode = mode, rootPath = rootPath, treeUri = treeUri?.toString())
        job = scope.launch { doOpen(context, rootPath, treeUri, mode) }
    }

    private suspend fun doOpen(context: Context, rootPath: String, treeUri: Uri?, mode: Mode) {
        try {
            _state.update { it.copy(phase = Phase.OPENING, message = "正在移出 .nomedia…") }
            val op = FileOp.create(context, mode, treeUri)

            val moved = op.moveOut(rootPath)
            _state.update { it.copy(nomediaMoved = moved, message = "已移出 $moved 个 .nomedia") }

            val subDirs = op.listSubDirs(rootPath).ifEmpty { listOf(rootPath) }
            val total = op.countMediaFiles(rootPath)
            _state.update {
                it.copy(
                    dirs = subDirs.map { p -> DirProgress(p) },
                    totalFiles = total,
                    phase = Phase.SCANNING,
                    message = "正在分批触发媒体扫描…"
                )
            }

            MediaScanner.scanDirs(context, subDirs) { _, totalBatch, batchDirs, timeoutDirs ->
                val cur = _state.value
                val updated = cur.dirs.map { d ->
                    when {
                        timeoutDirs.contains(d.path) ->
                            d.copy(status = DirStatus.TIMEOUT, note = "可能未扫完")
                        batchDirs.contains(d.path) && d.status == DirStatus.PENDING ->
                            d.copy(status = DirStatus.DONE)
                        else -> d
                    }
                }
                val indexed = MediaStoreQuery.countIndexed(context, rootPath)
                _state.update {
                    it.copy(
                        dirs = updated,
                        indexedFiles = indexed,
                        message = "扫描批次 ${batchDirs.lastIndex + 1}/$totalBatch · 已索引 $indexed/$total"
                    )
                }
                Notifications.notifyScan(
                    context,
                    if (total > 0) ((indexed * 100) / total).toInt().coerceIn(0, 100) else 0,
                    indexed, total
                )
            }

            _state.update { it.copy(phase = Phase.BACKUP_WINDOW, message = "去网盘备份相册，完成后回来点「恢复隐藏」") }
            Notifications.notifyBackupReminder(context)
        } catch (e: Throwable) {
            _state.update { it.copy(phase = Phase.IDLE, message = "打开失败：${e.message}") }
        }
    }

    // ---- 恢复流程 ----

    fun startRestore(context: Context) {
        if (job?.isActive == true) return
        val cur = _state.value
        val root = cur.rootPath ?: return
        val mode = cur.mode
        val treeUri = cur.treeUri?.let(Uri::parse)
        job = scope.launch { doRestore(context, root, treeUri, mode) }
    }

    private suspend fun doRestore(context: Context, rootPath: String, treeUri: Uri?, mode: Mode) {
        try {
            _state.update { it.copy(phase = Phase.RESTORING, message = "正在恢复 .nomedia…") }
            val op = FileOp.create(context, mode, treeUri)

            val restored = op.restore(rootPath)
            _state.update { it.copy(nomediaRestored = restored, message = "已恢复 $restored 个 .nomedia，清理索引中…") }

            // 重扫目录，让 MediaProvider 发现 .nomedia 并自动清理索引
            MediaScanner.scanSingle(context, rootPath)

            // 双保险清理
            if (mode == Mode.SHIZUKU) {
                try {
                    ShizukuRunner.exec(
                        "content delete --uri content://media/external/images/media " +
                                "--where \"_data LIKE '${rootPath}/%'\""
                    )
                    ShizukuRunner.exec(
                        "content delete --uri content://media/external/video/media " +
                                "--where \"_data LIKE '${rootPath}/%'\""
                    )
                } catch (e: Throwable) {
                    // content delete 失败不影响主流程
                }
            } else {
                MediaStoreQuery.deleteIndexed(context, rootPath)
            }

            Notifications.cancelAll(context)
            Notifications.notifyRestoreDone(context)
            _state.update {
                it.copy(
                    phase = Phase.IDLE,
                    dirs = emptyList(),
                    indexedFiles = 0,
                    message = "已恢复隐藏，图库索引已清理"
                )
            }
        } catch (e: Throwable) {
            _state.update { it.copy(phase = Phase.IDLE, message = "恢复失败：${e.message}") }
        }
    }
}
