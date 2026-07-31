package com.nomedias.scan.core

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku shell 执行封装。
 * 需要用户安装 Shizuku 并通过 adb/无线调试激活后，App 内授权一次。
 */
object ShizukuRunner {

    private const val TAG = "ShizukuRunner"

    val isConnected: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

    val hasPermission: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

    /** 发起授权请求，结果在 Activity.onRequestPermissionsResult 中返回 */
    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    /** 在 Activity 收到授权结果后刷新监听状态 */
    fun addBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.addBinderReceivedListenerSticky(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "addBinderListener failed", e)
        }
    }

    fun removeBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.removeBinderReceivedListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "removeBinderListener failed", e)
        }
    }

    /**
     * 以 shell 身份执行命令并返回 stdout 内容。
     * 命令以 sh -c 方式执行。
     */
    @Throws(Exception::class)
    fun exec(cmd: String): String {
        if (!isConnected) throw IllegalStateException("Shizuku 未连接")
        if (!hasPermission) throw IllegalStateException("Shizuku 未授权")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            ?: throw IllegalStateException("Shizuku 无法启动 shell 进程")
        return try {
            BufferedReader(InputStreamReader(process.inputStream)).readText()
        } finally {
            try {
                process.waitFor()
            } catch (e: Throwable) {
                // ignore
            }
        }
    }

    /** 返回非零即认为失败 */
    @Throws(Exception::class)
    fun execChecked(cmd: String): String = exec(cmd)
}
