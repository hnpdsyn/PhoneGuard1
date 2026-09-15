package com.phonGuard.adbguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.phonGuard.adbguard.ui.screens.AdbGuardScreen
import com.phonGuard.adbguard.ui.screens.AdbPhotosScreen
import com.phonGuard.adbguard.ui.screens.LogScreen
import com.phonGuard.adbguard.ui.screens.TimelineScreen
import com.phonGuard.adbguard.ui.theme.AdbGuardTheme

/**
 * ADB防火墙 - 单功能主界面（主页 / 日志 / 照片 / 时间线 四态切换）
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
            AdbGuardTheme {
                var showLog by remember { mutableStateOf(openLog) }
                var showPhotos by remember { mutableStateOf(false) }
                var showTimeline by remember { mutableStateOf(false) }

                when {
                    showPhotos -> AdbPhotosScreen(onBack = { showPhotos = false })
                    showTimeline -> TimelineScreen(onBack = { showTimeline = false })
                    showLog -> LogScreen(onBack = { showLog = false })
                    else -> AdbGuardScreen(
                        onOpenLog = { showLog = true },
                        onOpenPhotos = { showPhotos = true },
                        onOpenTimeline = { showTimeline = true }
                    )
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
