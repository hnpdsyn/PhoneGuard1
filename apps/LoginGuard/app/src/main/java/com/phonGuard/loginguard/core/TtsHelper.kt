package com.phonGuard.loginguard.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS 语音告警（v1.1 新增）
 *
 * 触发暴力破解告警时播报"检测到暴力破解攻击，已自动锁机"：
 * - TextToSpeech 构造要求所在线程持有 Looper，这里统一切到主线程创建，避免 receiver 子线程崩溃
 * - 播报完成后（onDone/onError）立即 shutdown 释放资源，并带 10 秒兜底超时
 * - 无 TTS 引擎 / 初始化失败 / 播报异常一律静默降级，不影响通知与锁机流程
 */
object TtsHelper {

    /** 防止多路告警同时创建多个 TTS 实例 */
    private val speaking = AtomicBoolean(false)

    /** 兜底释放延迟（毫秒）：TTS 引擎异常不回调时强制释放 */
    private const val FALLBACK_RELEASE_MS = 10_000L

    /** 语音播报（异步；文本为空或正在播报时直接忽略） */
    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        if (!speaking.compareAndSet(false, true)) return  // 已在播报中，丢弃本次
        val appContext = context.applicationContext

        // 切到主线程创建 TextToSpeech（构造函数需要 Looper）
        Handler(Looper.getMainLooper()).post {
            try {
                val tts = TextToSpeech(appContext) { status ->
                    try {
                        if (status == TextToSpeech.SUCCESS) {
                            // 置为中文（设置失败不影响播报，TTS 会使用默认语言）
                            try {
                                tts.language = Locale.CHINA
                            } catch (_: Throwable) { }
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "loginguard_alert")
                        } else {
                            // 初始化失败：静默降级
                            release(tts)
                        }
                    } catch (_: Throwable) {
                        release(tts)
                    }
                }
                // 播报完成后释放资源
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) = release(tts)
                    override fun onError(utteranceId: String?) = release(tts)
                })
                // 兜底：TTS 引擎异常不回调时，最长挂 10 秒后强制释放
                Handler(Looper.getMainLooper()).postDelayed({
                    if (speaking.get()) release(tts)
                }, FALLBACK_RELEASE_MS)
            } catch (_: Throwable) {
                // 设备无 TTS 引擎等异常：静默降级
                speaking.set(false)
            }
        }
    }

    /** 停止并释放 TTS 资源 */
    private fun release(tts: TextToSpeech?) {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) { }
        speaking.set(false)
    }
}
