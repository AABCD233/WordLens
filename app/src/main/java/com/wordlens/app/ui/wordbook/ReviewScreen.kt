package com.wordlens.app.ui.wordbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.ui.ReviewUi
import com.wordlens.app.ui.formatDue

/**
 * 复习：先回想，再翻答案，然后自评。
 * box 越高间隔越长（MainViewModel.INTERVALS_DAYS），答错回到 0 并 10 分钟后再出现。
 */
@Composable
fun ReviewScreen(
    state: ReviewUi,
    speechRate: Float,
    onSpeak: (String) -> Unit,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onExit: () -> Unit
) {
    val item = state.queue.getOrNull(state.index)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onExit) { Text("退出") }
            Spacer(Modifier.weight(1f))
            Text(
                "${state.index + (if (state.finished) 0 else 1)} / ${state.queue.size}",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (item == null) {
                    Text("本轮结束 🎉", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这轮复习了 ${state.reviewed} 个词，进度已经记下。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                } else {
                    Text(item.word, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    if (item.phonetic.isNotBlank() && state.revealed) {
                        Text(
                            "/${item.phonetic}/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onSpeak(item.word) }) { Text("🔊 听一遍") }

                    Spacer(Modifier.height(18.dp))
                    if (!state.revealed) {
                        Text(
                            "先想一下它的意思，再看答案。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("显示释义") }
                    } else {
                        Column(
                            Modifier.fillMaxWidth().height(210.dp).verticalScroll(rememberScrollState())
                        ) {
                            val gloss = item.translation.ifBlank { item.definition }
                            gloss.lines().filter { it.isNotBlank() }.forEach { line ->
                                Text(line, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 1.dp))
                            }
                            if (item.context.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "原句：${item.context}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                            if (item.tags.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "标签：${item.tags.replace(",", " ")} · 目前第 ${item.box} 级 · ${formatDue(item.dueAt)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onGrade(false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("忘了") }
                            Button(onClick = { onGrade(true) }, modifier = Modifier.weight(1f)) { Text("记得") }
                        }
                    }
                }
            }
        }
    }
}
