package com.wordlens.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.wordlens.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 一个可切换的离线词典包。
 *
 * 内置包（ECDICT 核心 11 万词）随 APK 走；扩展包由用户按需下载到
 * `files/dicts/<id>.db`，不用重新装 APK 也能把词库换大。
 */
data class DictPack(
    val id: String,
    val name: String,
    val file: File,
    val words: Int,
    val builtIn: Boolean
) {
    val sizeMb: String get() = String.format("%.1f MB", file.length() / 1e6)
}

/**
 * 词典包的安装 / 切换 / 删除。
 *
 * 包格式就是 `dict.db`（SQLite，表结构见 data/dict/build_dict.py），
 * 允许直接下 `.db`，也允许下 `.gz`（手机流量友好，省一半多）。
 */
class DictPackManager(
    context: Context,
    private val store: DictStore,
    private val settings: AppSettings
) {

    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "dicts").apply { mkdirs() }

    /** 正在用的包 id；空串 = 内置包 */
    fun activeId(): String = settings.state.value.activePackId

    fun installed(): List<DictPack> {
        val out = ArrayList<DictPack>()
        out += builtInPack()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".db") }?.sortedBy { it.name }?.forEach { f ->
            out += DictPack(
                id = f.name.removeSuffix(".db"),
                name = f.name.removeSuffix(".db"),
                file = f,
                words = countWords(f),
                builtIn = false
            )
        }
        return out
    }

    fun builtInPack(): DictPack {
        val f = store.builtInFile()
        return DictPack(id = BUILTIN_ID, name = "内置核心词典", file = f, words = 0, builtIn = true)
    }

    /**
     * 从网址下载一个词典包并切换过去。
     * @param onProgress 已下载字节数回调（在 IO 线程，别直接改 UI 状态之外的东西）
     */
    suspend fun install(
        url: String,
        name: String,
        onProgress: (Long) -> Unit = {}
    ): DictPack = install(listOf(url), name, onProgress)

    /**
     * 依次尝试多个地址，前一个失败就换下一个。
     * 国内直连 GitHub Release 经常断，官方包会同时给加速镜像（gh-proxy 之类）。
     */
    suspend fun install(
        urls: List<String>,
        name: String,
        onProgress: (Long) -> Unit = {}
    ): DictPack {
        var last: Throwable? = null
        for ((i, u) in urls.withIndex()) {
            try {
                return installOne(u, name, onProgress)
            } catch (t: Throwable) {
                last = t
                onProgress(-(i + 1))   // 负数表示「第 i+1 个源失败，正在换源」
            }
        }
        throw last ?: IllegalStateException("没有可用的下载地址")
    }

    private suspend fun installOne(
        url: String,
        name: String,
        onProgress: (Long) -> Unit
    ): DictPack = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "网址要以 http(s):// 开头" }
        dir.mkdirs()
        val safe = (name.ifBlank { "词典包" })
            .replace(Regex("[^0-9A-Za-z\\u4e00-\\u9fa5_-]"), "_")
            .take(40)
        val tmp = File(dir, "$safe.part")
        val target = uniqueTarget(safe)

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WordLens/1.1 (dict pack)")
                setRequestProperty("Accept-Encoding", "identity")
            }
            val code = conn.responseCode
            if (code !in 200..299) error("下载失败：HTTP $code")

            var input: InputStream = conn.inputStream
            // 看前两个字节判断是不是 gzip
            val head = ByteArray(2)
            var headLen = 0
            while (headLen < 2) {
                val n = input.read(head, headLen, 2 - headLen)
                if (n <= 0) break
                headLen += n
            }
            val isGzip = headLen == 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
            input = if (isGzip) {
                GZIPInputStream(java.io.SequenceInputStream(head.inputStream(), input))
            } else {
                java.io.SequenceInputStream(head.inputStream(), input)
            }

            var total = 0L
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total % (2L * 1024 * 1024) < buf.size) onProgress(total)
                }
            }
            onProgress(total)
            checkSqlite(tmp)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val words = countWords(target)
            if (words <= 0) {
                target.delete()
                error("这个文件不像词典包（里面没有 dict 表）")
            }
            settings.update { it.copy(activePackId = target.name.removeSuffix(".db")) }
            store.usePack(target)
            DictPack(target.name.removeSuffix(".db"), safe, target, words, false)
        } finally {
            runCatching { conn?.disconnect() }
            tmp.delete()
        }
    }

    suspend fun activate(pack: DictPack) = withContext(Dispatchers.IO) {
        if (pack.builtIn) {
            settings.update { it.copy(activePackId = "") }
            store.useBuiltIn()
        } else {
            settings.update { it.copy(activePackId = pack.id) }
            store.usePack(pack.file)
        }
        Unit
    }

    suspend fun delete(pack: DictPack) = withContext(Dispatchers.IO) {
        if (pack.builtIn) return@withContext
        if (pack.id == activeId()) {
            settings.update { it.copy(activePackId = "") }
            store.useBuiltIn()
        }
        pack.file.delete()
    }

    private fun uniqueTarget(base: String): File {
        var f = File(dir, "$base.db")
        var i = 2
        while (f.exists()) {
            f = File(dir, "${base}_$i.db")
            i++
        }
        return f
    }

    /** SQLite 文件头是 "SQLite format 3\0"，先挡掉下错文件的情况 */
    private fun checkSqlite(file: File) {
        require(file.length() > 512) { "下载到的文件太小，不可能是词典包" }
        val head = ByteArray(16)
        file.inputStream().use { it.read(head) }
        val magic = String(head, Charsets.US_ASCII)
        require(magic.startsWith("SQLite format 3")) {
            "这个文件不是 SQLite 词典（可能下到了网页或压缩包）"
        }
    }

    private fun countWords(file: File): Int = runCatching {
        if (!file.exists() || file.length() < 512) return 0
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(1) FROM dict", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    }.getOrDefault(0)

    companion object {
        const val BUILTIN_ID = "builtin"

        /** 官方全量词典包的原始地址 */
        const val OFFICIAL_PACK_URL =
            "https://github.com/AABCD233/WordLens/releases/download/dict-v1/dict-full.db.gz"

        /**
         * 按「先快后稳」排的候选源：gh-proxy 系镜像走一遍，最后才直连 GitHub。
         * 任何一个成功就用它。
         */
        val OFFICIAL_PACK_MIRRORS: List<String> = listOf(
            "https://v4.gh-proxy.org/" + OFFICIAL_PACK_URL,
            "https://ghfast.top/" + OFFICIAL_PACK_URL,
            "https://gh-proxy.com/" + OFFICIAL_PACK_URL,
            OFFICIAL_PACK_URL
        )
    }
}
