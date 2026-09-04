package com.phonGuard.core

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
 * 安全日志引擎 - 记录所有安全事件
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
     * 读取今日日志
     */
    suspend fun getTodayLogs(): List<SecurityEvent> {
        return withContext(Dispatchers.IO) {
            try {
                val today = fileDateFormat.format(Date())
                val logFile = File(logDir, "security_$today.json")
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
                }.reversed() // 最新的在前
            } catch (e: Exception) {
                emptyList()
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

    data class SecurityEvent(
        val id: String = "${System.currentTimeMillis()}_${(1000..9999).random()}",
        val type: String,          // app_lock, adb, intrusion, sim, login
        val subType: String = "",
        val message: String,
        val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        val severity: Int = 0,     // 0=info, 1=warning, 2=critical
        val details: JSONObject? = null
    )
}