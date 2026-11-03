package com.wordlens.app

import android.content.Context
import com.wordlens.app.data.db.AppDatabase
import com.wordlens.app.data.db.DictPackManager
import com.wordlens.app.data.db.DictStore
import com.wordlens.app.data.dict.Cache
import com.wordlens.app.data.dict.DictionaryRepository
import com.wordlens.app.data.dict.FreeDictionaryApi
import com.wordlens.app.data.dict.LlmClient
import com.wordlens.app.data.dict.TranslateApi
import com.wordlens.app.data.dict.YoudaoDictApi
import com.wordlens.app.data.ocr.OcrEngine
import com.wordlens.app.data.settings.AppSettings
import com.wordlens.app.data.tts.TtsEngine

/**
 * 手写依赖容器：整个 App 只有一份，挂在 Application 上。
 * 不引 Hilt/Koin，省掉注解处理器和一堆构建风险。
 */
class AppContainer(context: Context) {

    val settings = AppSettings(context)
    val database = AppDatabase(context)
    val dictStore = DictStore(context)
    val ocr = OcrEngine(context)
    val tts = TtsEngine(context)
    val dictPacks = DictPackManager(context, dictStore, settings)

    private val cache = object : Cache {
        override suspend fun get(key: String): String? = database.cacheGet(key)
        override suspend fun put(key: String, payload: String) = database.cachePut(key, payload)
    }

    private val freeApi = FreeDictionaryApi(cache)
    private val translateApi = TranslateApi(cache, settings)
    private val youdaoDict = YoudaoDictApi(cache)
    private val llm = LlmClient(settings)

    val dictionary = DictionaryRepository(
        dictStore = dictStore,
        db = database,
        settings = settings,
        freeApi = freeApi,
        translateApi = translateApi,
        youdaoDict = youdaoDict,
        llm = llm
    )
}
