package com.fitlog.app.ui.workout

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.fitlog.app.R
import com.fitlog.app.ui.calendar.CalendarDetailIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

private class PosterCamera : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData = ClipData.newRawUri("LiftingLoop 照片", input)
    }
}

@Composable
internal fun WorkoutPosterEditor(summary: SessionSummary, initialPhoto: String? = null, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screen = LocalView.current.rootView
    val phoneRatio = remember { (screen.height.toFloat() / screen.width.coerceAtLeast(1)).coerceIn(1.45f, 2.55f) }
    var photo by rememberSaveable { mutableStateOf(initialPhoto) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf(summary.title) }
    var ratio by rememberSaveable { mutableFloatStateOf(phoneRatio) }
    var originalRatio by rememberSaveable { mutableFloatStateOf(phoneRatio) }
    var initialPhotoRead by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(false) }
    var adjust by rememberSaveable { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(true) }
    var renderedOptions by remember { mutableStateOf<PosterOptions?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val options = PosterOptions(title, photo, ratio = ratio, photoScale = zoom, photoX = panX, photoY = panY)
    // Gestures move only the GPU photo layer; record layout is not rebuilt on every touch.
    val overlayOptions = options.copy(photoScale = 1f, photoX = 0f, photoY = 0f)
    val ready = !rendering && bitmap != null && renderedOptions == overlayOptions
    LaunchedEffect(initialPhoto) {
        if (initialPhoto != null && !initialPhotoRead) {
            val bounds = withContext(Dispatchers.IO) { BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(initialPhoto, it) } }
            if (bounds.outWidth > 0) { originalRatio = (bounds.outHeight.toFloat() / bounds.outWidth).coerceIn(.5f, 3f); ratio = originalRatio }
            initialPhotoRead = true
        }
    }
    fun resetPhoto() { zoom = 1f; panX = 0f; panY = 0f }
    fun message(value: String) { Toast.makeText(context, value, Toast.LENGTH_SHORT).show() }
    fun loadPhoto(uri: Uri, captured: String? = null) {
        working = true; failure = null
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val path = importPosterPhoto(context.applicationContext, uri)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    path to (bounds.outHeight.toFloat() / bounds.outWidth.coerceAtLeast(1)).coerceIn(.5f, 3f)
                }
                photo = imported.first; originalRatio = imported.second; ratio = originalRatio; resetPhoto()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure = "未能读取照片，请换一张图片重试" }
            finally { captured?.let { File(it).delete() }; working = false }
        }
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val path = pendingSave; pendingSave = null
        if (uri != null && path != null) {
            working = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> File(path).inputStream().use { it.copyTo(output) } }
                    }
                    message("图片已保存")
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri, null, null) } }
                    failure = "保存失败，请重新选择保存位置"
                }
                finally { working = false }
            }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) loadPhoto(uri) }
    val camera = rememberLauncherForActivityResult(PosterCamera()) { ok ->
        val path = pendingCamera; pendingCamera = null
        if (ok && path != null) loadPhoto(FileProvider.getUriForFile(context, "${context.packageName}.workout-sharing", File(path)), path)
        else path?.let { File(it).delete() }
    }
    LaunchedEffect(summary, overlayOptions) {
        rendering = true; failure = null
        try {
            val rendered = withContext(Dispatchers.Default) {
                val job = currentCoroutineContext()
                WorkoutPosterRenderer(summary, overlayOptions, { job.ensureActive() }, loadPhoto = false).use { document ->
                    document.render(background = false)
                }
            }
            // Compose's RenderThread may still be using the previous bitmap: let GC own it.
            bitmap = rendered; renderedOptions = overlayOptions
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure = "无法生成图片，请移除照片或缩短标题后重试" }
        finally { rendering = false }
    }
    fun export(save: Boolean) {
        if (working || !ready) return
        working = true; failure = null
        val snapshot = options
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { exportWorkoutPoster(context, summary, snapshot) }
                if (!save) shareWorkoutPosters(context, listOf(file))
                else if (Build.VERSION.SDK_INT >= 29) {
                    withContext(Dispatchers.IO) { saveWorkoutPosterToGallery(context, file) }
                    message("已保存到相册 · LiftingLoop")
                } else {
                    pendingSave = file.path
                    saveDocument.launch("LiftingLoop-${System.currentTimeMillis()}.png")
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failure = if (save) "保存失败，请重试" else "分享失败，请重试" }
            finally { working = false }
        }
    }
    Dialog(onDismissRequest = { if (!working) { if (fullscreen) fullscreen = false else dismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FitLogTheme {
            val view = LocalView.current
            DisposableEffect(fullscreen, view) {
                val controller = (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view) }
                controller?.isAppearanceLightStatusBars = !fullscreen
                controller?.isAppearanceLightNavigationBars = !fullscreen
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
                onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
            }
            BackHandler { if (!working) { if (fullscreen) fullscreen = false else dismiss() } }
            Column(Modifier.fillMaxSize().background(if (fullscreen) Night else Paper)
                .then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
                if (!fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(dismiss, enabled = !working) { Icon(Icons.Default.Close, "返回训练详情") }
                    Spacer(Modifier.weight(1f))
                    AppIconButton({ fullscreen = true }, Modifier.semantics { contentDescription = "全屏预览" }) { CalendarDetailIcon(expand = true) }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(FieldFill).padding(if (fullscreen) 0.dp else 16.dp), contentAlignment = Alignment.Center) {
                    val imageRatio = bitmap?.let { it.width.toFloat() / it.height } ?: (1 / ratio)
                    val canvasWidth = minOf(maxWidth, maxHeight * imageRatio)
                    Box(Modifier.width(canvasWidth).aspectRatio(imageRatio).clipToBounds().background(if (photo == null) CardFill else Night)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "训练图片编辑画布"
                            if (photo != null && !working) customActions = listOf(
                                CustomAccessibilityAction("放大照片") { zoom = (zoom * 1.2f).coerceAtMost(6f); true },
                                CustomAccessibilityAction("缩小照片") { zoom = (zoom / 1.2f).coerceAtLeast(.25f); true },
                                CustomAccessibilityAction("重置照片") { resetPhoto(); true })
                        }
                        .pointerInput(photo, working) {
                            if (photo != null && !working) detectTransformGestures { centroid, pan, factor, _ ->
                                val old = zoom
                                val next = (old * factor).coerceIn(.25f, 6f)
                                val change = next / old
                                // Keep the image under the fingers while scaling around their centroid.
                                panX = ((panX * size.width + pan.x + (centroid.x - size.width / 2f - panX * size.width) * (1 - change)) / size.width).coerceIn(-2f, 2f)
                                panY = ((panY * size.height + pan.y + (centroid.y - size.height / 2f - panY * size.height) * (1 - change)) / size.height).coerceIn(-2f, 2f)
                                zoom = next
                            }
                        }) {
                        if (photo != null) {
                            val transform = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom; scaleY = zoom; translationX = panX * size.width; translationY = panY * size.height
                            }
                            AsyncImage(File(photo!!), "背景照片，可拖动或双指缩放", transform, contentScale = ContentScale.Fit,
                                onError = { failure = "照片已失效，请重新选择" })
                        }
                        bitmap?.let { Image(it.asImageBitmap(), "完整训练记录，单张图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                    if ((rendering || working) && !fullscreen) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).size(22.dp), color = GreenAccent, strokeWidth = 2.dp)
                    if (fullscreen) AppIconButton({ fullscreen = false }, Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Default.Close, "返回编辑", tint = NightText) }
                }
                if (!fullscreen) {
                    if (photo != null) Text("拖动照片 · 双指缩放", Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PosterTool("照片", { Icon(painterResource(R.drawable.ic_poster_photo), null, Modifier.size(21.dp)) }, !working) { try { gallery.launch("image/*") } catch (_: Exception) { failure = "无法打开相册" } }
                        PosterTool("拍照", { Icon(painterResource(R.drawable.ic_home_camera), null, Modifier.size(21.dp)) }, !working) {
                            try {
                                val dir = File(context.cacheDir, "workout-captures").apply { check(isDirectory || mkdirs()) }
                                val file = File.createTempFile("camera-", ".jpg", dir); pendingCamera = file.path
                                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.workout-sharing", file))
                            } catch (_: Exception) { pendingCamera?.let { File(it).delete() }; pendingCamera = null; message("无法打开相机，请从相册选择") }
                        }
                        PosterTool("文字", { Icon(Icons.Default.Edit, null, Modifier.size(21.dp)) }, !working) { editTitle = true }
                        PosterTool("调整", { Icon(Icons.Default.Settings, null, Modifier.size(21.dp)) }, !working) { adjust = !adjust }
                    }
                    if (adjust) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            AppTextButton({ ratio = originalRatio; resetPhoto() }, enabled = !working) { Text("原图") }
                            AppTextButton({ ratio = phoneRatio }, enabled = !working) { Text("竖版") }
                            AppTextButton({ ratio = 1f }, enabled = !working) { Text("方形") }
                            AppTextButton({ resetPhoto() }, enabled = photo != null && !working) { Text("重置") }
                        }
                        if (photo != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AppTextButton({ photo = null; resetPhoto() }, enabled = !working) { Text("移除照片") }
                        }
                    }
                    failure?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppOutlinedButton({ export(true) }, Modifier.weight(1f).heightIn(min = 50.dp), enabled = ready && !working) { Text("保存图片") }
                        AppButton({ export(false) }, Modifier.weight(1f).heightIn(min = 50.dp), enabled = ready && !working,
                            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Ink)) { Text(if (working) "处理中…" else "分享") }
                    }
                }
            }
            if (editTitle) {
                var draft by remember { mutableStateOf(title) }
                AppAlertDialog(onDismissRequest = { editTitle = false }, title = { Text("图片标题") },
                    text = { OutlinedTextField(draft, { draft = it.take(80) }, label = { Text("仅修改分享图片") }, maxLines = 3) },
                    confirmButton = { AppTextButton({ title = draft.ifBlank { summary.title }; editTitle = false }) { Text("确定") } },
                    dismissButton = { AppTextButton({ editTitle = false }) { Text("取消") } })
            }
        }
    }
}

@Composable
private fun PosterTool(label: String, icon: @Composable () -> Unit, enabled: Boolean, action: () -> Unit) {
    AppTextButton(action, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icon(); Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
