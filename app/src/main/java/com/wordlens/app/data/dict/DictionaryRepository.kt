package com.wordlens.app.data.dict

import com.wordlens.app.data.db.AppDatabase
import com.wordlens.app.data.db.DictStore
import com.wordlens.app.data.model.Definition
import com.wordlens.app.data.model.DictRow
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

        // 1. 离线：精确命中 + 原形条目一起取
        //
        // 为什么两条都要：ECDICT 里派生词常有自己的条目，但信息不全。
        // 比如 cramming 这条，中文只写了名词义（n. 塞满…），英文释义却是 v. 的，
        // 而完整的 vt./vi./n. 都在 cram 那条里。只取一条就会「中文说 n、英文说 v」。
        val exactRow = runCatching { dictStore.lookup(normalized) }.getOrNull()
        val lemmaHit = if (exactRow == null) {
            runCatching { dictStore.lookupWithLemma(query) }.getOrNull()
        } else null
        val baseRow = exactRow?.let { row ->
            baseWordOf(row.exchange)
                ?.takeIf { it != row.word && isInflectionOf(normalized, it) }
                ?.let { base -> runCatching { dictStore.lookup(base) }.getOrNull() }
        }
        val rows = ArrayList<DictRow>(3)
        if (baseRow != null) rows += baseRow      // 原形条目更全，放前面
        if (exactRow != null) rows += exactRow
        if (exactRow == null && lemmaHit != null && rows.isEmpty()) rows += lemmaHit.row

        var result = if (rows.isEmpty()) {
            Definition.empty(query)
        } else {
            // 标题永远跟着「你点的那个词」走。
            // 以前这里取的是原形条目，于是点「过去式 studied」会跳回 study，看起来像绕了一圈。
            val primary = exactRow ?: lemmaHit?.row ?: rows.first()
            Definition(
                query = query,
                lemma = primary.word,
                matchedOffline = true,
                lemmaIsExact = exactRow != null && normalized == exactRow.word,
                baseWord = baseRow?.word?.takeIf { it != primary.word },
                // 音标优先用「点中的那个词形」的，cramming 要读 /ˈkræmɪŋ/ 而不是 cram
                phonetic = (exactRow?.phonetic?.ifBlank { null }
                    ?: rows.firstNotNullOfOrNull { it.phonetic.ifBlank { null } }),
                zhLines = rows.flatMap { DictText.zhLines(it.translation) }.distinct(),
                enMeanings = mergeEnMeanings(rows.map { DictText.enMeanings(it.definition) }),
                inflections = rows.map { DictText.inflections(it.exchange) }.firstOrNull { it.isNotEmpty() }
                    ?: emptyList(),
                tags = rows.flatMap { tagNames(it.tag) }.distinct(),
                collins = rows.maxOfOrNull { it.collins } ?: 0,
                oxford = rows.any { it.oxford == 1 },
                freq = rows.map { it.freq }.filter { it > 0 }.minOrNull() ?: 0,
                sources = setOf(SOURCE_OFFLINE)
            )
        }

        var networkFailed = false

        // 2. 免费英文词典：补音标/音频，并把例句合并进已有的英文释义
        if (s.useFreeDictionaryApi) {
            val call = runCatching { freeApi.lookup(result.lemma) }
            if (call.isNetworkError()) networkFailed = true
            call.getOrNull()?.let { entry ->
                result = result.copy(
                    phonetic = result.phonetic ?: entry.phonetic,
                    audioUrl = entry.audioUrl,
                    enMeanings = if (result.enMeanings.isEmpty()) entry.meanings
                    else mergeExamples(result.enMeanings, entry.meanings),
                    sources = result.sources + SOURCE_FREE_API
                )
            }
        }

        // 3. 有道在线词典：补中文 + 双语例句（用户手动打开才联网）
        if (s.useYoudaoOnlineDict) {
            val call = runCatching { youdaoDict.lookup(result.lemma) }
            if (call.isNetworkError()) networkFailed = true
            call.getOrNull()?.let { entry ->
                result = result.copy(
                    phonetic = result.phonetic ?: entry.phonetic,
                    zhLines = if (result.zhLines.isEmpty()) entry.zhLines else result.zhLines,
                    examples = if (entry.examples.isEmpty()) result.examples else entry.examples,
                    sources = result.sources + SOURCE_YOUDAO
                )
            }
        }

        // 4. 还是没有中文 —— 走用户选的翻译引擎兜底
        if (s.useMachineTranslate && result.zhLines.isEmpty()) {
            val call = translateApi.translate(result.lemma)
            if (call.isNetworkError()) networkFailed = true
            call.getOrNull()?.let { zh ->
                result = result.copy(
                    zhMachine = zh,
                    sources = result.sources + engineSourceName(s)
                )
            }
        }

        // 5. 用户自己写的释义：lemm 和原词都认，优先显示
        val note = runCatching { db.userNote(result.lemma) }.getOrNull()
            ?: result.baseWord?.let { runCatching { db.userNote(it) }.getOrNull() }
            ?: runCatching { db.userNote(normalized) }.getOrNull()

        return result.copy(onlineFailed = networkFailed, userNote = note)
    }

    /** 只有「连不上」才算断网提示；HTTP 404 之类的业务失败不打扰用户 */
    private fun Result<*>.isNetworkError(): Boolean {
        val e = exceptionOrNull() ?: return false
        return e is java.net.UnknownHostException ||
            e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ||
            (e.cause is java.net.UnknownHostException) ||
            (e.cause is java.net.ConnectException)
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

    /**
     * 判断 form 是不是 base 的派生形式。
     *
     * 为什么要判断：ECDICT 的 exchange 会把不规则变化也写成 `0:原形`，
     * 比如 better → good。那种情况把 good 的义项（n. 善行）合并进 better 反而是噪音。
     */
    private fun isInflectionOf(form: String, base: String): Boolean {
        if (form == base) return false
        if (form.startsWith(base)) return true                       // cram -> cramming
        val stem = base.dropLast(1)
        if (stem.length >= 3 && form.startsWith(stem)) return true   // make -> making / wolf -> wolves
        if (base.endsWith("y") && form.startsWith(base.dropLast(1) + "i")) return true // carry -> carried
        return false
    }

    /** exchange 里的 `0:cram` 就是原形 */
    private fun baseWordOf(exchange: String): String? {
        if (exchange.isBlank()) return null
        for (token in exchange.split('/', '|')) {
            val idx = token.indexOf(':')
            if (idx <= 0) continue
            if (token.substring(0, idx).trim() != "0") continue
            val value = token.substring(idx + 1).trim().lowercase()
            if (value.isNotEmpty() && value.all { it in 'a'..'z' || it == '-' || it == '\'' }) return value
        }
        return null
    }

    /** 多个条目的英文释义按词性合并，重复的释义去掉 */
    private fun mergeEnMeanings(groups: List<List<com.wordlens.app.data.model.EnMeaning>>): List<com.wordlens.app.data.model.EnMeaning> {
        val out = LinkedHashMap<String, MutableList<com.wordlens.app.data.model.EnDefinition>>()
        for (group in groups) {
            for (meaning in group) {
                val bucket = out.getOrPut(meaning.partOfSpeech) { ArrayList() }
                for (d in meaning.definitions) {
                    if (bucket.none { it.text.equals(d.text, ignoreCase = true) }) bucket += d
                }
            }
        }
        return out.map { (pos, defs) ->
            com.wordlens.app.data.model.EnMeaning(partOfSpeech = pos, definitions = defs)
        }
    }

    /** 自己没例句时，把免费词典给的例句按词性塞进去 */
    private fun mergeExamples(
        mine: List<com.wordlens.app.data.model.EnMeaning>,
        theirs: List<com.wordlens.app.data.model.EnMeaning>
    ): List<com.wordlens.app.data.model.EnMeaning> {
        val pool = theirs.flatMap { it.definitions }.mapNotNull { d -> d.example?.let { it to d } }
            .toMutableList()
        if (pool.isEmpty()) return mine
        return mine.map { meaning ->
            meaning.copy(
                definitions = meaning.definitions.map { d ->
                    if (d.example != null) d
                    else {
                        val hit = pool.firstOrNull { (_, src) ->
                            src.text.contains(d.text.take(24), ignoreCase = true) ||
                                d.text.contains(src.text.take(24), ignoreCase = true)
                        } ?: pool.firstOrNull()
                        if (hit != null) {
                            pool.remove(hit)
                            d.copy(example = hit.first)
                        } else d
                    }
                }
            )
        }
    }

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
