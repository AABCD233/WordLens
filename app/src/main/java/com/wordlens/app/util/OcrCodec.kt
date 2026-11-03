package com.wordlens.app.util

import com.wordlens.app.data.model.Bounds
import com.wordlens.app.data.model.OcrLine
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.model.OcrWord

/**
 * 把 OCR 结果序列化成一行字符串存进数据库。
 *
 * 为什么不用 org.json：这个类要能在纯 JVM 单测里跑（CI 会执行 testDebugUnitTest），
 * 而 android.jar 里的 org.json 在单测里只是空壳。
 *
 * 格式（v1）：
 *   1|宽|高|行~行~...
 *   行   := l,t,r,b~文本~词&词&...
 *   词   := l,t,r,b~文本
 * 文本里的 \ | ~ & 和换行都会被转义，所以可以放心 split。
 */
object OcrCodec {

    fun encode(result: OcrResult): String {
        val sb = StringBuilder(256 + result.words.size * 24)
        sb.append(VERSION).append('|')
        sb.append(result.imageWidth).append('|')
        sb.append(result.imageHeight).append('|')
        result.lines.forEachIndexed { i, line ->
            if (i > 0) sb.append('|')
            sb.append(line.bounds.left).append(',').append(line.bounds.top).append(',')
                .append(line.bounds.right).append(',').append(line.bounds.bottom)
            sb.append('~').append(escape(line.text)).append('~')
            line.words.forEachIndexed { j, word ->
                if (j > 0) sb.append('&')
                sb.append(word.bounds.left).append(',').append(word.bounds.top).append(',')
                    .append(word.bounds.right).append(',').append(word.bounds.bottom)
                sb.append('~').append(escape(word.text))
            }
        }
        return sb.toString()
    }

    fun decode(raw: String?): OcrResult {
        if (raw.isNullOrBlank()) return OcrResult.EMPTY
        val parts = raw.split('|')
        if (parts.size < 4 || parts[0] != VERSION) return OcrResult.EMPTY
        val width = parts[1].toIntOrNull() ?: 0
        val height = parts[2].toIntOrNull() ?: 0
        val lines = ArrayList<OcrLine>(parts.size - 3)
        for (i in 3 until parts.size) {
            val chunk = parts[i]
            if (chunk.isEmpty()) continue
            // 只切三段：词列表里每个词也是 "l,t,r,b~文本"，用无上限的 split 会把它切碎
            val lineParts = chunk.split('~', limit = 3)
            if (lineParts.size < 2) continue
            val box = parseBox(lineParts[0]) ?: continue
            val text = unescape(lineParts[1])
            val words = ArrayList<OcrWord>()
            if (lineParts.size >= 3 && lineParts[2].isNotEmpty()) {
                lineParts[2].split('&').forEachIndexed { idx, token ->
                    val wp = token.split('~', limit = 2)
                    if (wp.size < 2) return@forEachIndexed
                    val wb = parseBox(wp[0]) ?: return@forEachIndexed
                    words += OcrWord(
                        text = unescape(wp[1]),
                        bounds = wb,
                        lineIndex = lines.size,
                        order = idx
                    )
                }
            }
            lines += OcrLine(index = lines.size, text = text, bounds = box, words = words)
        }
        val fullText = lines.joinToString("\n") { it.text }
        return OcrResult(fullText, lines, width, height)
    }

    private fun parseBox(s: String): Bounds? {
        val n = s.split(',')
        if (n.size != 4) return null
        return Bounds(
            left = n[0].toIntOrNull() ?: return null,
            top = n[1].toIntOrNull() ?: return null,
            right = n[2].toIntOrNull() ?: return null,
            bottom = n[3].toIntOrNull() ?: return null
        )
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '|' -> sb.append("\\p")
                '~' -> sb.append("\\t")
                '&' -> sb.append("\\a")
                '\n' -> sb.append("\\n")
                '\r' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> sb.append('\\')
                    'p' -> sb.append('|')
                    't' -> sb.append('~')
                    'a' -> sb.append('&')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private const val VERSION = "1"
}
