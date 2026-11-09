@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wordlens.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.model.OcrLine
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.model.OcrWord
import com.wordlens.app.data.settings.Settings
import com.wordlens.app.ui.WordInputDialog
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
    onModeChange: (String) -> Unit,
    onRename: () -> Unit,
    onDeleteDoc: (() -> Unit)?,
    onTranslateAll: () -> Unit,
    onAnalyze: () -> Unit,
    onManualLookup: (String) -> Unit,
    onPrevDoc: () -> Unit,
    onNextDoc: () -> Unit,
    onMessage: (String) -> Unit
) {
    var manualOpen by remember { mutableStateOf(false) }
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
            onRename = onRename,
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

                settings.readerMode == "ai" -> AiArticleView(
                    state = state,
                    aiReady = settings.aiReady,
                    onAnalyze = onAnalyze
                )

                settings.readerMode == "zh" -> ZhTextView(
                    state = state,
                    onTranslate = onTranslateAll,
                    onRetry = { onTranslateAll() }
                )

                settings.readerMode == "image" && state.bitmap != null ->
                    ImageWordView(
                        state = state,
                        activeWord = word.query.takeIf { word.visible },
                        onWordTap = onWordTap,
                        onManualLookup = { manualOpen = true }
                    )

                else -> WordsView(
                    state = state,
                    dense = settings.readerMode == "text",
                    activeWord = word.query.takeIf { word.visible },
                    onWordTap = onWordTap
                )
            }

            // 翻页按钮画在内容之后，才不会被图片/文字盖住
            if (!state.loading && state.error == null) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SideNavButton("‹") { onPrevDoc() }
                    SideNavButton("›") { onNextDoc() }
                }
            }
        }
    }

    if (manualOpen) {
        WordInputDialog(
            onDismiss = { manualOpen = false },
            onConfirm = { w ->
                manualOpen = false
                if (w.isNotBlank()) onManualLookup(w)
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
private fun AiArticleView(state: ReaderUi, aiReady: Boolean, onAnalyze: () -> Unit) {
    val article = state.aiArticle
    when {
        !aiReady -> Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    "AI 精读需要先配置接口：设置 → AI → 填 Base URL、模型名和 Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        state.aiArticleLoading -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("AI 正在精读这一段…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        !article.isNullOrBlank() -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                article,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onAnalyze) { Text("重新精读") }
            Spacer(Modifier.height(24.dp))
        }

        else -> Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    if (state.aiArticleError != null) "AI 精读失败：${state.aiArticleError}"
                    else "让 AI 给这一段做精读笔记：主旨、重点词、难句拆解",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAnalyze) {
                    Text(if (state.aiArticleError != null) "重试" else "开始精读")
                }
            }
        }
    }
}

/** 图片上的半透明浮动小按钮 */
@Composable
private fun FloatChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
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
    onRename: (() -> Unit)? = null,
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
                if (onRename != null) TextButton(onClick = onRename) { Text("改名") }
                if (onDeleteDoc != null) TextButton(onClick = onDeleteDoc) { Text("删除") }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ModeChip("单词视图", mode == "words") { onModeChange("words") }
                ModeChip("原图对照", mode == "image", enabled = hasImage) { onModeChange("image") }
                ModeChip("纯文本", mode == "text") { onModeChange("text") }
                ModeChip("译文", mode == "zh") { onModeChange("zh") }
                ModeChip("AI 精读", mode == "ai") { onModeChange("ai") }
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

/**
 * 原图对照：双指缩放 + 单指拖动，点哪个词查哪个词。
 *
 * 缩放/平移都画在同一层 graphicsLayer 上（transformOrigin 固定在左上角），
 * 所以点击时只要把屏幕坐标反算回内容坐标，坐标就和词框完全对得上。
 */
/** 阅读页两侧的半透明翻页按钮 */
@Composable
private fun SideNavButton(symbol: String, onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.28f),
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = Color.White, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ImageWordView(
    state: ReaderUi,
    activeWord: String?,
    onWordTap: (String, String?) -> Unit,
    onManualLookup: () -> Unit
) {
    val bitmap = state.bitmap ?: return
    val ocr = state.ocr
    // 内容比例只认位图本身。以前用 OCR 的 imageWidth/Height 算容器比例，
    // 只要它和位图朝向不一致（EXIF、旋转），图就会被拉扁。
    val ratio = (bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)).takeIf { it in 0.05f..20f } ?: 1f
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var showBoxes by remember(state.docId) { mutableStateOf(true) }
    var scale by remember(state.docId) { mutableStateOf(1f) }
    var offset by remember(state.docId) { mutableStateOf(Offset.Zero) }

    // 内容尺寸：宽铺满视口，高按位图比例算
    fun contentSize(): Offset =
        if (viewport.width <= 0) Offset.Zero else Offset(viewport.width.toFloat(), viewport.width * ratio)

    /** 平移边界：内容缩放后不能拖出画面；1× 时长图也要能上下拖 */
    fun clampOffset(raw: Offset, s: Float): Offset {
        val content = contentSize()
        if (content.x <= 0f) return Offset.Zero
        val maxX = (content.x * s - viewport.width).coerceAtLeast(0f)
        val maxY = (content.y * s - viewport.height).coerceAtLeast(0f)
        return Offset(raw.x.coerceIn(-maxX, 0f), raw.y.coerceIn(-maxY, 0f))
    }

    /** 屏幕坐标 -> OCR 图像素坐标 */
    fun toImageSpace(pos: Offset): Offset {
        val content = contentSize()
        return (pos - offset) / scale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101413))
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(ocr, viewport) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    val k = next / scale
                    // 以捏合中心为锚点：o' = c - (c - o) * k
                    val anchored = centroid - (centroid - offset) * k
                    offset = clampOffset(anchored + pan, next)
                    scale = next
                }
            }
            .pointerInput(ocr, viewport) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val next = 2.5f
                            scale = next
                            offset = clampOffset(pos - pos * next, next)
                        }
                    },
                    onTap = { pos ->
                        val content = contentSize()
                        val inImage = toImageSpace(pos)
                        if (content.x <= 0f) return@detectTapGestures
                        val sx = content.x / ocr.imageWidth.coerceAtLeast(1)
                        val sy = content.y / ocr.imageHeight.coerceAtLeast(1)
                        val word = hitTest(ocr, inImage, sx, sy) ?: return@detectTapGestures
                        onWordTap(word.text, ocr.lines.getOrNull(word.lineIndex)?.text)
                    }
                )
            }
    ) {
        // 一块 Canvas 同时画位图和词框：位图用明确的 dstRect 绘制，
        // 不经过 Image / ContentScale / aspectRatio，父约束再怎么压也不会变形。
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            val cw = size.width
            val ch = cw * ratio
            if (cw <= 0f || ch <= 0f) return@Canvas
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(cw.toInt(), ch.toInt()),
                filterQuality = FilterQuality.Medium
            )
            if (!showBoxes) return@Canvas
            val sx = cw / ocr.imageWidth.coerceAtLeast(1)
            val sy = ch / ocr.imageHeight.coerceAtLeast(1)
            ocr.lines.forEach { line ->
                line.words.forEach { token ->
                    val isActive = activeWord != null && token.text.equals(activeWord, ignoreCase = true)
                    if (!token.hasLetters && !isActive) return@forEach
                    val left = token.bounds.left.toFloat() * sx
                    val top = token.bounds.top.toFloat() * sy
                    val w = token.bounds.width.toFloat() * sx
                    val h = token.bounds.height.toFloat() * sy
                    // 框要够显眼：平常态 1.6dp 半透明 + 浅色底衬，选中态 3dp 实色
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

        // 左下角：词框开关 + 手动查词（半透明，不挡内容）
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatChip(if (showBoxes) "▣ 词框开" else "▢ 词框关") { showBoxes = !showBoxes }
            Spacer(Modifier.width(8.dp))
            FloatChip("⌨ 手动查词") { onManualLookup() }
        }

        // 缩放提示 / 复位
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scale > 1.05f) {
                Text(
                    "×" + String.format("%.1f", scale),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "复位",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

private fun hitTest(ocr: OcrResult, tap: Offset, sx: Float, sy: Float): OcrWord? {
    if (sx <= 0f || sy <= 0f) return null
    val x = (tap.x / sx).toInt()
    val y = (tap.y / sy).toInt()
    val hits = ocr.words.filter { it.hasLetters && it.bounds.inflated(4, 12).contains(x, y) }
    if (hits.isEmpty()) return null
    val anchorY = ocr.lines.getOrNull(hits.first().lineIndex)
        ?.bounds?.let { (it.top + it.bottom) / 2 } ?: y
    return hits.minByOrNull { kotlin.math.abs(((it.bounds.top + it.bounds.bottom) / 2) - anchorY) }
}
