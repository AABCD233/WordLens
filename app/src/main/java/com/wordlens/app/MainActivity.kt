package com.wordlens.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wordlens.app.ui.MainViewModel
import com.wordlens.app.ui.RootScreen
import com.wordlens.app.ui.theme.WordLensTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WordLensApp).container
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(container) as T
        }
        val viewModel: MainViewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
        mainViewModel = viewModel

        // 从悬浮球进来时，直接弹查词框
        quickSearch = intent?.getBooleanExtra(
            com.wordlens.app.floating.FloatingService.EXTRA_QUICK_SEARCH, false
        ) == true

        setContent {
            val settings by container.settings.state.collectAsState()
            WordLensTheme(dynamicColor = settings.dynamicColor) {
                // 包一层 Surface：Text 默认取 LocalContentColor，裸放在 Box 里会是黑色，
                // 深色模式下就成了黑字黑底（生词本标题、翻译提示那些都是这个原因）
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                ) {
                RootScreen(
                    vm = viewModel,
                    quickSearch = quickSearch,
                    onQuickConsumed = { quickSearch = false }
                )
                }
            }
        }

        handleSharedImage(intent, viewModel)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(com.wordlens.app.floating.FloatingService.EXTRA_QUICK_SEARCH, false)) {
            quickSearch = true
        }
        mainViewModel?.let { handleSharedImage(intent, it) }
    }

    /** 悬浮球点开时要自动弹查词框 */
    private var quickSearch by mutableStateOf(false)

    /** 从相册/文件「分享 → 词镜」进来时，直接识别这张图 */
    private fun handleSharedImage(intent: Intent?, vm: MainViewModel) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        val type = intent?.type ?: return
        if (uri == null || !type.startsWith("image/")) return
        scope.launch {
            val file = kotlinx.coroutines.withContext(Dispatchers.IO) {
                com.wordlens.app.util.ImageFiles.copyIntoApp(this@MainActivity, uri)
            }
            vm.analyzeImage(uri, file)
        }
    }
}
