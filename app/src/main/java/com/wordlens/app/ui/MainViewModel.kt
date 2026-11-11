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
import kotlinx.coroutines.withContext
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

enum class DictationStage { SETUP, PRESENT, REVIEW, RESULT }

data class DictationUi(
    val stage: DictationStage = DictationStage.SETUP,
    /** 打乱后的题目（就是用户选中的那些词） */
    val queue: List<SavedWord> = emptyList(),
    /** PRESENT 阶段看到第几个；REVIEW 阶段同样用它 */
    val index: Int = 0,
    /** REVIEW 阶段每个词的对错，顺序与 queue 一致；null = 还没评 */
    val marks: List<Boolean?> = emptyList(),
    /** true = 显示英文（写中文）；false = 显示中文（写英文） */
    val reverse: Boolean = false,
    val pool: List<SavedWord> = emptyList(),
    val selected: Set<String> = emptySet()
) {
    val total: Int get() = queue.size
    val correct: Int get() = marks.count { it == true }
    val wrong: Int get() = marks.count { it == false }
}

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

    /** 本次连拍产生的文档 id（按拍摄顺序），阅读页翻页只在这一组里走 */
    private val batchIds = mutableListOf<Long>()
    private var batchActive = false

    private val _reader = MutableStateFlow(ReaderUi())
    val reader: StateFlow<ReaderUi> = _reader.asStateFlow()

    private val _word = MutableStateFlow(WordUi())
    val word: StateFlow<WordUi> = _word.asStateFlow()

    private val _wordbook = MutableStateFlow(WordbookUi())
    val wordbook: StateFlow<WordbookUi> = _wordbook.asStateFlow()

    private val _dictation = MutableStateFlow(DictationUi())
    val dictation: StateFlow<DictationUi> = _dictation.asStateFlow()

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
        scope.launch { processImage(uri, ownedFile) }
    }

    /**
     * 连拍：一张张串行识别，全部落库后再回到第一张。
     * 串行是关键——并发调用 OCR 会互相覆盖，后面的照片就不会入库。
     */
    fun analyzeBatch(items: List<Pair<Uri, java.io.File?>>) {
        if (items.isEmpty()) return
        _reader.value = ReaderUi(loading = true)
        scope.launch {
            batchIds.clear()
            batchActive = true
            items.forEach { (uri, file) -> processImage(uri, file) }
            batchActive = false
            batchIds.firstOrNull()?.let { openDocument(it) }
            _messages.tryEmit(
                if (batchIds.size > 1) "已识别 ${batchIds.size} 张，可用左右箭头翻页"
                else "已识别 ${batchIds.size} 张"
            )
        }
    }

    /** 识别一张并落库，返回文档 id；失败返回 null */
    private suspend fun processImage(uri: Uri, ownedFile: java.io.File?): Long? {
        return try {
            val result = app.ocr.recognize(uri)
            if (result.wordCount == 0) {
                _reader.value = ReaderUi(
                    loading = false,
                    error = "没有识别到英文文字。试试靠近一点、光线亮一点，或者换一张更清晰的图。"
                )
                return null
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
            if (batchActive) batchIds.add(id)
            _reader.value = ReaderUi(
                loading = false,
                docId = id,
                title = title,
                text = result.fullText,
                ocr = result,
                bitmap = ownedFile?.let { ImageFiles.loadSampled(it, 1600) }
            )
            refreshLibrary()
            id
        } catch (t: Throwable) {
            _reader.value = ReaderUi(
                loading = false,
                error = "识别失败：${t.message ?: t.javaClass.simpleName}"
            )
            null
        }
    }

    /** 当前阅读页是否处于「连拍多张」状态，用来决定要不要显示左右箭头 */
    fun inBatch(): Boolean = batchIds.size > 1

    fun openAdjacent(delta: Int) {
        // 连拍过就只在那一批里翻，不掺历史记录
        if (batchIds.size > 1) {
            val cur = _reader.value.docId
            val idx = batchIds.indexOf(cur)
            if (idx < 0) {
                _messages.tryEmit("这不是连拍里的照片")
                return
            }
            val target = batchIds.getOrNull(idx + delta)
            if (target == null) {
                _messages.tryEmit(
                    if (delta < 0) "已经是本组第一张" else "已经是本组最后一张"
                )
                return
            }
            _messages.tryEmit("本组第 ${idx + delta + 1} / ${batchIds.size} 张")
            openDocument(target)
            return
        }
        scope.launch {
            val docs = app.database.allDocuments()
            if (docs.isEmpty()) return@launch
            val cur = _reader.value.docId
            var idx = docs.indexOfFirst { it.id == cur }
            // docId 对不上时（刚识别完还没落库之类），退化为从最新一张开始
            if (idx < 0) idx = 0
            val target = docs.getOrNull(idx + delta)
            if (target == null) {
                _messages.tryEmit(if (delta < 0) "已经是第一张了" else "已经是最后一张了")
                return@launch
            }
            _messages.tryEmit("第 ${idx + delta + 1} / ${docs.size} 张")
            openDocument(target.id)
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

    /** 直接查词（不依赖 OCR），顺带记进历史 */
    fun searchWord(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val key = q.lowercase()
        val old = history().filter { it.lowercase() != key }
        val next = (listOf(q) + old).take(15)
        updateSettings { it.copy(searchHistory = next.joinToString(",")) }
        lookupWord(q, null)
    }

    /** 查词历史（最新在前） */
    fun history(): List<String> =
        settings.value.searchHistory.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun clearHistory() {
        updateSettings { it.copy(searchHistory = "") }
        _messages.tryEmit("已清空查词历史")
    }

    /** 删掉已识别的照片文件，文字记录保留 */
    fun clearImages() {
        scope.launch {
            val dir = java.io.File(app.appContext.filesDir, "documents")
            val n = dir.listFiles()?.count { it.isFile } ?: 0
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            _messages.tryEmit("已删除 $n 张本地照片（文字记录还在）")
        }
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
        if (!ok) {
            _messages.tryEmit(
                if (mode == TtsEngine.Mode.SYSTEM)
                    "系统语音不可用，已尝试在线朗读；仍无声请到设置把朗读引擎切成「在线朗读」"
                else "在线朗读没出声，检查一下网络"
            )
        }
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
            _review.value = ReviewUi(
                active = true,
                queue = queue.shuffled(),
                index = 0,
                revealed = false
            )
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

    // ---------------- 生词本分组 / 导出 / 批量删除 ----------------

    fun setWordGroup(word: String, group: String) {
        scope.launch {
            app.database.setWordGroup(word, group)
            refreshWordbook()
            _messages.tryEmit(if (group.isBlank()) "已移出分组" else "已归到「$group」")
        }
    }

    fun deleteWords(words: List<String>) {
        scope.launch {
            val n = app.database.deleteWords(words)
            refreshWordbook()
            _messages.tryEmit("已删除 $n 个生词")
        }
    }

    /** 导出纯英文单词清单（一行一个） */
    fun exportWordList(uri: android.net.Uri) {
        scope.launch {
            runCatching {
                val words = app.database.allWordNames()
                withContext(Dispatchers.IO) {
                    app.appContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(words.joinToString("\n").toByteArray())
                    }
                }
                _messages.tryEmit("已导出 ${words.size} 个单词")
            }.onFailure { _messages.tryEmit("导出失败：${it.message}") }
        }
    }

    // ---------------- 识别记录管理 ----------------

    fun deleteDocuments(ids: List<Long>) {
        scope.launch {
            val n = app.database.deleteDocuments(ids)
            refreshLibrary()
            _messages.tryEmit("已删除 $n 条记录")
        }
    }

    // ---------------- 默写 ----------------

    fun dictationHistory(): List<String> =
        settings.value.dictationHistory.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** 进入默写页时把生词本拉出来，供「自选」用 */
    fun loadDictationPool() {
        scope.launch {
            val all = app.database.allSavedWords()
            _dictation.value = _dictation.value.copy(pool = all)
        }
    }

    fun toggleDictationPick(word: String) {
        val cur = _dictation.value.selected
        _dictation.value = _dictation.value.copy(
            selected = if (word in cur) cur - word else cur + word
        )
    }

    fun clearDictationPicks() {
        _dictation.value = _dictation.value.copy(selected = emptySet())
    }

    fun selectAllDictation() {
        _dictation.value = _dictation.value.copy(
            selected = _dictation.value.pool.map { it.word }.toSet()
        )
    }

    /**
     * 开始默写：出题范围只取用户勾选的词（不勾就用全部生词），整体打乱，不限制数量。
     * 先只显示提示（英文或中文），用户在纸面上写；全部翻完后再逐词自评对错。
     */
    fun startDictation(reverse: Boolean) {
        val st = _dictation.value
        val source = if (st.selected.isNotEmpty()) st.pool.filter { it.word in st.selected } else st.pool
        if (source.isEmpty()) {
            _messages.tryEmit(
                if (st.pool.isEmpty()) "生词本是空的，先加几个词再来默写"
                else "还没勾选单词，勾几个或全选后再开始"
            )
            return
        }
        _dictation.value = st.copy(
            stage = DictationStage.PRESENT,
            queue = source.shuffled(),
            index = 0,
            marks = List(source.size) { null },
            reverse = reverse
        )
    }

    fun nextDictationCard() {
        val st = _dictation.value
        _dictation.value = if (st.index + 1 >= st.total) {
            st.copy(stage = DictationStage.REVIEW, index = 0)
        } else {
            st.copy(index = st.index + 1)
        }
    }

    fun prevDictationCard() {
        val st = _dictation.value
        _dictation.value = st.copy(index = (st.index - 1).coerceAtLeast(0))
    }

    /** REVIEW 阶段标记当前词对/错 */
    fun markDictation(correct: Boolean) {
        val st = _dictation.value
        if (st.index !in st.marks.indices) return
        val marks = st.marks.toMutableList()
        marks[st.index] = correct
        val next = if (st.index + 1 >= st.total) {
            recordDictation(marks.count { it == true }, st.total)
            st.copy(marks = marks, stage = DictationStage.RESULT)
        } else {
            st.copy(marks = marks, index = st.index + 1)
        }
        _dictation.value = next
    }

    /** 结果页之后回到设置页 */
    fun finishDictation() {
        val st = _dictation.value
        _dictation.value = DictationUi(pool = st.pool, selected = st.selected)
    }

    fun restartDictation() {
        val st = _dictation.value
        if (st.queue.isEmpty()) return
        _dictation.value = st.copy(
            stage = DictationStage.PRESENT,
            queue = st.queue.shuffled(),
            index = 0,
            marks = List(st.total) { null }
        )
    }

    private fun recordDictation(correct: Int, total: Int) {
        if (total <= 0) return
        val line = "$correct/$total@${formatTimestamp()}"
        val kept = (listOf(line) + dictationHistory()).take(20)
        updateSettings { it.copy(dictationHistory = kept.joinToString("\n")) }
    }

    private fun formatTimestamp(): String =
        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

    fun exitReview() {
        _review.value = ReviewUi()
        refreshWordbook()
    }

    /** 检查更新：查到新版本就提示，并给出 Release 地址 */
    fun checkUpdate() {
        scope.launch {
            _messages.tryEmit("正在检查更新…")
            val r = app.updates.check()
            r.onSuccess { latest ->
                if (latest.newer) {
                    _messages.tryEmit("发现新版本 ${latest.tag}，可在 Releases 下载")
                    _lastReleaseUrl = latest.url
                } else {
                    _messages.tryEmit("已是最新版本（${com.wordlens.app.BuildConfig.VERSION_NAME}）")
                }
            }.onFailure {
                _messages.tryEmit("检查更新失败：${it.message ?: "网络不通"}")
            }
        }
    }

    /** 检查到的新版本页面，供界面跳转 */
    private var _lastReleaseUrl: String? = null
    fun lastReleaseUrl(): String = _lastReleaseUrl ?: "https://github.com/AABCD233/WordLens/releases"

    /** 开/关悬浮球；返回 false 表示缺少「显示在其他应用上层」权限 */
    fun toggleFloating(on: Boolean): Boolean {
        if (on && !android.provider.Settings.canDrawOverlays(app.appContext)) return false
        updateSettings { it.copy(floatingButton = on) }
        if (on) com.wordlens.app.floating.FloatingService.start(app.appContext)
        else com.wordlens.app.floating.FloatingService.stop(app.appContext)
        return true
    }

    fun setDailyReminder(
        on: Boolean,
        hour: Int = settings.value.reminderHour,
        keepAlive: Boolean = settings.value.reminderKeepAlive
    ) {
        updateSettings { it.copy(dailyReminder = on, reminderHour = hour, reminderKeepAlive = keepAlive) }
        com.wordlens.app.reminder.ReminderService.stop(app.appContext)
        if (on) com.wordlens.app.reminder.ReminderService.start(app.appContext)
        _messages.tryEmit(
            when {
                !on -> "已关闭每日提醒"
                keepAlive -> "已开启每日 ${hour}:00 提醒（常驻通知保活）"
                else -> "已开启每日 ${hour}:00 提醒（未保活，可能被系统清理）"
            }
        )
    }

    fun acceptDisclaimer() {
        updateSettings { it.copy(disclaimerAccepted = true) }
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
