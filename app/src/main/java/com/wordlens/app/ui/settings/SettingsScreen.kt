package com.wordlens.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.settings.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    dictStatus: DictStatus,
    dictWords: Int,
    ttsReady: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onClearCache: () -> Unit,
    onTestSpeak: () -> Unit,
    onMessage: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Section("查词来源")
        Text(
            "离线词典是底座，下面两项只在离线没查到、或想补英文例句时才发请求。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            "免费英文词典（dictionaryapi.dev）",
            "补充英英释义和例句，不需要 key",
            settings.useFreeDictionaryApi
        ) { checked -> onUpdate { s -> s.copy(useFreeDictionaryApi = checked) } }
        ToggleRow(
            "机器翻译兜底（MyMemory）",
            "离线词典没收的词，给一句中文",
            settings.useMachineTranslate
        ) { checked -> onUpdate { s -> s.copy(useMachineTranslate = checked) } }

        Spacer(Modifier.height(14.dp))
        Section("AI 详解（可选）")
        ToggleRow("启用 AI 详解", "点词卡片上会出现「AI 详解」按钮", settings.aiEnabled) { checked ->
            onUpdate { s -> s.copy(aiEnabled = checked) }
        }
        if (settings.aiEnabled) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.aiBaseUrl,
                onValueChange = { v -> onUpdate { it.copy(aiBaseUrl = v) } },
                label = { Text("Base URL（OpenAI 兼容，含 /v1）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.aiModel,
                onValueChange = { v -> onUpdate { it.copy(aiModel = v) } },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.aiApiKey,
                onValueChange = { v -> onUpdate { it.copy(aiApiKey = v) } },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Key 只存在本机 App 私有目录的偏好文件里，不上传、不进日志。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))

        Section("朗读")
        ToggleRow("点词后自动朗读", null, settings.autoSpeak) { checked ->
            onUpdate { s -> s.copy(autoSpeak = checked) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("语速 ${"%.1f".format(settings.speechRate)}", modifier = Modifier.width(84.dp), fontSize = 13.sp)
            Slider(
                value = settings.speechRate,
                onValueChange = { v -> onUpdate { s -> s.copy(speechRate = v) } },
                valueRange = 0.6f..1.6f,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onTestSpeak, enabled = ttsReady) {
            Text(if (ttsReady) "试听" else "系统未装英文语音，去「设置 → 无障碍 → 文字转语音」装一个")
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))

        Section("离线词典")
        val statusText = when (dictStatus) {
            DictStatus.IDLE -> "未加载"
            DictStatus.PREPARING -> "正在解包…"
            DictStatus.READY -> "已就绪"
            DictStatus.MISSING -> "不可用（assets/dict.db 缺失，请重新构建或运行 data/dict/build_dict.py）"
        }
        Text(
            if (dictStatus == DictStatus.READY) "$statusText · $dictWords 个词" else statusText,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClearCache) { Text("清空网络查询缓存") }

        Spacer(Modifier.height(22.dp))
        Section("关于")
        Text(
            "词镜 WordLens 1.0.0 · 照片只在手机本地识别（ML Kit 端侧模型），不上传。\n" +
                "词典数据来自开源项目 ECDICT（MIT）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

