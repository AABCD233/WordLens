package com.wordlens.app.data.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.wordlens.app.data.model.DictRow
import com.wordlens.app.data.model.WordNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 离线词典状态，UI 据此显示「正在准备离线词典」或「词典不可用」 */
enum class DictStatus { IDLE, PREPARING, READY, MISSING }

/**
 * 只读的离线英汉词典（APK 资产 dict.db，首次使用时解包到 filesDir）。
 * 表结构见 data/dict/build_dict.py。
 */
class DictStore(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var handle: SQLiteDatabase? = null

    private val _status = MutableStateFlow(DictStatus.IDLE)
    val status: StateFlow<DictStatus> = _status.asStateFlow()

    private val targetFile: File get() = File(appContext.filesDir, "dict/dict.db")

    /** 当前打开的词典文件：默认内置包，切包后指向 files/dicts/<id>.db */
    @Volatile
    private var activeFile: File = targetFile

    /** 内置包解包后的位置，给词典包管理器用 */
    fun builtInFile(): File = targetFile

    /** 换一个词典包（文件必须已就位） */
    fun usePack(file: File) {
        synchronized(lock) {
            runCatching { handle?.close() }
            handle = null
            activeFile = file
            _status.value = DictStatus.IDLE
        }
    }

    /** 切回内置包 */
    fun useBuiltIn() = usePack(targetFile)

    /** 首次调用会把资产里的词典解包出来；已经在位就秒返回 */
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (handle != null && _status.value == DictStatus.READY) return@withContext true
            _status.value = DictStatus.PREPARING
            try {
                // 切到扩展包时，内置包也需要先在位（用户可能中途切回去）
                if (!isUpToDate()) unpack()
                val file = activeFile.takeIf { it.exists() && it.length() > 512 } ?: targetFile
                handle = SQLiteDatabase.openDatabase(
                    file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                _status.value = DictStatus.READY
                true
            } catch (t: Throwable) {
                _status.value = DictStatus.MISSING
                false
            }
        }
    }

    private fun isUpToDate(): Boolean {
        if (!targetFile.exists()) return false
        val saved = prefs().getLong(PREF_SIZE, -1L)
        return saved >= 0 && saved == targetFile.length()
    }

    private fun prefs() =
        appContext.getSharedPreferences("wordlens_dict", Context.MODE_PRIVATE)

    private fun unpack() {
        val fd = try {
            // aapt 会把 .db 压缩，此时 openFd 会抛异常 —— 走下面的流式分支即可
            appContext.assets.openFd(ASSET_NAME)
        } catch (t: Throwable) {
            // 压缩资产 / 部分 ROM 不支持 openFd：退化成流式解压写出
            appContext.assets.open(ASSET_NAME).use { input ->
                writeOut(input, expectedSize = -1L)
            }
            return
        }
        fd.use { descriptor ->
            descriptor.createInputStream().use { input ->
                writeOut(input, descriptor.startOffset + descriptor.length)
            }
        }
    }

    private fun writeOut(input: java.io.InputStream, expectedSize: Long) {
        targetFile.parentFile?.mkdirs()
        val tmp = File(targetFile.parentFile, "dict.db.tmp")
        FileOutputStream(tmp).use { out -> input.copyTo(out, DEFAULT_BUF) }
        if (expectedSize > 0 && tmp.length() != expectedSize) {
            tmp.delete()
            error("词典解包大小不符：${tmp.length()} != $expectedSize")
        }
        if (!tmp.renameTo(targetFile)) {
            tmp.copyTo(targetFile, overwrite = true, bufferSize = DEFAULT_BUF)
            tmp.delete()
        }
        prefs().edit().putLong(PREF_SIZE, targetFile.length()).apply()
    }

    private fun dbOrNull(): SQLiteDatabase? = handle

    suspend fun lookup(word: String): DictRow? = withContext(Dispatchers.IO) {
        val db = dbOrNull() ?: return@withContext null
        query(db, "SELECT * FROM dict WHERE word = ? LIMIT 1", arrayOf(word.lowercase()))
    }

    /**
     * 先按原词查，查不到再按词形还原候选（studying -> study, wolves -> wolf）。
     * 返回「命中行 + 实际命中的词形」，UI 会显示「study 的现在分词」这类提示。
     */
    suspend fun lookupWithLemma(raw: String): LemmaHit? = withContext(Dispatchers.IO) {
        val db = dbOrNull() ?: return@withContext null
        val normalized = WordNormalizer.normalize(raw)
        if (normalized.isEmpty()) return@withContext null
        for (candidate in (listOf(normalized) + WordNormalizer.lemmaCandidates(raw)).distinct()) {
            val row = query(db, "SELECT * FROM dict WHERE word = ? LIMIT 1", arrayOf(candidate))
            if (row != null) {
                return@withContext LemmaHit(row = row, matchedWord = candidate, isOriginal = candidate == normalized)
            }
        }
        null
    }

    /** 输入框联想 */
    suspend fun suggest(prefix: String, limit: Int = 20): List<DictRow> = withContext(Dispatchers.IO) {
        val db = dbOrNull() ?: return@withContext emptyList()
        val p = WordNormalizer.normalize(prefix)
        if (p.length < 2) return@withContext emptyList()
        val cursor = db.rawQuery(
            "SELECT * FROM dict WHERE word LIKE ? ORDER BY length(word), word LIMIT ?",
            arrayOf("$p%", limit.toString())
        )
        cursor.use { collect(cursor) }
    }

    suspend fun totalWords(): Int = withContext(Dispatchers.IO) {
        val db = dbOrNull() ?: return@withContext 0
        db.rawQuery("SELECT COUNT(1) FROM dict", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun query(db: SQLiteDatabase, sql: String, args: Array<String>): DictRow? =
        db.rawQuery(sql, args).use { if (it.moveToFirst()) it.toDictRow() else null }

    private fun collect(cursor: Cursor): List<DictRow> {
        val out = ArrayList<DictRow>(cursor.count)
        while (cursor.moveToNext()) out.add(cursor.toDictRow())
        return out
    }

    private fun Cursor.toDictRow() = DictRow(
        word = getString(getColumnIndexOrThrow("word")),
        phonetic = getStringOrEmpty("phonetic"),
        translation = getStringOrEmpty("translation"),
        definition = getStringOrEmpty("definition"),
        tag = getStringOrEmpty("tag"),
        exchange = getStringOrEmpty("exchange"),
        collins = getInt(getColumnIndexOrThrow("collins")),
        oxford = getInt(getColumnIndexOrThrow("oxford")),
        freq = getInt(getColumnIndexOrThrow("frq"))
    )

    private fun Cursor.getStringOrEmpty(name: String): String {
        val i = getColumnIndex(name)
        return if (i < 0 || isNull(i)) "" else getString(i) ?: ""
    }

    data class LemmaHit(val row: DictRow, val matchedWord: String, val isOriginal: Boolean)

    private companion object {
        const val ASSET_NAME = "dict.db"
        const val PREF_SIZE = "dict_size"
        const val DEFAULT_BUF = 128 * 1024
    }
}
