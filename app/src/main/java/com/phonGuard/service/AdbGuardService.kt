package com.phonGuard.service

import android.app.Service
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.phonGuard.PhoneGuardApp
import com.phonGuard.R
import com.phonGuard.core.NotificationHelper
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ADB防火墙服务 - 检测ADB连接状态，防止远程ADB入侵
 */
class AdbGuardService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastAdbEnabled = false
    private var lastWifiIp = ""

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_PHOTO) {
            captureIntruderPhoto()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            PhoneGuardApp.CHANNEL_SERVICE,
            "ADB防火墙已开启",
            "正在监控ADB连接状态"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        // 定期检查ADB状态
        scope.launch {
            while (isActive) {
                try {
                    checkAdbState()
                } catch (e: Exception) {
                    // ignore
                }
                delay(5000) // 每5秒检查一次
            }
        }

        // 监听网络变化
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { checkAdbState() }
                }
                override fun onLost(network: Network) {
                    scope.launch { checkAdbState() }
                }
            })
        } catch (e: Exception) {
            // ignore
        }
    }

    private suspend fun checkAdbState() {
        val config = (application as PhoneGuardApp).configManager
        if (!config.adbGuardEnabled) return

        val currentAdbEnabled = isAdbEnabled()
        val currentWifiIp = getWifiIpAddress()

        if (currentAdbEnabled != lastAdbEnabled) {
            lastAdbEnabled = currentAdbEnabled
            if (currentAdbEnabled) {
                // ADB被开启
                logAdbEvent("adb", "enabled", "检测到ADB调试已开启！")

                NotificationHelper.sendAlertNotification(
                    this,
                    PhoneGuardApp.CHANNEL_ADB_GUARD,
                    "⚠️ ADB调试已开启",
                    "ADB调试功能已被开启，如果非您本人操作请立即检查！",
                    ADB_ALERT_ID
                )

                // 如果开启了拍照取证，自动拍照
                if (config.adbTakePhoto) {
                    captureIntruderPhoto()
                }

                // 如果开启了自动关闭，尝试关闭ADB
                if (config.adbAutoDisable) {
                    disableAdb()
                }
            }
        }

        // 检查WiFi IP变化（无线ADB连接检测）
        if (currentWifiIp.isNotEmpty() && currentWifiIp != lastWifiIp) {
            lastWifiIp = currentWifiIp
            if (currentAdbEnabled) {
                // ADB在WiFi环境下开启，可能存在无线ADB风险
                logAdbEvent("adb", "wifi_adb", "ADB在WiFi环境下开启，IP: $currentWifiIp")
            }
        }
    }

    private fun isAdbEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Global.ADB_ENABLED
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun disableAdb() {
        try {
            // ADB状态无法通过普通应用API修改，需要系统权限
            // 这里只能检测和告警，提醒用户手动关闭
            NotificationHelper.sendAlertNotification(
                this,
                PhoneGuardApp.CHANNEL_ADB_GUARD,
                "🔴 无法自动关闭ADB",
                "请前往「开发者选项」手动关闭USB调试，防止远程入侵！",
                ADB_DISABLE_ALERT_ID
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getWifiIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
        } catch (e: Exception) {
            ""
        }
    }

    private fun captureIntruderPhoto() {
        scope.launch(Dispatchers.IO) {
            var camera: Camera? = null
            try {
                // 先发通知提示正在拍照
                NotificationHelper.sendAlertNotification(
                    this@AdbGuardService,
                    PhoneGuardApp.CHANNEL_ADB_GUARD,
                    "📸 正在拍照取证",
                    "准备使用前置摄像头拍照...",
                    3003
                )

                // 找前置摄像头
                val cameraIndex = findFrontCameraIndex()
                if (cameraIndex < 0) {
                    logAdbEvent("adb", "photo_failed", "未找到前置摄像头")
                    NotificationHelper.sendAlertNotification(
                        this@AdbGuardService,
                        PhoneGuardApp.CHANNEL_ADB_GUARD,
                        "❌ 拍照失败",
                        "未找到前置摄像头",
                        3003
                    )
                    return@launch
                }

                // 打开前置摄像头
                camera = Camera.open(cameraIndex)

                // 设置参数
                val params = camera.parameters
                val pictureSizes = params.supportedPictureSizes
                if (pictureSizes != null && pictureSizes.size > 0) {
                    val bestSize = pictureSizes.minBy { Math.abs(it.width - 640) + Math.abs(it.height - 480) }
                    params.setPictureSize(bestSize.width, bestSize.height)
                }
                // 设置预览尺寸
                val previewSizes = params.supportedPreviewSizes
                if (previewSizes != null && previewSizes.size > 0) {
                    val bestPreview = previewSizes.minBy { Math.abs(it.width - 320) + Math.abs(it.height - 240) }
                    params.setPreviewSize(bestPreview.width, bestPreview.height)
                }
                camera.parameters = params

                // 使用 SurfaceTexture 做虚拟预览（不需要显示）
                val surfaceTexture = SurfaceTexture(0)
                surfaceTexture.setDefaultBufferSize(
                    params.previewSize?.width ?: 320,
                    params.previewSize?.height ?: 240
                )
                camera.setPreviewTexture(surfaceTexture)
                camera.startPreview()

                // 等预览准备好
                delay(500)

                // 准备文件
                val photoDir = File(filesDir, "adb_photos").also { it.mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val photoFile = File(photoDir, "adb_intruder_$timestamp.jpg")

                // 拍照
                val latch = CountDownLatch(1)
                var photoSuccess = false
                camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                    try {
                        if (data != null) {
                            FileOutputStream(photoFile).use { it.write(data) }
                            photoSuccess = true
                            logAdbEvent("adb", "photo_captured", "入侵拍照成功: ${photoFile.name}")
                        }
                    } catch (e: Exception) {
                        logAdbEvent("adb", "photo_failed", "保存照片失败: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                })

                // 等待拍照完成（最长8秒）
                val done = latch.await(8, TimeUnit.SECONDS)
                if (!done) {
                    logAdbEvent("adb", "photo_failed", "拍照超时")
                    NotificationHelper.sendAlertNotification(
                        this@AdbGuardService,
                        PhoneGuardApp.CHANNEL_ADB_GUARD,
                        "❌ 拍照超时",
                        "相机未响应，可能是权限或设备兼容问题",
                        3003
                    )
                } else if (photoSuccess) {
                    NotificationHelper.sendAlertNotification(
                        this@AdbGuardService,
                        PhoneGuardApp.CHANNEL_ADB_GUARD,
                        "✅ 拍照成功",
                        "入侵者照片已保存: ${photoFile.name}",
                        3003
                    )
                }

            } catch (e: Exception) {
                logAdbEvent("adb", "photo_failed", "拍照失败: ${e.message}")
                NotificationHelper.sendAlertNotification(
                    this@AdbGuardService,
                    PhoneGuardApp.CHANNEL_ADB_GUARD,
                    "❌ 拍照失败",
                    "错误: ${e.message}",
                    3003
                )
            } finally {
                try {
                    camera?.stopPreview()
                    camera?.release()
                } catch (_: Exception) { }
            }
        }
    }

    private fun findFrontCameraIndex(): Int {
        try {
            val numberOfCameras = Camera.getNumberOfCameras()
            val cameraInfo = CameraInfo()
            for (i in 0 until numberOfCameras) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    return i
                }
            }
        } catch (_: Exception) { }
        return -1
    }

    private fun logAdbEvent(type: String, subType: String, message: String) {
        try {
            kotlinx.coroutines.runBlocking {
                val logger = (application as PhoneGuardApp).securityLogger
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = type,
                        subType = subType,
                        message = message,
                        severity = 1
                    )
                )
            }
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val ADB_ALERT_ID = 2001
        private const val ADB_DISABLE_ALERT_ID = 2002
        const val ACTION_TEST_PHOTO = "com.phonGuard.action.TEST_PHOTO"
    }
}