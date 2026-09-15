package com.phonGuard.applock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonGuard.applock.AppLockApp
import com.phonGuard.applock.MainActivity
import com.phonGuard.applock.core.ConfigManager
import com.phonGuard.applock.ui.theme.AppLockTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.floor

/**
 * 伪装入口Activity - 桌面图标伪装成计算器/备忘录后由此启动
 * - 计算器模式：真实可用的计算器，输入主密码后按「=」进入应用
 * - 记事本模式：仿系统便签，文本框输入主密码后按返回键/保存进入应用
 * 验证失败时行为与普通计算器/便签完全一致，不暴露任何密码验证痕迹
 */
class DisguiseActivity : ComponentActivity() {

    private lateinit var noteFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = (application as AppLockApp).configManager
        noteFile = File(filesDir, "disguise_note.txt")

        setContent {
            // 伪装界面统一使用深色主题，贴近系统计算器/便签风格
            AppLockTheme(darkTheme = true) {
                when (config.disguiseMode) {
                    ConfigManager.DISGUISE_NOTES -> NotesDisguiseScreen(
                        masterPin = config.masterPin,
                        initialText = remember { loadNoteText() },
                        onPinConfirmed = { enterMain() },
                        onExit = { text -> saveNoteText(text); finish() },
                        onSave = { text -> saveNoteText(text) }
                    )
                    else -> CalculatorDisguiseScreen(
                        masterPin = config.masterPin,
                        onPinConfirmed = { enterMain() }
                    )
                }
            }
        }
    }

    /** 验证通过，进入真实主界面 */
    private fun enterMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun loadNoteText(): String {
        return try {
            if (noteFile.exists()) noteFile.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun saveNoteText(text: String) {
        try {
            noteFile.writeText(text)
        } catch (_: Exception) { }
    }
}

// ==================== 计算器伪装 ====================

/** 四则运算，除零返回 null（界面显示"错误"） */
private fun compute(a: Double, b: Double, op: String): Double? = when (op) {
    "+" -> a + b
    "−" -> a - b
    "×" -> a * b
    "÷" -> if (b == 0.0) null else a / b
    else -> null
}

/** 结果格式化：整数去掉小数点，避免浮点尾差噪音 */
private fun formatResult(d: Double): String {
    if (d.isNaN() || d.isInfinite()) return "错误"
    return if (d == floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()
}

/**
 * 计算器伪装界面（Material 深色风格，竖排按钮网格）
 * 主密码验证隐藏在「=」键中：输入主密码后按「=」直接进入应用；输错则按普通计算器逻辑处理
 */
@Composable
fun CalculatorDisguiseScreen(masterPin: String, onPinConfirmed: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var accumulator by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var freshEntry by remember { mutableStateOf(true) }

    val onKey: (String) -> Unit = { key ->
        when {
            key == "C" -> {
                display = "0"; accumulator = null; pendingOp = null; freshEntry = true
            }
            key == "⌫" -> {
                when {
                    freshEntry -> { /* 刚出结果/刚换操作数时退格无效果 */ }
                    display.length > 1 -> display = display.dropLast(1)
                    else -> { display = "0"; freshEntry = true }
                }
            }
            key == "+" || key == "−" || key == "×" || key == "÷" -> {
                val cur = display.toDoubleOrNull()
                if (cur != null) {
                    val acc = accumulator
                    val op = pendingOp
                    if (acc != null && op != null && !freshEntry) {
                        // 连续运算：先结算上一步
                        val r = compute(acc, cur, op)
                        if (r == null) {
                            display = "错误"; accumulator = null; pendingOp = null; freshEntry = true
                        } else {
                            display = formatResult(r); accumulator = r; pendingOp = key; freshEntry = true
                        }
                    } else {
                        accumulator = cur; pendingOp = key; freshEntry = true
                    }
                }
            }
            key == "=" -> {
                // 主密码验证入口：输入主密码后按「=」解锁；不匹配则像普通计算器一样无特殊反应
                if (display == masterPin) {
                    onPinConfirmed()
                } else {
                    val cur = display.toDoubleOrNull()
                    val acc = accumulator
                    val op = pendingOp
                    if (acc != null && op != null && cur != null && !freshEntry) {
                        val r = compute(acc, cur, op)
                        display = if (r == null) "错误" else formatResult(r)
                        accumulator = null; pendingOp = null; freshEntry = true
                    }
                }
            }
            key == "." -> {
                when {
                    freshEntry -> { display = "0."; freshEntry = false }
                    !display.contains(".") -> display = "$display."
                }
            }
            key[0].isDigit() -> {
                when {
                    freshEntry || display == "错误" -> { display = key; freshEntry = false }
                    display == "0" -> display = key
                    display.length < 12 -> display += key
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // 显示区
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    text = display,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 按钮网格（竖排 5 行）
            val rows = listOf(
                listOf("C", "⌫", "÷", "×"),
                listOf("7", "8", "9", "−"),
                listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "=")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key -> CalcButton(key, Modifier.weight(1f), onKey) }
                }
            }
            // 底行：0 跨两列 + 小数点（占位 Spacer 保持四列网格对齐）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalcButton("0", Modifier.weight(2f), onKey)
                CalcButton(".", Modifier.weight(1f), onKey)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CalcButton(label: String, modifier: Modifier, onKey: (String) -> Unit) {
    val isOperator = label == "+" || label == "−" || label == "×" || label == "÷" || label == "="
    val isFunction = label == "C" || label == "⌫"
    val container = when {
        isOperator -> MaterialTheme.colorScheme.primary
        isFunction -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
        else -> Color(0xFF26262F)
    }
    Surface(
        onClick = { onKey(label) },
        modifier = modifier.height(64.dp),
        shape = CircleShape,
        color = container,
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==================== 备忘录伪装 ====================

/**
 * 备忘录伪装界面（仿系统便签：标题栏 + 多行文本框）
 * 文本框内容为主密码时：按返回键或点保存 → 进入应用；否则表现与普通便签一致（正常保存/退出）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesDisguiseScreen(
    masterPin: String,
    initialText: String,
    onPinConfirmed: () -> Unit,
    onExit: (String) -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    // 拦截返回键：内容是主密码则解锁，否则正常退出（等效普通便签返回）
    BackHandler {
        if (text.trim() == masterPin) {
            onPinConfirmed()
        } else {
            onExit(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备忘录", fontWeight = FontWeight.Medium) },
                actions = {
                    IconButton(onClick = {
                        if (text.trim() == masterPin) onPinConfirmed() else onSave(text)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            placeholder = { Text("输入内容…") },
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, lineHeight = 24.sp)
        )
    }
}
