package com.wordlens.app.util

import com.wordlens.app.data.model.Bounds
import com.wordlens.app.data.model.OcrLine
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.model.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCodecTest {

    private fun sample(): OcrResult {
        val words = listOf(
            OcrWord("Hello,", Bounds(10, 20, 80, 40), 0, 0),
            // 故意塞进分隔符，验证转义是否可靠
            OcrWord("a|b&c~d\\e", Bounds(85, 20, 160, 40), 0, 1),
            OcrWord("line2", Bounds(10, 60, 70, 84), 1, 2)
        )
        val lines = listOf(
            OcrLine(0, "Hello, a|b&c~d\\e", Bounds(10, 20, 160, 40), words.take(2)),
            OcrLine(1, "line2", Bounds(10, 60, 70, 84), words.drop(2))
        )
        return OcrResult("Hello, a|b&c~d\\e\nline2", lines, 1080, 1440)
    }

    @Test
    fun `round trip keeps words and bounds`() {
        val encoded = OcrCodec.encode(sample())
        val decoded = OcrCodec.decode(encoded)

        assertEquals(1080, decoded.imageWidth)
        assertEquals(1440, decoded.imageHeight)
        assertEquals(2, decoded.lines.size)
        assertEquals(3, decoded.words.size)

        val tricky = decoded.words[1]
        assertEquals("a|b&c~d\\e", tricky.text)
        assertEquals(Bounds(85, 20, 160, 40), tricky.bounds)
        assertEquals(1, tricky.lineIndex)
        assertEquals(2, decoded.wordCount)
    }

    @Test
    fun `decode tolerates junk`() {
        assertEquals(0, OcrCodec.decode(null).words.size)
        assertEquals(0, OcrCodec.decode("").words.size)
        assertEquals(0, OcrCodec.decode("garbage").words.size)
        assertEquals(0, OcrCodec.decode("9|100|200|x").words.size)
    }

    @Test
    fun `newlines inside line text are escaped`() {
        val line = OcrLine(0, "a\nb", Bounds(0, 0, 10, 10), listOf(OcrWord("a", Bounds(0, 0, 5, 5), 0, 0)))
        val decoded = OcrCodec.decode(OcrCodec.encode(OcrResult("a\nb", listOf(line), 10, 10)))
        assertEquals("a\nb", decoded.lines.first().text)
        assertTrue(decoded.fullText.contains("a"))
    }
}
