package com.phonGuard.simguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.phonGuard.simguard.ui.screens.LogScreen
import com.phonGuard.simguard.ui.screens.SimGuardScreen
import com.phonGuard.simguard.ui.theme.SimGuardTheme

/**
 * SIM卡卫士 - 单功能主界面（主页 / 日志 两态切换）
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果由页面自行处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求通知权限（Android 13+）与电话状态权限
        requestPermissions()

        val openLog = intent?.getBooleanExtra("open_log", false) ?: false

        setContent {
            SimGuardTheme {
                var showLog by remember { mutableStateOf(openLog) }

                if (showLog) {
                    LogScreen(onBack = { showLog = false })
                } else {
                    SimGuardScreen(onOpenLog = { showLog = true })
                }
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.READ_PHONE_STATE
                    )
                )
                return
            }
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        }
    }
}
