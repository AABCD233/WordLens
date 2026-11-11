package com.wordlens.app.data.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wordlens.app.data.model.Bounds
import com.wordlens.app.data.model.OcrLine
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.model.OcrWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 端侧英文 OCR（ML Kit Text Recognition v2，模型打进 APK）。
 * 不联网、不上传照片、不需要 Google Play 服务下载模型。
 */
class OcrEngine(private val context: Context) {

    @Volatile
    private var recognizer: TextRecognizer? = null

    private fun client(): TextRecognizer = recognizer ?: synchronized(this) {
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
    }

    /**
     * 释放识别器。ML Kit 的识别器一创建就常驻 30–50MB，
     * 退出阅读页后一段时间没人用就关掉，下次识别时再重建（约 1 秒）。
     */
    fun close() {
        synchronized(this) {
            runCatching { recognizer?.close() }
            recognizer = null
        }
    }

    /**
     * @param source 照片 URI（相机输出的本地文件，或相册 Picker 返回的 content://）
     */
    suspend fun recognize(source: Uri): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context.applicationContext, source)
        recognizeImage(image)
    }

    suspend fun recognizeImage(image: InputImage): OcrResult {
        val text = client().process(image).awaitOrThrow()
        return text.toOcrResult(image.width, image.height)
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}

private suspend fun <T> Task<T>.awaitOrThrow(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { if (cont.isActive) cont.cancel() }
}

/** 把 ML Kit 的层级结构压平到我们的模型；每一行按空白拆成可点击的词 */
internal fun Text.toOcrResult(imageWidth: Int, imageHeight: Int): OcrResult {
    val lines = ArrayList<OcrLine>()
    var order = 0

    for (block in textBlocks) {
        for (line in block.lines) {
            val elements = line.elements
            val wordTokens = ArrayList<OcrWord>(elements.size)
            val lineBox: Rect = line.boundingBox ?: Rect(0, 0, 0, 0)

            if (elements.isNotEmpty()) {
                for (element in elements) {
                    val raw = element.text
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) continue
                    val box = element.boundingBox ?: lineBox
                    wordTokens += OcrWord(
                        text = trimmed,
                        bounds = Bounds(box.left, box.top, box.right, box.bottom),
                        lineIndex = lines.size,
                        order = order++
                    )
                }
            } else if (line.text.isNotBlank()) {
                // 某些 ROM/版本不给 element 级别，退化成「整行按空格拆」
                wordTokens += splitLineManually(line.text.trim(), lineBox, lines.size, order)
                order += wordTokens.size
            }

            val text = line.text.trim()
            if (text.isEmpty() && wordTokens.isEmpty()) continue
            lines += OcrLine(
                index = lines.size,
                text = text,
                bounds = Bounds(lineBox.left, lineBox.top, lineBox.right, lineBox.bottom),
                words = wordTokens
            )
        }
    }

    val fullText = lines.joinToString("\n") { it.text }
    return OcrResult(
        fullText = fullText.ifBlank { text.ifBlank { "" } },
        lines = lines,
        imageWidth = imageWidth,
        imageHeight = imageHeight
    )
}

/**
 * 整行没有 element 时，用等宽近似估出每个词的框：
 * 按字符数比例把行宽切给各词，误差对「点词」够用。
 */
private fun splitLineManually(
    lineText: String,
    box: Rect,
    lineIndex: Int,
    startOrder: Int
): List<OcrWord> {
    val tokens = lineText.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val totalChars = tokens.sumOf { it.length } + (tokens.size - 1)
    if (totalChars <= 0) return emptyList()
    val unit = box.width().toFloat() / totalChars
    var x = box.left.toFloat()
    return tokens.mapIndexed { i, token ->
        val left = x
        x += token.length * unit
        val right = x
        x += unit // 空格
        OcrWord(
            text = token,
            bounds = Bounds(left.toInt(), box.top, right.toInt(), box.bottom),
            lineIndex = lineIndex,
            order = startOrder + i
        )
    }
}
