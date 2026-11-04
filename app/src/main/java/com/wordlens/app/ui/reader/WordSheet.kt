@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordlens.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import com.wordlens.app.data.dict.AiKind
import com.wordlens.app.data.model.Definition
import com.wordlens.app.data.settings.Settings
import com.wordlens.app.ui.WordUi

/**
 * 点词后的释义卡。用自绘的底部面板而不是 ModalBottomSheet，
 * 少一个 Experimental API，行为也更好控（面板内可滚动、点遮罩关闭）。
 */
@Composable
fun WordSheet(
    word: WordUi,
    settings: Settings,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onSave: () -> Unit,
    onAiExplain: (String) -> Unit,
    onTranslateExample: (String) -> Unit,
    onEditNote: () -> Unit,
    onLookupWord: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩：点空白处关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Header(word, onSpeak, onDismiss)

                when {
                    word.loading -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }

                    word.definition != null -> DefinitionBody(
                        def = word.definition,
                        settings = settings,
                        word = word,
                        onSpeak = onSpeak,
                        onSave = onSave,
                        onAiExplain = onAiExplain,
                        onTranslateExample = onTranslateExample,
                        onEditNote = onEditNote,
                        onLookupWord = onLookupWord,
                        onCopy = onCopy
                    )

                    else -> Text("没有查到结果", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun Header(word: WordUi, onSpeak: (String) -> Unit, onDismiss: () -> Unit) {
    val title = word.definition?.lemma?.takeIf { it.isNotBlank() } ?: word.query
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            word.definition?.phonetic?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "/$it/",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = { onSpeak(title) }) { Text("🔊 朗读") }
        TextButton(onClick = onDismiss) { Text("关闭") }
    }
}

@Composable
private fun DefinitionBody(
    def: Definition,
    settings: Settings,
    word: WordUi,
    onSpeak: (String) -> Unit,
    onSave: () -> Unit,
    onAiExplain: (String) -> Unit,
    onTranslateExample: (String) -> Unit,
    onEditNote: () -> Unit,
    onLookupWord: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    // 断网提示：只影响「在线补充」这一层，离线释义照常可用
    if (def.onlineFailed) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                "没连上网络，现在只显示离线词典的内容。联网后可以补英文例句和双语例句。",
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    // 合并了原形条目时，给一个「原形」入口（标题仍是点中的词）
    def.baseWord?.let { base ->
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "原形",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            WordChip(base) { onLookupWord(base) }
            Spacer(Modifier.width(6.dp))
            Text(
                "（已合并它的释义）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 原形提示：点的是 studying，词典给的是 study —— 原形本身可以再点一次
    if (!def.lemmaIsExact && def.lemma.isNotBlank() && !def.lemma.equals(def.query, ignoreCase = true)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "「${def.query}」按原形",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            WordChip(def.lemma) { onLookupWord(def.lemma) }
            Text(
                "查",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    if (def.tags.isNotEmpty() || def.oxford || def.collins > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (def.oxford) Badge("牛津核心词")
            if (def.collins > 0) Badge("柯林斯 ${def.collins} 星")
            def.tags.forEach { Badge(it) }
        }
        Spacer(Modifier.height(10.dp))
    }

    // 中文释义
    if (def.zhLines.isNotEmpty()) {
        SectionTitle("释义")
        def.zhLines.forEach { line -> ZhLine(line) }
    } else if (def.zhMachine != null) {
        SectionTitle("机器翻译")
        Text(
            def.zhMachine,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
    } else if (def.enMeanings.isEmpty()) {
        Text(
            "离线词典没有收录这个词。可以在「设置」里打开在线补充，或配置 AI 接口后点「AI 详解」。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
    }

    // 英文释义 + 例句
    if (def.enMeanings.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("英文释义")
        def.enMeanings.take(3).forEach { meaning ->
            val posZh = posChinese(meaning.partOfSpeech)
            if (meaning.partOfSpeech.isNotBlank() || posZh.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meaning.partOfSpeech.isNotBlank()) {
                        Text(
                            meaning.partOfSpeech,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (posZh.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            posZh,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            meaning.definitions.take(4).forEach { d ->
                Text(
                    "· ${d.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp
                )
                d.example?.let { ex -> ExampleBlock(ex, word, onTranslateExample) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // 双语例句（有道在线补的）
    if (def.examples.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("例句")
        def.examples.take(4).forEachIndexed { index, ex ->
            Column(Modifier.padding(top = if (index == 0) 0.dp else 10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        ex.en,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "读",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSpeak(ex.en) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                // 译文紧跟原句，句与句之间才留空
                ex.zh?.let { zh ->
                    Text(
                        zh,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }

    // 词形变化：每个都能点进去查（应对「只认识变形、不认识原形」的场景）
    if (def.inflections.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SectionTitle("词形（可点）")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            def.inflections.forEach { line ->
                val form = formOf(line)
                if (form != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.removeSuffix(form).trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        WordChip(form) { onLookupWord(form) }
                    }
                } else {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 我的释义：自己补的内容，优先于词典
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("我的释义")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEditNote) {
            Text(if (def.userNote.isNullOrBlank()) "添加" else "修改", fontSize = 12.sp)
        }
    }
    if (def.userNote.isNullOrBlank()) {
        Text(
            "词典解释不合适？可以自己写一条，只存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                def.userNote,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
    }

    // 上下文句子
    word.sentence?.takeIf { it.isNotBlank() }?.let { sentence ->
        Spacer(Modifier.height(12.dp))
        SectionTitle("原句")
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    sentence,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSpeak(sentence) }) { Text("读") }
            }
        }
    }

    // AI：三个维度
    if (settings.aiReady) {
        Spacer(Modifier.height(12.dp))
        SectionTitle("AI")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AiKind.values().forEach { kind ->
                val running = word.aiLoading && word.aiKind == kind.id
                FilterChipCompat(
                    label = if (running) "分析中…" else kind.label,
                    selected = word.aiKind == kind.id && word.aiText != null,
                    enabled = !word.aiLoading,
                    onClick = { onAiExplain(kind.id) }
                )
            }
        }
        if (word.aiLoading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
        }
        word.aiText?.takeIf { it.isNotBlank() }?.let { ai ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    ai,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 21.sp
                )
            }
        }
        word.aiError?.let {
            Spacer(Modifier.height(6.dp))
            Text("AI 调用失败：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("加入生词本") }
        Spacer(Modifier.width(10.dp))
        OutlinedButton(onClick = {
            onCopy(
                listOfNotNull(
                    def.lemma,
                    def.phonetic?.let { "/$it/" },
                    def.primaryGloss,
                    def.enMeanings.firstOrNull()?.definitions?.firstOrNull()?.text
                ).joinToString("  ")
            )
        }) { Text("复制") }
    }

    if (def.sources.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "来源：" + def.sources.joinToString(" / "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一句英文例句 + 按需翻译 */
@Composable
private fun ExampleBlock(example: String, word: WordUi, onTranslate: (String) -> Unit) {
    val translated = word.exampleZh[example]
    Column(Modifier.padding(start = 6.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "e.g. $example",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                modifier = Modifier.weight(1f)
            )
            if (translated == null) {
                if (word.translatingExample == example) {
                    CircularProgressIndicator(Modifier.height(14.dp).width(14.dp))
                } else {
                    Text(
                        "译",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onTranslate(example) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        translated?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** 中文释义里的词性前缀正则：允许前面挂 `(abnormal 的复数)` 这种注释 */
private val POS_PREFIX = Regex(
    "^((?:[(（][^)）]*[)）]\\s*)*)((adj|adv|abbr|prep|conj|pron|num|art|int|aux|pl|vt|vi|ad|n|v|a)\\.)",
    RegexOption.IGNORE_CASE
)

@Composable
private fun ZhLine(line: String) {
    val accent = MaterialTheme.colorScheme.secondary
    val annotated = remember(line, accent) {
        val m = POS_PREFIX.find(line)
        if (m == null) {
            AnnotatedString(line)
        } else {
            val start = m.range.first + m.groupValues[1].length
            val end = start + m.groupValues[2].length  // 含结尾的点
            buildAnnotatedString {
                append(line)
                if (end <= line.length) {
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), start, end)
                }
            }
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = 24.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

/** 词性代码 -> 中文，给不熟缩写的学习者看 */
private fun posChinese(code: String): String = when (code.trim().lowercase()) {
    "n." -> "名词"
    "v." -> "动词"
    "vt." -> "及物动词"
    "vi." -> "不及物动词"
    "adj." -> "形容词"
    "adv." -> "副词"
    "prep." -> "介词"
    "conj." -> "连词"
    "pron." -> "代词"
    "num." -> "数词"
    "art." -> "冠词"
    "int." -> "感叹词"
    "aux." -> "助动词"
    "abbr." -> "缩写"
    "pl." -> "复数"
    else -> ""
}

/** 词形行尾那个单词，例如「过去分词 / 过去式 studied」里的 studied */
private fun formOf(line: String): String? {
    val token = line.trim().substringAfterLast(' ').trim()
    if (token.isEmpty() || token.any { it.isDigit() }) return null
    if (!token.all { it in 'a'..'z' || it in 'A'..'Z' || it == '-' || it == '\'' }) return null
    return token
}

/** 可点的小词块 */
@Composable
private fun WordChip(word: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            word,
            fontSize = 12.sp,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** 简易胶囊按钮：不依赖 FilterChip，避开实验性 API */
@Composable
private fun FilterChipCompat(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        fontSize = 12.sp,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
