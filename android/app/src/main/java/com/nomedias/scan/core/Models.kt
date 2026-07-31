package com.nomedias.scan.core

/** 运行模式 */
enum class Mode { SHIZUKU, SAFE }

/** 全局阶段状态机 */
enum class Phase {
    IDLE,            // 空闲
    OPENING,         // 正在移出 .nomedia
    SCANNING,        // 正在触发媒体扫描
    BACKUP_WINDOW,   // 备份窗口期（等用户去网盘备份）
    RESTORING        // 正在恢复 .nomedia
}

/** 单个子目录的扫描状态 */
enum class DirStatus { PENDING, SCANNING, DONE, TIMEOUT }

/** 子目录进度条目 */
data class DirProgress(
    val path: String,
    var status: DirStatus = DirStatus.PENDING,
    var note: String = ""
)

/** 全局状态（StateFlow 对外暴露） */
data class ScanState(
    val phase: Phase = Phase.IDLE,
    val mode: Mode = Mode.SAFE,
    val rootPath: String? = null,
    val treeUri: String? = null,
    val dirs: List<DirProgress> = emptyList(),
    val nomediaMoved: Int = 0,      // 移出的 .nomedia 数量
    val nomediaRestored: Int = 0,   // 恢复的 .nomedia 数量
    val totalFiles: Long = 0,       // 实际媒体文件数（进度分母）
    val indexedFiles: Long = 0,     // 已索引数（进度分子）
    val message: String = ""
)
