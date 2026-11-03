@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordlens.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.model.OcrLine
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.model.OcrWord
import com.wordlens.app.data.settings.Settings
import com.wordlens.app.ui.ReaderUi
import com.wordlens.app.ui.WordUi

/**
 * 阅读页：把识别结果变成「可以点的英文」。
 * 三种视图：单词（逐行可点）/ 原图对照（按框命中）/ 纯文本（连续阅读、字号更小）。
 */
@Composable
fun ReaderScreen(
    state: ReaderUi,
    word: WordUi,
    settings: Settings,
    onBack: () -> Unit,
    onWordTap: (String, String?) -> Unit,
    onDismissWord: () -> Unit,
    onSpeak: (String) -> Unit,
    onSaveWord: () -> Unit,
    onAiExplain: () -> Unit,
    onModeChange: (String) -> Unit,
    onDeleteDoc: (() -> Unit)?,
    onTranslateAll: () -> Unit,
    onTranslateExample: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    // 在 Composable 作用域里先取出剪贴板，才能在下面的 onClick lambda 里用
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ReaderHeader(
            title = if (state.docId != 0L) state.title else "正在识别…",
            mode = settings.readerMode,
            hasImage = state.bitmap != null,
            onBack = onBack,
            onModeChange = onModeChange,
            onDeleteDoc = onDeleteDoc,
            onCopyAll = {
                clipboard.setText(AnnotatedString(state.text))
                onMessage("已复制全文")
            },
            canCopy = state.text.isNotBlank()
        )
        HorizontalDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在识别文字…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                state.error != null -> Centered {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Text(state.error ?: "", textAlign = TextAlign.Center, lineHeight = 22.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onBack) { Text("回到拍照") }
                    }
                }

                settings.readerMode == "zh" -> ZhTextView(
                    state = state,
                    onTranslate = onTranslateAll,
                    onRetry = { onTranslateAll() }
                )

                settings.readerMode == "image" && state.bitmap != null ->
                    ImageWordView(state = state, activeWord = word.query.takeIf { word.visible }, onWordTap = onWordTap)

                else -> WordsView(
                    state = state,
                    dense = settings.readerMode == "text",
                    activeWord = word.query.takeIf { word.visible },
                    onWordTap = onWordTap
                )
            }
        }
    }

    if (word.visible) {
        WordSheet(
            word = word,
            settings = settings,
            onDismiss = onDismissWord,
            onSpeak = onSpeak,
            onSave = onSaveWord,
            onAiExplain = onAiExplain,
            onTranslateExample = { example -> onTranslateExample(example) },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                onMessage("已复制")
            }
        )
    }
}

@Composable
private fun ZhTextView(state: ReaderUi, onTranslate: () -> Unit, onRetry: () -> Unit) {
    val zh = state.zhText
    when {
        state.zhLoading -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在翻译…（长文会分几段翻）", style = MaterialTheme.typography.bodyMedium)
            }
        }

        !zh.isNullOrBlank() -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                zh,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text("换个引擎重翻") }
            Spacer(Modifier.height(24.dp))
        }

        else -> Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    if (state.zhError != null) "翻译失败：${state.zhError}" else "把这一整篇英文翻成中文",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onTranslate) {
                    Text(if (state.zhError != null) "重试" else "翻译全文")
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ReaderHeader(
    title: String,
    mode: String,
    hasImage: Boolean,
    onBack: () -> Unit,
    onModeChange: (String) -> Unit,
    onDeleteDoc: (() -> Unit)?,
    onCopyAll: () -> Unit,
    canCopy: Boolean
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text(
                    title,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
                if (canCopy) TextButton(onClick = onCopyAll) { Text("复制") }
                if (onDeleteDoc != null) TextButton(onClick = onDeleteDoc) { Text("删除") }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip("单词视图", mode == "words") { onModeChange("words") }
                ModeChip("原图对照", mode == "image", enabled = hasImage) { onModeChange("image") }
                ModeChip("纯文本", mode == "text") { onModeChange("text") }
                ModeChip("译文", mode == "zh") { onModeChange("zh") }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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

/** 逐行渲染，每个词都可点；标点也一起显示，读起来不像贴纸条 */
@Composable
private fun WordsView(
    state: ReaderUi,
    dense: Boolean,
    activeWord: String?,
    onWordTap: (String, String?) -> Unit
) {
    val lines = state.ocr.lines.filter { it.text.isNotBlank() }
    if (lines.isEmpty()) {
        Centered { Text("这一页没有可显示的文字") }
        return
    }
    val fontSize: TextUnit = if (dense) 17.sp else 20.sp
    val lineHeight: TextUnit = if (dense) 27.sp else 32.sp

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(lines, key = { it.index }) { line ->
            LineRow(line, fontSize, lineHeight, activeWord, onWordTap)
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun LineRow(
    line: OcrLine,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    activeWord: String?,
    onWordTap: (String, String?) -> Unit
) {
    if (line.words.isEmpty()) {
        Text(
            line.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        line.words.forEach { token ->
            val isActive = activeWord != null && token.text.equals(activeWord, ignoreCase = true)
            WordToken(token, isActive, fontSize, lineHeight) { onWordTap(token.text, line.text) }
        }
    }
}

@Composable
private fun WordToken(
    token: OcrWord,
    active: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onClick: () -> Unit
) {
    Text(
        token.text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = when {
            active -> MaterialTheme.colorScheme.onPrimaryContainer
            !token.hasLetters -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onBackground
        },
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    )
}

/** 原图对照：图按比例铺满屏宽，词框按同一比例画上去，点击取最靠近手指的那一格 */
@Composable
private fun ImageWordView(
    state: ReaderUi,
    activeWord: String?,
    onWordTap: (String, String?) -> Unit
) {
    val bitmap = state.bitmap ?: return
    val ocr = state.ocr
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        var displayed by remember { mutableStateOf(IntSize.Zero) }
        val imageW = ocr.imageWidth.coerceAtLeast(1)
        val scale = if (displayed.width > 0) displayed.width.toFloat() / imageW else 1f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "识别用的原图",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { displayed = it }
                .pointerInput(ocr, displayed) {
                    detectTapGestures { offset: Offset ->
                        val word = hitTest(ocr, offset, scale) ?: return@detectTapGestures
                        onWordTap(word.text, ocr.lines.getOrNull(word.lineIndex)?.text)
                    }
                }
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            if (scale <= 0f) return@Canvas
            ocr.lines.forEach { line ->
                line.words.forEach { token ->
                    val isActive = activeWord != null && token.text.equals(activeWord, ignoreCase = true)
                    if (!token.hasLetters && !isActive) return@forEach
                    val left = token.bounds.left.toFloat() * scale
                    val top = token.bounds.top.toFloat() * scale
                    val w = token.bounds.width.toFloat() * scale
                    val h = token.bounds.height.toFloat() * scale
                    // 框要够显眼：平常态 1.6dp 半透明 + 浅色描边打底，选中态 3dp 实色
                    val strokeWidth = if (isActive) 3.dp.toPx() else 1.6.dp.toPx()
                    if (!isActive) {
                        drawRect(
                            color = Color(0x33FFFFFF),
                            topLeft = Offset(left, top),
                            size = Size(w, h)
                        )
                    }
                    drawRect(
                        color = if (isActive) Color(0xFFE8A33D) else Color(0xA6009E8C),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }
}

private fun hitTest(ocr: OcrResult, tap: Offset, scale: Float): OcrWord? {
    if (scale <= 0f) return null
    val x = (tap.x / scale).toInt()
    val y = (tap.y / scale).toInt()
    val hits = ocr.words.filter { it.hasLetters && it.bounds.inflated(4, 12).contains(x, y) }
    if (hits.isEmpty()) return null
    val anchorY = ocr.lines.getOrNull(hits.first().lineIndex)
        ?.bounds?.let { (it.top + it.bottom) / 2 } ?: y
    return hits.minByOrNull { kotlin.math.abs(((it.bounds.top + it.bounds.bottom) / 2) - anchorY) }
}
