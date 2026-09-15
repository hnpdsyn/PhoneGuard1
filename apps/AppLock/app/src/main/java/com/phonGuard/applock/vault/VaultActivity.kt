package com.phonGuard.applock.vault

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.phonGuard.applock.AppLockApp
import com.phonGuard.applock.ui.LockVerifyScreen
import com.phonGuard.applock.ui.theme.AppLockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 隐私保险箱Activity - 进入需验证主密码（复用现有密码验证逻辑）
 * 支持导入图片/视频（SAF）、全屏查看、播放、导出、删除
 */
class VaultActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = (application as AppLockApp).configManager
        val storage = VaultStorage(this)

        setContent {
            AppLockTheme {
                var unlocked by remember { mutableStateOf(false) }
                if (!unlocked) {
                    // 验证阶段：深色底 + 复用密码验证界面
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F1A)) {
                        LockVerifyScreen(
                            onUnlock = {
                                config.vaultEnabled = true
                                unlocked = true
                            },
                            onCancel = { finish() },
                            targetPackage = "隐私保险箱"
                        )
                    }
                } else {
                    VaultScreen(storage = storage, onClose = { finish() })
                }
            }
        }
    }
}

/**
 * 保险箱主界面
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(storage: VaultStorage, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<VaultStorage.VaultEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<VaultStorage.VaultEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<VaultStorage.VaultEntry?>(null) }
    var pendingExport by remember { mutableStateOf<VaultStorage.VaultEntry?>(null) }
    var viewImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var viewStartIndex by remember { mutableStateOf(0) }
    var playVideo by remember { mutableStateOf<File?>(null) }

    // 首次加载列表
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { storage.list() }
        loading = false
    }

    // SAF 多选导入（图片 + 视频）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                importing = true
                var success = 0
                var tooLarge = 0
                var failed = 0
                uris.forEach { uri ->
                    when (withContext(Dispatchers.IO) { storage.import(uri) }) {
                        is VaultStorage.ImportResult.Success -> success++
                        is VaultStorage.ImportResult.TooLarge -> tooLarge++
                        else -> failed++
                    }
                }
                entries = withContext(Dispatchers.IO) { storage.list() }
                importing = false
                val msg = buildString {
                    if (success > 0) append("已导入保险箱，原文件请手动从相册删除")
                    if (tooLarge > 0) append("；${tooLarge} 个视频超过500MB未导入")
                    if (failed > 0) append("；${failed} 个文件导入失败")
                }
                if (msg.isNotEmpty()) {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // SAF 导出（CreateDocument 恢复原文件名）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val entry = pendingExport
        pendingExport = null
        if (uri != null && entry != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { storage.export(entry, uri) }
                Toast.makeText(
                    context,
                    if (ok) "已导出「${entry.originalName}」" else "导出失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私保险箱", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("image/*", "video/*")) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "导入图片或视频")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                entries.isEmpty() -> {
                    // 空状态引导
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "保险箱是空的",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 + 号导入私密图片和视频，\n导入后的文件只有在这里才能查看",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { it.uuid }) { entry ->
                            Box {
                                VaultEntryItem(
                                    entry = entry,
                                    file = storage.fileOf(entry),
                                    onClick = {
                                        if (entry.type == "video") {
                                            playVideo = storage.fileOf(entry)
                                        } else {
                                            val images = entries.filter { it.type != "video" }
                                            viewImages = images.map { storage.fileOf(it) }
                                            viewStartIndex =
                                                images.indexOfFirst { it.uuid == entry.uuid }
                                                    .coerceAtLeast(0)
                                        }
                                    },
                                    onLongClick = { menuFor = entry }
                                )
                                // 长按菜单：导出 / 删除
                                DropdownMenu(
                                    expanded = menuFor == entry,
                                    onDismissRequest = { menuFor = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("导出") },
                                        onClick = {
                                            menuFor = null
                                            pendingExport = entry
                                            exportLauncher.launch(entry.originalName)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            menuFor = null
                                            deleteTarget = entry
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 导入遮罩
            if (importing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("正在导入…", color = Color.White)
                    }
                }
            }
        }
    }

    // 删除确认对话框
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定从保险箱删除「${target.originalName}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { storage.delete(target) }
                        entries = withContext(Dispatchers.IO) { storage.list() }
                        deleteTarget = null
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // 图片全屏查看（点击放大、左右翻页）
    if (viewImages.isNotEmpty()) {
        VaultImageViewer(
            files = viewImages,
            initialIndex = viewStartIndex,
            onClose = { viewImages = emptyList() }
        )
    }

    // 视频播放
    playVideo?.let { file ->
        VaultVideoPlayer(file = file, onClose = { playVideo = null })
    }
}

// ==================== 条目 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultEntryItem(
    entry: VaultStorage.VaultEntry,
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                VaultThumb(
                    path = file.absolutePath,
                    isVideo = entry.type == "video",
                    modifier = Modifier.fillMaxSize()
                )
                if (entry.type == "video") {
                    Icon(
                        Icons.Default.PlayCircleOutline,
                        contentDescription = "视频",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    entry.originalName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatTime(entry.importTime)} · ${formatSize(file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

/** 缩略图：图片按需解码 / 视频取帧，均在 IO 线程完成 */
@Composable
private fun VaultThumb(path: String, isVideo: Boolean, modifier: Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val bmp = withContext(Dispatchers.IO) {
            try {
                if (isVideo) {
                    ThumbnailUtils.createVideoThumbnail(
                        path, MediaStore.Images.Thumbnails.MINI_KIND
                    )
                } else {
                    decodeScaledBitmap(path, 512)
                }
            } catch (_: Exception) {
                null
            }
        }
        bitmap = bmp?.asImageBitmap()
        failed = bmp == null
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (failed) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

// ==================== 图片全屏查看 ====================

/** 全屏图片查看器：左右翻页、点击关闭 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultImageViewer(files: List<File>, initialIndex: Int, onClose: () -> Unit) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { files.size }
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ViewerImage(path = files[page].absolutePath, onTap = onClose)
            }
            Text(
                "${pagerState.currentPage + 1} / ${files.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun ViewerImage(path: String, onTap: () -> Unit) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                decodeScaledBitmap(path, 2048)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

// ==================== 视频播放（MediaPlayer + TextureView） ====================

/** 视频播放器：不引入 ExoPlayer，使用平台 MediaPlayer + TextureView */
@Composable
private fun VaultVideoPlayer(file: File, onClose: () -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(16f / 9f) }

    val releaseAndClose: () -> Unit = {
        try {
            player?.release()
        } catch (_: Exception) { }
        player = null
        onClose()
    }

    Dialog(
        onDismissRequest = releaseAndClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.Center)
                    .aspectRatio(aspect),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                st: SurfaceTexture, width: Int, height: Int
                            ) {
                                try {
                                    val p = MediaPlayer()
                                    p.setDataSource(file.absolutePath)
                                    p.setSurface(Surface(st))
                                    p.setAudioAttributes(
                                        android.media.AudioAttributes.Builder()
                                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                            .setContentType(
                                                android.media.AudioAttributes.CONTENT_TYPE_MOVIE
                                            )
                                            .build()
                                    )
                                    p.setOnPreparedListener { mp ->
                                        prepared = true
                                        duration = mp.duration
                                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                            aspect =
                                                mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                                        }
                                        isPlaying = true
                                        mp.start()
                                    }
                                    p.setOnCompletionListener { isPlaying = false }
                                    p.prepareAsync()
                                    player = p
                                } catch (_: Exception) { }
                            }

                            override fun onSurfaceTextureSizeChanged(
                                st: SurfaceTexture, width: Int, height: Int
                            ) {
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                try {
                                    player?.release()
                                } catch (_: Exception) { }
                                player = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                }
            )

            // 关闭按钮
            IconButton(
                onClick = releaseAndClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }

            // 底部控制条
            if (prepared) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Slider(
                        value = position.toFloat(),
                        onValueChange = {
                            seeking = true
                            position = it.toInt()
                        },
                        onValueChangeFinished = {
                            try {
                                player?.seekTo(position)
                            } catch (_: Exception) { }
                            seeking = false
                        },
                        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            player?.let { p ->
                                try {
                                    if (p.isPlaying) {
                                        p.pause(); isPlaying = false
                                    } else {
                                        p.start(); isPlaying = true
                                    }
                                } catch (_: Exception) { }
                            }
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White
                            )
                        }
                        Text(
                            "${formatMs(position)} / ${formatMs(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    // 播放进度轮询
    LaunchedEffect(prepared) {
        while (prepared) {
            val p = player
            if (p != null && !seeking) {
                position = try {
                    p.currentPosition
                } catch (_: Exception) {
                    position
                }
            }
            delay(500)
        }
    }
}

// ==================== 工具函数 ====================

/** 按目标边长解码本地图片（inSampleSize 降采样，避免大图 OOM） */
private fun decodeScaledBitmap(path: String, maxDim: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    return BitmapFactory.decodeFile(
        path, BitmapFactory.Options().apply { inSampleSize = sample }
    )
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1e3)
    else -> "$bytes B"
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
