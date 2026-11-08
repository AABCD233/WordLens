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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.ui.DictationUi
import com.wordlens.app.ui.theme.PageTitleStyle

/**
 * 单词默写：从生词本抽词，看提示写英文，自评对错，记成绩。
 *
 * 提示可以只给中文（写英文），也可以只给英文（写中文）——设置里选的模式决定。
 */
@Composable
fun DictationScreen(
    state: DictationUi,
    history: List<String>,
    onStart: (Int, Boolean) -> Unit,   // 题数、是否「英文→中文」
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onSpeak: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("默写", style = PageTitleStyle)
        Text(
            "从生词本里抽词，写出来对答案",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        when {
            state.finished -> ResultCard(state, history, onStart, onFinish)
            state.active -> QuestionCard(state, onInput, onSubmit, onNext, onSpeak)
            else -> StartCard(history, onStart)
        }
    }
}

@Composable
private fun StartCard(history: List<String>, onStart: (Int, Boolean) -> Unit) {
    var count by remember { mutableStateOf(10) }
    var reverse by remember { mutableStateOf(false) }   // false = 中文提示写英文

    Card {
        Text("出题设置", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("题目数量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf(5, 10, 20).forEach { n ->
                ChoiceChip("$n 个", count == n) { count = n }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("提问方式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            ChoiceChip("看中文·写英文", !reverse) { reverse = false }
            ChoiceChip("看英文·写中文", reverse) { reverse = true }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onStart(count, reverse) }, modifier = Modifier.fillMaxWidth()) {
            Text("开始默写")
        }
        Text(
            "题目从「生词本」里随机抽取；生词本是空的就先加几个词。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (history.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Card {
            Text("成绩记录", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            history.take(12).forEach { line ->
                val parts = line.split("@")
                Text(
                    "${parts.getOrElse(1) { "" }}   ${parts.firstOrNull() ?: line}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    state: DictationUi,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onSpeak: (String) -> Unit
) {
    val current = state.queue.getOrNull(state.index) ?: return
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${state.index + 1} / ${state.queue.size} 题",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "答对 ${state.correct}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.queue.size },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
        Spacer(Modifier.height(18.dp))

        // 题干
        Text(
            if (state.reverse) current.word else (current.translation.lineSequence().firstOrNull() ?: current.word),
            fontSize = if (state.reverse) 40.sp else 26.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 36.sp
        )
        if (!state.reverse && current.phonetic.isNotBlank()) {
            Text(
                "/${current.phonetic}/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            label = { Text(if (state.reverse) "写出中文意思" else "写出英文单词") },
            singleLine = true,
            enabled = !state.revealed,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (!state.revealed) {
            Button(onClick = onSubmit, enabled = state.input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("提交")
            }
        } else {
            Surface(
                color = if (state.lastCorrect) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (state.lastCorrect) "答对了 ✓" else "答案：${if (state.reverse) current.translation else current.word}",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!state.reverse && current.translation.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            current.translation.lineSequence().firstOrNull() ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSpeak(if (state.reverse) current.word else current.translation) }) {
                    Text("朗读")
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(if (state.index + 1 >= state.queue.size) "看成绩" else "下一题")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    state: DictationUi,
    history: List<String>,
    onStart: (Int, Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val total = state.queue.size.coerceAtLeast(1)
    Card {
        Text("本轮结束", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "答对 ${state.correct} / $total",
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "正确率 ${state.correct * 100 / total}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onStart(total, state.reverse) }, modifier = Modifier.weight(1f)) {
                Text("再来一轮")
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                Text("返回")
            }
        }
    }
}

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
        Text(
            label,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
