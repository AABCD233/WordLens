package com.wordlens.app.data.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 词根 / 词族小库（assets/morph.db，约 2.4 MB）。
 *
 * 和词典包完全解耦：只按单词字符串查，换任何词典都能用，查不到就返回空。
 * 数据来源 morphemelexicon.com（非商用引用，见 README 致谢）。
 */
class MorphStore(private val context: Context) {

    @Volatile
    private var db: SQLiteDatabase? = null

    private fun open(): SQLiteDatabase? = db ?: synchronized(this) {
        db ?: runCatching {
            val file = File(context.filesDir, "morph.db")
            if (!file.exists() || file.length() == 0L) {
                context.assets.open("morph.db").use { input ->
                    file.outputStream().use { out -> input.copyTo(out) }
                }
            }
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .also { db = it }
        }.getOrNull()
    }

    /** 同词族的派生词（排除 -s/-ed/-ing 这类屈折变化，那些已经在「词形变化」里） */
    suspend fun derivatives(word: String): List<String> = withContext(Dispatchers.IO) {
        val d = open() ?: return@withContext emptyList()
        val w = word.lowercase()
        val lemma = runCatching {
            d.rawQuery("SELECT lemma FROM family WHERE word=? LIMIT 1", arrayOf(w)).use {
                if (it.moveToFirst()) it.getString(0) else w
            }
        }.getOrDefault(w)
        val raw = runCatching {
            d.rawQuery(
                "SELECT word, inflection FROM family WHERE lemma=? AND word<>? ORDER BY length(word) LIMIT 40",
                arrayOf(lemma, w)
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(c.getString(0) to (c.getInt(1) == 1))
                }
            }
        }.getOrDefault(emptyList())

        val family = (raw.map { it.first } + lemma).toSet()
        // 站点对部分屈折形式没打标记（happier/happiest），这里再按后缀+词族兜一层
        raw.asSequence()
            .filter { (form, flagged) -> !flagged && !isInflection(form, family) }
            .map { it.first }
            .take(12)
            .toList()
    }

    /** 去掉屈折后缀后还在同一词族里，就当它是屈折变化而不是派生词 */
    private fun isInflection(form: String, family: Set<String>): Boolean {
        for (suf in listOf("iest", "ier", "est", "ing", "ed", "es", "er", "s", "d")) {
            if (!form.endsWith(suf) || form.length <= suf.length + 2) continue
            val base = form.dropLast(suf.length)
            if (base in family) return true
            // 双写尾字母：planned -> plan，bigger -> big
            if (base.length > 2 && base[base.length - 1] == base[base.length - 2]) {
                if (base.dropLast(1) in family) return true
            }
            // 去 e 加 ing/er：making -> make
            if ((base + "e") in family) return true
        }
        return false
    }

    /**
     * 词缀 / 词根提示。只做「高置信度」匹配：
     *  - 前缀：词以它开头，且长度 >= 3
     *  - 后缀：词以它结尾，且长度 >= 3
     *  - 词根：词以它开头且长度 >= 4，最多给 2 条
     */
    suspend fun morphology(word: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val d = open() ?: return@withContext emptyList()
        val w = word.lowercase()
        if (w.length < 5) return@withContext emptyList()
        val out = mutableListOf<Pair<String, String>>()

        runCatching {
            d.rawQuery(
                "SELECT affix, type, zh FROM affix WHERE length(affix)>=3 AND ? LIKE affix || '%' " +
                    "AND type='prefix' ORDER BY length(affix) DESC LIMIT 2",
                arrayOf(w)
            ).use { c ->
                while (c.moveToNext()) out.add(c.getString(0) to "前缀 · ${c.getString(2)}")
            }
            d.rawQuery(
                "SELECT affix, type, zh FROM affix WHERE length(affix)>=3 AND ? LIKE '%' || affix " +
                    "AND type='suffix' ORDER BY length(affix) DESC LIMIT 2",
                arrayOf(w)
            ).use { c ->
                while (c.moveToNext()) out.add(c.getString(0) to "后缀 · ${c.getString(2)}")
            }
            d.rawQuery(
                "SELECT root, zh FROM root WHERE length(root)>=4 AND ? LIKE root || '%' " +
                    "AND length(root) <= length(?)-2 " +
                    "ORDER BY length(root) DESC, freq DESC LIMIT 2",
                arrayOf(w, w)
            ).use { c ->
                while (c.moveToNext()) {
                    val zh = c.getString(1).ifBlank { "" }
                    if (zh.isBlank()) continue
                    out.add(c.getString(0) + "-" to "词根 · $zh")
                }
            }
        }
        out.distinctBy { it.first }.take(4)
    }
}
