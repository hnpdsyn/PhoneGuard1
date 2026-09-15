package com.phonGuard.securitylog

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import com.phonGuard.securitylog.ui.screens.LogScreen
import com.phonGuard.securitylog.ui.theme.SecurityLogTheme
import java.io.File
import kotlinx.coroutines.*

/**
 * 安全日志 - 独立的事件记录与导出工具（查看/筛选/导出）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SecurityLogTheme {
                LogScreen(
                    onShareExport = { file -> shareFile(file) }
                )
            }
        }
    }

    /** 通过系统分享面板导出日志文件 */
    private fun shareFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "导出安全日志"))
        } catch (_: Exception) { }
    }
}
