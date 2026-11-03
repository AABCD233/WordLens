package com.wordlens.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户偏好。用 SharedPreferences 落地（零额外依赖），
 * 对外暴露 StateFlow 让 Compose 直接订阅。
 */
data class Settings(
    /** 离线词典没查到 / 想补英文例句时，是否请求免费词典 API */
    val useFreeDictionaryApi: Boolean = true,
    /** 完全查不到时，是否用机器翻译给一句中文 */
    val useMachineTranslate: Boolean = true,
    /** AI 详解（OpenAI 兼容接口）*/
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "gpt-4o-mini",
    /** 点词后自动朗读 */
    val autoSpeak: Boolean = false,
    val speechRate: Float = 1.0f,
    /** 阅读页默认视图：words / image / text */
    val readerMode: String = "words"
) {
    val aiReady: Boolean get() = aiEnabled && aiApiKey.isNotBlank()
}

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wordlens_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun read() = Settings(
        useFreeDictionaryApi = prefs.getBoolean(K_FREE, true),
        useMachineTranslate = prefs.getBoolean(K_TRANS, true),
        aiEnabled = prefs.getBoolean(K_AI_ON, false),
        aiBaseUrl = prefs.getString(K_AI_URL, Settings().aiBaseUrl) ?: Settings().aiBaseUrl,
        aiApiKey = prefs.getString(K_AI_KEY, "") ?: "",
        aiModel = prefs.getString(K_AI_MODEL, Settings().aiModel) ?: Settings().aiModel,
        autoSpeak = prefs.getBoolean(K_AUTO_SPEAK, false),
        speechRate = prefs.getFloat(K_RATE, 1.0f),
        readerMode = prefs.getString(K_MODE, "words") ?: "words"
    )

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit().apply {
            putBoolean(K_FREE, next.useFreeDictionaryApi)
            putBoolean(K_TRANS, next.useMachineTranslate)
            putBoolean(K_AI_ON, next.aiEnabled)
            putString(K_AI_URL, next.aiBaseUrl)
            putString(K_AI_KEY, next.aiApiKey)
            putString(K_AI_MODEL, next.aiModel)
            putBoolean(K_AUTO_SPEAK, next.autoSpeak)
            putFloat(K_RATE, next.speechRate)
            putString(K_MODE, next.readerMode)
        }.apply()
    }

    fun clearAiKey() {
        prefs.edit().remove(K_AI_KEY).apply()
        update { it.copy(aiApiKey = "") }
    }

    private companion object {
        const val K_FREE = "use_free_dict_api"
        const val K_TRANS = "use_translate"
        const val K_AI_ON = "ai_enabled"
        const val K_AI_URL = "ai_base_url"
        const val K_AI_KEY = "ai_api_key"
        const val K_AI_MODEL = "ai_model"
        const val K_AUTO_SPEAK = "auto_speak"
        const val K_RATE = "tts_rate"
        const val K_MODE = "reader_mode"
    }
}
