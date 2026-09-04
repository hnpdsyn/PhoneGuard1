package com.phonGuard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import kotlin.concurrent.thread
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.phonGuard.PhoneGuardApp
import com.phonGuard.service.AppLockAccessibilityService
import com.phonGuard.ui.theme.PhoneGuardTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 应用锁验证Activity - 透明主题，覆盖在应用上方
 * 密码错误时自动用前置摄像头拍照取证
 */
class LockVerifyActivity : ComponentActivity() {

    private var targetPackage: String = ""
    private lateinit var photoDir: File

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            capturePhotoInternal()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra("target_package") ?: ""
        photoDir = File(filesDir, "lock_photos").also { it.mkdirs() }

        setContent {
            PhoneGuardTheme {
                LockVerifyScreen(
                    onUnlock = {
                        AppLockAccessibilityService.markUnlocked(targetPackage)
                        finish()
                    },
                    onCancel = { finish() },
                    onPasswordError = { capturePhoto() },
                    targetPackage = getAppName(targetPackage)
                )
            }
        }
    }

    /** 密码错误时触发拍照（检查权限后执行） */
    private fun capturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        capturePhotoInternal()
    }

    /** 实际拍照逻辑 - 使用旧Camera API，前置摄像头 */
    private fun capturePhotoInternal() {
        thread {
            var camera: Camera? = null
            try {
                val cameraIndex = findFrontCameraIndex()
                if (cameraIndex < 0) return@thread

                camera = Camera.open(cameraIndex)
                val params = camera.parameters

                // 设置拍照尺寸
                val pictureSizes = params.supportedPictureSizes
                if (pictureSizes != null && pictureSizes.size > 0) {
                    val bestSize = pictureSizes.minBy {
                        Math.abs(it.width - 640) + Math.abs(it.height - 480)
                    }
                    params.setPictureSize(bestSize.width, bestSize.height)
                }
                // 设置预览尺寸
                val previewSizes = params.supportedPreviewSizes
                if (previewSizes != null && previewSizes.size > 0) {
                    val bestPreview = previewSizes.minBy {
                        Math.abs(it.width - 320) + Math.abs(it.height - 240)
                    }
                    params.setPreviewSize(bestPreview.width, bestPreview.height)
                }
                camera.parameters = params

                // 虚拟预览
                val surfaceTexture = SurfaceTexture(0)
                surfaceTexture.setDefaultBufferSize(320, 240)
                camera.setPreviewTexture(surfaceTexture)
                camera.startPreview()

                Thread.sleep(500)

                // 保存照片
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val photoFile = File(photoDir, "lock_attempt_$timestamp.jpg")

                val latch = CountDownLatch(1)
                camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                    try {
                        if (data != null) {
                            FileOutputStream(photoFile).use { it.write(data) }
                        }
                    } catch (_: Exception) { }
                    finally { latch.countDown() }
                })

                latch.await(8, TimeUnit.SECONDS)

            } catch (_: Exception) { }
            finally {
                try {
                    camera?.stopPreview()
                    camera?.release()
                } catch (_: Exception) { }
            }
        }
    }

    /** 查找前置摄像头索引 */
    private fun findFrontCameraIndex(): Int {
        return try {
            val numberOfCameras = Camera.getNumberOfCameras()
            val cameraInfo = CameraInfo()
            for (i in 0 until numberOfCameras) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    return i
                }
            }
            -1
        } catch (_: Exception) { -1 }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

@Composable
fun LockVerifyScreen(
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
    onPasswordError: () -> Unit = {},
    targetPackage: String
) {
    val app = PhoneGuardApp.instance
    val config = app.configManager
    var pinInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "应用已锁定",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "请输入主密码以访问 $targetPackage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN输入
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter { c -> c.isDigit() }.take(6)
                        error = ""
                    },
                    label = { Text("主密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) {
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (pinInput == config.masterPin) {
                            onUnlock()
                        } else {
                            attempts++
                            // 密码错误，拍照取证
                            onPasswordError()
                            error = if (attempts >= 5) {
                                "尝试次数过多，请稍后再试"
                            } else {
                                "密码错误，还剩 ${5 - attempts} 次机会"
                            }
                            pinInput = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = pinInput.length >= 4
                ) {
                    Text("解锁", fontSize = 16.sp)
                }

                if (attempts < 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onCancel) {
                        Text("返回主页")
                    }
                }
            }
        }
    }
}