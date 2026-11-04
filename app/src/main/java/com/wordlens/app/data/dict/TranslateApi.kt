package com.wordlens.app.data.dict

import com.wordlens.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 在线翻译源。每个引擎都有自己的鉴权方式，这里统一成 `translate(text)`。
 *
 * 免费 / 免配置的是 MyMemory；其余需要在设置里填自己的 key，
 * 好处是不会因为某个公共服务下线就整个功能不可用。
 */
enum class TranslateEngine(val id: String, val label: String, val hint: String) {
    MYMEMORY("mymemory", "MyMemory", "免费，无需配置；整段有长度上限"),
    AI("ai", "AI 接口", "用「AI 详解」里的配置，译文最通顺"),
    YOUDAO("youdao", "有道智云", "需应用 ID + 应用密钥（openapi.youdao.com）"),
    BAIDU("baidu", "百度翻译", "需 APP ID + 密钥（fanyi-api.baidu.com）"),
    AZURE("azure", "必应 / Azure", "需订阅密钥 + 区域（portal.azure.com）"),
    GOOGLE("google", "Google 翻译", "免配置，部分网络环境不可达");

    companion object {
        fun of(id: String?): TranslateEngine =
            values().firstOrNull { it.id == id } ?: MYMEMORY
    }
}

/**
 * 多引擎翻译。所有结果按「引擎 + 原文」缓存，换引擎不会拿到旧译文。
 */
class TranslateApi(
    private val cache: Cache,
    private val settings: AppSettings
) {

    /** 供词条卡片和全文翻译共用 */
    suspend fun translate(text: String, from: String = "en", to: String = "zh-CN"): Result<String> =
        withContext(Dispatchers.IO) {
            val clean = text.trim()
            if (clean.isEmpty()) return@withContext Result.success("")
            val engine = TranslateEngine.of(settings.state.value.translateEngine)
            val key = "mt:${engine.id}:${from}>${to}:" + clean.hashCode() + ":" + clean.length
            cache.get(key)?.let { return@withContext Result.success(it) }

            val out = runCatching { dispatch(engine, clean, from, to) }
            out.getOrNull()?.takeIf { it.isNotBlank() }?.let { cache.put(key, it) }
            if (out.isSuccess) Result.success(out.getOrThrow()) else {
                Result.failure(out.exceptionOrNull() ?: IllegalStateException("翻译失败"))
            }
        }

    private fun dispatch(engine: TranslateEngine, text: String, from: String, to: String): String =
        when (engine) {
            TranslateEngine.MYMEMORY -> myMemory(text, from, to)
            TranslateEngine.AI -> aiTranslate(text)
            TranslateEngine.YOUDAO -> youdao(text, to)
            TranslateEngine.BAIDU -> baidu(text, from, to)
            TranslateEngine.AZURE -> azure(text, from, to)
            TranslateEngine.GOOGLE -> google(text, from, to)
        }

    // ---------------- 各引擎 ----------------

    private fun myMemory(text: String, from: String, to: String): String {
        val body = Http.get(
            "https://api.mymemory.translated.net/get?q=${Http.urlEncode(text)}" +
                "&langpair=${Http.urlEncode(from)}|${Http.urlEncode(to)}"
        )
        val obj = JSONObject(body)
        val txt = obj.optJSONObject("responseData")?.optString("translatedText", "").orEmpty().trim()
        val status = obj.optInt("responseStatus", 200)
        if (status != 200) throw IllegalStateException("MyMemory 返回 $status")
        if (txt.contains("MYMEMORY WARNING", true)) throw IllegalStateException("MyMemory 今日额度用完了")
        if (txt.isBlank() || txt.equals(text, true)) throw IllegalStateException("MyMemory 没有给出译文")
        return txt
    }

    private fun google(text: String, from: String, to: String): String {
        val body = Http.get(
            "https://translate.googleapis.com/translate_a/single?client=gtx" +
                "&sl=$from&tl=$to&dt=t&q=${Http.urlEncode(text)}"
        )
        val arr = JSONArray(body).optJSONArray(0) ?: throw IllegalStateException("Google 返回格式不对")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            sb.append(arr.optJSONArray(i)?.optString(0, "").orEmpty())
        }
        return sb.toString().trim().ifBlank { throw IllegalStateException("Google 没有给出译文") }
    }

    private fun baidu(text: String, from: String, to: String): String {
        val s = settings.state.value
        if (s.baiduAppId.isBlank() || s.baiduKey.isBlank()) {
            throw IllegalStateException("还没填百度翻译的 APP ID / 密钥")
        }
        val salt = System.currentTimeMillis().toString()
        val sign = md5(s.baiduAppId + text + salt + s.baiduKey)
        val langTo = if (to.startsWith("zh")) "zh" else to
        val body = Http.postForm(
            "https://fanyi-api.baidu.com/api/trans/vip/translate",
            mapOf(
                "q" to text, "from" to from, "to" to langTo,
                "appid" to s.baiduAppId, "salt" to salt, "sign" to sign
            )
        )
        val obj = JSONObject(body)
        obj.optString("error_code", "").takeIf { it.isNotBlank() }?.let {
            throw IllegalStateException("百度翻译错误 $it：${obj.optString("error_msg", "")}")
        }
        val arr = obj.optJSONArray("trans_result") ?: throw IllegalStateException("百度翻译没有结果")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            sb.append(arr.getJSONObject(i).optString("dst", ""))
            if (i < arr.length() - 1) sb.append('\n')
        }
        return sb.toString().trim().ifBlank { throw IllegalStateException("百度翻译没有给出译文") }
    }

    private fun youdao(text: String, to: String): String {
        val s = settings.state.value
        if (s.youdaoAppKey.isBlank() || s.youdaoAppSecret.isBlank()) {
            throw IllegalStateException("还没填有道智云的应用 ID / 应用密钥")
        }
        val salt = java.util.UUID.randomUUID().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()
        val input = truncateForYoudao(text)
        val sign = sha256(s.youdaoAppKey + input + salt + curtime)
        val langTo = if (to.startsWith("zh")) "zh-CHS" else to
        val body = Http.postForm(
            "https://openapi.youdao.com/api",
            mapOf(
                "q" to text, "from" to "en", "to" to langTo,
                "appKey" to s.youdaoAppKey, "salt" to salt,
                "sign" to sign, "signType" to "v3", "curtime" to curtime
            )
        )
        val obj = JSONObject(body)
        obj.optString("errorCode", "0").takeIf { it != "0" }?.let {
            throw IllegalStateException("有道智云错误码 $it")
        }
        val arr = obj.optJSONArray("translation") ?: throw IllegalStateException("有道没有结果")
        return arr.optString(0, "").trim().ifBlank { throw IllegalStateException("有道没有给出译文") }
    }

    /** 有道 v3 签名要求：超过 20 字符时取「前 10 + 长度 + 后 10」 */
    private fun truncateForYoudao(q: String): String =
        if (q.length <= 20) q else q.substring(0, 10) + q.length + q.substring(q.length - 10)

    private fun azure(text: String, from: String, to: String): String {
        val s = settings.state.value
        if (s.azureKey.isBlank()) throw IllegalStateException("还没填 Azure 翻译的订阅密钥")
        val langTo = if (to.startsWith("zh")) "zh-Hans" else to
        val body = Http.postJson(
            "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=$from&to=$langTo",
            JSONArray().put(JSONObject().put("Text", text)).toString(),
            headers = buildMap {
                put("Ocp-Apim-Subscription-Key", s.azureKey)
                if (s.azureRegion.isNotBlank()) put("Ocp-Apim-Subscription-Region", s.azureRegion)
            }
        )
        val arr = JSONArray(body)
        val tr = arr.optJSONObject(0)?.optJSONArray("translations")
            ?: throw IllegalStateException("Azure 没有结果")
        return tr.optJSONObject(0)?.optString("text", "").orEmpty().trim()
            .ifBlank { throw IllegalStateException("Azure 没有给出译文") }
    }

    private fun aiTranslate(text: String): String {
        val s = settings.state.value
        if (!s.aiReady) throw IllegalStateException("还没配置 AI 接口")
        val payload = JSONObject().apply {
            put("model", s.aiModel)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put(
                    "content",
                    "你是英译中译者。把用户给的英文译成自然、地道的简体中文。" +
                        "只输出译文本身，不要解释、不要引号、不要加「译文：」这类前缀。"
                ))
                put(JSONObject().put("role", "user").put("content", text))
            })
        }
        val body = Http.postJson(
            s.aiBaseUrl.trimEnd('/') + "/chat/completions",
            payload.toString(),
            headers = mapOf("Authorization" to "Bearer ${s.aiApiKey}"),
            timeoutMs = 60000
        )
        return JSONObject(body)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }

    // ---------------- 摘要 ----------------

    private fun md5(s: String): String = digest("MD5", s)
    private fun sha256(s: String): String = digest("SHA-256", s)

    private fun digest(alg: String, s: String): String =
        MessageDigest.getInstance(alg).digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * 有道词典的公开网页接口，用来给离线词典「补中文」。
 * 只在用户手动打开时调用（默认关闭），一次只取音标 + 前几条中文释义。
 */
class YoudaoDictApi(private val cache: Cache) {

    data class Entry(
        val phonetic: String?,
        val zhLines: List<String>,
        val examples: List<com.wordlens.app.data.model.DefExample> = emptyList()
    )

    suspend fun lookup(word: String): Entry? = withContext(Dispatchers.IO) {
        val key = "yd:${word.lowercase()}"
        cache.get(key)?.let { return@withContext parse(it) }
        val body = runCatching {
            Http.get(
                "https://dict.youdao.com/jsonapi?q=${Http.urlEncode(word)}",
                headers = mapOf("Referer" to "https://dict.youdao.com/")
            )
        }.getOrNull() ?: return@withContext null
        parse(body)?.also { cache.put(key, body) }
    }

    private fun parse(body: String): Entry? = runCatching {
        val root = JSONObject(body)
        val words = root.optJSONObject("ec")?.optJSONArray("word") ?: return null
        if (words.length() == 0) return null
        val w = words.getJSONObject(0)
        val phone = w.optString("ukphone", "").ifBlank { w.optString("usphone", "") }
            .takeIf { it.isNotBlank() }
        val trs = w.optJSONArray("trs") ?: return null
        val lines = ArrayList<String>(trs.length())
        for (i in 0 until trs.length().coerceAtMost(8)) {
            val tr = trs.getJSONObject(i).optJSONArray("tr") ?: continue
            val item = tr.optJSONObject(0)?.optJSONObject("l")?.optJSONArray("i") ?: continue
            val text = item.optString(0, "").trim()
            if (text.isNotBlank()) lines += text
        }
        // 双语例句：blng_sents_part.sentence-pair[]，自带中文翻译
        val examples = ArrayList<com.wordlens.app.data.model.DefExample>()
        val pairs = root.optJSONObject("blng_sents_part")?.optJSONArray("sentence-pair")
        if (pairs != null) {
            for (i in 0 until pairs.length().coerceAtMost(4)) {
                val p = pairs.optJSONObject(i) ?: continue
                val en = p.optString("sentence", "").trim()
                val zh = p.optString("sentence-translation", "").trim()
                if (en.isNotBlank()) {
                    examples += com.wordlens.app.data.model.DefExample(en, zh.ifBlank { null })
                }
            }
        }
        if (lines.isEmpty() && phone == null && examples.isEmpty()) null
        else Entry(phone, lines, examples)
    }.getOrNull()
}
