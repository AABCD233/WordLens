package com.wordlens.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.tts.TtsEngine
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.wordlens.app.ui.reader.WordSheet
import com.wordlens.app.ui.search.SearchScreen
import com.wordlens.app.ui.capture.CaptureScreen
import com.wordlens.app.ui.reader.ReaderScreen
import com.wordlens.app.ui.settings.SettingsScreen
import com.wordlens.app.ui.wordbook.ReviewScreen
import com.wordlens.app.ui.wordbook.WordbookScreen

private enum class Tab { Camera, Search, Wordbook, Settings }

/**
 * 顶层导航：三页（拍照 / 生词本 / 设置）+ 阅读页 + 复习页。
 * 只有 5 个目的地，自己管路由比引 navigation-compose 更省事也更可控。
 */
@Composable
fun RootScreen(vm: MainViewModel) {
    val library by vm.library.collectAsState()
    val reader by vm.reader.collectAsState()
    val word by vm.word.collectAsState()
    val wordbook by vm.wordbook.collectAsState()
    val review by vm.review.collectAsState()
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
            onMessage = vm::toast
        )

        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Camera,
                        onClick = { tab = Tab.Camera },
                        icon = { Emoji("📷") },
                        label = { Text("拍照") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Search,
                        onClick = { tab = Tab.Search },
                        icon = { Emoji("🔍") },
                        label = { Text("查词") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Wordbook,
                        onClick = { tab = Tab.Wordbook; vm.refreshWordbook() },
                        icon = { Emoji("📚") },
                        label = { Text(if (wordbook.due > 0) "生词 ${wordbook.due}" else "生词本") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Emoji("⚙️") },
                        label = { Text("设置") }
                    )
                }
            }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
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
                        onMessage = vm::toast
                    )

                    Tab.Search -> SearchScreen(
                        query = searchQuery,
                        suggestions = suggestions,
                        dictReady = dictStatus == DictStatus.READY,
                        dictWords = dictWords,
                        onQueryChange = vm::onSearchQueryChange,
                        onSubmit = { q -> vm.searchWord(q) },
                        onPick = { w -> vm.searchWord(w) },
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

                    Tab.Settings -> SettingsScreen(
                        settings = settings,
                        dictStatus = dictStatus,
                        dictWords = dictWords,
                        ttsReady = ttsState == TtsEngine.State.READY,
                        onUpdate = vm::updateSettings,
                        onClearCache = vm::clearNetworkCache,
                        onTestSpeak = { vm.speak("This page is about offline dictionaries.") },
                        packs = packs,
                        activePackId = settings.activePackId,
                        packBusy = packBusy,
                        onInstallPack = vm::installPack,
                        onActivatePack = vm::activatePack,
                        onDeletePack = vm::deletePack,
                        onMessage = vm::toast
                    )
                }
            }
        }
    }

    // 词卡：所有页面共用（拍照阅读、直接查词都能弹）
    if (word.visible) {
        WordSheet(
            word = word,
            settings = settings,
            onDismiss = vm::dismissWord,
            onSpeak = vm::speak,
            onSave = vm::saveCurrentWord,
            onAiExplain = vm::requestAiExplain,
            onTranslateExample = { example -> vm.translateExample(example) },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                vm.toast("已复制")
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

@Composable
private fun Emoji(emoji: String) {
    Text(emoji, textAlign = TextAlign.Center)
}
