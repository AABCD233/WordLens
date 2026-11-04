package com.wordlens.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.wordlens.app.data.dict.TranslateEngine
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.wordlens.app.data.db.DictPack
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.settings.Settings

/** 官方全量词典包（GitHub Release 资产，可直接在浏览器里打开核对） */
private const val OFFICIAL_PACK_URL =
    "https://github.com/AABCD233/WordLens/releases/download/dict-v1/dict-full.db.gz"

@Composable
fun SettingsScreen(
    settings: Settings,
    dictStatus: DictStatus,
    dictWords: Int,
    ttsReady: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onClearCache: () -> Unit,
    onTestSpeak: () -> Unit,
    packs: List<DictPack>,
    activePackId: String,
    packBusy: String?,
    onInstallPack: (String, String) -> Unit,
    onActivatePack: (DictPack) -> Unit,
    onDeletePack: (DictPack) -> Unit,
    onMessage: (String) -> Unit
) {
    val packUrl = remember { mutableStateOf("") }
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

        Spacer(Modifier.height(18.dp))
        Section("在线翻译源")
        Text(
            "例句翻译、全文翻译和「离线没查到的词」都走这里选的引擎。缓存按引擎分开，换引擎不会拿到旧译文。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(6.dp))
        TranslateEngine.values().forEach { engine ->
            EngineRow(
                engine = engine,
                selected = settings.translateEngine == engine.id
            ) { onUpdate { s -> s.copy(translateEngine = engine.id) } }
        }

        when (settings.translateEngine) {
            TranslateEngine.BAIDU.id -> {
                Spacer(Modifier.height(8.dp))
                KeyField("百度 APP ID", settings.baiduAppId) { v -> onUpdate { s -> s.copy(baiduAppId = v) } }
                KeyField("百度密钥", settings.baiduKey) { v -> onUpdate { s -> s.copy(baiduKey = v) } }
            }
            TranslateEngine.YOUDAO.id -> {
                Spacer(Modifier.height(8.dp))
                KeyField("有道应用 ID", settings.youdaoAppKey) { v -> onUpdate { s -> s.copy(youdaoAppKey = v) } }
                KeyField("有道应用密钥", settings.youdaoAppSecret) { v -> onUpdate { s -> s.copy(youdaoAppSecret = v) } }
            }
            TranslateEngine.AZURE.id -> {
                Spacer(Modifier.height(8.dp))
                KeyField("Azure 订阅密钥", settings.azureKey) { v -> onUpdate { s -> s.copy(azureKey = v) } }
                KeyField("Azure 区域（如 eastasia）", settings.azureRegion) { v -> onUpdate { s -> s.copy(azureRegion = v) } }
            }
            TranslateEngine.AI.id -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (settings.aiReady) "使用下面「AI 详解」里配置的接口翻译。"
                    else "请先到下面「AI 详解」里填好接口地址和 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settings.aiReady) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    lineHeight = 19.sp
                )
            }
            else -> Unit
        }

        Spacer(Modifier.height(10.dp))
        ToggleRow(
            "有道词典在线补中文",
            "离线词典没给中文时，再问一次有道词典（需联网）",
            settings.useYoudaoOnlineDict
        ) { checked -> onUpdate { s -> s.copy(useYoudaoOnlineDict = checked) } }

        Spacer(Modifier.height(18.dp))
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
        Text(
            "词典包可以切换。内置包随 APK 走（11 万词）；更大的全量包从网上下载，不用重装 APK。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(6.dp))
        packs.forEach { pack ->
            val isActive = if (pack.builtIn) activePackId.isBlank() else activePackId == pack.id
            PackRow(
                pack = pack,
                active = isActive,
                onActivate = { onActivatePack(pack) },
                onDelete = { onDeletePack(pack) }
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = packUrl.value,
            onValueChange = { packUrl.value = it },
            label = { Text("词典包网址（.db 或 .db.gz）", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { onInstallPack(packUrl.value, "扩展词典包") },
                enabled = packBusy == null && packUrl.value.isNotBlank()
            ) { Text(if (packBusy == null) "下载并切换" else packBusy) }
            if (packBusy == null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    packUrl.value = OFFICIAL_PACK_URL
                    onMessage("已填入官方全量包地址")
                }) { Text("填入官方地址", fontSize = 12.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onClearCache) { Text("清空网络查询缓存") }

        Spacer(Modifier.height(22.dp))
        Section("关于")
        Text(
            "词镜 WordLens 1.2.0 · 照片只在手机本地识别（ML Kit 端侧模型），不上传。\n" +
                "词典数据来自开源项目 ECDICT（MIT）。\n" +
                "签名指纹 48:5f:0e:1d:…:20:d0:a7（固定证书，覆盖安装不会冲突）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun PackRow(
    pack: DictPack,
    active: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (active) "✓ " else "") + pack.name,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
            val subtitle = if (pack.builtIn) {
                if (pack.words > 0) "随 APK 内置 · ${pack.words} 词" else "随 APK 内置"
            } else {
                "${pack.words} 词 · ${pack.sizeMb}"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!active) {
            Text(
                "切换",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onActivate)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (!pack.builtIn) {
            Text(
                "删除",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EngineRow(
    engine: TranslateEngine,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected) "◉" else "○",
            fontSize = 15.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(engine.label, style = MaterialTheme.typography.bodyLarge, lineHeight = 21.sp)
            Text(
                engine.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
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

