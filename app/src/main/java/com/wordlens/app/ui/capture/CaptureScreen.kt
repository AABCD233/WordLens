package com.wordlens.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.statusBarsPadding
import com.wordlens.app.ui.theme.PageTitleStyle
import com.wordlens.app.data.db.DictStatus
import com.wordlens.app.data.db.DocumentRow
import com.wordlens.app.ui.formatTime
import com.wordlens.app.util.ImageFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Composable
fun CaptureScreen(
    recent: List<DocumentRow>,
    dictStatus: DictStatus,
    onOpen: (Long) -> Unit,
    onCaptured: (Uri, java.io.File?) -> Unit,
    onRename: (DocumentRow) -> Unit,
    onDelete: (DocumentRow) -> Unit,
    onMessage: (String) -> Unit,
    onBatch: (List<Pair<Uri, java.io.File?>>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    // 连拍模式：先攒几张，再一起识别
    var batchMode by remember { mutableStateOf(false) }
    val pending = remember { androidx.compose.runtime.mutableStateListOf<Pair<Uri, java.io.File?>>() }
    val batching = batchMode && pending.isNotEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        permissionAsked = true
        if (!ok) onMessage("没有相机权限，也可以用下面的「从相册选图」")
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) { ImageFiles.copyIntoApp(context, uri) }
                if (batchMode) {
                    pending.add(uri to copied)
                    onMessage("已加入 ${pending.size} 张")
                } else {
                    onCaptured(uri, copied)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
        // 标题栏：内容画到状态栏下面，只在这里让出高度
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("词镜", style = PageTitleStyle)
                Text(
                    "拍照识英文 · 点词即查义",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (dictStatus == DictStatus.READY) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    if (dictStatus == DictStatus.READY) "离线词典就绪" else "词典准备中",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        if (dictStatus == DictStatus.PREPARING) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "正在准备离线词典（首次启动，约 1–2 秒）…",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            if (granted) {
                CameraPreview(
                    lensFacing = lensFacing,
                    modifier = Modifier.fillMaxSize(),
                    onReady = { imageCapture = it }
                )
                // 取景提示框
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.86f)
                        .height(200.dp)
                        .background(Color.Transparent)
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                ) {
                    Text(
                        "把书页/文章放进取景框，尽量拍正、拍清楚",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("需要相机权限", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "拍照识别英文需要相机；不想给权限的话，直接用下面的「从相册选图」也能用。",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        if (permissionAsked) {
                            // 第二次被拒后系统不再弹窗，只能引导去设置
                            onMessage("请到「设置 → 应用 → 词镜 → 权限」里打开相机")
                        }
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text(if (permissionAsked) "再次申请" else "允许使用相机") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start
            ) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("相册") }
            }

            ShutterButton(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        onMessage("相机还没准备好，稍等一下或改用相册")
                        return@ShutterButton
                    }
                    val file = ImageFiles.newCaptureFile(context)
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                if (batchMode) {
                                    pending.add(Uri.fromFile(file) to file)
                                    onMessage("已拍 ${pending.size} 张，点「识别」一次性处理")
                                } else {
                                    onCaptured(Uri.fromFile(file), file)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onMessage("拍照失败：${exception.message}")
                            }
                        }
                    )
                },
            )

            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End
            ) {
            OutlinedButton(
                onClick = {
                    if (batchMode && pending.isNotEmpty()) {
                        // 一次识别多张：逐张跑，最后停在最后一张的阅读页
                        val items = pending.toList()
                        pending.clear()
                        batchMode = false
                        // 交给 VM 串行识别，全部落库后自动停在第一张
                        onBatch(items)
                    } else {
                        batchMode = !batchMode
                        if (!batchMode) pending.clear()
                        onMessage(if (batchMode) "连拍已开：多拍几张再一起识别" else "连拍已关")
                    }
                }
            ) {
                Text(
                    when {
                        batchMode && pending.isNotEmpty() -> "识别 ${pending.size} 张"
                        batchMode -> "结束连拍"
                        else -> "连拍"
                    }
                )
            }

            OutlinedButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) { Text("翻转") }
            }
        }

        if (batchMode) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    if (pending.isEmpty()) "连拍已开：连拍多页，拍完点「识别」一起处理"
                    else "已拍 ${pending.size} 张，继续拍或点「识别 ${pending.size} 张」",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (recent.isNotEmpty()) {
            Text(
                "最近识别",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(recent, key = { it.id }) { doc ->
                    var menuOpen by remember(doc.id) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.width(184.dp).clip(RoundedCornerShape(20.dp))
                            .clickable { onOpen(doc.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    doc.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(end = 18.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${doc.wordCount} 词 · ${formatTime(doc.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 右上角小菜单：改名 / 删除
                            Box(Modifier.align(Alignment.TopEnd)) {
                                Text(
                                    "⋯",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { menuOpen = true }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            menuOpen = false
                                            onRename(doc)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            menuOpen = false
                                            onDelete(doc)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("怎么用", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "拍一段英文（课本、说明书、菜单都行）→ 自动识别成可点击的单词 → 点哪个词都行，立刻出音标、中文释义、例句和朗读。",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

/** 相机快门：白色细环 + 实心内圈，点按即拍 */
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(72.dp).border(3.dp, Color.White, CircleShape))
        Box(Modifier.size(56.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    modifier: Modifier = Modifier,
    onReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(lensFacing) {
        val provider = awaitCameraProvider(context) ?: return@LaunchedEffect
        runCatching {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                capture
            )
            onReady(capture)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider? =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (cont.isActive) {
                    cont.resume(runCatching { future.get() }.getOrNull())
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { runCatching { future.cancel(true) } }
    }
