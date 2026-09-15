package com.phonGuard.loginguard.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.loginguard.LoginGuardApp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 取证照片查看页（v1.1 新增）
 * 从应用私有目录 Pictures/ 读取前置摄像头取证照片，按时间倒序展示，
 * 支持点击放大预览与删除单张（带确认对话框）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val app = LoginGuardApp.instance
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var previewPhoto by remember { mutableStateOf<File?>(null) }

    // 从私有目录 Pictures/ 重新加载照片列表（按拍摄时间倒序）
    fun reload() {
        val dir = File(app.filesDir, "Pictures")
        photos = dir.listFiles()
            ?.filter { it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        TopAppBar(
            title = { Text("取证照片") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无取证照片", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "连续密码输错达到拍照阈值后，取证照片将保存在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            Text(
                "共 ${photos.size} 张（按时间倒序）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.absolutePath }) { photo ->
                    PhotoItem(
                        photo = photo,
                        onClick = { previewPhoto = photo },
                        onDelete = { pendingDelete = photo }
                    )
                }
            }
        }
    }

    // 大图预览对话框
    previewPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { previewPhoto = null },
            title = {
                Text(photo.name, style = MaterialTheme.typography.titleSmall)
            },
            text = {
                val bitmap = remember(photo.absolutePath) { decodePhoto(photo, thumbnail = false) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "取证照片大图",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Text("照片加载失败")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    previewPhoto = null
                    pendingDelete = photo
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { previewPhoto = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 删除确认对话框
    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除取证照片") },
            text = { Text("将永久删除「${photo.name}」，且无法恢复。确定删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        photo.delete()
                    } catch (_: Throwable) { }
                    pendingDelete = null
                    previewPhoto = null
                    reload()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 单张照片卡片：缩略图 + 文件名 + 拍摄时间 + 删除按钮（点击卡片放大预览） */
@Composable
private fun PhotoItem(photo: File, onClick: () -> Unit, onDelete: () -> Unit) {
    // 缩略图按需解码（缩小加载，避免整页大图导致卡顿/OOM）
    val thumbnail = remember(photo.absolutePath, photo.lastModified()) {
        decodePhoto(photo, thumbnail = true)
    }
    val timeText = remember(photo.lastModified()) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(photo.lastModified()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "取证照片缩略图",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    photo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除照片",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 按需解码照片文件；thumbnail=true 时缩小加载（最长边约 360px 级别），失败返回 null */
private fun decodePhoto(file: File, thumbnail: Boolean): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options()
        if (thumbnail) {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
            opts.inSampleSize = sample
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (_: Throwable) {
        null
    }
}
