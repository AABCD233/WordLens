package com.wordlens.app.ui.dictation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.db.SavedWord
import com.wordlens.app.ui.DictationStage
import com.wordlens.app.ui.DictationUi
import com.wordlens.app.ui.theme.PageTitleStyle

/**
 * 单词默写：不判分、不输入。
 *
 * 出题阶段只给提示（英文或中文）+ 朗读，用户在纸上写；
 * 全部翻完后逐词展示完整释义，用户自己标「对/错」；最后给正确率和逐词结果。
 */
@Composable
fun DictationScreen(
    state: DictationUi,
    history: List<String>,
    onStart: (Boolean) -> Unit,          // 是否「看英文写中文」
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onMark: (Boolean) -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    onSpeak: (String) -> Unit,
    onTogglePick: (String) -> Unit,
    onClearPicks: () -> Unit,
    onSelectAll: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("默写", style = PageTitleStyle.copy(color = MaterialTheme.colorScheme.onBackground))
        Text(
            "遮住释义，看提示把单词写下来",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        when (state.stage) {
            DictationStage.SETUP -> Setup(state, history, onStart, onTogglePick, onClearPicks, onSelectAll)
            DictationStage.PRESENT -> Present(state, onNext, onPrev, onSpeak)
            DictationStage.REVIEW -> Review(state, onMark, onSpeak)
            DictationStage.RESULT -> Result(state, onRestart, onFinish)
        }
    }
}

// ---------------- 设置 ----------------

@Composable
private fun Setup(
    state: DictationUi,
    history: List<String>,
    onStart: (Boolean) -> Unit,
    onTogglePick: (String) -> Unit,
    onClearPicks: () -> Unit,
    onSelectAll: () -> Unit
) {
    var reverse by remember { mutableStateOf(false) }

    Card {
        Text("提问方式", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("看中文·写英文", !reverse) { reverse = false }
            ChoiceChip("看英文·写中文", reverse) { reverse = true }
        }
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "选词（${state.selected.size}/${state.pool.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "全选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { onSelectAll() }
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
            )
            Text(
                "清空",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { onClearPicks() }
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        if (state.pool.isEmpty()) {
            Text(
                "生词本还是空的，先去加几个词",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.pool.take(60).forEach { w ->
                val on = w.word in state.selected
                Row(
                    Modifier.fillMaxWidth().clickable { onTogglePick(w.word) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.word, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            w.translation.lineSequence().firstOrNull() ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            if (state.pool.size > 60) {
                Text(
                    "只列前 60 个；全选会用上全部 ${state.pool.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onStart(reverse) },
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.selected.isEmpty()) "先勾选要默写的单词"
                else "开始默写（${state.selected.size} 个，已打乱）"
            )
        }
    }

    if (history.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Card {
            Text("成绩记录", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            history.take(10).forEach { line ->
                val parts = line.split("@")
                Text(
                    "${parts.getOrElse(1) { "" }}    ${parts.firstOrNull() ?: line}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// ---------------- 出题（只给提示，不输入） ----------------

@Composable
private fun Present(state: DictationUi, onNext: () -> Unit, onPrev: () -> Unit, onSpeak: (String) -> Unit) {
    val cur = state.queue.getOrNull(state.index) ?: return
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.index + 1} / ${state.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            // 一律念英文单词：中文提示走系统 TTS 念不出来，念英文还能帮着回忆拼写
            TextButton(onClick = { onSpeak(cur.word) }) { Text("朗读") }
        }
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                if (state.reverse) cur.word else firstGloss(cur),
                fontSize = if (state.reverse) 34.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "在纸上写下来，写完再点「下一个」",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrev, enabled = state.index > 0, modifier = Modifier.weight(1f)) {
                Text("上一个")
            }
            Button(onClick = onNext, modifier = Modifier.weight(2f)) {
                Text(if (state.index + 1 >= state.total) "写完了，开始批改" else "下一个")
            }
        }
    }
}

// ---------------- 逐词自评 ----------------

@Composable
private fun Review(state: DictationUi, onMark: (Boolean) -> Unit, onSpeak: (String) -> Unit) {
    val cur = state.queue.getOrNull(state.index) ?: return
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "批改 ${state.index + 1} / ${state.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSpeak(cur.word) }) { Text("朗读") }
        }
        Spacer(Modifier.height(12.dp))
        Text(cur.word, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        if (cur.phonetic.isNotBlank()) {
            Text(
                "/${cur.phonetic}/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            fullGloss(cur),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 23.sp
        )
        if (cur.definition.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                cur.definition.take(220),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onMark(false) },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("写错了") }
            Button(onClick = { onMark(true) }, modifier = Modifier.weight(1f)) {
                Text("写对了")
            }
        }
    }
}

// ---------------- 结果 ----------------

@Composable
private fun Result(state: DictationUi, onRestart: () -> Unit, onFinish: () -> Unit) {
    val total = state.total.coerceAtLeast(1)
    Card {
        Text("本轮结果", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.correct} / $total",
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "正确率 ${state.correct * 100 / total}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("再来一轮") }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("返回") }
        }
    }
    Spacer(Modifier.height(14.dp))
    Card {
        Text("逐词结果", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        state.queue.forEachIndexed { i, w ->
            val ok = state.marks.getOrNull(i) == true
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (ok) "✓" else "✗", color = if (ok) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.word, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        firstGloss(w),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------- 小工具 ----------------

private fun firstGloss(w: SavedWord): String =
    w.translation.lineSequence().firstOrNull { it.isNotBlank() }
        ?: w.definition.lineSequence().firstOrNull { it.isNotBlank() }
        ?: w.word

private fun fullGloss(w: SavedWord): String =
    w.translation.ifBlank { w.definition }.ifBlank { "（没有释义）" }

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}
