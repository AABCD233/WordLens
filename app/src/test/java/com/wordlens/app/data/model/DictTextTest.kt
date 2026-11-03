package com.wordlens.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ECDICT 原始字段的清洗规则。这些断言都对应真机截图里出现过的 bug，
 * 改解析时先跑这里。
 */
class DictTextTest {

    @Test
    fun `literal backslash-n becomes a real newline`() {
        val raw = "n. a concise explanation\\nn. clarity of outline"
        val parts = DictText.unescape(raw).split('\n')
        assertEquals(2, parts.size)
        assertEquals("n. a concise explanation", parts[0])
        assertFalse(DictText.unescape(raw).contains("\\n"))
    }

    @Test
    fun `adjective abbreviation becomes adj`() {
        assertEquals(listOf("adj. 美丽的"), DictText.zhLines("a. 美丽的"))
        assertEquals(listOf("adj. 快乐的；幸福的"), DictText.zhLines("a. 快乐的, 幸福的"))
        assertEquals(listOf("adj. 好的"), DictText.zhLines("adj. 好的"))
    }

    @Test
    fun `other parts of speech are normalized`() {
        assertEquals(listOf("n. 苹果；家伙"), DictText.zhLines("n. 苹果, 家伙"))
        assertEquals(listOf("vt. 学习；读书"), DictText.zhLines("vt. 学习, 读书"))
        assertEquals(listOf("adv. 快速地"), DictText.zhLines("ad. 快速地"))
    }

    @Test
    fun `domain tags are translated and get no trailing space`() {
        assertEquals(listOf("（计算机）清晰度"), DictText.zhLines("[计] 清晰度"))
        assertEquals(listOf("（医学）苹果"), DictText.zhLines("[医] 苹果"))
    }

    @Test
    fun `auto generated plural entries keep their note and still get a pos`() {
        val out = DictText.zhLines("(abnormal 的复数) a. 反常的, 不规则的")
        assertEquals(listOf("(abnormal 的复数)adj. 反常的；不规则的"), out)
    }

    @Test
    fun `english meanings are grouped by part of speech`() {
        val def = DictText.enMeanings(
            "n. benefit\\nn. moral excellence\\na. having desirable qualities"
        )
        assertEquals(2, def.size)
        assertEquals("n.", def[0].partOfSpeech)
        assertEquals(2, def[0].definitions.size)
        assertEquals("benefit", def[0].definitions[0].text)
        assertEquals("adj.", def[1].partOfSpeech)
        assertEquals("having desirable qualities", def[1].definitions[0].text)
    }

    @Test
    fun `english meanings tolerate a missing dot after n`() {
        // 库里真的有 `n a concise explanation...` 这种没点的写法
        val def = DictText.enMeanings("n a concise explanation of the meaning")
        assertEquals(1, def.size)
        assertEquals("n.", def[0].partOfSpeech)
        assertEquals("a concise explanation of the meaning", def[0].definitions[0].text)
    }

    @Test
    fun `verb forms split on slash and drop exchange codes`() {
        // definition 这条的 exchange 是 `0:definition/1:s`，以前会漏出 "1:s"
        assertEquals(listOf("原形 definition"), DictText.inflections("0:definition/1:s"))

        val study = DictText.inflections("s:studies/i:studying/d:studied/p:studied/3:studies")
        assertTrue(study.contains("复数 / 三单 studies"))
        assertTrue(study.contains("过去分词 / 过去式 studied"))
        assertTrue(study.contains("现在分词 studying"))

        assertEquals(listOf("比较级 happier", "最高级 happiest"),
            DictText.inflections("r:happier/t:happiest"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals(0, DictText.zhLines("").size)
        assertEquals(0, DictText.enMeanings("").size)
        assertEquals(0, DictText.inflections("").size)
    }
}
