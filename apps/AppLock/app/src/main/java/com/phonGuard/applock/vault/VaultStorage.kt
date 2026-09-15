package com.phonGuard.applock.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * 隐私保险箱存储管理
 * - 文件存放于私有目录 filesDir/vault/，内含 .nomedia 防止相册扫描
 * - 导入文件重命名为 vault_<uuid>.dat，不保留原名（原名/导入时间/类型存于 vault_index.json）
 * - 加密性不做要求（私有目录已隔离），重点在文件名与入口隔离
 */
class VaultStorage(private val context: Context) {

    /** 保险箱条目 */
    data class VaultEntry(
        val uuid: String,
        val originalName: String,
        val importTime: Long,
        val type: String // "image" / "video"
    )

    /** 导入结果 */
    sealed class ImportResult {
        object Success : ImportResult()
        object TooLarge : ImportResult()
        object Failed : ImportResult()
    }

    private val vaultDir: File = File(context.filesDir, "vault")
    private val indexFile: File = File(vaultDir, "vault_index.json")

    companion object {
        /** 视频导入大小上限：500MB */
        const val MAX_VIDEO_BYTES: Long = 500L * 1024 * 1024
        private const val COPY_BUFFER_SIZE = 64 * 1024
    }

    init {
        vaultDir.mkdirs()
        try {
            val nomedia = File(vaultDir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        } catch (_: Exception) { }
    }

    // ========== 查询 ==========

    @Synchronized
    fun list(): List<VaultEntry> = loadIndex()

    fun fileOf(entry: VaultEntry): File = File(vaultDir, "vault_${entry.uuid}.dat")

    // ========== 导入 ==========

    /**
     * 从 SAF Uri 导入文件（须在 IO 线程调用）
     * 视频超过 500MB 拒绝导入（Unknown size 时边拷贝边计数，超限中断）
     */
    @Synchronized
    fun import(uri: Uri): ImportResult {
        return try {
            val type = resolveType(uri)
            val displayName = queryDisplayName(uri) ?: "未命名"

            // 视频大小检查
            if (type == "video") {
                val declaredSize = querySize(uri)
                if (declaredSize > MAX_VIDEO_BYTES) return ImportResult.TooLarge
            }

            val uuid = UUID.randomUUID().toString()
            val dest = File(vaultDir, "vault_$uuid.dat")
            val input = context.contentResolver.openInputStream(uri) ?: return ImportResult.Failed

            val complete = input.use { ins ->
                FileOutputStream(dest).use { fos ->
                    if (type == "video") {
                        // 声明大小可能缺失，拷贝过程中二次校验
                        copyWithLimit(ins, fos, MAX_VIDEO_BYTES)
                    } else {
                        ins.copyTo(fos); true
                    }
                }
            }
            if (!complete) {
                dest.delete()
                return ImportResult.TooLarge
            }

            val entry = VaultEntry(uuid, displayName, System.currentTimeMillis(), type)
            val index = loadIndex()
            index.add(entry)
            saveIndex(index)
            ImportResult.Success
        } catch (_: Exception) {
            ImportResult.Failed
        }
    }

    // ========== 导出 ==========

    /** 导出条目到 SAF 目标 Uri（恢复原文件名，由 CreateDocument 的 displayName 决定） */
    @Synchronized
    fun export(entry: VaultEntry, targetUri: Uri): Boolean {
        return try {
            val src = fileOf(entry)
            if (!src.exists()) return false
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    // ========== 删除 ==========

    /** 删除条目：删文件 + 更新索引 */
    @Synchronized
    fun delete(entry: VaultEntry): Boolean {
        val fileDeleted = try {
            fileOf(entry).delete()
        } catch (_: Exception) {
            false
        }
        val index = loadIndex().filter { it.uuid != entry.uuid }
        saveIndex(index)
        return fileDeleted
    }

    // ========== 内部工具 ==========

    /** 判断导入类型 */
    private fun resolveType(uri: Uri): String {
        val mime = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        if (mime != null) {
            if (mime.startsWith("video/")) return "video"
            if (mime.startsWith("image/")) return "image"
        }
        // mime 缺失时按扩展名兜底
        val name = queryDisplayName(uri) ?: ""
        return if (name.matches(Regex(".*\\.(mp4|mov|avi|mkv|3gp|webm|m4v)$", RegexOption.IGNORE_CASE))) {
            "video"
        } else {
            "image"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun querySize(uri: Uri): Long {
        return try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    /** 限量拷贝：超过 limit 返回 false 并停止写入 */
    private fun copyWithLimit(ins: InputStream, out: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = ins.read(buffer)
            if (read == -1) return true
            total += read
            if (total > limit) return false
            out.write(buffer, 0, read)
        }
    }

    // ========== 索引读写（vault_index.json，使用平台内置 org.json） ==========

    private fun loadIndex(): MutableList<VaultEntry> {
        val result = mutableListOf<VaultEntry>()
        try {
            if (!indexFile.exists()) return result
            val arr = JSONArray(indexFile.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    VaultEntry(
                        uuid = obj.optString("uuid"),
                        originalName = obj.optString("name", "未命名"),
                        importTime = obj.optLong("time", 0L),
                        type = obj.optString("type", "image")
                    )
                )
            }
        } catch (_: Exception) { }
        return result
    }

    private fun saveIndex(entries: List<VaultEntry>) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("uuid", e.uuid)
                        .put("name", e.originalName)
                        .put("time", e.importTime)
                        .put("type", e.type)
                )
            }
            indexFile.writeText(arr.toString())
        } catch (_: Exception) { }
    }
}
