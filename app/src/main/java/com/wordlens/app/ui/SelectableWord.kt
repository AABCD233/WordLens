package com.wordlens.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/**
 * 可长按取词的文本：长按某个英文单词，就交给 [onWord] 去查。
 *
 * 用 TextLayoutResult.getOffsetForPosition 把手指位置换算成字符下标，
 * 再向两侧扩到空格边界 —— 比自己算行高可靠。
 */
@Composable
fun SelectableWord(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onWord: (String) -> Unit
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.then(
            Modifier.pointerInput(text) {
                detectTapGestures(
                    onLongPress = { pos ->
                        val l = layout ?: return@detectTapGestures
                        val offset = runCatching { l.getOffsetForPosition(pos) }.getOrNull()
                            ?: return@detectTapGestures
                        val word = wordAt(text, offset)
                        if (word.isNotBlank()) onWord(word)
                    }
                )
            }
        )
    )
}

/** 取 offset 所在的英文单词（向两侧扩到非字母边界） */
internal fun wordAt(text: String, offset: Int): String {
    if (text.isEmpty()) return ""
    var i = offset.coerceIn(0, text.length - 1)
    fun isWordChar(c: Char) = c.isLetter() || c == '-' || c == '\''
    if (!isWordChar(text[i])) {
        // 点在空格/标点上时，优先取左边那个词的结尾
        var j = i
        while (j >= 0 && !isWordChar(text[j])) j--
        if (j < 0) return ""
        i = j
    }
    var start = i
    var end = i
    while (start > 0 && isWordChar(text[start - 1])) start--
    while (end < text.length - 1 && isWordChar(text[end + 1])) end++
    return text.substring(start, end + 1).trim('\'', '-')
}
