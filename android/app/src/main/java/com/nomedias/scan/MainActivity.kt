package com.nomedias.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.nomedias.scan.core.Mode
import com.nomedias.scan.core.Phase
import com.nomedias.scan.core.ScanManager
import com.nomedias.scan.core.ScanService
import com.nomedias.scan.core.ShizukuRunner
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private companion object {
        const val REQ_PERMS = 100
        const val REQ_SHIZUKU = 101
    }

    private var rootPath: String? = null
    private var treeUri: Uri? = null
    private var currentMode: Mode = Mode.SAFE

    private lateinit var tvFolder: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvStats: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var dirsContainer: LinearLayout
    private lateinit var btnOpen: Button
    private lateinit var btnRestore: Button
    private lateinit var groupMode: RadioGroup
    private lateinit var tvShizukuHint: TextView

    private val pickFolder =
        registerForActivityResult(OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val path = treeUriToPath(uri)
                if (path == null) {
                    Toast.makeText(this, "无法解析该文件夹路径，请换一个位置", Toast.LENGTH_LONG).show()
                } else {
                    rootPath = path
                    treeUri = uri
                    tvFolder.text = path
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFolder = findViewById(R.id.tvFolder)
        tvStatus = findViewById(R.id.tvStatus)
        tvMessage = findViewById(R.id.tvMessage)
        tvStats = findViewById(R.id.tvStats)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        dirsContainer = findViewById(R.id.dirsContainer)
        btnOpen = findViewById(R.id.btnOpen)
        btnRestore = findViewById(R.id.btnRestore)
        groupMode = findViewById(R.id.groupMode)
        tvShizukuHint = findViewById(R.id.tvShizukuHint)

        findViewById<Button>(R.id.btnPick).setOnClickListener { pickFolder.launch(null) }

        groupMode.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == R.id.radioShizuku) Mode.SHIZUKU else Mode.SAFE
            updateModeHint()
        }

        btnOpen.setOnClickListener { onOpenClicked() }
        btnRestore.setOnClickListener { onRestoreClicked() }

        // Shizuku 连接状态监听
        ShizukuRunner.addBinderListener(ShizukuBinderListener())

        requestRuntimePermissions()

        lifecycleScope.launch {
            ScanManager.state.collect { render(it) }
        }
    }

    private inner class ShizukuBinderListener : Shizuku.ShizukuBinderReceivedListener {
        override fun binderReceived() = runOnUiThread { updateModeHint() }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuRunner.removeBinderListener(ShizukuBinderListener())
    }

    // ---- 权限 ----

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SHIZUKU) {
            updateModeHint()
        }
    }

    // ---- 交互 ----

    private fun onOpenClicked() {
        val path = rootPath ?: run {
            Toast.makeText(this, "请先选择目标文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentMode == Mode.SHIZUKU && !ShizukuRunner.hasPermission) {
            Toast.makeText(this, "需要先授权 Shizuku（点右上角模式提示里的授权）", Toast.LENGTH_LONG).show()
            ShizukuRunner.requestPermission(REQ_SHIZUKU)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("临时打开媒体扫描")
            .setMessage("将移出该文件夹下的所有 .nomedia，触发系统媒体扫描。\n\n$path\n\n开始后请保持本应用在前台直到扫描完成。")
            .setPositiveButton("开始") { _, _ ->
                ScanService.startOpen(this, path, treeUri, currentMode.name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onRestoreClicked() {
        if (ScanManager.state.value.rootPath == null) {
            Toast.makeText(this, "当前没有打开过的文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("恢复隐藏")
            .setMessage("将放回所有 .nomedia 并清理媒体库索引，图库恢复原样。确定？")
            .setPositiveButton("确定") { _, _ -> ScanService.startRestore(this) }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---- UI 渲染 ----

    private fun updateModeHint() {
        val shizukuOk = ShizukuRunner.isConnected && ShizukuRunner.hasPermission
        tvShizukuHint.text = if (shizukuOk) {
            "Shizuku 已连接并授权，文件操作秒级完成"
        } else {
            "Shizuku 未就绪：请先安装/激活 Shizuku，再点此授权"
        }
        tvShizukuHint.setOnClickListener {
            if (currentMode == Mode.SHIZUKU && !shizukuOk) {
                ShizukuRunner.requestPermission(REQ_SHIZUKU)
            } else {
                Toast.makeText(this, tvShizukuHint.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(s: com.nomedias.scan.core.ScanState) {
        tvStatus.text = when (s.phase) {
            Phase.IDLE -> "状态：空闲"
            Phase.OPENING -> "状态：正在移出 .nomedia…"
            Phase.SCANNING -> "状态：正在扫描媒体库…"
            Phase.BACKUP_WINDOW -> "状态：备份窗口期（去网盘备份，完成后点恢复）"
            Phase.RESTORING -> "状态：正在恢复 .nomedia…"
        }
        tvMessage.text = s.message
        tvStats.text = "移出 .nomedia：${s.nomediaMoved} 个 · 恢复：${s.nomediaRestored} 个"
        if (s.rootPath != null && tvFolder.text.isEmpty()) {
            tvFolder.text = s.rootPath
        }

        val scanning = s.phase == Phase.SCANNING
        val backup = s.phase == Phase.BACKUP_WINDOW
        val restoring = s.phase == Phase.RESTORING
        progressBar.isVisible = scanning
        tvProgress.isVisible = scanning
        btnOpen.isEnabled = s.phase == Phase.IDLE
        btnRestore.isEnabled = s.phase == Phase.IDLE && s.rootPath != null
        btnRestore.isVisible = s.phase == Phase.IDLE || s.phase == Phase.BACKUP_WINDOW

        if (scanning) {
            val pct = if (s.totalFiles > 0) ((s.indexedFiles * 100) / s.totalFiles).toInt().coerceIn(0, 100) else 0
            progressBar.progress = pct
            tvProgress.text = "已索引 ${s.indexedFiles} / ${s.totalFiles} 个文件（$pct%）"
        }

        dirsContainer.removeAllViews()
        if (s.dirs.isNotEmpty() && (scanning || restoring)) {
            for (d in s.dirs) {
                val tv = TextView(this)
                tv.textSize = 13f
                tv.text = when (d.status) {
                    com.nomedias.scan.core.DirStatus.PENDING -> "○ ${d.path}"
                    com.nomedias.scan.core.DirStatus.SCANNING -> "… ${d.path}"
                    com.nomedias.scan.core.DirStatus.DONE -> "✓ ${d.path}"
                    com.nomedias.scan.core.DirStatus.TIMEOUT -> "△ ${d.path}（${d.note}）"
                }
                tv.setPadding(0, 2, 0, 2)
                dirsContainer.addView(tv)
            }
        }
        if (backup) {
            Toast.makeText(this, "扫描完成！去网盘备份，完成后回来点「恢复隐藏」", Toast.LENGTH_LONG).show()
        }
    }

    // ---- 工具 ----

    /** treeUri -> 真实路径（兼容 primary: / raw: 两种格式） */
    private fun treeUriToPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            return null
        }
        return when {
            docId.startsWith("primary:") ->
                "${Environment.getExternalStorageDirectory().absolutePath}/${docId.removePrefix("primary:")}"
            docId.startsWith("raw:") -> docId.removePrefix("raw:")
            else -> null
        }
    }
}
