package com.wordlens.app.data.dict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 极小化的 HTTP 工具（HttpURLConnection + org.json）。
 *
 * 刻意不引 OkHttp / kotlinx-serialization：少两个坐标就少两类
 * 「版本不兼容 / 仓库不可达」的 CI 失败，这个 App 的请求量也用不上。
 */
object Http {

    const val UA = "WordLens/1.0 (Android; word lookup)"

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): String =
        request(url, "GET", null, headers, timeoutMs)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 45000): String =
        request(url, "POST", body.toByteArray(Charsets.UTF_8), headers + mapOf("Content-Type" to "application/json"), timeoutMs)

    /** 表单提交：百度 / 有道这些老接口只吃 x-www-form-urlencoded */
    fun postForm(url: String, fields: Map<String, String>, timeoutMs: Int = 20000): String {
        val body = fields.entries.joinToString("&") { (k, v) -> urlEncode(k) + "=" + urlEncode(v) }
        return request(
            url, "POST", body.toByteArray(Charsets.UTF_8),
            mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"), timeoutMs
        )
    }

    private fun request(url: String, method: String, body: ByteArray?, headers: Map<String, String>, timeoutMs: Int): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Encoding", "gzip")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                instanceFollowRedirects = true
                doInput = true
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Length", body.size.toString())
                    setFixedLengthStreamingMode(body.size)
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) {
                wrapStream(conn, conn.inputStream)
            } else {
                wrapStream(conn, conn.errorStream ?: conn.inputStream)
            }
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            conn?.disconnect()
        }
    }

    private fun wrapStream(conn: HttpURLConnection, input: java.io.InputStream?) =
        input?.let { if (conn.contentEncoding.equals("gzip", true)) GZIPInputStream(it) else it }

    fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** dictionaryapi.dev 的返回结构 */
data class FreeApiEntry(
    val phonetic: String?,
    val audioUrl: String?,
    val meanings: List<com.wordlens.app.data.model.EnMeaning>
)

/** 免费英文词典 API（Free Dictionary API，无需 key） */
class FreeDictionaryApi(private val cache: Cache) {

    suspend fun lookup(word: String): FreeApiEntry? = withContext(Dispatchers.IO) {
        val key = "fd:${word.lowercase()}"
        cache.get(key)?.let { return@withContext parse(it) }
        val url = "https://api.dictionaryapi.dev/api/v2/entries/en/${Http.urlEncode(word)}"
        val body = Http.get(url)
        cache.put(key, body)
        parse(body)
    }

    private fun parse(body: String): FreeApiEntry? = runCatching {
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        var phonetic: String? = null
        var audio: String? = null
        val meanings = ArrayList<com.wordlens.app.data.model.EnMeaning>()
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            if (phonetic.isNullOrBlank()) phonetic = entry.optString("phonetic", "").takeIf { it.isNotBlank() }
            val phons = entry.optJSONArray("phonetics") ?: JSONArray()
            for (j in 0 until phons.length()) {
                val p = phons.getJSONObject(j)
                if (phonetic.isNullOrBlank()) phonetic = p.optString("text", "").takeIf { it.isNotBlank() }
                val a = p.optString("audio", "").takeIf { it.isNotBlank() }
                if (audio.isNullOrBlank()) audio = a
            }
            val ms = entry.optJSONArray("meanings") ?: continue
            for (j in 0 until ms.length()) {
                val m = ms.getJSONObject(j)
                val defs = ArrayList<com.wordlens.app.data.model.EnDefinition>()
                val ds = m.optJSONArray("definitions") ?: continue
                for (k in 0 until ds.length().coerceAtMost(6)) {
                    val d = ds.getJSONObject(k)
                    defs += com.wordlens.app.data.model.EnDefinition(
                        text = d.optString("definition", ""),
                        example = d.optString("example", "").takeIf { it.isNotBlank() },
                        synonyms = optStringList(d.optJSONArray("synonyms"))
                    )
                }
                if (defs.isNotEmpty()) {
                    meanings += com.wordlens.app.data.model.EnMeaning(
                        partOfSpeech = m.optString("partOfSpeech", "").ifBlank { "-" },
                        definitions = defs
                    )
                }
            }
        }
        if (meanings.isEmpty() && phonetic.isNullOrBlank()) null
        else FreeApiEntry(phonetic, audio, meanings)
    }.getOrNull()

    private fun optStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out += arr.optString(i, "")
        return out.filter { it.isNotBlank() }
    }
}

/** AI 请求的三种维度 */
enum class AiKind(val id: String, val label: String) {
    EXPLAIN("explain", "AI 详解"),
    EXAMPLES("examples", "AI 例句"),
    COLLOCATIONS("collocations", "AI 搭配")
}

/** OpenAI 兼容的对话接口：词卡解析 + 整篇精读 */
class LlmClient(private val settings: com.wordlens.app.data.settings.AppSettings) {

    suspend fun explain(
        word: String,
        sentence: String?,
        kind: AiKind = AiKind.EXPLAIN
    ): Result<String> = withContext(Dispatchers.IO) {
        val s = settings.state.value
        if (!s.aiReady) return@withContext Result.failure(IllegalStateException("未配置 AI 接口"))
        val system = when (kind) {
            AiKind.EXPLAIN ->
                "你是英语老师。用中文讲清这个英文词在句子里的意思，格式固定四段（每段一行，前缀用【】：" +
                    "【音标与词性】【中文释义】【例句】(给 1 个带翻译的新例句)【近义与搭配】。只讲这个单词本身，不寒暄。"
            AiKind.EXAMPLES ->
                "你是英语老师。为这个英文词造 3 个难度递进、场景不同的例句，" +
                    "每行格式：英文例句 —— 中文翻译。最后一行用「【搭配】」列出 2-3 个高频搭配。不要解释词义。"
            AiKind.COLLOCATIONS ->
                "你是英语老师。只讲这个英文词的用法：先列 4-6 个高频搭配（动词/介词/名词组合），" +
                    "每个给一句极短的中文说明；再给 1-2 组易混词辨析（和哪个词容易混、区别是什么）。格式紧凑，不要例句。"
        }
        ask(system, buildString {
            append("单词：").append(word)
            if (!sentence.isNullOrBlank()) append("\n上下文：").append(sentence.take(240))
        }, 700)
    }

    /** 整篇精读：主旨 + 重点词 + 难句 + 复习建议 */
    suspend fun analyzeArticle(text: String): Result<String> = withContext(Dispatchers.IO) {
        val s = settings.state.value
        if (!s.aiReady) return@withContext Result.failure(IllegalStateException("未配置 AI 接口"))
        val system =
            "你是英语精读老师。读用户给的英文短文，用中文输出精读笔记，严格按下面的格式，" +
                "总长度控制在 600 字内，不要复述原文，不要寒暄：\n" +
                "【主旨】一句话概括这段在讲什么。\n" +
                "【重点词】挑 4-6 个对理解最关键的词，每行「单词 词性 中文 — 为什么重要(10 字内)」。\n" +
                "【难句】挑 2-3 句结构复杂的，每句先给中文意思，再用一句话说清结构。\n" +
                "【复习建议】一句话，说读完这段该重点记什么。"
        ask(system, text.take(3000), 900)
    }

    private fun ask(system: String, user: String, maxTokens: Int): Result<String> = runCatching {
        val s = settings.state.value
        val payload = JSONObject().apply {
            put("model", s.aiModel)
            put("temperature", 0.3)
            put("max_tokens", maxTokens)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                }
            )
        }
        val body = Http.postJson(
            s.aiBaseUrl.trimEnd('/') + "/chat/completions",
            payload.toString(),
            headers = mapOf("Authorization" to "Bearer ${s.aiApiKey}"),
            timeoutMs = 60000
        )
        JSONObject(body)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }
}

/** 网络结果的本地缓存（存 AppDatabase.web_cache 表）*/
interface Cache {
    suspend fun get(key: String): String?
    suspend fun put(key: String, payload: String)
}
