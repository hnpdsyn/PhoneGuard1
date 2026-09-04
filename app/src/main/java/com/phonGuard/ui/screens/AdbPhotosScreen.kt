package com.phonGuard.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.phonGuard.PhoneGuardApp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPhotosScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = PhoneGuardApp.instance
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<File?>(null) }
    var sourceLabel by remember { mutableStateOf("") }

    // 加载照片列表（同时从adb_photos/和lock_photos/读取）
    LaunchedEffect(Unit) {
        val allPhotos = mutableListOf<File>()

        // ADB入侵拍照
        val adbDir = File(app.filesDir, "adb_photos")
        if (adbDir.exists()) {
            allPhotos.addAll(
                adbDir.listFiles()
                    ?.filter { it.name.endsWith(".jpg") || it.name.endsWith(".png") }
                    ?: emptyList()
            )
        }

        // 应用锁拍照
        val lockDir = File(app.filesDir, "lock_photos")
        if (lockDir.exists()) {
            allPhotos.addAll(
                lockDir.listFiles()
                    ?.filter { it.name.endsWith(".jpg") || it.name.endsWith(".png") }
                    ?: emptyList()
            )
        }

        photos = allPhotos.sortedByDescending { it.lastModified() }
        sourceLabel = "共 ${photos.size} 张"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("入侵照片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectedPhoto != null) {
                        IconButton(onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    selectedPhoto!!
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享照片"))
                            } catch (_: Exception) { }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        IconButton(onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    selectedPhoto!!
                                )
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/jpeg")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(viewIntent)
                            } catch (_: Exception) { }
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "打开")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedPhoto != null) {
            // 全屏预览模式
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(selectedPhoto) {
                    try {
                        BitmapFactory.decodeFile(selectedPhoto?.absolutePath)
                    } catch (_: Exception) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "照片预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
                // 点击空白返回列表
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(onClick = { selectedPhoto = null }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭预览",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            // 照片列表
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (photos.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "暂无入侵照片",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    "ADB调试或应用锁密码错误时会自动拍照",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // 来源标记行
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            sourceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(photos, key = { it.absolutePath }) { photo ->
                        val isLockPhoto = photo.parentFile?.name == "lock_photos"
                        Card(
                            onClick = { selectedPhoto = photo },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val bitmap = remember(photo) {
                                    try {
                                        val opts = BitmapFactory.Options().apply {
                                            inSampleSize = 4 // 缩略图
                                        }
                                        BitmapFactory.decodeFile(photo.absolutePath, opts)
                                    } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = photo.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // 来源标记
                                if (isLockPhoto) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            "锁",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}