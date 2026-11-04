package com.wordlens.app.data.model

/** 词典条目的统一呈现模型：离线数据 + 在线补充 + AI 详解合并到这里 */
data class EnDefinition(
    val text: String,
    val example: String? = null,
    val synonyms: List<String> = emptyList()
)

/** 一条双语例句 */
data class DefExample(
    val en: String,
    val zh: String? = null
)

data class EnMeaning(
    val partOfSpeech: String,
    val definitions: List<EnDefinition>
)

/** 一次「点词」的结果。UI 层只认这个类，不关心数据来自离线库还是网络 */
data class Definition(
    val query: String,
    val lemma: String = query,
    val matchedOffline: Boolean = false,
    /** 点到的词就是词典原形时为 true（studying 查到 study 时为 false）*/
    val lemmaIsExact: Boolean = true,
    val phonetic: String? = null,
    /** 中文释义，按词性分行，例如 "n. 知识；学问" */
    val zhLines: List<String> = emptyList(),
    val enMeanings: List<EnMeaning> = emptyList(),
    /** 离线没命中时，在线机器翻译给出的一句中文 */
    val zhMachine: String? = null,
    /** 词形变化，例如 "pl. apples / p. apple" 已展开成人话 */
    val inflections: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val collins: Int = 0,
    val oxford: Boolean = false,
    val freq: Int = 0,
    val audioUrl: String? = null,
    /** 双语例句（有道在线补的，需要联网） */
    val examples: List<DefExample> = emptyList(),
    /** 想联网补充但网络不可用（断网/超时），UI 会提示「当前只显示离线内容」 */
    val onlineFailed: Boolean = false,
    val aiText: String? = null,
    val sources: Set<String> = emptySet()
) {
    val hasAnyContent: Boolean
        get() = zhLines.isNotEmpty() || enMeanings.isNotEmpty() || zhMachine != null || aiText != null

    val primaryGloss: String
        get() = zhLines.firstOrNull()?.trim()
            ?: zhMachine?.trim()
            ?: enMeanings.firstOrNull()?.definitions?.firstOrNull()?.text?.trim()
            ?: ""

    companion object {
        fun empty(query: String) = Definition(query = query, lemma = WordNormalizer.normalize(query))
    }
}

/** 离线词典一行 */
data class DictRow(
    val word: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
    val tag: String,
    val exchange: String,
    val collins: Int,
    val oxford: Int,
    val freq: Int
)
