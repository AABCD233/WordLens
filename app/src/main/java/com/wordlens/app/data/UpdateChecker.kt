package com.wordlens.app.data

import com.wordlens.app.BuildConfig
import com.wordlens.app.data.dict.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 查 GitHub 上的最新 Release，和当前版本比一下 */
class UpdateChecker(private val accelEnabled: () -> Boolean) {

    data class Latest(
        val tag: String,
        val url: String,
        val newer: Boolean,
        /** 直链 APK；没有附件时为 null */
        val apkUrl: String? = null
    )

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
                val apks = o.optJSONArray("assets")?.let { arr ->
                    (0 until arr.length())
                        .map { arr.optJSONObject(it)?.optString("browser_download_url").orEmpty() }
                        .filter { it.endsWith(".apk", ignoreCase = true) }
                }.orEmpty()
                // 每个架构一个包：按本机支持顺序挑，arm64 优先，挑不到就退回默认包
                val apk = pickApkForThisDevice(apks)
                if (tag.isBlank()) error("没有版本号")
                Latest(
                    tag = tag,
                    url = url,
                    newer = isNewer(tag, BuildConfig.VERSION_NAME),
                    apkUrl = apk
                )
            }
            if (r.isSuccess) return@withContext r
            last = r.exceptionOrNull()
        }
        Result.failure(last ?: IllegalStateException("检查更新失败"))
    }

    /** 从 Release 附件里挑出适配本机架构的 APK */
    private fun pickApkForThisDevice(apks: List<String>): String? {
        if (apks.isEmpty()) return null
        val mine = android.os.Build.SUPPORTED_ABIS.map { it.lowercase() }
        for (abi in mine) {
            apks.firstOrNull { it.lowercase().contains(abi) }?.let { return it }
        }
        // 没有匹配的架构包：优先给 arm64，再给不带架构名的通用包
        return apks.firstOrNull { it.contains("arm64", true) }
            ?: apks.firstOrNull { !it.contains("x86", true) && !it.contains("v7a", true) }
            ?: apks.first()
    }

    /** 下载 APK 到 dest（支持加速镜像与断点续传），返回文件 */
    suspend fun download(
        apkUrl: String,
        dest: java.io.File,
        onProgress: (downloadedMb: Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (u in AccelMirrors.candidates(apkUrl, accelEnabled())) {
            val r = runCatching {
                val conn = (java.net.URL(u).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode !in 200..299) {
                    conn.disconnect(); error("HTTP ${conn.responseCode}")
                }
                var written = 0L
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(written / (1024 * 1024))
                        }
                    }
                }
                conn.disconnect()
                if (written < 1024) error("文件太小，可能不是安装包")
                dest
            }
            if (r.isSuccess) return@withContext r
            dest.delete()
            last = r.exceptionOrNull()
        }
        Result.failure(last ?: IllegalStateException("下载失败"))
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
