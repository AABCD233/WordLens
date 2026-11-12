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
/** 一条词根/词缀提示；confident=false 表示是按拼写推测的 */
data class MorphHint(
    val form: String,
    val meaning: String,
    val confident: Boolean
)

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

    private data class RootRow(val root: String, val zh: String, val freq: Int, val kind: String)

    private var rootCache: List<RootRow>? = null

    private fun roots(d: SQLiteDatabase): List<RootRow> =
        rootCache ?: synchronized(this) {
            rootCache ?: runCatching {
                d.rawQuery(
                    "SELECT root, zh, freq, kind FROM root WHERE length(root)>=4 AND zh<>''", null
                ).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(RootRow(c.getString(0), c.getString(1), c.getInt(2), c.getString(3)))
                        }
                    }
                }
            }.getOrDefault(emptyList()).also { rootCache = it }
        }

    /**
     * 词根匹配：算词与词根的最长公共前缀 LCP，
     * 要求 LCP ≥ 4、覆盖词根长度的大部分（差 ≤2），并且至少占词长一半。
     * fortunate × fortune → LCP "fortun"(6) 命中；fortunate × fort → LCP 4 但不到词长一半，被排除。
     */
    /** 返回值第三项是「是否精确」：词以完整词根开头才算可信 */
    private fun matchRoots(d: SQLiteDatabase, w: String): List<Triple<String, String, Boolean>> {
        if (w.length < 6) return emptyList()
        // 本词已经是词族的词根（fortune / struct 这种），再往上拆只会拆出更短的巧合前缀，
        // 而且和「同根词」那一节重复。这种情况直接不给词根。
        if (lemmaOf(d, w) == w) return emptyList()
        val stem = stemOf(d, w)
        if (stem.length < 4) return emptyList()

        val scored = mutableListOf<Pair<Boolean, Triple<Int, Int, RootRow>>>()
        for (r in roots(d)) {
            val root = r.root
            if (root == w) continue
            // 词根是词干的前缀（struct-ural ← struct），或词干是词根的前缀（fortun-ate ← fortune）
            val lcp = commonPrefixLen(stem, root)
            if (lcp < 4) continue
            if (lcp < root.length && lcp < stem.length) continue   // 必须一方完整包含另一方
            if (lcp * 2 < w.length) continue
            // 只有「词以完整词根开头」且词根本身是真正的词根（etymon/combining）才算可信；
            // base 类多是普通单词（inform、fortune），当词根用容易误导，统一标成推测。
            val trueRoot = r.kind == "etymon" || r.kind == "combining" || r.kind == "pie"
            val exact = w.startsWith(root) && trueRoot
            scored.add(exact to Triple(lcp, root.length, r))
        }
        return scored.sortedWith(
            compareByDescending<Pair<Boolean, Triple<Int, Int, RootRow>>> { it.first }
                .thenByDescending { it.second.first }
                .thenByDescending { it.second.second }
                .thenByDescending { it.second.third.freq }
        ).filterIndexed { _, pair ->
            // 去掉「是另一个候选的前缀」的冗余项：spect 与 spectr 只留 spect
            noneOf { other -> other.second.third !== pair.second.third &&
                pair.second.third.root.startsWith(other.second.third.root) }
        }.take(2).map {
            Triple(it.second.third.root, it.second.third.zh, it.first)
        }
    }

    /**
     * 词干：词和它词族的 lemma 的公共前缀。
     * fortunate → lemma fortune → fortun（正好是拉丁词根 fortun-）。
     *
     * 刻意**不剥派生前缀**：试过按 affix 表剥，会把 fortunate 削成 tunate、
     * construction 削成 nstruction，错误比收益多。宁可少给提示。
     */
    private fun lemmaOf(d: SQLiteDatabase, w: String): String = runCatching {
        d.rawQuery("SELECT lemma FROM family WHERE word=? LIMIT 1", arrayOf(w)).use {
            if (it.moveToFirst()) it.getString(0) else w
        }
    }.getOrDefault(w)

    private fun stemOf(d: SQLiteDatabase, w: String): String {
        val lemma = lemmaOf(d, w)
        val stem = commonPrefix(w, lemma)
        return if (stem.length >= 4) stem else w
    }

    private fun commonPrefix(a: String, b: String): String {
        val n = commonPrefixLen(a, b)
        return a.substring(0, n)
    }

    private fun commonPrefixLen(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
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
    suspend fun morphology(word: String): List<MorphHint> = withContext(Dispatchers.IO) {
        val d = open() ?: return@withContext emptyList()
        val w = word.lowercase()
        if (w.length < 5) return@withContext emptyList()
        val out = mutableListOf<MorphHint>()

        runCatching {
            d.rawQuery(
                "SELECT affix, type, zh FROM affix WHERE length(affix)>=3 AND ? LIKE affix || '%' " +
                    "AND type='prefix' ORDER BY length(affix) DESC LIMIT 2",
                arrayOf(w)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(MorphHint(c.getString(0), "前缀 · ${c.getString(2)}", true))
                }
            }
            d.rawQuery(
                "SELECT affix, type, zh FROM affix WHERE length(affix)>=3 AND ? LIKE '%' || affix " +
                    "AND type='suffix' ORDER BY length(affix) DESC LIMIT 2",
                arrayOf(w)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(MorphHint(c.getString(0), "后缀 · ${c.getString(2)}", true))
                }
            }
            // 词根：不能只判「以词根开头」——fortunate 的真正词干是 fortun-（来自 fortune），
            // 而 fort- 只是碰巧是前缀。改成算最长公共前缀，并要求它覆盖词根绝大部分、且占词的一半以上。
            matchRoots(d, w).forEach { (root, zh, confident) ->
                out.add(MorphHint("$root-", "词根 · $zh", confident))
            }
        }
        out.distinctBy { it.form }.take(4)
    }
}
