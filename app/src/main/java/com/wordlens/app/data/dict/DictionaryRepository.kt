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
    private val youdaoDict: YoudaoDictApi,
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

        // 3. 有道词典网页接口：离线没中文时补一份（需用户手动打开）
        if (s.useYoudaoOnlineDict && result.zhLines.isEmpty()) {
            runCatching { youdaoDict.lookup(result.lemma) }.getOrNull()?.let { entry ->
                result = result.copy(
                    phonetic = result.phonetic ?: entry.phonetic,
                    zhLines = entry.zhLines,
                    sources = result.sources + SOURCE_YOUDAO
                )
            }
        }

        // 4. 还是没有中文 —— 走用户选的翻译引擎兜底
        if (s.useMachineTranslate && result.zhLines.isEmpty()) {
            translateApi.translate(result.lemma).getOrNull()?.let { zh ->
                result = result.copy(
                    zhMachine = zh,
                    sources = result.sources + engineSourceName(s)
                )
            }
        }

        return result
    }

    /**
     * 整段翻译。长文按句子切成若干块分别翻（免费接口有长度限制），
     * 每块失败不影响其它块，最后把成功的拼起来，全部失败才算失败。
     */
    suspend fun translateText(text: String): Result<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.success("")
        val chunks = chunkForTranslate(clean)
        val out = StringBuilder()
        var lastError: Throwable? = null
        for (chunk in chunks) {
            val r = translateApi.translate(chunk)
            if (r.isSuccess) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(r.getOrThrow())
            } else {
                lastError = r.exceptionOrNull()
            }
        }
        return if (out.isNotEmpty()) Result.success(out.toString()) else
            Result.failure(lastError ?: IllegalStateException("翻译失败"))
    }

    /** 按句子边界切块，单块不超过 [MAX_CHUNK] 字符 */
    internal fun chunkForTranslate(text: String, maxChunk: Int = MAX_CHUNK): List<String> {
        if (text.length <= maxChunk) return listOf(text)
        val sentences = text.replace(Regex("([.!?;:]\\s+)"), "$1\u0001").split('\u0001')
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (raw in sentences) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            if (s.length > maxChunk) {
                if (cur.isNotEmpty()) { out += cur.toString(); cur.setLength(0) }
                s.chunked(maxChunk).forEach { out += it }
                continue
            }
            if (cur.length + s.length + 1 > maxChunk && cur.isNotEmpty()) {
                out += cur.toString()
                cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out.ifEmpty { listOf(text) }
    }

    private fun engineSourceName(s: com.wordlens.app.data.settings.Settings): String =
        "翻译·" + TranslateEngine.of(s.translateEngine).label

    /** 「AI 详解 / 例句 / 搭配」按需触发 */
    suspend fun aiExplain(
        word: String,
        sentence: String?,
        kind: AiKind = AiKind.EXPLAIN
    ): Result<String> = llm.explain(word, sentence, kind)

    /** 整篇 AI 精读 */
    suspend fun aiAnalyzeArticle(text: String): Result<String> = llm.analyzeArticle(text)

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
        const val SOURCE_YOUDAO = "有道词典"
        const val MAX_CHUNK = 450
    }
}
