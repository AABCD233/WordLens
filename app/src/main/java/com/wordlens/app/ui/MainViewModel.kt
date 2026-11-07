package com.wordlens.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.wordlens.app.AppContainer
import com.wordlens.app.data.db.DocumentRow
import com.wordlens.app.data.dict.AiKind
import com.wordlens.app.data.db.DictPack
import com.wordlens.app.data.db.DictPackManager
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.db.SavedWord
import com.wordlens.app.data.model.Definition
import com.wordlens.app.data.model.OcrResult
import com.wordlens.app.data.settings.Settings
import com.wordlens.app.data.tts.TtsEngine
import com.wordlens.app.data.model.WordNormalizer
import com.wordlens.app.util.ImageFiles
import com.wordlens.app.util.OcrCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReaderUi(
    val loading: Boolean = false,
    val docId: Long = 0,
    val title: String = "",
    val text: String = "",
    val ocr: OcrResult = OcrResult.EMPTY,
    val bitmap: Bitmap? = null,
    val error: String? = null,
    /** 全文译文；null 表示还没翻过 */
    val zhText: String? = null,
    val zhLoading: Boolean = false,
    val zhError: String? = null,
    val aiArticle: String? = null,
    val aiArticleLoading: Boolean = false,
    val aiArticleError: String? = null
)

data class WordUi(
    val visible: Boolean = false,
    val query: String = "",
    val sentence: String? = null,
    val loading: Boolean = false,
    val definition: Definition? = null,
    val aiText: String? = null,
    val aiLoading: Boolean = false,
    val aiError: String? = null,
    /** 当前 AI 结果是哪个维度：explain / examples / collocations */
    val aiKind: String = "",
    /** 例句译文：key 是原文 */
    val exampleZh: Map<String, String> = emptyMap(),
    val translatingExample: String? = null
)

data class WordbookUi(
    val items: List<SavedWord> = emptyList(),
    val total: Int = 0,
    val due: Int = 0,
    val query: String = ""
)

data class ReviewUi(
    val active: Boolean = false,
    val queue: List<SavedWord> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val finished: Boolean = false,
    val reviewed: Int = 0
)

class MainViewModel(private val app: AppContainer) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: StateFlow<Settings> = app.settings.state
    val dictStatus: StateFlow<DictStatus> = app.dictStore.status
    val ttsState = app.tts.state

    private val _library = MutableStateFlow<List<DocumentRow>>(emptyList())
    val library: StateFlow<List<DocumentRow>> = _library.asStateFlow()

    private val _reader = MutableStateFlow(ReaderUi())
    val reader: StateFlow<ReaderUi> = _reader.asStateFlow()

    private val _word = MutableStateFlow(WordUi())
    val word: StateFlow<WordUi> = _word.asStateFlow()

    private val _wordbook = MutableStateFlow(WordbookUi())
    val wordbook: StateFlow<WordbookUi> = _wordbook.asStateFlow()

    private val _review = MutableStateFlow(ReviewUi())
    val review: StateFlow<ReviewUi> = _review.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _dictWords = MutableStateFlow(0)
    val dictWords: StateFlow<Int> = _dictWords.asStateFlow()

    private val _packs = MutableStateFlow<List<DictPack>>(emptyList())
    val packs: StateFlow<List<DictPack>> = _packs.asStateFlow()

    /** 下载词典包时的进度文字，null = 空闲 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var suggestJob: kotlinx.coroutines.Job? = null

    private val _packBusy = MutableStateFlow<String?>(null)
    val packBusy: StateFlow<String?> = _packBusy.asStateFlow()

    init {
        scope.launch {
            // 上次选的扩展包还在就继续用它
            val active = app.dictPacks.activeId()
            if (active.isNotBlank()) {
                app.dictPacks.installed().firstOrNull { it.id == active && !it.builtIn }
                    ?.let { app.dictStore.usePack(it.file) }
            }
            app.dictionary.ensureDictReady()
            _dictWords.value = app.dictionary.dictWordCount()
            refreshPacks()
        }
        refreshLibrary()
    }

    // ---------------- 文档 / OCR ----------------

    fun refreshLibrary() {
        scope.launch { _library.value = app.database.recentDocuments(60) }
    }

    /** 拍照（自己落盘的 File）或相册（content://），统一走这里 */
    fun analyzeImage(uri: Uri, ownedFile: File? = null) {
        _reader.value = ReaderUi(loading = true)
        scope.launch {
            try {
                val result = app.ocr.recognize(uri)
                if (result.wordCount == 0) {
                    _reader.value = ReaderUi(
                        loading = false,
                        error = "没有识别到英文文字。试试靠近一点、光线亮一点，或者换一张更清晰的图。"
                    )
                    return@launch
                }
                val title = result.lines.firstOrNull { it.text.isNotBlank() }?.text
                    ?.take(40)?.trim()
                    ?: "识别于 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

                val doc = DocumentRow(
                    title = title,
                    imagePath = ownedFile?.absolutePath,
                    fullText = result.fullText,
                    wordsJson = OcrCodec.encode(result),
                    imageWidth = result.imageWidth,
                    imageHeight = result.imageHeight,
                    wordCount = result.wordCount,
                    createdAt = System.currentTimeMillis()
                )
                val id = app.database.insertDocument(doc)
                _reader.value = ReaderUi(
                    loading = false,
                    docId = id,
                    title = title,
                    text = result.fullText,
                    ocr = result,
                    bitmap = ownedFile?.let { ImageFiles.loadSampled(it, 1600) }
                )
                refreshLibrary()
            } catch (t: Throwable) {
                _reader.value = ReaderUi(loading = false, error = "识别失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun openDocument(id: Long) {
        _reader.value = ReaderUi(loading = true, docId = id)
        scope.launch {
            val doc = app.database.document(id)
            if (doc == null) {
                _reader.value = ReaderUi(loading = false, error = "这条记录已经不在了")
                return@launch
            }
            val ocr = OcrCodec.decode(doc.wordsJson)
            _reader.value = ReaderUi(
                loading = false,
                docId = doc.id,
                title = doc.title,
                text = doc.fullText,
                ocr = ocr,
                bitmap = doc.imagePath?.let { path ->
                    val f = File(path)
                    if (f.exists()) ImageFiles.loadSampled(f, 1600) else null
                }
            )
        }
    }

    fun closeReader() {
        _reader.value = ReaderUi()
        _word.value = WordUi()
    }

    fun renameDocument(id: Long, newTitle: String) {
        val title = newTitle.trim().take(80)
        if (title.isEmpty()) {
            _messages.tryEmit("名字不能为空")
            return
        }
        scope.launch {
            app.database.updateDocumentTitle(id, title)
            if (_reader.value.docId == id) _reader.value = _reader.value.copy(title = title)
            refreshLibrary()
            _messages.tryEmit("已改名为「$title」")
        }
    }

    fun deleteDocument(id: Long) {
        scope.launch {
            app.database.document(id)?.imagePath?.let { ImageFiles.deleteQuietly(File(it)) }
            app.database.deleteDocument(id)
            refreshLibrary()
            _messages.tryEmit("已删除这条记录")
        }
    }

    // ---------------- 查词 ----------------

    fun lookupWord(raw: String, sentence: String? = null) {
        val key = WordNormalizer.normalize(raw)
        if (key.isEmpty()) return
        _word.value = WordUi(visible = true, query = raw, sentence = sentence, loading = true)
        scope.launch {
            val def = runCatching { app.dictionary.lookup(raw) }.getOrElse { Definition.empty(raw) }
            _word.value = _word.value.copy(loading = false, definition = def, query = raw)
            if (def.onlineFailed) {
                _messages.tryEmit("没联网，只显示了离线释义")
            }
            if (settings.value.autoSpeak) speak(def.lemma)
        }
    }

    /** 直接查词页的输入联想（防抖 180ms，别每敲一个字母就查库） */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        suggestJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = scope.launch {
            kotlinx.coroutines.delay(180)
            _suggestions.value = runCatching {
                app.dictionary.suggest(q.lowercase(), 20)
            }.getOrDefault(emptyList())
        }
    }

    fun clearSearch() {
        suggestJob?.cancel()
        _searchQuery.value = ""
        _suggestions.value = emptyList()
    }

    /** 直接查词（不依赖 OCR） */
    fun searchWord(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        lookupWord(q, null)
    }

    fun dismissWord() {
        _word.value = WordUi()
    }

    fun speakCurrent() {
        val w = _word.value.definition?.lemma ?: _word.value.query
        speak(w)
    }

    fun speak(text: String) {
        val s = settings.value
        val mode = if (s.ttsMode == "online") TtsEngine.Mode.ONLINE else TtsEngine.Mode.SYSTEM
        val ok = app.tts.speak(text, s.speechRate, mode)
        if (!ok) _messages.tryEmit("朗读失败：系统语音不可用，也没连上网络")
    }

    fun requestAiExplain(kind: String = AiKind.EXPLAIN.id) {
        val current = _word.value
        val word = current.definition?.lemma ?: current.query
        if (word.isBlank()) return
        val k = AiKind.values().firstOrNull { it.id == kind } ?: AiKind.EXPLAIN
        _word.value = current.copy(aiLoading = true, aiError = null, aiKind = k.id, aiText = null)
        scope.launch {
            val result = app.dictionary.aiExplain(word, current.sentence, k)
            _word.value = _word.value.copy(
                aiLoading = false,
                aiText = result.getOrNull(),
                aiError = result.exceptionOrNull()?.message
            )
        }
    }

    /** 整篇 AI 精读 */
    fun analyzeArticle(force: Boolean = false) {
        val state = _reader.value
        if (state.text.isBlank()) {
            _messages.tryEmit("还没有识别到文字")
            return
        }
        if (state.aiArticleLoading) return
        if (!force && !state.aiArticle.isNullOrBlank()) return
        _reader.value = state.copy(aiArticleLoading = true, aiArticleError = null)
        scope.launch {
            val result = app.dictionary.aiAnalyzeArticle(state.text)
            val cur = _reader.value
            _reader.value = result.fold(
                onSuccess = { cur.copy(aiArticleLoading = false, aiArticle = it, aiArticleError = null) },
                onFailure = { cur.copy(aiArticleLoading = false, aiArticleError = it.message ?: "AI 调用失败") }
            )
        }
    }

    /** 翻译点词卡片里的一条英文例句 */
    fun translateExample(example: String) {
        val text = example.trim()
        if (text.isEmpty()) return
        if (_word.value.exampleZh.containsKey(text)) return
        _word.value = _word.value.copy(translatingExample = text)
        scope.launch {
            val result = app.dictionary.translateText(text)
            val current = _word.value
            _word.value = if (result.isSuccess) {
                current.copy(
                    exampleZh = current.exampleZh + (text to result.getOrThrow()),
                    translatingExample = null
                )
            } else {
                _messages.tryEmit("例句翻译失败：" + (result.exceptionOrNull()?.message ?: "未知错误"))
                current.copy(translatingExample = null)
            }
        }
    }

    /** 翻译整篇识别出来的英文 */
    fun translateFullText(force: Boolean = false) {
        val state = _reader.value
        if (state.text.isBlank()) {
            _messages.tryEmit("还没有识别到文字")
            return
        }
        if (state.zhLoading) return
        if (!force && !state.zhText.isNullOrBlank()) return
        _reader.value = state.copy(zhLoading = true, zhError = null)
        scope.launch {
            val result = app.dictionary.translateText(state.text)
            val current = _reader.value
            _reader.value = if (result.isSuccess) {
                current.copy(zhLoading = false, zhText = result.getOrThrow(), zhError = null)
            } else {
                current.copy(
                    zhLoading = false,
                    zhError = result.exceptionOrNull()?.message ?: "翻译失败"
                )
            }
        }
    }

    /** 保存/清除用户自己写的释义，保存后立刻刷新当前词卡 */
    fun saveUserNote(word: String, note: String) {
        val key = word.trim()
        if (key.isEmpty()) return
        scope.launch {
            app.database.setUserNote(key, note)
            val cur = _word.value
            val def = cur.definition
            if (def != null && (def.lemma.equals(key, true) || def.query.equals(key, true))) {
                _word.value = cur.copy(definition = def.copy(userNote = note.trim().ifBlank { null }))
            }
            _messages.tryEmit(if (note.isBlank()) "已清除自定义释义" else "已保存你的释义")
        }
    }

    fun saveCurrentWord() {
        val def = _word.value.definition ?: return
        val word = def.lemma.ifBlank { _word.value.query }
        if (word.isBlank()) return
        scope.launch {
            app.database.saveWord(
                SavedWord(
                    word = word,
                    phonetic = def.phonetic.orEmpty(),
                    translation = def.zhLines.joinToString("\n").ifBlank { def.zhMachine.orEmpty() },
                    definition = def.enMeanings.firstOrNull()?.definitions?.firstOrNull()?.text.orEmpty(),
                    tags = def.tags.joinToString(","),
                    context = _word.value.sentence.orEmpty(),
                    source = "ocr",
                    box = 0,
                    dueAt = System.currentTimeMillis(),
                    addedAt = System.currentTimeMillis()
                )
            )
            refreshWordbook()
            _messages.tryEmit("已加入生词本：$word")
        }
    }

    // ---------------- 生词本 ----------------

    fun refreshWordbook(query: String = _wordbook.value.query) {
        scope.launch {
            val items = app.database.savedWords(query.ifBlank { null }, 500)
            val (total, due) = app.database.wordStats()
            _wordbook.value = WordbookUi(items = items, total = total, due = due, query = query)
        }
    }

    fun searchWordbook(query: String) {
        _wordbook.value = _wordbook.value.copy(query = query)
        refreshWordbook(query)
    }

    fun deleteSaved(word: String) {
        scope.launch {
            app.database.deleteWord(word)
            refreshWordbook()
            _messages.tryEmit("已移出生词本：$word")
        }
    }

    // ---------------- 复习 ----------------

    fun startReview() {
        scope.launch {
            val queue = app.database.dueWords(System.currentTimeMillis(), 40)
            if (queue.isEmpty()) {
                _messages.tryEmit("今天没有到期的词，去拍照读一段吧")
                return@launch
            }
            _review.value = ReviewUi(active = true, queue = queue, index = 0, revealed = false)
        }
    }

    fun revealReview() {
        _review.value = _review.value.copy(revealed = true)
    }

    fun gradeReview(remembered: Boolean) {
        val state = _review.value
        val item = state.queue.getOrNull(state.index) ?: return
        val nextBox = if (remembered) (item.box + 1).coerceAtMost(INTERVALS_DAYS.size - 1) else 0
        val dueAt = if (remembered) {
            System.currentTimeMillis() + INTERVALS_DAYS[nextBox] * 24L * 3600_000L
        } else {
            System.currentTimeMillis() + 10 * 60_000L // 10 分钟后再出现
        }
        scope.launch {
            app.database.applyReview(item.word, nextBox, dueAt)
            val nextIndex = state.index + 1
            val done = nextIndex >= state.queue.size
            _review.value = state.copy(
                index = nextIndex,
                revealed = false,
                finished = done,
                reviewed = state.reviewed + 1
            )
            if (done) refreshWordbook()
        }
    }

    fun exitReview() {
        _review.value = ReviewUi()
        refreshWordbook()
    }

    // ---------------- 备份 / 恢复 ----------------

    fun exportBackup(uri: android.net.Uri) {
        scope.launch {
            runCatching { app.backup.exportTo(uri) }
                .onSuccess { _messages.tryEmit("备份已导出") }
                .onFailure { _messages.tryEmit("导出失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        scope.launch {
            runCatching { app.backup.importFrom(uri) }
                .onSuccess { r ->
                    refreshLibrary()
                    refreshWordbook()
                    _messages.tryEmit(
                        "已导入：记录 ${r.documents} 条、生词 ${r.words} 个、释义 ${r.notes} 条" +
                            if (r.skipped > 0) "（跳过重复 ${r.skipped} 条）" else ""
                    )
                }
                .onFailure { _messages.tryEmit("导入失败：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    // ---------------- 设置 ----------------

    fun updateSettings(transform: (Settings) -> Settings) = app.settings.update(transform)

    fun clearNetworkCache() {
        scope.launch {
            app.database.cacheClear()
            _messages.tryEmit("已清空网络缓存")
        }
    }

    // ---------------- 词典包 ----------------

    fun refreshPacks() {
        scope.launch {
            _packs.value = app.dictPacks.installed()
            _dictWords.value = app.dictionary.dictWordCount()
        }
    }

    /** 一键下载官方全量包：多个加速镜像依次尝试 */
    fun installOfficialPack() {
        if (_packBusy.value != null) return
        _packBusy.value = "正在连接…"
        scope.launch {
            val result = runCatching {
                app.dictPacks.install(DictPackManager.OFFICIAL_PACK_MIRRORS, "全量词典包") { done ->
                    _packBusy.value = if (done >= 0) {
                        "已下载 " + String.format("%.1f MB", done / 1e6)
                    } else {
                        "第 ${-done} 个镜像失败，换源重试…"
                    }
                }
            }
            _packBusy.value = null
            result.onSuccess { pack ->
                refreshPacks()
                _messages.tryEmit("已安装并切换到「${pack.name}」（${pack.words} 词）")
            }.onFailure { t ->
                _messages.tryEmit("下载失败：" + (t.message ?: t.javaClass.simpleName))
            }
        }
    }

    fun installPack(url: String, name: String) {
        if (url.isBlank()) {
            _messages.tryEmit("先填一个词典包网址")
            return
        }
        if (_packBusy.value != null) return
        _packBusy.value = "正在连接…"
        scope.launch {
            val result = runCatching {
                app.dictPacks.install(url, name) { done ->
                    _packBusy.value = "已下载 " + String.format("%.1f MB", done / 1e6)
                }
            }
            _packBusy.value = null
            result.onSuccess { pack ->
                refreshPacks()
                _messages.tryEmit("已安装并切换到「${pack.name}」（${pack.words} 词）")
            }.onFailure { t ->
                _messages.tryEmit("安装失败：" + (t.message ?: t.javaClass.simpleName))
            }
        }
    }

    fun activatePack(pack: DictPack) {
        scope.launch {
            app.dictPacks.activate(pack)
            app.dictionary.ensureDictReady()
            refreshPacks()
            _messages.tryEmit("已切换到「${pack.name}」")
        }
    }

    fun deletePack(pack: DictPack) {
        if (pack.builtIn) return
        scope.launch {
            app.dictPacks.delete(pack)
            app.dictionary.ensureDictReady()
            refreshPacks()
            _messages.tryEmit("已删除「${pack.name}」")
        }
    }

    fun reloadDictionary() {
        scope.launch {
            _dictWords.value = app.dictionary.dictWordCount()
        }
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
        runCatching { app.ocr.close() }
    }

    companion object {
        /** box 0 表示刚背错/新词，按 10 分钟后再来；其余按天数递增 */
        val INTERVALS_DAYS = intArrayOf(0, 1, 2, 4, 7, 15, 30)
    }
}
