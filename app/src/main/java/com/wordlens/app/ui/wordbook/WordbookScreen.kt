package com.wordlens.app.ui.wordbook

import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.db.SavedWord
import com.wordlens.app.ui.WordbookUi
import com.wordlens.app.ui.theme.PageTitleStyle
import com.wordlens.app.ui.formatDue
import com.wordlens.app.ui.formatTime

@Composable
fun WordbookScreen(
    state: WordbookUi,
    dictReady: Boolean,
    onSearch: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStartReview: () -> Unit,
    onRefresh: () -> Unit,
    onSetGroup: (String, String) -> Unit = { _, _ -> },
    onSetGroupBatch: (List<String>, String) -> Unit = { _, _ -> },
    onExportText: () -> Unit = {},
    onDeleteMany: (List<String>) -> Unit = {},
    onReviewSelected: (List<String>) -> Unit = {},
    groups: List<String> = emptyList()
) {
    var query by remember { mutableStateOf(state.query) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var groupDialogFor by remember { mutableStateOf<String?>(null) }
    var batchGroup by remember { mutableStateOf<List<String>?>(null) }
    val customGroups = groups

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text("生词本", style = PageTitleStyle.copy(color = MaterialTheme.colorScheme.onBackground))
                Text(
                    "点词加进来，按间隔重复复习",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onExportText) { Text("导出", fontSize = 12.sp) }
            TextButton(onClick = {
                selectMode = !selectMode
                if (!selectMode) selected = emptySet()
            }) { Text(if (selectMode) "完成" else "多选", fontSize = 12.sp) }
        }

        if (selectMode) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selected.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        selected = if (selected.size == state.items.size)
                            emptySet() else state.items.map { it.word }.toSet()
                    }) { Text("全选", fontSize = 12.sp) }
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { onReviewSelected(selected.toList()) }
                    ) { Text("复习选中", fontSize = 12.sp) }
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { batchGroup = selected.toList() }
                    ) { Text("分组", fontSize = 12.sp) }
                    TextButton(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            onDeleteMany(selected.toList())
                            selected = emptySet()
                            selectMode = false
                        }
                    ) { Text("删除", fontSize = 12.sp) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onSearch(query) },
                label = { Text("搜索生词") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            // 不到期也能复习：随机抽 20 个过一遍
            Button(onClick = onStartReview) {
                Text(if (state.due > 0) "复习 ${state.due}" else "随机复习")
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Stat("累计", state.total.toString())
                Stat("待复习", state.due.toString())
                Stat("本次筛选", state.items.size.toString())
            }
        }

        if (state.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("生词本还是空的", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "拍一张英文页面，点里面的词，再按「加入生词本」就会存到这里。" +
                        "词会自动排进复习队列：认得就拉长间隔，忘了就 10 分钟后再来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
            return@Column
        }

        // 按分组聚拢：自定义分组优先，没有就用加入日期
        val groups = remember(state.items) {
            state.items.groupBy { it.group.ifBlank { dateGroup(it.addedAt) } }
                .toList()
                .sortedByDescending { (name, _) -> name }
        }
        val collapsed = remember { mutableStateMapOf<String, Boolean>() }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { (name, words) ->
                item(key = "header_$name") {
                    GroupHeader(
                        name = name,
                        count = words.size,
                        collapsed = collapsed[name] == true,
                        onToggle = { collapsed[name] = collapsed[name] != true }
                    )
                }
                if (collapsed[name] != true) {
                    items(words, key = { it.word }) { item ->
                        WordRow(
                            item = item,
                            onOpenWord = onOpenWord,
                            onDelete = onDelete,
                            onAssignGroup = { groupDialogFor = item.word },
                            selectMode = selectMode,
                            checked = item.word in selected,
                            onToggleSelect = {
                                selected = if (item.word in selected)
                                    selected - item.word else selected + item.word
                            }
                        )
                    }
                }
            }
        }

        batchGroup?.let { words ->
            GroupDialog(
                word = "${words.size} 个词",
                groups = customGroups,
                onDismiss = { batchGroup = null },
                onConfirm = { g ->
                    onSetGroupBatch(words, g)
                    batchGroup = null
                    selected = emptySet()
                    selectMode = false
                }
            )
        }

        groupDialogFor?.let { w ->
            GroupDialog(
                word = w,
                groups = customGroups,
                onDismiss = { groupDialogFor = null },
                onConfirm = { g ->
                    onSetGroup(w, g)
                    groupDialogFor = null
                }
            )
        }
    }
}

private fun dateGroup(ts: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(ts))

@Composable
private fun GroupHeader(name: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (collapsed) "▸" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 词",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupDialog(
    word: String,
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(groups.isEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("把 $word 放到…") },
        text = {
            Column {
                if (groups.isNotEmpty() && !creating) {
                    Text(
                        "点一个组直接移过去",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { g ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable { onConfirm(g) }
                            ) {
                                Text(
                                    g,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "＋ 新建分组",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { creating = true }
                        )
                        Text(
                            "按日期（清除分组）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onConfirm("") }
                        )
                    }
                } else {
                    Text(
                        "填一个分组名，比如「四级」「课本第 3 单元」；留空就按加入日期分组。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text("新分组名", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (groups.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "返回已有分组",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { creating = false }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WordRow(
    item: SavedWord,
    onOpenWord: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAssignGroup: () -> Unit,
    selectMode: Boolean = false,
    checked: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    var expanded by remember(item.word) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 只有词本身可点开释义卡；点「详情 / 删除」或展开后的文字都不会误触
                if (selectMode) {
                    Text(
                        if (checked) "☑" else "☐",
                        fontSize = 18.sp,
                        color = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            onClickLabel = if (selectMode) "选中" else "查看释义"
                        ) {
                            if (selectMode) onToggleSelect() else onOpenWord(item.word)
                        }
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                ) {
                    Text(item.word, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (item.phonetic.isNotBlank()) {
                        Text(
                            "/${item.phonetic}/",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!selectMode) {
                    TextButton(onClick = onAssignGroup) {
                        Text("分组", fontSize = 12.sp)
                    }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "详情", fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDelete(item.word) }) {
                        Text("删除", fontSize = 12.sp)
                    }
                }
            }
            val gloss = item.translation.lineSequence().firstOrNull { it.isNotBlank() }
                ?: item.definition.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            Text(
                gloss.ifBlank { "（无释义）" },
                maxLines = if (expanded) 12 else 1,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                if (item.context.isNotBlank()) {
                    Text(
                        "原句：${item.context}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
                if (item.definition.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "英文：${item.definition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "第 ${item.box} 级 · ${formatDue(item.dueAt)} · 已复习 ${item.reviewCount} 次 · 加入于 ${formatTime(item.addedAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
