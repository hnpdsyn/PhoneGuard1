@file:Suppress("DEPRECATION")

package com.phonGuard.loginguard.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.Camera
import androidx.core.content.ContextCompat
import com.phonGuard.loginguard.LoginGuardApp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 静默取证拍照（v1.1 新增）
 *
 * 使用旧版 android.hardware.Camera API（高兼容方案，targetSdk 34 上仍可用），
 * 后台无预览 SurfaceTexture 方案（不需要真实显示界面）：
 * - 在 receiver 回调中开子线程执行，绝不阻塞系统回调主线程
 * - Camera 在 finally 中 release，确保任何异常路径都会释放相机
 * - 无摄像头设备 / CAMERA 权限被拒 / 相机被占用等一律静默降级，绝不崩溃
 * - 照片保存到应用私有目录 Pictures/ 下，文件名带时间戳
 * - 成功后写入 SecurityLogger 时间线 + 发送告警通知（点击跳转账证照片列表）
 */
object PhotoCapture {

    /** 防止并发重复开相机（receiver 可能被高频触发） */
    private val capturing = AtomicBoolean(false)

    /** 取证告警通知 ID（点击跳转账证照片页） */
    private const val PHOTO_ALERT_ID = 6003

    /**
     * 拍摄一张前置取证照片（异步子线程执行，调用方立即返回）
     */
    fun captureIntruderPhoto(context: Context) {
        val appContext = context.applicationContext
        if (!capturing.compareAndSet(false, true)) return  // 已在拍照中，跳过本次
        Thread {
            var camera: Camera? = null
            try {
                // 运行时权限检查：CAMERA 未授权则静默降级（不崩溃）
                if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    logEvent(appContext, "photo_failed", "拍照取证跳过：CAMERA 权限未授予")
                    return@Thread
                }

                // 定位前置摄像头（无摄像头设备静默降级）
                val cameraIndex = findFrontCameraIndex()
                if (cameraIndex < 0) {
                    logEvent(appContext, "photo_failed", "拍照取证失败：未找到前置摄像头")
                    return@Thread
                }

                camera = Camera.open(cameraIndex)

                // 选择最小的可用拍摄尺寸（取证够用，省内存省电）
                val params = camera.parameters
                val pictureSizes = params.supportedPictureSizes
                if (pictureSizes != null && pictureSizes.isNotEmpty()) {
                    val size = pictureSizes.minBy { it.width * it.height }
                    params.setPictureSize(size.width, size.height)
                }
                val previewSizes = params.supportedPreviewSizes
                if (previewSizes != null && previewSizes.isNotEmpty()) {
                    val preview = previewSizes.minBy { it.width * it.height }
                    params.setPreviewSize(preview.width, preview.height)
                }
                camera.parameters = params

                // 无预览界面方案：用 SurfaceTexture 提供虚拟预览目标（不需要真实显示）
                val surfaceTexture = SurfaceTexture(0)
                surfaceTexture.setDefaultBufferSize(
                    params.previewSize?.width ?: 320,
                    params.previewSize?.height ?: 240
                )
                camera.setPreviewTexture(surfaceTexture)
                camera.startPreview()
                Thread.sleep(600)  // 等待预览稳定，避免拍到黑帧

                // 拍摄（最长等待 8 秒，超时放弃）
                val data = takeShot(camera)
                if (data == null) {
                    logEvent(appContext, "photo_failed", "拍照取证失败：相机未响应（超时）")
                    return@Thread
                }

                // 保存到应用私有目录 Pictures/，文件名带时间戳
                val photoDir = File(appContext.filesDir, "Pictures").also { it.mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val photoFile = File(photoDir, "login_intruder_$stamp.jpg")

                if (savePhoto(data, photoFile)) {
                    logEvent(appContext, "photo_captured", "📸 拍照取证成功：${photoFile.name}")
                    // 取证告警通知：点击跳转取证照片列表界面
                    NotificationHelper.sendAlertNotification(
                        appContext,
                        LoginGuardApp.CHANNEL_LOGIN_GUARD,
                        "📸 拍照取证完成",
                        "检测到连续密码输错，已拍摄前置取证照片",
                        PHOTO_ALERT_ID,
                        "gallery"
                    )
                } else {
                    logEvent(appContext, "photo_failed", "拍照取证失败：照片保存失败")
                }
            } catch (_: Throwable) {
                // 任何相机异常（设备不支持/被占用等）静默降级，保证 receiver 不崩溃
                try {
                    logEvent(appContext, "photo_failed", "拍照取证异常：设备不支持或相机被占用")
                } catch (_: Throwable) { }
            } finally {
                // Camera 必须释放，确保后续可以再次打开
                try {
                    camera?.stopPreview()
                } catch (_: Throwable) { }
                try {
                    camera?.release()
                } catch (_: Throwable) { }
                capturing.set(false)
            }
        }.start()
    }

    /** 单张拍摄，返回 JPEG 数据；超过 8 秒未回调返回 null */
    private fun takeShot(camera: Camera): ByteArray? {
        val latch = CountDownLatch(1)
        var data: ByteArray? = null
        camera.takePicture(null, null, Camera.PictureCallback { d, _ ->
            data = d
            latch.countDown()
        })
        latch.await(8, TimeUnit.SECONDS)
        return data
    }

    /** 照片叠加时间水印后保存为 JPEG（保存失败返回 false） */
    private fun savePhoto(data: ByteArray, file: File): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inMutable = true }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            if (bitmap == null) {
                // 解码失败时直接保存原始数据
                FileOutputStream(file).use { it.write(data) }
            } else {
                drawWatermark(bitmap)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 左下角叠加"LOGIN ALERT + 拍摄时间"水印，便于事后举证 */
    private fun drawWatermark(bitmap: Bitmap) {
        try {
            val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val alertText = "LOGIN ALERT"
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
        } catch (_: Throwable) { }
    }

    /** 定位前置摄像头索引；不存在返回 -1 */
    private fun findFrontCameraIndex(): Int {
        return try {
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
            }
            -1
        } catch (_: Throwable) {
            -1
        }
    }

    /** 取证事件写入 SecurityLogger 时间线（安全日志引擎） */
    private fun logEvent(context: Context, subType: String, message: String) {
        try {
            val logger = (context.applicationContext as? LoginGuardApp)?.securityLogger ?: return
            logger.logEvent(
                SecurityLogger.SecurityEvent(
                    type = "login",
                    subType = subType,
                    message = message,
                    severity = if (subType == "photo_captured") 2 else 1
                )
            )
        } catch (_: Throwable) { }
    }
}
