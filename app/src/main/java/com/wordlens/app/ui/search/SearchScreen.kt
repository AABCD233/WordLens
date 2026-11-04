package com.wordlens.app.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 直接查词页：不拍照也能查词。
 * 输入时先给联想（来自离线词典的前缀索引），回车或点「查」直接出释义卡。
 */
@Composable
fun SearchScreen(
    query: String,
    suggestions: List<String>,
    dictReady: Boolean,
    dictWords: Int,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPick: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("输入英文单词", fontSize = 12.sp) },
                    placeholder = { Text("例如 grammar、definitions", fontSize = 13.sp) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            TextButton(onClick = { onQueryChange("") }) { Text("清空", fontSize = 12.sp) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) })
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onSubmit(query) },
                        enabled = query.isNotBlank()
                    ) { Text("查一下", fontWeight = FontWeight.Medium) }
                    Text(
                        if (dictReady) "离线词典 $dictWords 词" else "离线词典准备中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider()

        when {
            !dictReady -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text("正在准备离线词典…", style = MaterialTheme.typography.bodySmall)
                }
            }

            suggestions.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(suggestions, key = { it }) { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(w) }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            w,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            }

            query.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "离线词典里没有以「$query」开头的词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onSubmit(query) }) { Text("还是查一下（可能命中变形）") }
                }
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text("不拍照也能查", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "输入单词后会先给联想，点一下就出音标、中文释义、英文释义、词形变化，还能加进生词本。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text("试试这些", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleChip("grammar") { onPick(it) }
                    SampleChip("however") { onPick(it) }
                    SampleChip("diligent") { onPick(it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleChip("nevertheless") { onPick(it) }
                    SampleChip("studying") { onPick(it) }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "小提示：点词卡片上的「加入生词本」，之后到「生词本 → 复习」按间隔重复背。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun SampleChip(word: String, onPick: (String) -> Unit) {
    Text(
        word,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            .clickable { onPick(word) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
