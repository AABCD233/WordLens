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
    /** 阅读页默认视图：words / image / text / zh */
    val readerMode: String = "words",
    /** 在线翻译用哪个引擎（见 TranslateEngine.id） */
    val translateEngine: String = "mymemory",
    /** 有道词典网页接口补中文（默认关，要联网） */
    val useYoudaoOnlineDict: Boolean = false,
    // ---- 各家翻译服务的凭据，只在用户自己填了才会用到 ----
    val baiduAppId: String = "",
    val baiduKey: String = "",
    val youdaoAppKey: String = "",
    val youdaoAppSecret: String = "",
    val azureKey: String = "",
    val azureRegion: String = "global",
    /** 当前使用的扩展词典包 id；空串 = 内置核心词典 */
    val activePackId: String = "",
    // ---- 词卡显示哪些区块（单词+音标+中文释义固定显示，不可关）----
    val cardShowUserNote: Boolean = true,
    val cardShowEnMeaning: Boolean = true,
    val cardShowExamples: Boolean = true,
    val cardShowPhrases: Boolean = true,
    val cardShowUsage: Boolean = true,
    val cardShowRoot: Boolean = true,
    val cardShowFamily: Boolean = true,
    val cardShowForms: Boolean = true,

    /** 每次打开 App 自动检查更新 */
    val autoCheckUpdate: Boolean = true,
    /** 底栏常驻（不随滚动隐藏） */
    val pinBottomBar: Boolean = false,
    /** 是否开启应用外悬浮球查词 */
    val floatingButton: Boolean = false,
    /** 下载 GitHub 资源时是否走加速镜像 */
    val githubAccel: Boolean = true,
    /** 首次启动的免责声明是否已确认 */
    val disclaimerAccepted: Boolean = false,
    /** 默写成绩历史：每条形如 "答对/总数@时间"，换行分隔 */
    val dictationHistory: String = "",
    /** 直接查词的历史，逗号分隔（最多 15 个） */
    val searchHistory: String = "",
    /** 朗读引擎：system = 系统 TTS，online = 有道发音（联网） */
    val ttsMode: String = "system",
    /** Android 12+ 是否跟随系统壁纸取色（Material You） */
    val dynamicColor: Boolean = true
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
        readerMode = prefs.getString(K_MODE, "words") ?: "words",
        translateEngine = prefs.getString(K_ENGINE, "mymemory") ?: "mymemory",
        useYoudaoOnlineDict = prefs.getBoolean(K_YD_DICT, false),
        baiduAppId = prefs.getString(K_BD_ID, "") ?: "",
        baiduKey = prefs.getString(K_BD_KEY, "") ?: "",
        youdaoAppKey = prefs.getString(K_YD_KEY, "") ?: "",
        youdaoAppSecret = prefs.getString(K_YD_SECRET, "") ?: "",
        azureKey = prefs.getString(K_AZ_KEY, "") ?: "",
        azureRegion = prefs.getString(K_AZ_REGION, "global") ?: "global",
        activePackId = prefs.getString(K_PACK, "") ?: "",
        cardShowUserNote = prefs.getBoolean(K_C_NOTE, true),
        cardShowEnMeaning = prefs.getBoolean(K_C_EN, true),
        cardShowExamples = prefs.getBoolean(K_C_EX, true),
        cardShowPhrases = prefs.getBoolean(K_C_PHRASE, true),
        cardShowUsage = prefs.getBoolean(K_C_USAGE, true),
        cardShowRoot = prefs.getBoolean(K_C_ROOT, true),
        cardShowFamily = prefs.getBoolean(K_C_FAMILY, true),
        cardShowForms = prefs.getBoolean(K_C_FORMS, true),
        autoCheckUpdate = prefs.getBoolean(K_AUTO_UPDATE, true),
        pinBottomBar = prefs.getBoolean(K_PIN_BAR, false),
        floatingButton = prefs.getBoolean(K_FLOATING, false),
        githubAccel = prefs.getBoolean(K_ACCEL, true),
        disclaimerAccepted = prefs.getBoolean(K_DISCLAIMER, false),
        dictationHistory = prefs.getString(K_DICT_HIST, "") ?: "",
        searchHistory = prefs.getString(K_HISTORY, "") ?: "",
        ttsMode = prefs.getString(K_TTS_MODE, "system") ?: "system",
        dynamicColor = prefs.getBoolean(K_DYNAMIC, true)
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
            putString(K_ENGINE, next.translateEngine)
            putBoolean(K_YD_DICT, next.useYoudaoOnlineDict)
            putString(K_BD_ID, next.baiduAppId)
            putString(K_BD_KEY, next.baiduKey)
            putString(K_YD_KEY, next.youdaoAppKey)
            putString(K_YD_SECRET, next.youdaoAppSecret)
            putString(K_AZ_KEY, next.azureKey)
            putString(K_AZ_REGION, next.azureRegion)
            putString(K_PACK, next.activePackId)
            putBoolean(K_C_NOTE, next.cardShowUserNote)
            putBoolean(K_C_EN, next.cardShowEnMeaning)
            putBoolean(K_C_EX, next.cardShowExamples)
            putBoolean(K_C_PHRASE, next.cardShowPhrases)
            putBoolean(K_C_USAGE, next.cardShowUsage)
            putBoolean(K_C_ROOT, next.cardShowRoot)
            putBoolean(K_C_FAMILY, next.cardShowFamily)
            putBoolean(K_C_FORMS, next.cardShowForms)
            putBoolean(K_AUTO_UPDATE, next.autoCheckUpdate)
            putBoolean(K_PIN_BAR, next.pinBottomBar)
            putBoolean(K_FLOATING, next.floatingButton)
            putBoolean(K_ACCEL, next.githubAccel)
            putBoolean(K_DISCLAIMER, next.disclaimerAccepted)
            putString(K_DICT_HIST, next.dictationHistory)
            putString(K_HISTORY, next.searchHistory)
            putString(K_TTS_MODE, next.ttsMode)
            putBoolean(K_DYNAMIC, next.dynamicColor)
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
        const val K_ENGINE = "translate_engine"
        const val K_YD_DICT = "youdao_online_dict"
        const val K_BD_ID = "baidu_app_id"
        const val K_BD_KEY = "baidu_key"
        const val K_YD_KEY = "youdao_app_key"
        const val K_YD_SECRET = "youdao_app_secret"
        const val K_AZ_KEY = "azure_key"
        const val K_AZ_REGION = "azure_region"
        const val K_PACK = "active_dict_pack"
        const val K_DYNAMIC = "dynamic_color"
        const val K_TTS_MODE = "tts_mode"
        const val K_HISTORY = "search_history"
        const val K_DICT_HIST = "dictation_history"
        const val K_ACCEL = "github_accel"
        const val K_FLOATING = "floating_button"
        const val K_PIN_BAR = "pin_bottom_bar"
        const val K_AUTO_UPDATE = "auto_check_update"
        const val K_C_NOTE = "card_note"
        const val K_C_EN = "card_en"
        const val K_C_EX = "card_examples"
        const val K_C_PHRASE = "card_phrases"
        const val K_C_USAGE = "card_usage"
        const val K_C_ROOT = "card_root"
        const val K_C_FAMILY = "card_family"
        const val K_C_FORMS = "card_forms"
        const val K_DISCLAIMER = "disclaimer_accepted"
    }
}
