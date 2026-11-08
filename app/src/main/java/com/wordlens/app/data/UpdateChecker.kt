package com.wordlens.app.data

import com.wordlens.app.BuildConfig
import com.wordlens.app.data.dict.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 查 GitHub 上的最新 Release，和当前版本比一下 */
class UpdateChecker(private val accelEnabled: () -> Boolean) {

    data class Latest(val tag: String, val url: String, val newer: Boolean)

    private val api = "https://api.github.com/repos/AABCD233/WordLens/releases/latest"

    suspend fun check(): Result<Latest> = withContext(Dispatchers.IO) {
        val candidates = AccelMirrors.candidates(api, accelEnabled())
        var last: Throwable? = null
        for (u in candidates) {
            val r = runCatching {
                val body = Http.get(u, headers = mapOf("Accept" to "application/vnd.github+json"))
                val o = JSONObject(body)
                val tag = o.optString("tag_name", "").removePrefix("v")
                val url = o.optString("html_url", "https://github.com/AABCD233/WordLens/releases")
                if (tag.isBlank()) error("没有版本号")
                Latest(tag = tag, url = url, newer = isNewer(tag, BuildConfig.VERSION_NAME))
            }
            if (r.isSuccess) return@withContext r
            last = r.exceptionOrNull()
        }
        Result.failure(last ?: IllegalStateException("检查更新失败"))
    }

    /** 简单版本号比较：1.10.0 > 1.9.9 */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").mapNotNull { it.toIntOrNull() }
        val b = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
