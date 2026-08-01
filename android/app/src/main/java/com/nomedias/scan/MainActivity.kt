package com.nomedias.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
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
import rikka.shizuku.Shizuku
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private companion object {
        const val REQ_PERMS = 100
        const val REQ_SHIZUKU = 101
    }

    private var currentMode: Mode = Mode.SAFE

    private lateinit var tvFolder: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvStats: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var dirsContainer: LinearLayout
    private lateinit var btnRemove: Button
    private lateinit var btnAdd: Button
    private lateinit var btnScan: Button
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
                val storageRoot = Environment.getExternalStorageDirectory().absolutePath
                when {
                    path == null -> {
                        // 解析失败：Shizuku 模式可手动输入路径；SAF 模式提示重选并附诊断信息
                        if (currentMode == Mode.SHIZUKU) {
                            showManualPathDialog(uri)
                        } else {
                            Toast.makeText(
                                this,
                                "无法解析该文件夹路径，请重新选择。\nURI: $uri",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    path == storageRoot -> {
                        Toast.makeText(this, "请选择具体的文件夹（不要选存储根目录）", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        tvFolder.text = path
                        ScanManager.select(path, uri, currentMode)
                        ScanManager.detect(this)
                    }
                }
            }
        }

    /** 解析失败时（Shizuku 模式）让用户手动输入真实路径 */
    private fun showManualPathDialog(uri: Uri) {
        val input = EditText(this).apply {
            hint = "/storage/emulated/0/你的文件夹"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("输入文件夹路径")
            .setMessage("无法从文件选择器解析路径（$uri）。\n请手动输入完整路径，例如 /storage/emulated/0/xxx")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val p = input.text.toString().trim()
                if (p.startsWith("/")) {
                    tvFolder.text = p
                    ScanManager.select(p, null, currentMode)
                    ScanManager.detect(this)
                } else {
                    Toast.makeText(this, "路径需以 / 开头", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
        btnRemove = findViewById(R.id.btnRemove)
        btnAdd = findViewById(R.id.btnAdd)
        btnScan = findViewById(R.id.btnScan)
        groupMode = findViewById(R.id.groupMode)
        tvShizukuHint = findViewById(R.id.tvShizukuHint)

        findViewById<Button>(R.id.btnPick).setOnClickListener { pickFolder.launch(null) }

        groupMode.setOnCheckedChangeListener { _, checkedId ->
            currentMode = if (checkedId == R.id.radioShizuku) Mode.SHIZUKU else Mode.SAFE
            updateModeHint()
            // 切换模式后重新检测（fileOp 变化；Shizuku 模式可输入路径，SAF 模式沿用已授权 treeUri）
            if (ScanManager.state.value.rootPath != null) {
                ScanManager.detect(this)
            }
        }

        btnRemove.setOnClickListener { ScanManager.removeNomedia(this) }
        btnAdd.setOnClickListener { ScanManager.addNomedia(this) }
        btnScan.setOnClickListener { ScanService.startScan(this) }

        // Shizuku 连接状态监听
        ShizukuRunner.addBinderListener(ShizukuBinderListener())

        requestRuntimePermissions()

        lifecycleScope.launch {
            ScanManager.state.collect { render(it) }
        }
    }

    private inner class ShizukuBinderListener : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() = runOnUiThread { updateModeHint() }
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

    // ---- UI 渲染 ----

    private fun updateModeHint() {
        val shizukuOk = ShizukuRunner.isConnected && ShizukuRunner.hasPermission
        tvShizukuHint.text = if (shizukuOk) {
            "Shizuku 已连接并授权"
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
        // 状态栏
        val nomediaText = when (s.hasNomedia) {
            null -> "（未检测）"
            true -> "存在 —— 图库不可见"
            false -> "不存在 —— 图库可见"
        }
        tvStatus.text = "文件夹：${s.rootPath ?: "未选择"}\n.nomedia：$nomediaText"
        tvMessage.text = s.message
        tvStats.text = "模式：${if (s.mode == Mode.SHIZUKU) "Shizuku" else "SAF"}" +
                if (s.totalFiles > 0) " · 文件数：${s.totalFiles}" else ""

        // 按钮状态
        val idle = s.phase == Phase.IDLE
        btnRemove.isVisible = idle && s.hasNomedia == true
        btnAdd.isVisible = idle && s.hasNomedia == false && s.rootPath != null
        btnScan.isEnabled = idle && s.rootPath != null

        // 进度区
        val scanning = s.phase == Phase.SCANNING
        progressBar.isVisible = scanning
        tvProgress.isVisible = scanning
        if (scanning) {
            val pct = if (s.totalFiles > 0) ((s.indexedFiles * 100) / s.totalFiles).toInt().coerceIn(0, 100) else 0
            progressBar.progress = pct
            tvProgress.text = if (s.totalFiles > 0) "已索引 ${s.indexedFiles} / ${s.totalFiles}（$pct%）" else "已索引 ${s.indexedFiles}"
        }

        // 子目录列表
        dirsContainer.removeAllViews()
        for (d in s.dirs) {
            val tv = TextView(this)
            tv.textSize = 13f
            tv.text = "○ ${d.path}"
            tv.setPadding(0, 2, 0, 2)
            dirsContainer.addView(tv)
        }
    }

    // ---- 工具 ----

    /** treeUri -> 真实路径（兼容 primary / primary: / raw: 多种格式，失败时从 uri 路径段解码兜底） */
    private fun treeUriToPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            Uri.decode(uri.lastPathSegment) ?: return null
        }
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        return when {
            docId.startsWith("raw:") -> docId.removePrefix("raw:")
            docId.startsWith("primary") -> {
                val rel = docId.removePrefix("primary").removePrefix(":")
                if (rel.isEmpty()) storageRoot else "$storageRoot/$rel"
            }
            else -> null
        }
    }
}
