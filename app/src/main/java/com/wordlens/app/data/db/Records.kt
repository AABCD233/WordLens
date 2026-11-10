package com.wordlens.app.data.db

/** 一次识别得到的「文档」 */
data class DocumentRow(
    val id: Long = 0,
    val title: String,
    val imagePath: String?,
    val fullText: String,
    val wordsJson: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val wordCount: Int,
    val createdAt: Long
)

/** 生词本条目（Leitner 间隔复习）*/
data class SavedWord(
    val word: String,
    val phonetic: String = "",
    val translation: String = "",
    val definition: String = "",
    val tags: String = "",
    val context: String = "",
    val source: String = "ocr",
    val box: Int = 0,
    val dueAt: Long = 0,
    val addedAt: Long = 0,
    val reviewedAt: Long = 0,
    val reviewCount: Int = 0,
    /** 自定义分组；空串表示按加入日期自动分组 */
    val group: String = ""
) {
    val isNew: Boolean get() = reviewCount == 0
}
