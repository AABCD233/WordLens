package com.wordlens.app.data.dict

import com.wordlens.app.data.db.AppDatabase
import com.wordlens.app.data.db.DictStore
import com.wordlens.app.data.model.Definition
import com.wordlens.app.data.model.DictText
import com.wordlens.app.data.model.WordNormalizer
import com.wordlens.app.data.settings.AppSettings
import com.wordlens.app.data.db.DictStatus

/**
 * 查词编排：离线词典优先（快、离线可用），在线 API 只做补充，AI 按需。
 * 层级：
 *  1) 离线英汉（ECDICT，114k 词）
 *  2) 免费英文词典 API（dictionaryapi.dev）：英英释义 + 例句 + 发音音频
 *  3) 机器翻译（MyMemory）：离线没收录的词给一句中文
 *  4) AI 详解（用户自配 OpenAI 兼容接口）：点按钮才触发
 */
class DictionaryRepository(
    private val dictStore: DictStore,
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val freeApi: FreeDictionaryApi,
    private val translateApi: TranslateApi,
    private val llm: LlmClient
) {

    val dictStatus: DictStatus get() = dictStore.status.value

    suspend fun ensureDictReady(): Boolean = dictStore.ensureReady()

    suspend fun lookup(raw: String): Definition {
        val query = raw.trim()
        if (query.isEmpty()) return Definition.empty(query)
        val normalized = WordNormalizer.normalize(query)
        if (normalized.isEmpty()) return Definition.empty(query)

        val s = settings.state.value

        // 1. 离线
        val offlineHit = runCatching { dictStore.lookupWithLemma(query) }.getOrNull()
        var result = if (offlineHit != null) {
            Definition(
                query = query,
                lemma = offlineHit.row.word,
                matchedOffline = true,
                lemmaIsExact = offlineHit.isOriginal,
                phonetic = offlineHit.row.phonetic.ifBlank { null },
                zhLines = DictText.zhLines(offlineHit.row.translation),
                enMeanings = DictText.enMeanings(offlineHit.row.definition),
                inflections = DictText.inflections(offlineHit.row.exchange),
                tags = tagNames(offlineHit.row.tag),
                collins = offlineHit.row.collins,
                oxford = offlineHit.row.oxford == 1,
                freq = offlineHit.row.freq,
                sources = setOf(SOURCE_OFFLINE)
            )
        } else Definition.empty(query)

        // 2. 免费英文词典（离线没命中、或想要例句时）
        if (s.useFreeDictionaryApi && (!result.matchedOffline || result.enMeanings.isEmpty())) {
            runCatching { freeApi.lookup(result.lemma) }.getOrNull()?.let { entry ->
                result = result.copy(
                    phonetic = result.phonetic ?: entry.phonetic,
                    audioUrl = entry.audioUrl,
                    enMeanings = if (result.enMeanings.isEmpty()) entry.meanings else result.enMeanings,
                    sources = result.sources + SOURCE_FREE_API
                )
            }
        }

        // 3. 离线没有、免费词典也没有中文 —— 机器翻译兜底
        if (s.useMachineTranslate && result.zhLines.isEmpty() && !result.matchedOffline) {
            runCatching { translateApi.translate(result.lemma) }.getOrNull()?.let { zh ->
                result = result.copy(zhMachine = zh, sources = result.sources + SOURCE_TRANSLATE)
            }
        }

        return result
    }

    /** 「AI 详解」按需触发 */
    suspend fun aiExplain(word: String, sentence: String?): Result<String> =
        llm.explain(word, sentence)

    suspend fun suggest(prefix: String, limit: Int = 20): List<String> =
        runCatching {
            dictStore.suggest(prefix, limit).map { it.word }
        }.getOrDefault(emptyList())

    suspend fun dictWordCount(): Int = runCatching { dictStore.totalWords() }.getOrDefault(0)

    // ---------- helpers ----------

    private val TAG_ZH = mapOf(
        "zk" to "中考", "gk" to "高考", "cet4" to "四级", "cet6" to "六级", "ky" to "考研",
        "ielts" to "雅思", "toefl" to "托福", "gre" to "GRE", "gmat" to "GMAT", "sat" to "SAT",
        "bpec" to "商务英语", "pect" to "公共英语"
    )

    private fun tagNames(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { TAG_ZH[it.trim().lowercase()] }.distinct()
    }

    companion object {
        const val SOURCE_OFFLINE = "离线词典"
        const val SOURCE_FREE_API = "免费词典"
        const val SOURCE_TRANSLATE = "机器翻译"
        const val SOURCE_AI = "AI"
    }
}
