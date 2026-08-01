package com.nomedias.scan.fileops

import android.content.Context
import android.net.Uri
import com.nomedias.scan.core.Mode

/**
 * 文件操作抽象（只针对目标文件夹**根目录**的 .nomedia）：
 * 检测 / 移除 / 添加 .nomedia，以及目录枚举、媒体文件计数。
 */
interface FileOp {
    /** 根目录是否存在 .nomedia */
    suspend fun detectNomedia(rootPath: String): Boolean

    /** 移除根目录的 .nomedia，返回是否成功 */
    suspend fun removeNomedia(rootPath: String): Boolean

    /** 在根目录创建 .nomedia，返回是否成功 */
    suspend fun addNomedia(rootPath: String): Boolean

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
