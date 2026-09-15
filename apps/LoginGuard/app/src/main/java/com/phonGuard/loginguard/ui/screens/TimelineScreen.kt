package com.phonGuard.loginguard.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.loginguard.LoginGuardApp
import com.phonGuard.loginguard.core.SecurityLogger
import kotlinx.coroutines.launch

/**
 * 事件时间线（v1.1 新增）- 按时间倒序展示所有安全事件
 * 覆盖：解锁成功 / 密码输错 / 自动锁机 / 拍照取证 等
 * 图标区分事件类型；点击"拍照取证"事件可跳转账证照片页查看
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(onBack: () -> Unit, onOpenGallery: () -> Unit = {}) {
    val app = LoginGuardApp.instance
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<SecurityLogger.SecurityEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        events = app.securityLogger.getAllLogs()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        TopAppBar(
            title = { Text("事件时间线") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "清空时间线")
                }
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        events = app.securityLogger.getAllLogs()
                        isLoading = false
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无安全事件", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "解锁成功 / 密码输错 / 自动锁机 / 拍照取证等事件将按时间倒序在此展示",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            Text(
                "共 ${events.size} 条事件（按时间倒序）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    TimelineItem(
                        event = event,
                        onClick = if (event.subType == "photo_captured") {
                            { onOpenGallery() }  // 拍照取证事件 → 跳转账证照片页
                        } else null
                    )
                }
            }
        }
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空事件时间线") },
            text = { Text("将删除所有安全事件记录，且无法恢复。确定清空吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        app.securityLogger.clearLogs()
                        events = emptyList()
                    }
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 时间线事件视觉元素（图标 + 类型标签） */
private data class TimelineVisual(val icon: ImageVector, val label: String)

private fun timelineVisual(event: SecurityLogger.SecurityEvent): TimelineVisual {
    return when {
        event.subType == "unlock_success" ->
            TimelineVisual(Icons.Default.LockOpen, "解锁成功")
        event.subType == "password_failed" ->
            TimelineVisual(Icons.Default.Error, "密码输错")
        event.subType == "auto_lock" ->
            TimelineVisual(Icons.Default.Lock, "自动锁机")
        event.subType == "brute_force" ->
            TimelineVisual(Icons.Default.Warning, "暴力破解")
        event.subType == "photo_captured" ->
            TimelineVisual(Icons.Default.PhotoCamera, "拍照取证")
        event.subType == "photo_failed" ->
            TimelineVisual(Icons.Default.PhotoCamera, "拍照取证失败")
        else -> TimelineVisual(Icons.Default.Info, event.type.uppercase())
    }
}

@Composable
private fun TimelineItem(event: SecurityLogger.SecurityEvent, onClick: (() -> Unit)? = null) {
    val severityColor = when (event.severity) {
        2 -> MaterialTheme.colorScheme.error
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val visual = timelineVisual(event)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (event.severity >= 2) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                visual.icon,
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        visual.label,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall,
                        color = severityColor
                    )
                    Text(
                        event.timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "查看照片",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
