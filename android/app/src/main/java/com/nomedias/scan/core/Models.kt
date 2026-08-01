package com.nomedias.scan.core

/** 运行模式 */
enum class Mode { SHIZUKU, SAFE }

/** 全局阶段状态机 */
enum class Phase {
    IDLE,        // 空闲（可操作）
    REMOVING,    // 正在移除 .nomedia
    ADDING,      // 正在添加 .nomedia
    SCANNING     // 已触发媒体扫描（等待系统索引）
}

/** 单个子目录的扫描状态 */
enum class DirStatus { PENDING, DONE }

/** 子目录进度条目 */
data class DirProgress(
    val path: String,
    var status: DirStatus = DirStatus.PENDING
)

/** 全局状态（StateFlow 对外暴露） */
data class ScanState(
    val phase: Phase = Phase.IDLE,
    val mode: Mode = Mode.SAFE,
    val rootPath: String? = null,
    val treeUri: String? = null,
    /** 根目录是否存在 .nomedia；null = 尚未检测 */
    val hasNomedia: Boolean? = null,
    val dirs: List<DirProgress> = emptyList(),
    val totalFiles: Long = 0,      // 实际媒体文件数（进度分母，后台统计）
    val indexedFiles: Long = 0,    // 已索引数（进度分子，轮询更新）
    val message: String = ""
)
