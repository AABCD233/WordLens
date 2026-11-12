package com.wordlens.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 重命名对话框（最近识别的记录改名用） */
@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(initial) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("改个名字") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 80) text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("标题", fontSize = 12.sp) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "把识别出的第一行当标题经常不好读，可以改成「课本 U3」「周报材料」这种",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 自定义释义编辑框。
 * 多行输入，保存后立刻显示在词卡最上面，只存本机。
 */
@Composable
fun NoteDialog(
    word: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「$word」的我的释义") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    label = { Text("可以写释义、易混点、记忆方法……", fontSize = 12.sp) },
                    minLines = 4,
                    maxLines = 8
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "只保存在这台手机上，会跟着这个词一直显示。清空后保存即可删除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 手动查词：OCR 认错字、或者图上没这个词时，直接敲一个查 */
@Composable
fun WordInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动查词") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("英文单词", fontSize = 12.sp) },
                    placeholder = { Text("识别错的词可以在这里直接查", fontSize = 12.sp) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("查") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 删除确认（删除不可撤销，单独问一句） */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


/** 首次启动的免责声明：读 5 秒才允许继续 */
@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    var left by remember { mutableStateOf(8) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            kotlinx.coroutines.delay(1000)
            left -= 1
        }
    }
    AlertDialog(
        // 点外部/返回都不关，必须读完点按钮
        onDismissRequest = {},
        title = { Text("使用前请先读一遍") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Section("一、关于这个 App")
                Body(
                    "词镜是一个个人开发的英语学习辅助工具，用拍照识别英文文章、点词查释义。" +
                        "项目由 AI 参与编写，目前仍在开发初期，功能不完整、边界情况可能没覆盖，" +
                        "也可能存在崩溃、卡顿、耗电等缺陷。"
                )

                Section("二、释义仅供参考")
                Body(
                    "词典内容来自开源词库 ECDICT（约 11 万词）与第三方在线服务，" +
                        "机器整理的词性、释义、例句可能存在错误或过时。" +
                        "请不要把本 App 的解释当作考试答案、翻译交付或法律、医疗等专业用途的依据。"
                )

                Section("三、你的数据")
                Body(
                    "· 照片与识别结果只存在手机本地，不会上传到任何服务器。\n" +
                        "· 生词本、自定义释义、复习记录存在本地数据库；卸载 App 会一并删除，建议定期用「数据备份」导出。\n" +
                        "· 联网功能（在线词典、翻译、AI 详解、在线朗读）会把你要查的单词或句子发送到对应服务商，整篇翻译/精读则发送全文。\n" +
                        "· 使用 AI 功能需要你自己填接口地址和密钥，密钥只存在本机。"
                )

                Section("四、网络与第三方服务")
                Body(
                    "在线功能依赖第三方接口，可能随时限流、改协议或停止服务，导致查不到、报错或很慢；" +
                        "这些都不影响离线查词、生词本和复习。若开启 AI，产生的调用费用由你的账号承担。"
                )

                Section("五、权限说明")
                Body(
                    "相机：拍照识别；相册：选图识别；通知：悬浮球前台服务需要常驻通知；" +
                        "「显示在其他应用上层」：仅在你主动开启悬浮球查词时使用。"
                )

                Section("六、责任")
                Body(
                    "本软件按「现状」提供，不附带任何担保。因使用本软件造成的成绩、翻译、数据丢失等问题，" +
                        "作者不承担责任。继续使用即表示你已理解并接受以上内容。"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = left == 0) {
                Text(if (left == 0) "我已阅读并同意" else "请阅读（${left}s）")
            }
        }
    )
}

@Composable
private fun Section(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 3.dp)
    )
}

@Composable
private fun Body(t: String) {
    Text(t, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
}


/** 发现新版本：问用户要不要下载，顺便让他选走不走加速 */
@Composable
fun UpdateDialog(
    tag: String,
    accel: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var useAccel by remember { mutableStateOf(accel) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 $tag") },
        text = {
            Column {
                Text(
                    "下载安装包后会自动打开系统安装器。安装包装完就没用了，App 下次启动会自动删掉。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = useAccel,
                        onCheckedChange = { useAccel = it }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("使用 GitHub 加速下载", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "国内直连可能很慢，走镜像更稳",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(useAccel) }) { Text("下载并安装") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
