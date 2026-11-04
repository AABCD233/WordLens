package com.wordlens.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordlens.app.data.db.DictPack
import com.wordlens.app.data.db.DictPackManager
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.dict.TranslateEngine
import com.wordlens.app.data.settings.Settings

/**
 * 设置页。分组 + 可折叠：常用项默认展开，进阶项默认收起，
 * 免得一屏塞十几行看着乱。
 */
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
    onInstallOfficial: () -> Unit,
    onActivatePack: (DictPack) -> Unit,
    onDeletePack: (DictPack) -> Unit,
    onMessage: (String) -> Unit
) {
    val engine = TranslateEngine.of(settings.translateEngine)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // ---------- 查词与翻译 ----------
        SectionCard(
            title = "查词与翻译",
            subtitle = "在线词典 · 翻译引擎：${engine.label}",
            defaultExpanded = true
        ) {
            ToggleRow(
                "免费英文词典",
                "dictionaryapi.dev，补英英释义和例句，无需 key",
                settings.useFreeDictionaryApi
            ) { checked -> onUpdate { it.copy(useFreeDictionaryApi = checked) } }

            ToggleRow(
                "机器翻译兜底",
                "离线没收录的词，用下面选的引擎给一句中文",
                settings.useMachineTranslate
            ) { checked -> onUpdate { it.copy(useMachineTranslate = checked) } }

            ToggleRow(
                "有道词典在线补中文",
                "离线释义偏薄时再问一次有道（需联网）",
                settings.useYoudaoOnlineDict
            ) { checked -> onUpdate { it.copy(useYoudaoOnlineDict = checked) } }

            Spacer(Modifier.height(10.dp))
            SubTitle("翻译引擎")
            TranslateEngine.values().forEach { e ->
                EngineRow(e, settings.translateEngine == e.id) {
                    onUpdate { it.copy(translateEngine = e.id) }
                }
            }
            when (settings.translateEngine) {
                TranslateEngine.BAIDU.id -> {
                    Spacer(Modifier.height(6.dp))
                    KeyField("百度 APP ID", settings.baiduAppId) { v -> onUpdate { it.copy(baiduAppId = v) } }
                    KeyField("百度密钥", settings.baiduKey) { v -> onUpdate { it.copy(baiduKey = v) } }
                }
                TranslateEngine.YOUDAO.id -> {
                    Spacer(Modifier.height(6.dp))
                    KeyField("有道应用 ID", settings.youdaoAppKey) { v -> onUpdate { it.copy(youdaoAppKey = v) } }
                    KeyField("有道应用密钥", settings.youdaoAppSecret) { v -> onUpdate { it.copy(youdaoAppSecret = v) } }
                }
                TranslateEngine.AZURE.id -> {
                    Spacer(Modifier.height(6.dp))
                    KeyField("Azure 订阅密钥", settings.azureKey) { v -> onUpdate { it.copy(azureKey = v) } }
                    KeyField("Azure 区域（如 eastasia）", settings.azureRegion) { v -> onUpdate { it.copy(azureRegion = v) } }
                }
                TranslateEngine.AI.id -> {
                    Spacer(Modifier.height(6.dp))
                    Hint(
                        if (settings.aiReady) "使用下面「AI」分组里配置的接口翻译。"
                        else "请先到下面「AI」分组里填好接口地址和 Key。",
                        error = !settings.aiReady
                    )
                }
                else -> Unit
            }
        }

        // ---------- 离线词典 ----------
        SectionCard(
            title = "离线词典",
            subtitle = dictSummary(dictStatus, dictWords, activePackId),
            defaultExpanded = false
        ) {
            packs.forEach { pack ->
                val active = if (pack.builtIn) activePackId.isBlank() else activePackId == pack.id
                PackRow(pack, active, { onActivatePack(pack) }, { onDeletePack(pack) })
            }

            Spacer(Modifier.height(10.dp))
            if (packBusy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(packBusy, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onInstallOfficial,
                enabled = packBusy == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("下载官方全量包（76 万词 · 约 37 MB）") }
            Hint("走 gh-proxy 加速镜像，失败会自动换源。装完自动切换，不用重装 App。")

            Spacer(Modifier.height(10.dp))
            SubTitle("或者填自己的词典包地址")
            val url = remember { mutableStateOf("") }
            OutlinedTextField(
                value = url.value,
                onValueChange = { url.value = it },
                label = { Text("http(s)://…（.db 或 .db.gz）", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onInstallPack(url.value, "扩展词典包") },
                    enabled = packBusy == null && url.value.isNotBlank()
                ) { Text("下载并切换") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    url.value = DictPackManager.OFFICIAL_PACK_URL
                    onMessage("已填入官方包原始地址")
                }) { Text("填入官方地址", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                Text("清空网络查询缓存")
            }
        }

        // ---------- 朗读 ----------
        SectionCard(
            title = "朗读",
            subtitle = if (ttsReady) "系统语音已就绪" else "未检测到英文语音",
            defaultExpanded = false
        ) {
            ToggleRow("点词后自动朗读", null, settings.autoSpeak) { checked ->
                onUpdate { it.copy(autoSpeak = checked) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "语速 ${"%.1f".format(settings.speechRate)}",
                    modifier = Modifier.width(84.dp),
                    fontSize = 13.sp
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = { v -> onUpdate { it.copy(speechRate = v) } },
                    valueRange = 0.6f..1.6f,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onTestSpeak, enabled = ttsReady) {
                Text(if (ttsReady) "试听一句" else "系统未装英文语音，去「设置 → 无障碍 → 文字转语音」装一个")
            }
        }

        // ---------- AI ----------
        SectionCard(
            title = "AI",
            subtitle = if (settings.aiReady) "已配置：${settings.aiModel}" else "未配置（可选）",
            defaultExpanded = settings.aiEnabled
        ) {
            ToggleRow(
                "启用 AI",
                "词卡上的「AI 详解 / 例句 / 搭配」和整篇精读都靠它",
                settings.aiEnabled
            ) { checked -> onUpdate { it.copy(aiEnabled = checked) } }
            if (settings.aiEnabled) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.aiBaseUrl,
                    onValueChange = { v -> onUpdate { it.copy(aiBaseUrl = v) } },
                    label = { Text("Base URL（OpenAI 兼容，含 /v1）", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.aiModel,
                    onValueChange = { v -> onUpdate { it.copy(aiModel = v) } },
                    label = { Text("模型名", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.aiApiKey,
                    onValueChange = { v -> onUpdate { it.copy(aiApiKey = v) } },
                    label = { Text("API Key", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Hint("Key 只存在本机 App 私有目录的偏好文件里，不上传、不进日志。")
            }
        }

        // ---------- 关于 ----------
        SectionCard(title = "关于", subtitle = "词镜 WordLens 1.3.0", defaultExpanded = false) {
            Text(
                "照片只在手机本地识别（ML Kit 端侧模型），不上传。\n" +
                    "词典数据来自开源项目 ECDICT（MIT）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "签名证书 SHA-256（固定，覆盖安装不会冲突）：\n" +
                    "48:5f:0e:1d:de:10:65:73:4c:a5:08:ca:f3:5c:d0:f2:9f:2d:aa:cf:62:0c:28:e7:24:6b:c9:db:42:20:d0:a7",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun dictSummary(status: DictStatus, words: Int, activePackId: String): String = when (status) {
    DictStatus.READY -> if (activePackId.isBlank()) "内置包 · $words 词" else "扩展包 · $words 词"
    DictStatus.PREPARING -> "正在解包…"
    DictStatus.MISSING -> "不可用（assets/dict.db 缺失）"
    DictStatus.IDLE -> "未加载"
}

// ---------------- 小组件 ----------------

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    defaultExpanded: Boolean,
    content: @Composable () -> Unit
) {
    val expanded = remember { mutableStateOf(defaultExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded.value = !expanded.value }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (expanded.value) "收起" else "展开",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded.value) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 6.dp)
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
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EngineRow(engine: TranslateEngine, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
private fun PackRow(
    pack: DictPack,
    active: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
            TextButton(onClick = onActivate) { Text("切换", fontSize = 12.sp) }
        }
        if (!pack.builtIn) {
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
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
