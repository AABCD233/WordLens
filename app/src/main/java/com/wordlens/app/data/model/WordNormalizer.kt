package com.wordlens.app.data.model

/**
 * 把 OCR 出来的 token（可能带标点、大小写、复数等）规整成适合查词的形态。
 */
object WordNormalizer {

    private val leading = charArrayOf('"', '\u201c', '\u2018', '\'', '(', '[', '{', '<', '*', '\u2014')
    private val trailing = charArrayOf(
        ',', '.', ';', ':', '!', '?', '"', '\u201d', '\u2019', '\'', ')', ']', '}', '>', '*',
        '\u2014', '\u2013', '…', '»', '«'
    )

    fun stripPunctuation(raw: String): String {
        var s = raw.trim()
        while (s.isNotEmpty() && s.first() in leading) s = s.substring(1)
        while (s.isNotEmpty() && s.last() in trailing) s = s.dropLast(1)
        return s.trim()
    }

    fun normalize(raw: String): String = stripPunctuation(raw).lowercase().trim()

    fun isLikelyWord(raw: String): Boolean {
        val s = normalize(raw)
        if (s.length < 2 || s.length > 45) return false
        return s.all { it in 'a'..'z' || it == '-' || it == '\'' }
    }

    /**
     * 极简词形还原：OCR 点到的往往是 "studying / carried / wolves / it's"。
     * 返回按优先级排列的候选原形（含自身），查词典时依次尝试。
     */
    fun lemmaCandidates(raw: String): List<String> {
        val w = normalize(raw)
        if (w.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out += w

        // 所有格 / 缩写
        if (w.endsWith("'s")) out += w.dropLast(2)
        if (w.endsWith("s'")) out += w.dropLast(2)

        // 复数
        if (w.endsWith("ies") && w.length > 4) out += w.dropLast(3) + "y"
        if (w.endsWith("ves") && w.length > 4) {
            out += w.dropLast(3) + "f"
            out += w.dropLast(3) + "fe"
        }
        if (w.endsWith("es") && w.length > 3) {
            out += w.dropLast(2)
            if (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("zes") || w.endsWith("ches") || w.endsWith("shes")) {
                out += w.dropLast(2)
            }
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length > 2) out += w.dropLast(1)

        // 过去式 / 分词
        if (w.endsWith("ied") && w.length > 4) out += w.dropLast(3) + "y"
        if (w.endsWith("ed") && w.length > 3) {
            out += w.dropLast(1)            // hoped -> hope
            out += w.dropLast(2)            // walked -> walk
            out += w.dropLast(2) + "e"      // walked -> walke (会被词典 miss，无害)
        }
        // 双写辅音: stopped -> stop
        if (w.endsWith("ed") && w.length > 4) {
            val stem2 = w.dropLast(2)
            if (stem2.length >= 3 && stem2[stem2.length - 1] == stem2[stem2.length - 2] && stem2.last() !in "aeiouwxy") {
                out += stem2.dropLast(1)
            }
        }

        // 动名词
        if (w.endsWith("ing") && w.length > 5) {
            out += w.dropLast(3)            // reading -> read
            out += w.dropLast(3) + "e"      // making -> make
            val stem3 = w.dropLast(3)
            if (stem3.length >= 3 && stem3[stem3.length - 1] == stem3[stem3.length - 2] && stem3.last() !in "aeiouwxy") {
                out += stem3.dropLast(1)    // running -> run
            }
        }

        // 比较级 / 最高级 / 副词
        if (w.endsWith("ier") && w.length > 4) out += w.dropLast(3) + "y"      // happier -> happy
        if (w.endsWith("iest") && w.length > 5) out += w.dropLast(4) + "y"     // happiest -> happy
        if (w.endsWith("er") && w.length > 4) {
            out += w.dropLast(2)
            val stemEr = w.dropLast(2)
            if (stemEr.length >= 3 && stemEr.last() == stemEr[stemEr.length - 2] && stemEr.last() !in "aeiouwxy") {
                out += stemEr.dropLast(1)                                     // bigger -> big
            }
        }
        if (w.endsWith("est") && w.length > 5) {
            out += w.dropLast(3)
            val stemEst = w.dropLast(3)
            if (stemEst.length >= 3 && stemEst.last() == stemEst[stemEst.length - 2] && stemEst.last() !in "aeiouwxy") {
                out += stemEst.dropLast(1)                                    // biggest -> big
            }
        }
        if (w.endsWith("ly") && w.length > 4) out += w.dropLast(2)

        return out.filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' || c == '-' || c == '\'' } }
    }
}
