package com.nomedias.scan.core

import android.content.Context
import android.net.Uri
import com.nomedias.scan.fileops.FileOp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全局调度器：提供四个独立原子操作——
 * 选择文件夹（select + 自动 detect）、移除 .nomedia、添加 .nomedia、触发媒体扫描。
 * UI 通过 state 订阅状态与进度。
 */
object ScanManager {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var pollingJob: Job? = null

    private fun currentFileOp(context: Context): FileOp {
        val s = _state.value
        return FileOp.create(context, s.mode, s.treeUri?.let(Uri::parse))
    }

    /** 选择文件夹后初始化状态 */
    fun select(rootPath: String, treeUri: Uri?, mode: Mode) {
        job?.cancel()
        pollingJob?.cancel()
        _state.value = ScanState(mode = mode, rootPath = rootPath, treeUri = treeUri?.toString())
    }

    /** 检测根目录是否有 .nomedia（选文件夹后 / 切换模式后调用） */
    fun detect(context: Context) {
        val root = _state.value.rootPath ?: return
        scope.launch {
            runCatching { currentFileOp(context).detectNomedia(root) }
                .onSuccess { has ->
                    _state.update {
                        it.copy(
                            hasNomedia = has,
                            message = if (has) "检测到 .nomedia —— 当前图库不可见"
                                      else "未检测到 .nomedia —— 图库可见"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(message = "检测失败：${e.message}") }
                }
        }
    }

    /** 移除根目录 .nomedia（之后点「触发媒体扫描」让图库出现） */
    fun removeNomedia(context: Context) {
        val root = _state.value.rootPath ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(phase = Phase.REMOVING, message = "正在移除 .nomedia…") }
            runCatching { currentFileOp(context).removeNomedia(root) }
                .onSuccess { ok ->
                    _state.update {
                        it.copy(
                            phase = Phase.IDLE,
                            hasNomedia = false,
                            message = if (ok) "已移除 .nomedia —— 点「触发媒体扫描」让图库显示"
                                      else "移除失败：文件不存在或无权访问"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(phase = Phase.IDLE, message = "移除失败：${e.message}") }
                }
        }
    }

    /** 在根目录创建 .nomedia（之后点「触发媒体扫描」清理图库索引） */
    fun addNomedia(context: Context) {
        val root = _state.value.rootPath ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(phase = Phase.ADDING, message = "正在添加 .nomedia…") }
            runCatching { currentFileOp(context).addNomedia(root) }
                .onSuccess { ok ->
                    _state.update {
                        it.copy(
                            phase = Phase.IDLE,
                            hasNomedia = true,
                            message = if (ok) "已添加 .nomedia —— 点「触发媒体扫描」清理图库索引"
                                      else "添加失败：目录不可写"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(phase = Phase.IDLE, message = "添加失败：${e.message}") }
                }
        }
    }

    /** 触发媒体扫描（入队后由系统后台索引，进度轮询展示） */
    fun startScan(context: Context) {
        val root = _state.value.rootPath ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update {
                it.copy(phase = Phase.SCANNING, dirs = emptyList(), indexedFiles = 0, message = "正在触发媒体扫描…")
            }
            val op = currentFileOp(context)

            // 子目录分批入队（不等待系统回调，永不阻塞）
            runCatching {
                val subDirs = op.listSubDirs(root).ifEmpty { listOf(root) }
                _state.update { it.copy(dirs = subDirs.map { p -> DirProgress(p) }) }
                MediaScanner.scanDirs(context, subDirs)
            }

            // 文件总数后台统计（进度分母）
            scope.launch {
                runCatching { op.countMediaFiles(root) }
                    .onSuccess { total -> _state.update { it.copy(totalFiles = total) } }
            }

            startPolling(context, root)
        }
    }

    /** 轮询 MediaStore 计数刷新进度；最多 5 分钟（每 3s 一次） */
    private fun startPolling(context: Context, root: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            for (i in 0 until 100) {
                delay(3000)
                val idx = MediaStoreQuery.countIndexed(context, root)
                val total = _state.value.totalFiles
                _state.update {
                    it.copy(
                        indexedFiles = idx,
                        message = if (total > 0) "等待系统索引…已索引 $idx / $total"
                                  else "等待系统索引…已索引 $idx"
                    )
                }
            }
            _state.update {
                it.copy(
                    phase = Phase.IDLE,
                    message = "轮询结束（系统可能仍在后台索引，可再点「触发媒体扫描」刷新）"
                )
            }
        }
    }
}
