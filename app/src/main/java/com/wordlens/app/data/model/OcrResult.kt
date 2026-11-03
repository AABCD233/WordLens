package com.wordlens.app.data.model

/**
 * OCR 结果模型：与 Android 框架无关，方便单测。
 * 坐标是「原图像素坐标系」，UI 侧再按显示缩放换算。
 */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    /** 命中测试时把框稍微放大，手指粗、OCR 词框窄 */
    fun inflated(hPadding: Int, vPadding: Int): Bounds = Bounds(
        left = left - hPadding,
        top = top - vPadding,
        right = right + hPadding,
        bottom = bottom + vPadding
    )

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}

/** 一个「词」，对应 ML Kit 的 Text.Element（拉丁文识别下到字符/词级别） */
data class OcrWord(
    val text: String,
    val bounds: Bounds,
    val lineIndex: Int,
    val order: Int
) {
    /** 去掉首尾标点后的纯词形，用于查词典 */
    val lookupKey: String get() = WordNormalizer.stripPunctuation(text)
    val hasLetters: Boolean get() = text.any { it.isLetter() }
}

data class OcrLine(
    val index: Int,
    val text: String,
    val bounds: Bounds,
    val words: List<OcrWord>
)

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine>,
    val imageWidth: Int,
    val imageHeight: Int
) {
    val words: List<OcrWord> = lines.flatMap { it.words }
    val wordCount: Int = words.count { it.hasLetters }

    companion object {
        val EMPTY = OcrResult("", emptyList(), 0, 0)
    }
}
