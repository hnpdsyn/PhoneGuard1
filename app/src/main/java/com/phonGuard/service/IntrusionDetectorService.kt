package com.phonGuard.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.NotificationHelper
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 入侵检测服务 - 监听锁屏失败，触发拍照/定位/警报
 */
class IntrusionDetectorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentFailCount = 0
    private var isLocked = false
    private var lastScreenOffTime = 0L
    private var countedThisScreenOn = false // 防止同一轮亮屏重复计数

    // 锁屏状态广播接收器
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    lastScreenOffTime = System.currentTimeMillis()
                    isLocked = true
                    countedThisScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    // 屏幕亮起，可能有人尝试解锁
                }
                Intent.ACTION_USER_PRESENT -> {
                    // 用户成功解锁，重置计数
                    if (isLocked) {
                        isLocked = false
                        currentFailCount = 0
                        countedThisScreenOn = false
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        registerScreenReceiver()
        startFailCountMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_INTRUSION -> {
                // 手动触发入侵测试
                triggerIntrusionAttempt()
            }
            ACTION_RESET_COUNT -> {
                currentFailCount = 0
                countedThisScreenOn = false
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationHelper.showServiceNotification(
            this,
            PhoneGuardApp.CHANNEL_SERVICE,
            "入侵检测已开启",
            "正在监控异常解锁行为"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun startFailCountMonitor() {
        scope.launch {
            while (isActive) {
                try {
                    if (isLocked && !countedThisScreenOn) {
                        // 屏幕亮起超过3秒未解锁，记为一次失败尝试
                        val screenOnDuration = System.currentTimeMillis() - lastScreenOffTime
                        if (screenOnDuration > 3000) {
                            currentFailCount++
                            countedThisScreenOn = true // 同一轮亮屏只计一次
                            logEvent("intrusion", "attempt", "检测到解锁失败 #$currentFailCount")
                            checkIntrusionThreshold()
                        }
                    }
                    delay(2000)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    /**
     * 手动触发入侵检测（供外部调用）
     */
    fun triggerIntrusionAttempt() {
        scope.launch {
            currentFailCount++
            logEvent("intrusion", "manual_trigger", "手动触发入侵测试 #$currentFailCount")
            checkIntrusionThreshold()
        }
    }

    private suspend fun checkIntrusionThreshold() {
        val config = (application as PhoneGuardApp).configManager
        if (!config.intrusionEnabled) return

        val threshold = config.intrusionThreshold
        if (currentFailCount >= threshold) {
            handleIntrusionDetected()
            currentFailCount = 0 // 触发后重置
            countedThisScreenOn = false
        }
    }

    private suspend fun handleIntrusionDetected() {
        val config = (application as PhoneGuardApp).configManager
        logEvent("intrusion", "detected", "检测到入侵行为！连续失败次数: $currentFailCount")

        // 1. 拍照取证
        if (config.intrusionTakePhoto) {
            captureIntruderPhoto()
        }

        // 2. 获取位置
        var locationInfo: String? = null
        if (config.intrusionTrackLocation) {
            locationInfo = getCurrentLocation()
        }

        // 3. 播放警报
        if (config.intrusionPlayAlarm) {
            playAlarmSound()
        }

        // 4. 发送通知（含位置信息）
        val locationMsg = if (locationInfo != null) "\n📍 $locationInfo" else ""
        NotificationHelper.sendAlertNotification(
            this,
            PhoneGuardApp.CHANNEL_INTRUSION,
            "🚨 入侵检测告警！",
            "检测到多次解锁失败，已拍照取证！$locationMsg",
            INTRUSION_ALERT_ID
        )

        // 5. 记录日志
        logEvent("intrusion", "alert_sent", "入侵告警通知已发送$locationMsg")
    }

    private suspend fun captureIntruderPhoto() {
        withContext(Dispatchers.IO) {
            var camera: Camera? = null
            try {
                // 找前置摄像头
                val cameraIndex = findFrontCameraIndex()
                if (cameraIndex < 0) {
                    logEvent("intrusion", "photo_failed", "未找到前置摄像头")
                    return@withContext
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
                val previewSizes = params.supportedPreviewSizes
                if (previewSizes != null && previewSizes.size > 0) {
                    val bestPreview = previewSizes.minBy { Math.abs(it.width - 320) + Math.abs(it.height - 240) }
                    params.setPreviewSize(bestPreview.width, bestPreview.height)
                }
                camera.parameters = params

                // 虚拟预览
                val surfaceTexture = SurfaceTexture(0)
                surfaceTexture.setDefaultBufferSize(
                    params.previewSize?.width ?: 320,
                    params.previewSize?.height ?: 240
                )
                camera.setPreviewTexture(surfaceTexture)
                camera.startPreview()

                delay(500)

                // 保存照片
                val photoDir = File(filesDir, "photos").also { it.mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val photoFile = File(photoDir, "intruder_$timestamp.jpg")

                val latch = CountDownLatch(1)
                var photoSuccess = false
                camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                    try {
                        if (data != null) {
                            FileOutputStream(photoFile).use { it.write(data) }
                            photoSuccess = true
                            logEvent("intrusion", "photo_captured", "入侵拍照成功: ${photoFile.name}")
                        }
                    } catch (e: Exception) {
                        logEvent("intrusion", "photo_failed", "保存照片失败: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                })

                val done = latch.await(8, TimeUnit.SECONDS)
                if (photoSuccess) {
                    NotificationHelper.sendAlertNotification(
                        this@IntrusionDetectorService,
                        PhoneGuardApp.CHANNEL_INTRUSION,
                        "📸 入侵拍照成功",
                        "照片已保存: ${photoFile.name}",
                        3004
                    )
                }

            } catch (e: Exception) {
                logEvent("intrusion", "photo_failed", "拍照失败: ${e.message}")
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

    private suspend fun getCurrentLocation(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (location != null) {
                    val locStr = "${location.latitude}, ${location.longitude}"
                    logEvent("intrusion", "location", "已获取位置: $locStr")
                    locStr
                } else {
                    logEvent("intrusion", "location", "无法获取位置信息")
                    "无法获取位置"
                }
            } catch (e: SecurityException) {
                logEvent("intrusion", "location", "位置权限未授予")
                "权限不足"
            }
        }
    }

    private fun playAlarmSound() {
        try {
            // 震动
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 200, 500, 200, 500),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }

            // 播放系统警报音
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri != null) {
                val ringtone = RingtoneManager.getRingtone(this, alarmUri)
                ringtone?.play()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun logEvent(type: String, subType: String, message: String) {
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
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val INTRUSION_ALERT_ID = 4001
        const val ACTION_TEST_INTRUSION = "com.phonGuard.action.TEST_INTRUSION"
        const val ACTION_RESET_COUNT = "com.phonGuard.action.RESET_INTRUSION_COUNT"
    }
}