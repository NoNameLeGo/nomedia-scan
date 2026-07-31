package com.nomedias.scan.fileops

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SAF 模式：通过系统文件选择器授权的文件夹，用 DocumentFile 操作。
 * 无需 Shizuku，但遍历/操作超大目录明显更慢（走 ContentProvider）。
 * .nomedia 通常为空文件：移出 = 删除并记录目录，恢复 = 重建空文件。
 */
class SafFileOp(private val context: Context, private val treeUri: Uri?) : FileOp {

    private val prefs = context.getSharedPreferences("nomedia_record", Context.MODE_PRIVATE)

    /** rootPath -> 曾含 .nomedia 的目录相对路径（逗号分隔） */
    private fun recordKey(rootPath: String) = "dirs_${rootPath.hashCode()}"

    private fun rootDoc(): DocumentFile? =
        treeUri?.let { DocumentFile.fromTreeUri(context, it) }

    override suspend fun moveOut(rootPath: String): Int = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext 0
        val found = mutableListOf<String>()
        // 根目录优先：绝大多数场景 .nomedia 只在根目录，一次 findFile 即完成（秒级）
        val rootNomedia = root.findFile(".nomedia")
        if (rootNomedia != null) {
            rootNomedia.delete()
            found.add("") // 相对路径为空 = 根目录
        } else {
            // 兜底：个别场景 .nomedia 在子目录，才做全树遍历
            dfsFind(root, "", found) { doc -> doc.delete() }
        }
        prefs.edit().putString(recordKey(rootPath), found.joinToString(",")).apply()
        found.size
    }

    override suspend fun restore(rootPath: String): Int = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext 0
        val saved = prefs.getString(recordKey(rootPath), "") ?: ""
        val rels = saved.split(",").filter { it.isNotEmpty() }
        var restored = 0
        for (rel in rels) {
            val dir = resolveRel(root, rel) ?: continue
            if (dir.findFile(".nomedia") == null) {
                if (dir.createFile("", ".nomedia") != null) restored++
            }
        }
        prefs.edit().remove(recordKey(rootPath)).apply()
        restored
    }

    override suspend fun listSubDirs(rootPath: String): List<String> = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext emptyList()
        root.listFiles()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                dir.name?.let { "$rootPath/$it" }
            }
    }

    override suspend fun countMediaFiles(rootPath: String): Long = withContext(Dispatchers.IO) {
        val root = rootDoc() ?: return@withContext 0L
        var count = 0L
        dfsCount(root, count) { c -> count = c }
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

    /** 深度遍历，找 .nomedia；onFound 回调每个命中（delete 或记录） */
    private fun dfsFind(
        doc: DocumentFile,
        rel: String,
        found: MutableList<String>,
        onFound: (DocumentFile) -> Unit
    ) {
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                val childRel = if (rel.isEmpty()) child.name ?: "" else "$rel/${child.name}"
                dfsFind(child, childRel, found, onFound)
            } else if (child.name == ".nomedia") {
                found.add(rel)
                onFound(child)
            }
        }
    }

    /** 深度遍历计数媒体文件 */
    private fun dfsCount(doc: DocumentFile, count: Long, set: (Long) -> Unit) {
        var c = count
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                dfsCount(child, c, set)
            } else if (isMedia(child.name)) {
                c++
                set(c)
            }
        }
        set(c)
    }

    /** 按相对路径找到子目录 */
    private fun resolveRel(root: DocumentFile, rel: String): DocumentFile? {
        var cur = root
        for (seg in rel.split("/").filter { it.isNotEmpty() }) {
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }
}
