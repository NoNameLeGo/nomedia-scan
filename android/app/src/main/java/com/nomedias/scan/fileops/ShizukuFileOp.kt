package com.nomedias.scan.fileops

import com.nomedias.scan.core.ShizukuRunner

/**
 * Shizuku 模式：以 shell 身份直接操作根目录的 .nomedia，秒级完成。
 * 所有路径用单引号转义，防止路径含空格/特殊字符。
 */
class ShizukuFileOp : FileOp {

    /** sh 单引号转义 */
    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    override suspend fun detectNomedia(rootPath: String): Boolean {
        val n = ShizukuRunner.exec(
            "find ${shq(rootPath)} -maxdepth 1 -name \".nomedia\" | wc -l"
        ).trim().toIntOrNull() ?: 0
        return n > 0
    }

    override suspend fun removeNomedia(rootPath: String): Boolean {
        ShizukuRunner.exec("rm -f ${shq(rootPath)}/.nomedia")
        return true
    }

    override suspend fun addNomedia(rootPath: String): Boolean {
        ShizukuRunner.exec("touch ${shq(rootPath)}/.nomedia")
        return true
    }

    override suspend fun listSubDirs(rootPath: String): List<String> =
        ShizukuRunner.exec("find ${shq(rootPath)} -mindepth 1 -maxdepth 1 -type d")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    override suspend fun countMediaFiles(rootPath: String): Long {
        val ext = "(" +
                "-iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.gif' " +
                "-o -iname '*.webp' -o -iname '*.bmp' -o -iname '*.heic' -o -iname '*.heif' " +
                "-o -iname '*.mp4' -o -iname '*.mkv' -o -iname '*.mov' -o -iname '*.avi' " +
                "-o -iname '*.webm' -o -iname '*.3gp' " +
                ")"
        return ShizukuRunner.exec(
            "find ${shq(rootPath)} -type f $ext | wc -l"
        ).trim().toLongOrNull() ?: 0L
    }
}
