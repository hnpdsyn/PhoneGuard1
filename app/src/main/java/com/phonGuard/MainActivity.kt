package com.phonGuard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.phonGuard.core.PermissionManager
import com.phonGuard.service.*
import com.phonGuard.ui.screens.*
import com.phonGuard.ui.theme.PhoneGuardTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 权限结果由各页面自行处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        setContent {
            PhoneGuardTheme {
                var selectedTab by remember { mutableStateOf(0) }
                var showPermissionDialog by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                label = { Text("首页") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                label = { Text("应用锁") },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                                label = { Text("ADB") },
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                                label = { Text("入侵") },
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("更多") },
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> HomeScreen(
                                onStartService = { startAllServices() },
                                onStopService = { stopAllServices() }
                            )
                            1 -> AppLockScreen()
                            2 -> AdbGuardScreen()
                            3 -> IntrusionScreen()
                            4 -> MoreScreen(
                                onNavigateToSim = { selectedTab = 5 },
                                onNavigateToLogin = { selectedTab = 6 },
                                onNavigateToLog = { selectedTab = 7 },
                                onNavigateToSettings = { selectedTab = 8 },
                                onNavigateToAdbPhotos = { selectedTab = 9 }
                            )
                            5 -> SimGuardScreen(onBack = { selectedTab = 4 })
                            6 -> LoginGuardScreen(onBack = { selectedTab = 4 })
                            7 -> LogScreen(onBack = { selectedTab = 4 })
                            8 -> SettingsScreen(onBack = { selectedTab = 4 })
                            9 -> AdbPhotosScreen(onBack = { selectedTab = 4 })
                        }
                    }
                }
            }
        }
    }

    private fun startAllServices() {
        startForegroundService(Intent(this, AppLockService::class.java))
        startForegroundService(Intent(this, AdbGuardService::class.java))
        startForegroundService(Intent(this, IntrusionDetectorService::class.java))
        startForegroundService(Intent(this, SimGuardService::class.java))
        startForegroundService(Intent(this, LoginGuardService::class.java))

        val app = application as PhoneGuardApp
        app.configManager.allServicesEnabled = true
    }

    private fun stopAllServices() {
        stopService(Intent(this, AppLockService::class.java))
        stopService(Intent(this, AdbGuardService::class.java))
        stopService(Intent(this, IntrusionDetectorService::class.java))
        stopService(Intent(this, SimGuardService::class.java))
        stopService(Intent(this, LoginGuardService::class.java))

        val app = application as PhoneGuardApp
        app.configManager.allServicesEnabled = false
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

// 导航图标定义
data class NavItem(
    val title: String,
    val icon: ImageVector,
    val screen: String
)