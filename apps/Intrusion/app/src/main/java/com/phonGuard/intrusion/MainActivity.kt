package com.phonGuard.intrusion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.phonGuard.intrusion.ui.screens.IntrusionScreen
import com.phonGuard.intrusion.ui.screens.LogScreen
import com.phonGuard.intrusion.ui.theme.IntrusionTheme

/**
 * 入侵卫士 - 单功能主界面（主页 / 日志 两态切换）
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果由页面自行处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        val openLog = intent?.getBooleanExtra("open_log", false) ?: false

        setContent {
            IntrusionTheme {
                var showLog by remember { mutableStateOf(openLog) }

                if (showLog) {
                    LogScreen(onBack = { showLog = false })
                } else {
                    IntrusionScreen(onOpenLog = { showLog = true })
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            }
        }
    }
}
