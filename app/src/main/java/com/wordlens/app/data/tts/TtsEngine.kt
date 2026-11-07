package com.wordlens.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.Locale

/**
 * 朗读：两条路。
 *  - SYSTEM：系统 TTS。零延迟、离线，但很多国产 ROM 没有英文语音包，会直接不可用。
 *  - ONLINE：有道词典的发音音频（dict.youdao.com/dictvoice），只要联网就能念，
 *    不占系统 TTS、也不要求装英文语音。
 * 默认系统优先，失败自动退回在线。
 */
class TtsEngine(context: Context) {

    enum class State { INIT, READY, UNAVAILABLE }
    enum class Mode { SYSTEM, ONLINE }

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(State.INIT)
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null

    init {
        val engine = TextToSpeech(appContext) { status ->
            main.post { handleInit(status) }
        }
        tts = engine
    }

    private fun handleInit(status: Int) {
        val engine = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            runCatching {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            // 别只用 setLanguage 的返回值判断：有的引擎 setLanguage 成功但实际没数据。
            // 依次尝试英式/美式，再看 isLanguageAvailable 的真实结果。
            val ok = listOf(Locale.UK, Locale.US, Locale.ENGLISH).any { locale ->
                runCatching {
                    engine.setLanguage(locale)
                    engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
                }.getOrDefault(false)
            }
            _state.value = if (ok) State.READY else State.UNAVAILABLE
        } else {
            _state.value = State.UNAVAILABLE
        }
    }

    /** 系统 TTS 是否可用（初始化完成且找到英文语音） */
    val systemReady: Boolean get() = _state.value == State.READY

    fun speak(text: String, rate: Float = 1.0f, mode: Mode = Mode.SYSTEM): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        return when (mode) {
            Mode.SYSTEM -> if (speakSystem(clean, rate)) true else speakOnline(clean)
            Mode.ONLINE -> speakOnline(clean)
        }
    }

    private fun speakSystem(text: String, rate: Float): Boolean {
        val engine = tts ?: return false
        if (_state.value != State.READY) return false
        return runCatching {
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wl_${System.currentTimeMillis()}") ==
                TextToSpeech.SUCCESS
        }.getOrDefault(false)
    }

    /** 有道发音：单词/短语都能念，返回 mp3，直接交给 MediaPlayer */
    private fun speakOnline(text: String): Boolean = runCatching {
        releasePlayer()
        val q = URLEncoder.encode(text, "UTF-8")
        val url = "https://dict.youdao.com/dictvoice?type=2&audio=$q"
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(url)
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ -> true }
            setOnCompletionListener { releasePlayer() }
            prepareAsync()
        }
        player = mp
        true
    }.getOrDefault(false)

    private fun releasePlayer() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    fun shutdown() {
        releasePlayer()
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
