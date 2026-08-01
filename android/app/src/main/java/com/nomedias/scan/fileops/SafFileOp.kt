package com.nomedias.scan.fileops

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF 模式：通过系统文件选择器授权的文件夹，用 DocumentFile 操作根目录 .nomedia。
 * 无需 Shizuku；.nomedia 为空文件，移除=删除、添加=重建。
 */
class SafFileOp(private val context: Context, private val treeUri: Uri?) : FileOp {

    private fun rootDoc(): DocumentFile? =
        treeUri?.let { DocumentFile.fromTreeUri(context, it) }

    override suspend fun detectNomedia(rootPath: String): Boolean = withContext(Dispatchers.IO) {
        rootDoc()?.findFile(".nomedia") != null
    }

    override suspend fun removeNomedia(rootPath: String): Boolean = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext false
        val f = root.findFile(".nomedia") ?: return@withContext false
        f.delete()
    }

    override suspend fun addNomedia(rootPath: String): Boolean = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext false
        if (root.findFile(".nomedia") != null) return@withContext true
        root.createFile("", ".nomedia") != null
    }

    override suspend fun listSubDirs(rootPath: String): List<String> = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext emptyList()
        root.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { dir -> dir.name?.let { "$rootPath/$it" } }
    }

    override suspend fun countMediaFiles(rootPath: String): Long = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext 0L
        var count = 0L
        dfsCount(root) { c -> count = c }
        count
    }

    // ---- 内部工具 ----

    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExts = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

    private fun isMedia(name: String?): Boolean {
        if (name == null) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExts || ext in videoExts
    }

    /** 深度遍历计数媒体文件（SAF 模式大目录较慢，放后台） */
    private fun dfsCount(doc: DocumentFile, set: (Long) -> Unit) {
        var c = 0L
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                dfsCount(child) { sub -> c += sub }
            } else if (isMedia(child.name)) {
                c++
            }
        }
        set(c)
    }
}
