package com.wordlens.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.tts.TtsEngine
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.wordlens.app.ui.reader.WordSheet
import com.wordlens.app.ui.search.SearchScreen
import com.wordlens.app.ui.capture.CaptureScreen
import com.wordlens.app.ui.dictation.DictationScreen
import com.wordlens.app.ui.reader.ReaderScreen
import com.wordlens.app.ui.settings.SettingsScreen
import com.wordlens.app.ui.wordbook.ReviewScreen
import com.wordlens.app.ui.wordbook.WordbookScreen

private enum class Tab { Camera, Search, Wordbook, Dictation, Settings }

/**
 * 顶层导航：三页（拍照 / 生词本 / 设置）+ 阅读页 + 复习页。
 * 只有 5 个目的地，自己管路由比引 navigation-compose 更省事也更可控。
 */
@Composable
fun RootScreen(
    vm: MainViewModel,
    quickSearch: Boolean = false,
    onQuickConsumed: () -> Unit = {}
) {
    val library by vm.library.collectAsState()
    val reader by vm.reader.collectAsState()
    val word by vm.word.collectAsState()
    val wordbook by vm.wordbook.collectAsState()
    val review by vm.review.collectAsState()
    val dictation by vm.dictation.collectAsState()
    val settings by vm.settings.collectAsState()
    val dictStatus by vm.dictStatus.collectAsState()
    val dictWords by vm.dictWords.collectAsState()
    val ttsState by vm.ttsState.collectAsState()
    val packs by vm.packs.collectAsState()
    val packBusy by vm.packBusy.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val suggestions by vm.suggestions.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.Camera) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    // 全局提示条：messages 以前根本没人收集，所以所有提示都发进了虚空
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // 系统 Toast：不占布局、不盖住词卡内容、不吃触摸
        vm.messages.collect { msg ->
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 上滑把胶囊收起来、下滑露出来（照 PiliPlus 的手感）
    var navVisible by remember { mutableStateOf(true) }
    val scrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.Drag) {
                    when {
                        available.y < -4f -> navVisible = false
                        available.y > 4f -> navVisible = true
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    var noteWord by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameInitial by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val clipboard = LocalClipboardManager.current

    val readerVisible = reader.loading || reader.docId != 0L || reader.error != null

    BackHandler(enabled = reviewing) {
        reviewing = false
        vm.exitReview()
    }
    BackHandler(enabled = !reviewing && readerVisible) { vm.closeReader() }
    BackHandler(enabled = !reviewing && !readerVisible && tab != Tab.Camera) { tab = Tab.Camera }

    when {
        reviewing -> ReviewScreen(
            state = review,
            speechRate = settings.speechRate,
            onSpeak = vm::speak,
            onReveal = vm::revealReview,
            onGrade = vm::gradeReview,
            onExit = {
                reviewing = false
                vm.exitReview()
            }
        )

        readerVisible -> ReaderScreen(
            state = reader,
            word = word,
            settings = settings,
            onBack = vm::closeReader,
            onWordTap = vm::lookupWord,
            onModeChange = { mode -> vm.updateSettings { it.copy(readerMode = mode) } },
            onRename = {
                renameInitial = reader.title
                renameId = reader.docId
            },
            onDeleteDoc = reader.docId.takeIf { it != 0L }?.let { id -> { deleteId = id } },
            onTranslateAll = { vm.translateFullText() },
            onAnalyze = { vm.analyzeArticle(force = reader.aiArticle != null) },
            onManualLookup = { w -> vm.lookupWord(w, null) },
            onPrevDoc = { vm.openAdjacent(-1) },
            onNextDoc = { vm.openAdjacent(+1) },
            showPager = vm.inBatch(),
            onMessage = vm::toast
        )

        else -> Box(Modifier.fillMaxSize()) {
            // 内容铺满整屏（各页自己留出底部安全区），胶囊浮在上面
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .nestedScroll(scrollConnection)
            ) {
                when (tab) {
                    Tab.Camera -> CaptureScreen(
                        recent = library,
                        dictStatus = dictStatus,
                        onOpen = vm::openDocument,
                        onCaptured = vm::analyzeImage,
                        onRename = { doc ->
                            renameInitial = doc.title
                            renameId = doc.id
                        },
                        onDelete = { doc -> deleteId = doc.id },
                        onMessage = vm::toast,
                        onBatch = vm::analyzeBatch
                    )

                    Tab.Search -> SearchScreen(
                        query = searchQuery,
                        suggestions = suggestions,
                        dictReady = dictStatus == DictStatus.READY,
                        dictWords = dictWords,
                        onQueryChange = vm::onSearchQueryChange,
                        onSubmit = { q -> vm.searchWord(q) },
                        onPick = { w -> vm.searchWord(w) },
                        history = vm.history(),
                        onClearHistory = vm::clearHistory,
                        onMessage = vm::toast
                    )

                    Tab.Wordbook -> WordbookScreen(
                        state = wordbook,
                        dictReady = dictStatus == DictStatus.READY,
                        onSearch = vm::searchWordbook,
                        onOpenWord = { w -> vm.lookupWord(w) },
                        onDelete = vm::deleteSaved,
                        onStartReview = {
                            reviewing = true
                            vm.startReview()
                        },
                        onRefresh = { vm.refreshWordbook() }
                    )

                    Tab.Dictation -> DictationScreen(
                        state = dictation,
                        history = vm.dictationHistory(),
                        onStart = vm::startDictation,
                        onNext = vm::nextDictationCard,
                        onPrev = vm::prevDictationCard,
                        onMark = vm::markDictation,
                        onRestart = vm::restartDictation,
                        onFinish = vm::finishDictation,
                        onSpeak = vm::speak,
                        onTogglePick = vm::toggleDictationPick,
                        onClearPicks = vm::clearDictationPicks,
                        onSelectAll = vm::selectAllDictation
                    )

                    Tab.Settings -> SettingsScreen(
                        settings = settings,
                        dictStatus = dictStatus,
                        dictWords = dictWords,
                        ttsReady = ttsState == TtsEngine.State.READY,
                        onUpdate = vm::updateSettings,
                        onClearCache = vm::clearNetworkCache,
                        packs = packs,
                        activePackId = settings.activePackId,
                        packBusy = packBusy,
                        onInstallPack = vm::installPack,
                        onInstallOfficial = vm::installOfficialPack,
                        onActivatePack = vm::activatePack,
                        onDeletePack = vm::deletePack,
                        onClearHistory = vm::clearHistory,
                        onClearImages = vm::clearImages,
                        onCheckUpdate = vm::checkUpdate,
                        onToggleFloating = vm::toggleFloating,
                        onRequestOverlayPermission = {
                            val i = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + ctx.packageName)
                            )
                            runCatching { ctx.startActivity(i) }
                        },
                        onExportBackup = { uri -> vm.exportBackup(uri) },
                        onImportBackup = { uri -> vm.importBackup(uri) },
                        onMessage = vm::toast
                    )
                }
            }

            // 悬浮导航栏（PiliPlus 那种：不贴边、圆角、浮在内容上）
            androidx.compose.animation.AnimatedVisibility(
                visible = navVisible,
                enter = androidx.compose.animation.slideInVertically { it } +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { it } +
                    androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
            FloatingNavBar(
                current = tab,
                dueCount = wordbook.due,
                onSelect = { next ->
                    tab = next
                    if (next == Tab.Wordbook) vm.refreshWordbook()
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 30.dp, vertical = 14.dp)
            )
            }
        }
    }

    // 悬浮球点进来：直接弹输入框查词
    var showQuickInput by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        if (tab == Tab.Dictation) vm.loadDictationPool()
    }

    LaunchedEffect(quickSearch) {
        if (quickSearch) {
            showQuickInput = true
            onQuickConsumed()
        }
    }
    if (showQuickInput) {
        WordInputDialog(
            onDismiss = { showQuickInput = false },
            onConfirm = { w ->
                showQuickInput = false
                vm.searchWord(w)
            }
        )
    }

    if (!settings.disclaimerAccepted) {
        DisclaimerDialog(onAccept = vm::acceptDisclaimer)
    }

    // 词卡：所有页面共用（拍照阅读、直接查词都能弹）
    if (word.visible) {
        WordSheet(
            word = word,
            settings = settings,
            onDismiss = vm::dismissWord,
            onSpeak = vm::speak,
            onSave = vm::saveCurrentWord,
            onAiExplain = { kind -> vm.requestAiExplain(kind) },
            onTranslateExample = { example -> vm.translateExample(example) },
            onEditNote = { noteWord = word.definition?.lemma?.ifBlank { null } ?: word.query },
            onLookupWord = { w -> vm.lookupWord(w, word.sentence) },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                vm.toast("已复制")
            }
        )
    }

    noteWord?.let { w ->
        NoteDialog(
            word = w,
            initial = word.definition?.userNote.orEmpty(),
            onDismiss = { noteWord = null },
            onConfirm = { note ->
                vm.saveUserNote(w, note)
                noteWord = null
            }
        )
    }

    renameId?.let { id ->
        RenameDialog(
            initial = renameInitial,
            onDismiss = { renameId = null },
            onConfirm = { newTitle ->
                vm.renameDocument(id, newTitle)
                renameId = null
            }
        )
    }

    deleteId?.let { id ->
        ConfirmDeleteDialog(
            title = "删除这条识别记录？",
            message = "照片和识别结果会一起删掉，删了没法恢复。生词本里的单词不受影响。",
            onDismiss = { deleteId = null },
            onConfirm = {
                vm.deleteDocument(id)
                if (reader.docId == id) vm.closeReader()
                deleteId = null
            }
        )
    }

}

/** 悬浮式底部导航：圆角胶囊 + 选中态药丸，浮在内容之上 */
@Composable
private fun FloatingNavBar(
    current: Tab,
    dueCount: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(NavIconKind.Camera, "拍照", current == Tab.Camera) { onSelect(Tab.Camera) }
            NavItem(NavIconKind.Search, "查词", current == Tab.Search) { onSelect(Tab.Search) }
            NavItem(
                NavIconKind.Book,
                if (dueCount > 0) "复习 $dueCount" else "生词本",
                current == Tab.Wordbook
            ) { onSelect(Tab.Wordbook) }
            NavItem(NavIconKind.Pen, "默写", current == Tab.Dictation) { onSelect(Tab.Dictation) }
            NavItem(NavIconKind.Settings, "设置", current == Tab.Settings) { onSelect(Tab.Settings) }
        }
    }
}

@Composable
private fun NavItem(kind: NavIconKind, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        label = "navBg"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NavIcon(
            kind = kind,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            sizeDp = 22
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Emoji(emoji: String) {
    Text(emoji, textAlign = TextAlign.Center)
}
