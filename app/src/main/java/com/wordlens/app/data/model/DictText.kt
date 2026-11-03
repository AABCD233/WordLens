package com.wordlens.app.data.model

/**
 * 把 ECDICT 的原始字段洗成人能读的样子。
 *
 * 这个类只做字符串处理、不碰 Android API，所以能在纯 JVM 单测里跑到
 * （CI 的 testDebugUnitTest 会真的执行这里的断言）。
 *
 * 踩过的坑（都在真机截图上验证过）：
 *  - `definition` 里的换行是**字面** `\n`（反斜杠 + n），不是真换行，直接显示会露出 "\n"
 *  - `exchange` 用 `/` 分隔（`r:happier/t:happiest`），不是 `|`
 *  - `exchange` 里的 `1:` / `2:` 是「变形码」（值只有 s/c 这种字母），不是单词，必须丢掉
 *  - 中文释义里形容词写作 `a.`，学习者习惯看 `adj.`
 */
object DictText {

    // ---------- 转义 ----------

    /** ECDICT 部分字段里换行被存成字面 `\n`，统一还原成真字符 */
    fun unescape(raw: String): String {
        if (raw.indexOf('\\') < 0) return raw
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    // ---------- 词性 ----------

    /** ECDICT 用的缩写 -> 学习词典里习惯的写法 */
    private val POS_ZH = mapOf(
        "n" to "n.", "v" to "v.", "vt" to "vt.", "vi" to "vi.", "vt/vi" to "vt./vi.",
        "a" to "adj.", "adj" to "adj.", "ad" to "adv.", "adv" to "adv.",
        "prep" to "prep.", "conj" to "conj.", "pron" to "pron.", "num" to "num.",
        "art" to "art.", "int" to "int.", "aux" to "aux.", "abbr" to "abbr.",
        "pl" to "pl.", "conj/adv" to "conj./adv."
    )

    /** WordNet 里英文释义的词性码 */
    private fun enPosToCode(code: String): String = when (code.lowercase()) {
        "n" -> "n."
        "v" -> "v."
        "a", "s" -> "adj."
        "r" -> "adv."
        else -> ""
    }

    /** 学科/领域标记，`[计]` 这种，翻成中文更友好 */
    private val DOMAIN_ZH = mapOf(
        "计" to "计算机", "医" to "医学", "化" to "化学", "经" to "经济", "商" to "商业",
        "律" to "法律", "机" to "机械", "电" to "电子", "数" to "数学", "物" to "物理",
        "生" to "生物", "军" to "军事", "农" to "农业", "建" to "建筑", "冶" to "冶金",
        "航海" to "航海", "航空" to "航空", "体" to "体育", "音" to "音乐", "戏" to "戏剧",
        "心" to "心理", "哲" to "哲学", "宗" to "宗教", "语" to "语言", "印" to "印刷",
        "食" to "食品", "摄" to "摄影", "影" to "电影", "视" to "电视",
        "广" to "广播", "新" to "新闻", "出" to "出版", "教" to "教育", "考" to "考试",
        "网" to "网络", "自" to "自动化", "汽" to "汽车", "船" to "船舶",
        "天" to "天文", "地" to "地质", "气" to "气象", "水" to "水利", "矿" to "矿业",
        "统" to "统计", "财" to "财政", "会" to "会计", "贸" to "贸易", "管" to "管理",
        "逻" to "逻辑", "修" to "修辞", "遗" to "遗传", "纺" to "纺织", "石" to "石油"
    )

    private val DOMAIN_RE = Regex("^\\s*[\\[［]([^\\]］]{1,4})[\\]］]\\s*")

    /** 行首注释，比如自动生成条目里的 `(abnormal 的复数)` */
    private val LEAD_NOTE_RE = Regex("^\\s*[(（][^)）]{0,40}[)）]\\s*")

    // ---------- 中文释义 ----------

    /**
     * `translation` -> 每行一条释义。
     * 词性统一成 `adj.` 风格；`[计]` 这类领域标记翻成 `（计算机）`；
     * 词性行里的英文逗号改中文分号，读起来更顺。
     */
    fun zhLines(translation: String): List<String> {
        if (translation.isBlank()) return emptyList()
        return unescape(translation)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { normalizeZhLine(it) }
            .filter { it.isNotEmpty() }
    }

    private fun normalizeZhLine(line: String): String {
        var rest = line
        val sb = StringBuilder()

        // 自动生成条目开头常有 `(abnormal 的复数)`，偶尔还连着两个
        repeat(2) {
            val note = LEAD_NOTE_RE.find(rest) ?: return@repeat
            sb.append(note.value.trim())
            rest = rest.substring(note.range.last + 1)
        }

        // 前缀可能同时有词性和领域标记，最多剥三轮（见过 `(xx 的复数) [医] a. ...`）
        repeat(3) {
            val domain = DOMAIN_RE.find(rest)
            if (domain != null) {
                val code = domain.groupValues[1].trim()
                sb.append('（').append(DOMAIN_ZH[code] ?: code).append('）')
                rest = rest.substring(domain.range.last + 1)
                return@repeat
            }
            val pos = matchPos(rest)
            if (pos != null) {
                sb.append(pos.first)
                rest = pos.second
            }
        }

        // ECDICT 用 ; 和 , 当分隔符，一律换成中文分号
        var body = rest.trim().replace("; ", "；").replace(";", "；")
        if (sb.isNotEmpty()) {
            body = body.replace(", ", "；").replace(",", "；")
            // 词性或半角括号后面才留空格；「（计算机）清晰度」不要空格
            if (body.isNotEmpty() && (sb.last() == '.' || sb.last() == ')')) sb.append(' ')
        }
        sb.append(body)
        // 只有词性没有释义时，别留一个光秃秃的 "adj."
        return sb.toString().trim()
    }

    /** 如果开头是已知词性缩写（带点），返回「归一化词性」和「剩余文本」 */
    private fun matchPos(text: String): Pair<String, String>? {
        val m = Regex("^([A-Za-z]{1,5})\\.\\s*").find(text) ?: return null
        val key = m.groupValues[1].lowercase()
        val code = POS_ZH[key] ?: return null
        val rest = text.substring(m.range.last + 1)
        if (rest.isBlank()) return null
        return code to rest
    }

    // ---------- 英文释义 ----------

    /**
     * `definition` -> 按词性分组的英文释义。
     * WordNet 的行首是 `n.` / `v.` / `a.` / `s.` / `r.`，相邻同词性的合并成一组。
     */
    fun enMeanings(definition: String): List<EnMeaning> {
        if (definition.isBlank()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<EnDefinition>>()
        for (rawLine in unescape(definition).split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            var pos = ""
            var body = line
            // 带点写法一律认；不带点时只认 n/v/s/r —— `a` 不带点和冠词 a 分不开
            val m = Regex("^([nvasr])\\.\\s+(.+)$", RegexOption.IGNORE_CASE).find(line)
                ?: Regex("^([nvsr])\\s+(.+)$", RegexOption.IGNORE_CASE).find(line)
            if (m != null) {
                pos = enPosToCode(m.groupValues[1])
                body = m.groupValues[2].trim()
            }
            if (body.isEmpty()) continue
            groups.getOrPut(pos) { ArrayList() } += EnDefinition(text = body)
        }
        return groups.map { (pos, defs) -> EnMeaning(partOfSpeech = pos, definitions = defs) }
    }

    // ---------- 词形 ----------

    private val EXCHANGE_LABEL = mapOf(
        "p" to "过去式", "d" to "过去分词", "i" to "现在分词",
        "3" to "三单", "s" to "复数", "r" to "比较级", "t" to "最高级",
        "0" to "原形"
    )

    /**
     * `exchange` 形如 `s:studies/i:studying/d:studied/p:studied`。
     * 同一个词形有多个标签时合并（studied 既是过去式又是过去分词）。
     * `1:` / `2:` 是变形码（值只有 s / c 之类），直接丢掉。
     */
    fun inflections(exchange: String): List<String> {
        if (exchange.isBlank()) return emptyList()
        val labelsByValue = LinkedHashMap<String, MutableList<String>>()
        for (pair in exchange.split('/', '|')) {
            val idx = pair.indexOf(':')
            if (idx <= 0) continue
            val key = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (value.isEmpty() || !value.first().isLetter()) continue
            val label = EXCHANGE_LABEL[key] ?: continue
            labelsByValue.getOrPut(value) { ArrayList() } += label
        }
        return labelsByValue.map { (value, labels) ->
            labels.distinct().joinToString(" / ") + " " + value
        }
    }
}
