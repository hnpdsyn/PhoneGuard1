package com.phonGuard.adbguard.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import com.phonGuard.adbguard.AdbGuardApp
import com.phonGuard.adbguard.core.ConfigManager
import com.phonGuard.adbguard.core.NotificationHelper
import com.phonGuard.adbguard.core.SecurityLogger
import com.phonGuard.adbguard.core.UsbAlertHelper
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ADB防火墙服务 - 检测ADB连接状态，防止远程ADB入侵
 *
 * v1.1 监控能力：
 * - ADB 开关状态（5秒轮询）
 * - WiFi IP 变化（无线ADB风险检测）
 * - USB 插拔实时广播（运行时注册，配合 Manifest 静态注册双通道，UsbAlertHelper 防抖）
 * - 无线调试开关（Android 11+，5秒轮询）
 * - 开发者选项开关（5秒轮询）
 * - 前置摄像头连拍2张取证（含时间水印）
 * - TTS 语音告警（按需 lazy 初始化，失败静默降级）
 */
class AdbGuardService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastAdbEnabled = false
    private var lastWifiIp = ""
    private var lastWirelessAdbEnabled = false
    private var lastDevOptionsEnabled = false

    // ===== USB 插拔实时监听（运行时注册，服务存活时实时响应） =====
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isNew = try {
                UsbAlertHelper.handleUsbEvent(
                    applicationContext,
                    intent.action,
                    UsbAlertHelper.deviceNameOf(intent)
                )
            } catch (_: Exception) {
                false
            }
            if (isNew) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> speakAlert("警告，检测到USB设备接入")
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> speakAlert("USB设备已断开")
                }
            }
        }
    }

    // ===== TTS 语音告警（按需 lazy 初始化，shutdown 时释放） =====
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startMonitoring()
        initTtsIfNeeded()
        registerUsbReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_PHOTO ||
            intent?.action == UsbAlertHelper.ACTION_USB_PHOTO
        ) {
            captureIntruderPhoto()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            AdbGuardApp.CHANNEL_SERVICE,
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
        val config = (application as AdbGuardApp).configManager
        if (!config.adbGuardEnabled) return

        val currentAdbEnabled = isAdbEnabled()
        val currentWifiIp = if (config.adbWifiMonitor) getWifiIpAddress() else ""

        if (currentAdbEnabled != lastAdbEnabled) {
            lastAdbEnabled = currentAdbEnabled
            if (currentAdbEnabled) {
                // ADB被开启
                logAdbEvent("adb", "enabled", "检测到ADB调试已开启！")

                NotificationHelper.sendAlertNotification(
                    this,
                    AdbGuardApp.CHANNEL_ADB_GUARD,
                    "⚠️ ADB调试已开启",
                    "ADB调试功能已被开启，如果非您本人操作请立即检查！",
                    ADB_ALERT_ID
                )

                // TTS 语音播报
                speakAlert("警告，检测到USB调试开启")

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

        // 无线调试检测（Android 11+）
        checkWirelessAdbState(config)

        // 开发者选项开关监控
        checkDevOptionsState(config)
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

    /**
     * 读取无线调试开关（API 30+）
     * Settings.Global.ADB_WIFI_ENABLED 为 hidden 常量，实际键值为 "adb_wifi_enabled"
     */
    private fun isWirelessAdbEnabled(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null // API 30 以下自动跳过
        return try {
            Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (e: Exception) {
            null
        }
    }

    /** 无线调试从关变开时告警（+可选拍照） */
    private fun checkWirelessAdbState(config: ConfigManager) {
        val current = isWirelessAdbEnabled() ?: return
        if (current == lastWirelessAdbEnabled) return
        lastWirelessAdbEnabled = current
        if (!current || !config.wirelessAdbAlertEnabled) return

        logAdbEvent("adb", "wireless_enabled", "检测到无线调试（无线ADB）已开启！")
        NotificationHelper.sendAlertNotification(
            this,
            AdbGuardApp.CHANNEL_ADB_GUARD,
            "📶 无线调试已开启",
            "无线调试（ADB over WiFi）已被开启，同一网络内的攻击者可无线连接您的手机！",
            WIRELESS_ALERT_ID
        )
        speakAlert("警告，检测到无线调试开启")
        if (config.adbTakePhoto) {
            captureIntruderPhoto()
        }
    }

    private fun isDevOptionsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /** 开发者选项从关变开时告警 */
    private fun checkDevOptionsState(config: ConfigManager) {
        if (!config.devOptionsAlertEnabled) return
        val current = isDevOptionsEnabled()
        if (current == lastDevOptionsEnabled) return
        lastDevOptionsEnabled = current
        if (!current) return

        logAdbEvent("adb", "dev_options_enabled", "检测到开发者选项已被开启！")
        NotificationHelper.sendAlertNotification(
            this,
            AdbGuardApp.CHANNEL_ADB_GUARD,
            "🛠️ 开发者选项已开启",
            "开发者选项已被开启，其中包含USB调试等高危开关，请立即检查！",
            DEV_OPTIONS_ALERT_ID
        )
        speakAlert("警告，检测到开发者选项开启")
    }

    private fun disableAdb() {
        try {
            // ADB状态无法通过普通应用API修改，需要系统权限
            // 这里只能检测和告警，提醒用户手动关闭
            NotificationHelper.sendAlertNotification(
                this,
                AdbGuardApp.CHANNEL_ADB_GUARD,
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

    // ===== TTS 语音告警 =====

    /** 按需 lazy 初始化 TTS（仅当开关开启时创建；失败静默降级，不影响通知） */
    private fun initTtsIfNeeded() {
        if (tts != null) return
        val config = try {
            (application as? AdbGuardApp)?.configManager ?: return
        } catch (_: Exception) {
            return
        }
        if (!config.ttsAlertEnabled) return
        try {
            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    try {
                        tts?.language = Locale.CHINA
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) {
            tts = null
            ttsReady = false
        }
    }

    /** 语音播报警告；TTS 未就绪/失败时静默降级 */
    private fun speakAlert(text: String) {
        try {
            val config = (application as? AdbGuardApp)?.configManager ?: return
            if (!config.ttsAlertEnabled) return
            initTtsIfNeeded()
            if (ttsReady) {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "adbguard_alert")
            }
        } catch (_: Exception) { }
    }

    // ===== USB 插拔运行时接收器 =====

    private fun registerUsbReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // ACTION_USB_* 为系统受保护广播，仅系统可发送，RECEIVER_EXPORTED 无伪造风险
                registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(usbReceiver, filter)
            }
        } catch (_: Exception) { }
    }

    // ===== 拍照取证（连拍2张 + 时间水印） =====

    private fun captureIntruderPhoto() {
        scope.launch(Dispatchers.IO) {
            var camera: Camera? = null
            try {
                // 先发通知提示正在拍照
                NotificationHelper.sendAlertNotification(
                    this@AdbGuardService,
                    AdbGuardApp.CHANNEL_ADB_GUARD,
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
                        AdbGuardApp.CHANNEL_ADB_GUARD,
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

                val config = (application as AdbGuardApp).configManager
                val photoDir = File(filesDir, "adb_photos").also { it.mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

                // 连拍 2 张（第二张留档防眨眼/遮挡）
                val shots = mutableListOf<ByteArray>()
                takeSingleShot(camera)?.let { shots.add(it) }
                delay(800)
                try {
                    // takePicture 后预览会停止，重启预览后拍摄第二张
                    camera.startPreview()
                    delay(400)
                    takeSingleShot(camera)?.let { shots.add(it) }
                } catch (e: Exception) {
                    logAdbEvent("adb", "photo_failed", "第二张连拍失败: ${e.message}")
                }

                if (shots.isEmpty()) {
                    logAdbEvent("adb", "photo_failed", "拍照超时")
                    NotificationHelper.sendAlertNotification(
                        this@AdbGuardService,
                        AdbGuardApp.CHANNEL_ADB_GUARD,
                        "❌ 拍照超时",
                        "相机未响应，可能是权限或设备兼容问题",
                        3003
                    )
                    return@launch
                }

                // 保存照片（水印处理在 IO 线程完成）
                var savedCount = 0
                shots.forEachIndexed { index, data ->
                    val photoFile = File(photoDir, "adb_intruder_${stamp}_${index + 1}.jpg")
                    if (saveShotWithWatermark(data, photoFile, config.watermarkEnabled)) {
                        savedCount++
                        logAdbEvent("adb", "photo_captured", "入侵拍照成功(#${index + 1}): ${photoFile.name}")
                    } else {
                        logAdbEvent("adb", "photo_failed", "保存照片失败(#${index + 1})")
                    }
                }

                if (savedCount > 0) {
                    NotificationHelper.sendAlertNotification(
                        this@AdbGuardService,
                        AdbGuardApp.CHANNEL_ADB_GUARD,
                        "✅ 拍照成功",
                        "入侵者照片已保存（共 $savedCount 张）",
                        3003
                    )
                }

            } catch (e: Exception) {
                logAdbEvent("adb", "photo_failed", "拍照失败: ${e.message}")
                NotificationHelper.sendAlertNotification(
                    this@AdbGuardService,
                    AdbGuardApp.CHANNEL_ADB_GUARD,
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

    /** 单张拍摄，返回 JPEG 数据（最长等待8秒，超时返回 null） */
    private suspend fun takeSingleShot(camera: Camera): ByteArray? {
        val latch = CountDownLatch(1)
        var data: ByteArray? = null
        camera.takePicture(null, null, Camera.PictureCallback { d, _ ->
            data = d
            latch.countDown()
        })
        latch.await(8, TimeUnit.SECONDS)
        return data
    }

    /** 保存照片；开启水印时在 Bitmap 叠加时间水印后存为 JPEG */
    private fun saveShotWithWatermark(data: ByteArray, file: File, watermarkEnabled: Boolean): Boolean {
        return try {
            if (watermarkEnabled) {
                val opts = BitmapFactory.Options().apply { inMutable = true }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                if (bitmap == null) {
                    FileOutputStream(file).use { it.write(data) }
                } else {
                    drawWatermark(bitmap)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    bitmap.recycle()
                }
            } else {
                FileOutputStream(file).use { it.write(data) }
            }
            true
        } catch (e: Exception) {
            logAdbEvent("adb", "photo_failed", "保存照片失败: ${e.message}")
            false
        }
    }

    /** 左下角叠加白色半透明底条 + 黑字：拍摄时间（yyyy-MM-dd HH:mm:ss）与 ADB ALERT 标记 */
    private fun drawWatermark(bitmap: Bitmap) {
        try {
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val alertText = "ADB ALERT"
            val canvas = Canvas(bitmap)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (bitmap.width / 22f).coerceAtLeast(18f)
                isFakeBoldText = true
            }
            val pad = textPaint.textSize * 0.45f
            val lineHeight = textPaint.textSize * 1.2f
            val bgPaint = Paint().apply { color = Color.argb(160, 255, 255, 255) }
            val bgWidth = maxOf(textPaint.measureText(timeText), textPaint.measureText(alertText)) + pad * 2
            val bgHeight = lineHeight * 2 + pad
            val left = 0f
            val top = bitmap.height - bgHeight
            canvas.drawRect(left, top, left + bgWidth, bitmap.height.toFloat(), bgPaint)
            canvas.drawText(timeText, pad, top + pad / 2 + textPaint.textSize, textPaint)
            canvas.drawText(alertText, pad, top + pad / 2 + textPaint.textSize + lineHeight, textPaint)
        } catch (_: Exception) { }
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

    private fun logAdbEvent(type: String, subType: String, message: String, severity: Int = 1) {
        try {
            kotlinx.coroutines.runBlocking {
                val logger = (application as AdbGuardApp).securityLogger
                logger.logEvent(
                    SecurityLogger.SecurityEvent(
                        type = type,
                        subType = subType,
                        message = message,
                        severity = severity
                    )
                )
            }
        } catch (_: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 注销运行时注册的 USB 接收器
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) { }
        // 释放 TTS 资源
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { }
        tts = null
        ttsReady = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val ADB_ALERT_ID = 2001
        private const val ADB_DISABLE_ALERT_ID = 2002
        private const val WIRELESS_ALERT_ID = 2005
        private const val DEV_OPTIONS_ALERT_ID = 2006
        const val ACTION_TEST_PHOTO = "com.phonGuard.adbguard.action.TEST_PHOTO"
    }
}
