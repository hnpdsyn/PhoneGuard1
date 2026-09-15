package com.phonGuard.securitylog.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 安全日志引擎（独立版） - 记录、读取、筛选、导出安全事件
 */
class SecurityLogger(private val context: Context) {

    private val logDir: File
        get() = File(context.filesDir, "logs").also { it.mkdirs() }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 记录安全事件
     */
    suspend fun logEvent(event: SecurityEvent) {
        withContext(Dispatchers.IO) {
            try {
                val today = fileDateFormat.format(Date())
                val logFile = File(logDir, "security_$today.json")

                val events = if (logFile.exists()) {
                    val text = logFile.readText()
                    if (text.isNotBlank()) JSONArray(text) else JSONArray()
                } else {
                    JSONArray()
                }

                val entry = JSONObject().apply {
                    put("id", event.id)
                    put("type", event.type)
                    put("subType", event.subType)
                    put("message", event.message)
                    put("timestamp", event.timestamp)
                    put("severity", event.severity)
                    put("details", event.details ?: JSONObject())
                }

                events.put(entry)

                // 只保留最近500条
                val trimmed = if (events.length() > 500) {
                    val start = events.length() - 500
                    JSONArray().apply {
                        for (i in start until events.length()) {
                            put(events.get(i))
                        }
                    }
                } else events

                logFile.writeText(trimmed.toString(2))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 记录一条演示事件（便于首次使用体验查看/筛选/导出功能）
     */
    suspend fun logDemoEvent() {
        logEvent(
            SecurityEvent(
                type = "general",
                subType = "demo",
                message = "演示事件：安全日志App已安装，可记录/查看/导出安全事件",
                severity = 0
            )
        )
    }

    /**
     * 读取今日日志
     */
    suspend fun getTodayLogs(): List<SecurityEvent> {
        val today = fileDateFormat.format(Date())
        return readLogFile("security_$today.json")
    }

    /**
     * 读取全部日志（跨日期，按时间倒序）
     */
    suspend fun getAllLogs(): List<SecurityEvent> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<SecurityEvent>()
            getLogFiles().forEach { file ->
                result.addAll(readLogFile(file.name))
            }
            result.sortedByDescending { it.timestamp }
        }
    }

    /**
     * 读取单个日志文件
     */
    private suspend fun readLogFile(fileName: String): List<SecurityEvent> {
        return withContext(Dispatchers.IO) {
            try {
                val logFile = File(logDir, fileName)
                if (!logFile.exists()) return@withContext emptyList()

                val text = logFile.readText()
                if (text.isBlank()) return@withContext emptyList()

                val array = JSONArray(text)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    SecurityEvent(
                        id = obj.getString("id"),
                        type = obj.getString("type"),
                        subType = obj.optString("subType", ""),
                        message = obj.getString("message"),
                        timestamp = obj.getString("timestamp"),
                        severity = obj.getInt("severity"),
                        details = obj.optJSONObject("details")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 导出日志为 JSON 文件，返回导出文件（供 FileProvider 分享）
     */
    suspend fun exportLogs(events: List<SecurityEvent>): File? {
        return withContext(Dispatchers.IO) {
            try {
                val exportDir = File(context.filesDir, "exports").also { it.mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFile = File(exportDir, "security_log_export_$timestamp.json")

                val array = JSONArray()
                events.forEach { event ->
                    array.put(
                        JSONObject().apply {
                            put("id", event.id)
                            put("type", event.type)
                            put("subType", event.subType)
                            put("message", event.message)
                            put("timestamp", event.timestamp)
                            put("severity", event.severity)
                            put("details", event.details ?: JSONObject())
                        }
                    )
                }
                exportFile.writeText(array.toString(2))
                exportFile
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取所有日志文件列表
     */
    fun getLogFiles(): List<File> {
        return logDir.listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 清空日志
     */
    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            logDir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 按保留天数清理旧日志
     */
    suspend fun cleanOldLogs(retentionDays: Int): Int {
        return withContext(Dispatchers.IO) {
            var deleted = 0
            val threshold = System.currentTimeMillis() - retentionDays * 24 * 3600 * 1000L
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < threshold) {
                    if (file.delete()) deleted++
                }
            }
            deleted
        }
    }

    data class SecurityEvent(
        val id: String = "${System.currentTimeMillis()}_${(1000..9999).random()}",
        val type: String,          // app_lock, adb, intrusion, sim, login, general
        val subType: String = "",
        val message: String,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        val severity: Int = 0,     // 0=info, 1=warning, 2=critical
        val details: JSONObject? = null
    )
}
