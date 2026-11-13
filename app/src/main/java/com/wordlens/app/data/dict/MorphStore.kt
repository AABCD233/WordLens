package com.wordlens.app.data.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 一条词素（前缀/词根/后缀），全部来自标注数据，不做拼写推测 */
data class MorphHint(
    val form: String,
    val role: String
)

/**
 * 词根 / 词族小库（assets/morph.db，约 4 MB），和词典包解耦，换任何词典都能用。
 *
 * 数据来源：
 *  - 词素切分：CityLex（经 morphoneme 仓库整理），只收它，umLabeller 有实测错误已弃用
 *  - 同族词：morphemelexicon.com
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
                buildList { while (c.moveToNext()) add(c.getString(0) to (c.getInt(1) == 1)) }
            }
        }.getOrDefault(emptyList())

        val family = (raw.map { it.first } + lemma).toSet()
        raw.asSequence()
            .filter { (form, flagged) -> !flagged && !isInflection(form, family) }
            .map { it.first }
            .take(12)
            .toList()
    }

    /**
     * 词素切分：CityLex 标注，形如 `<un<{believe}>able>` → un + believe + able。
     * 查不到就返回空——宁可没有，不给错的。
     */
    suspend fun morphology(word: String): List<MorphHint> = withContext(Dispatchers.IO) {
        val d = open() ?: return@withContext emptyList()
        val w = word.lowercase()
        runCatching {
            d.rawQuery("SELECT prefix, root, suffix FROM seg WHERE word=?", arrayOf(w)).use { c ->
                if (!c.moveToFirst()) return@withContext emptyList()
                val out = mutableListOf<MorphHint>()
                c.getString(0).orEmpty().split("--").filter { it.isNotBlank() }
                    .forEach { out.add(MorphHint("$it-", "前缀")) }
                c.getString(1).orEmpty().split("--").filter { it.isNotBlank() }
                    .forEach { out.add(MorphHint(it, "词根")) }
                c.getString(2).orEmpty().split("--").filter { it.isNotBlank() }
                    .forEach { out.add(MorphHint("-$it", "后缀")) }
                out
            }
        }.getOrDefault(emptyList())
    }

    /** 去掉屈折后缀后还在同一词族里，就当它是屈折变化而不是派生词 */
    private fun isInflection(form: String, family: Set<String>): Boolean {
        for (suf in listOf("iest", "ier", "est", "ing", "ed", "es", "er", "s", "d")) {
            if (!form.endsWith(suf) || form.length <= suf.length + 2) continue
            val base = form.dropLast(suf.length)
            if (base in family) return true
            if (base.length > 2 && base[base.length - 1] == base[base.length - 2]) {
                if (base.dropLast(1) in family) return true
            }
            if ((base + "e") in family) return true
        }
        return false
    }
}
