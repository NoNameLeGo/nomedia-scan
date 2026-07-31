package com.nomedias.scan.fileops

import android.content.Context
import android.net.Uri
import com.nomedias.scan.core.Mode

/**
 * 文件操作抽象：两种模式各自实现「移出/恢复 .nomedia、目录枚举、媒体文件计数」。
 */
interface FileOp {
    /** 移出目标目录下所有 .nomedia（改名 .nomedia.bak / 记录后删除），返回处理数量 */
    suspend fun moveOut(rootPath: String): Int

    /** 恢复所有 .nomedia，返回恢复数量 */
    suspend fun restore(rootPath: String): Int

    /** 顶层子目录真实路径列表（用于分批扫描） */
    suspend fun listSubDirs(rootPath: String): List<String>

    /** 实际媒体文件数（进度分母；按常见图片/视频扩展名统计） */
    suspend fun countMediaFiles(rootPath: String): Long

    companion object {
        fun create(context: Context, mode: Mode, treeUri: Uri?): FileOp =
            when (mode) {
                Mode.SHIZUKU -> ShizukuFileOp()
                Mode.SAFE -> SafFileOp(context, treeUri)
            }
    }
}
