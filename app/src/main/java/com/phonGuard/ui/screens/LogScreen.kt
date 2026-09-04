package com.phonGuard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonGuard.PhoneGuardApp
import com.phonGuard.core.SecurityLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val app = PhoneGuardApp.instance
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<SecurityLogger.SecurityEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        logs = app.securityLogger.getTodayLogs()
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        TopAppBar(
            title = { Text("安全日志") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        app.securityLogger.clearLogs()
                        logs = emptyList()
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "清空日志")
                }
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true
                        logs = app.securityLogger.getTodayLogs()
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
        } else if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无安全事件记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "一切正常，继续安心使用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { event ->
                    LogItem(event)
                }
            }
        }
    }
}

@Composable
private fun LogItem(event: SecurityLogger.SecurityEvent) {
    val severityColor = when (event.severity) {
        2 -> MaterialTheme.colorScheme.error
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val typeLabel = when (event.type) {
        "app_lock" -> "应用锁"
        "adb" -> "ADB"
        "intrusion" -> "入侵检测"
        "sim" -> "SIM卡"
        "login" -> "登录防护"
        else -> event.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // 严重度指示器
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .padding(end = 0.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = severityColor,
                    shape = MaterialTheme.shapes.small
                ) {}
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "[$typeLabel]",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        event.timestamp.substringAfterLast(" "),
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
        }
    }
}