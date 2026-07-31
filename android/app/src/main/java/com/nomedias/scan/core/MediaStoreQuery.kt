package com.nomedias.scan.core

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * 通过 MediaStore 统计某个目录下已被系统索引的媒体文件数，
 * 用于展示「已索引 / 实际文件」的扫描进度。
 */
object MediaStoreQuery {

    /** 统计 rootPath 目录下已索引的图片+视频数 */
    fun countIndexed(context: Context, rootPath: String): Long {
        val like = "$rootPath/%"
        return count(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, like)
                + count(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, like)
    }

    private fun count(context: Context, uri: Uri, like: String): Long {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf("COUNT(*)"),
                "_data LIKE ?",
                arrayOf(like),
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        } catch (e: Throwable) {
            0L
        }
    }

    /** 删除 rootPath 目录下的索引条目（SAF 模式恢复时双保险，权限不足时静默失败） */
    fun deleteIndexed(context: Context, rootPath: String) {
        val like = "$rootPath/%"
        try {
            context.contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "_data LIKE ?",
                arrayOf(like)
            )
        } catch (e: Throwable) {
            // 无权限删除时忽略，靠重扫自动清理
        }
        try {
            context.contentResolver.delete(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "_data LIKE ?",
                arrayOf(like)
            )
        } catch (e: Throwable) {
            // ignore
        }
    }
}
