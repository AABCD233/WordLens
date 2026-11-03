package com.wordlens.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点词之前最重要的两步：把 OCR token 洗干净、把词形还原回可查的形式。
 * 这些逻辑是纯 Kotlin，CI 上 testDebugUnitTest 会真的跑到。
 */
class WordNormalizerTest {

    @Test
    fun `strip punctuation on both ends`() {
        assertEquals("hello", WordNormalizer.stripPunctuation("  \"hello,\" "))
        assertEquals("don't", WordNormalizer.stripPunctuation("(don't)!"))
        assertEquals("well-known", WordNormalizer.stripPunctuation("well-known."))
        assertEquals("", WordNormalizer.stripPunctuation("…"))
    }

    @Test
    fun `normalize lowercases`() {
        assertEquals("grammar", WordNormalizer.normalize("Grammar;"))
    }

    @Test
    fun `word candidates for inflected forms`() {
        val cases = mapOf(
            "studying" to "study",
            "carried" to "carry",
            "wolves" to "wolf",
            "apples" to "apple",
            "bigger" to "big",
            "cities" to "city"
        )
        cases.forEach { (word, lemma) ->
            val candidates = WordNormalizer.lemmaCandidates(word)
            assertTrue("$word 应该还原成 $lemma，实际 $candidates", candidates.contains(lemma))
        }
    }

    @Test
    fun `possessive is stripped`() {
        val candidates = WordNormalizer.lemmaCandidates("teacher's")
        assertEquals("teacher's", WordNormalizer.normalize("teacher's"))
        assertTrue(candidates.contains("teacher"))
    }

    @Test
    fun `isLikelyWord rejects numbers and short tokens`() {
        assertFalse(WordNormalizer.isLikelyWord("2024"))
        assertFalse(WordNormalizer.isLikelyWord("a"))
        assertFalse(WordNormalizer.isLikelyWord("（"))
        assertTrue(WordNormalizer.isLikelyWord("sentence"))
    }

    @Test
    fun `doubled consonant stems are produced`() {
        assertTrue(WordNormalizer.lemmaCandidates("running").contains("run"))
        assertTrue(WordNormalizer.lemmaCandidates("stopped").contains("stop"))
    }
}
