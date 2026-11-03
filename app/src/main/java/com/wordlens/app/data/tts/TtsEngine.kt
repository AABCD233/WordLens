package com.wordlens.app.data.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** 系统 TTS 封装：朗读单词 / 例句，语速可调 */
class TtsEngine(context: Context) {

    enum class State { INIT, READY, UNAVAILABLE }

    private val _state = MutableStateFlow(State.INIT)
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    // 注意：OnInitListener 有可能在构造函数返回前就被调用，所以用可空字段 + 主线程转发，
    // 保证 handleInit 执行时 tts 一定已经赋值。
    private var tts: TextToSpeech? = null

    init {
        val engine = TextToSpeech(context.applicationContext) { status ->
            main.post { handleInit(status) }
        }
        tts = engine
    }

    private fun handleInit(status: Int) {
        val engine = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val ok = setLang(engine, Locale.UK) || setLang(engine, Locale.US)
            _state.value = if (ok) State.READY else State.UNAVAILABLE
        } else {
            _state.value = State.UNAVAILABLE
        }
    }

    private fun setLang(engine: TextToSpeech, locale: Locale): Boolean {
        val r = runCatching { engine.setLanguage(locale) }.getOrNull() ?: return false
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String, rate: Float = 1.0f) {
        if (_state.value != State.READY) return
        val engine = tts ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        runCatching {
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "wordlens_${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
