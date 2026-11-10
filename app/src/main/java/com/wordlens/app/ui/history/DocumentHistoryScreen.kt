package com.wordlens.app.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.db.DocumentRow
import com.wordlens.app.ui.formatTime
import com.wordlens.app.ui.theme.PageTitleStyle
import java.io.File

/** 拍照历史管理：缩略图 + 名称，可多选批量删除 */
@Composable
fun DocumentHistoryScreen(
    docs: List<DocumentRow>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (List<Long>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    "拍照历史",
                    style = PageTitleStyle.copy(color = MaterialTheme.colorScheme.onBackground)
                )
                Text(
                    "共 ${docs.size} 条 · 已选 ${selected.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                selected = if (selected.size == docs.size) emptySet() else docs.map { it.id }.toSet()
            }) { Text(if (selected.size == docs.size) "取消全选" else "全选") }
        }

        if (selected.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选中的 ${selected.size} 条记录",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (!confirming) {
                        Button(onClick = { confirming = true }) { Text("删除") }
                    } else {
                        TextButton(onClick = {
                            onDelete(selected.toList())
                            selected = emptySet()
                            confirming = false
                        }) { Text("确认删除") }
                        TextButton(onClick = { confirming = false }) { Text("取消") }
                    }
                }
            }
        }

        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有识别记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(docs, key = { it.id }) { doc ->
                val checked = doc.id in selected
                Surface(
                    color = if (checked) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            selected = if (checked) selected - doc.id else selected + doc.id
                        }
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(doc.imagePath)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                doc.title.ifBlank { "（未命名）" },
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${doc.wordCount} 词 · ${formatTime(doc.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (checked) "✓" else "○",
                            color = if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/** 缩略图：文件没了就显示「图片已删除」 */
@Composable
private fun Thumb(path: String?) {
    val bmp = remember(path) {
        path?.let { p ->
            runCatching {
                val f = File(p)
                if (!f.exists()) null else {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(p, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(width = 64.dp, height = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                "图片\n已删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}
