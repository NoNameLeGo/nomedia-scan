package com.nomedias.scan.fileops

import com.nomedias.scan.core.ShizukuRunner

/**
 * Shizuku 模式：以 shell 身份直接操作文件，秒级完成，适合超大目录。
 * 所有命令路径均用单引号转义，防止路径含空格/特殊字符。
 */
class ShizukuFileOp : FileOp {

    /** sh 单引号转义 */
    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    override suspend fun moveOut(rootPath: String): Int {
        val n = count(shq(rootPath), ".nomedia")
        if (n > 0) {
            ShizukuRunner.exec(
                "find ${shq(rootPath)} -type f -name \".nomedia\" -exec mv {} {}.bak \\;"
            )
        }
        return n
    }

    override suspend fun restore(rootPath: String): Int {
        val n = count(shq(rootPath), ".nomedia.bak")
        if (n > 0) {
            ShizukuRunner.exec(
                "find ${shq(rootPath)} -type f -name \"*.nomedia.bak\" " +
                        "-exec sh -c 'mv \"\\$1\" \"\\${1%.bak}\"' _ {} \\;"
            )
        }
        return n
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

    private fun count(rootQuoted: String, name: String): Int =
        ShizukuRunner.exec("find $rootQuoted -type f -name \"$name\" | wc -l")
            .trim().toIntOrNull() ?: 0
}
