package com.phonGuard.loginguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.phonGuard.loginguard.service.LoginGuardService
import com.phonGuard.loginguard.ui.screens.GalleryScreen
import com.phonGuard.loginguard.ui.screens.LogScreen
import com.phonGuard.loginguard.ui.screens.LoginGuardScreen
import com.phonGuard.loginguard.ui.screens.TimelineScreen
import com.phonGuard.loginguard.ui.theme.LoginGuardTheme

/**
 * 登录保护 - 单功能主界面（主页 / 日志 / 时间线 / 取证照片 四态切换）
 * v1.1：新增事件时间线与取证照片页；告警通知通过 open_screen extra 指定落地页
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果由页面自行处理 */ }

    /** 当前页面状态（activity 字段，便于 onNewIntent 时响应通知跳转） */
    private val screenState = mutableStateOf(SCREEN_HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求运行时权限：通知（Android 13+）+ 相机（取证拍照用）
        requestRequiredPermissions()

        // 首次进入根据通知携带的 extra 决定落地页
        screenState.value = resolveScreen(intent)

        setContent {
            LoginGuardTheme {
                when (screenState.value) {
                    SCREEN_LOG -> LogScreen(onBack = { screenState.value = SCREEN_HOME })
                    SCREEN_TIMELINE -> TimelineScreen(
                        onBack = { screenState.value = SCREEN_HOME },
                        onOpenGallery = { screenState.value = SCREEN_GALLERY }
                    )
                    SCREEN_GALLERY -> GalleryScreen(onBack = { screenState.value = SCREEN_HOME })
                    else -> LoginGuardScreen(
                        onOpenLog = { screenState.value = SCREEN_LOG },
                        onOpenTimeline = { screenState.value = SCREEN_TIMELINE },
                        onOpenGallery = { screenState.value = SCREEN_GALLERY }
                    )
                }
            }
        }
    }

    /**
     * singleTask 模式下应用已在前台时，点击通知走 onNewIntent，
     * 这里同步更新落地页，保证"点击取证通知 → 打开取证照片页"始终生效
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screenState.value = resolveScreen(intent)
    }

    /** 解析意图中的目标页面 extra（open_screen：log / timeline / gallery） */
    private fun resolveScreen(intent: Intent?): String {
        val screen = intent?.getStringExtra("open_screen") ?: return SCREEN_HOME
        return when (screen) {
            SCREEN_LOG, SCREEN_TIMELINE, SCREEN_GALLERY -> screen
            else -> SCREEN_HOME
        }
    }

    /** 一次性申请运行时权限：通知（13+）+ 相机（取证拍照） */
    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 过滤已授予的权限
        permissions.removeAll {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    companion object {
        private const val SCREEN_HOME = "home"
        private const val SCREEN_LOG = "log"
        private const val SCREEN_TIMELINE = "timeline"
        private const val SCREEN_GALLERY = "gallery"
    }
}
