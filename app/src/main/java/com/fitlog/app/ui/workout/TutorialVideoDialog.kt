package com.fitlog.app.ui.workout

import android.content.ActivityNotFoundException
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.fitlog.app.data.video.BilibiliVideoClient
import com.fitlog.app.data.video.TutorialMedia
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/** A separate playback window leaves the active workout and any unsaved set draft intact. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun TutorialVideoDialog(link: String, exercise: String, dismiss: () -> Unit,
    resolveVideo: suspend (String, Int?) -> TutorialMedia = { url, part -> BilibiliVideoClient().resolve(url, part) }) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val loader by rememberUpdatedState(resolveVideo)
    var media by remember { mutableStateOf<TutorialMedia?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var part by rememberSaveable(link) { mutableIntStateOf(0) }
    var position by rememberSaveable(link) { mutableLongStateOf(0L) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(false) }
    var landscapeVideo by remember(link) { mutableStateOf(true) }
    val activity = remember(context) { context.playbackActivity() }
    // Only this opaque playback window may rotate. Retain the Activity and playback
    // instance across the size change, then restore the app's portrait orientation.
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    // The Activity's cutout policy also bounds its child windows. Keeping this
    // outside the replaceable Dialog avoids restoring the policy mid-rotation.
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val oldFlags = window?.attributes?.flags
        if (fullscreen) window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val oldCutout = if (Build.VERSION.SDK_INT >= 28) window?.attributes?.layoutInDisplayCutoutMode else null
        if (fullscreen && Build.VERSION.SDK_INT >= 28 && window != null) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        onDispose {
            if (fullscreen && oldFlags != null && window != null) window.attributes = window.attributes.apply { flags = oldFlags }
            if (fullscreen && Build.VERSION.SDK_INT >= 28 && oldCutout != null && window != null) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = oldCutout }
        }
    }
    // Read these states in the owning composition, not only inside the effect:
    // Dialog content has its own composition and otherwise only its chrome updates.
    val orientation = if (fullscreen && landscapeVideo) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    SideEffect {
        if (activity != null && activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }
    val player = remember(link) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    }
    fun openSource() {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(media?.source ?: link))) }
        catch (_: ActivityNotFoundException) { error = "没有找到浏览器或 B站客户端" }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) landscapeVideo = size.width * size.pixelWidthHeightRatio > size.height
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(failure: PlaybackException) {
                position = player.currentPosition.coerceAtLeast(0)
                error = "视频流加载失败，请重试；也可以打开原视频"
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                position = player.currentPosition.coerceAtLeast(0)
                player.pause()
            }
        }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        onDispose {
            position = player.currentPosition.coerceAtLeast(0)
            owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(link, part, retry) {
        resolving = true; error = null
        player.stop(); player.clearMediaItems()
        try {
            val result = withTimeout(30000) { loader(link, part.takeIf { it > 0 }) }
            media = result
            player.setMediaSource(tutorialSource(context, result))
            player.setPlaybackSpeed(speed)
            player.seekTo(position)
            player.prepare()
            player.playWhenReady = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } catch (_: TimeoutCancellationException) { error = "读取视频超时，请检查网络后重试" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = if (failure is IllegalStateException || failure is IllegalArgumentException)
                failure.message ?: "视频暂时无法播放" else "无法连接视频服务，请检查网络后重试"
        } finally { resolving = false }
    }
    val configuration = LocalConfiguration.current
    // Compose 1.7's existing Dialog can retain its portrait window constraints
    // after an in-place Activity rotation. Replace only the window, not the player.
    key(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
    Dialog(onDismissRequest = { if (fullscreen) fullscreen = false else dismiss() },
        // Use the explicit native window size below. Compose 1.7's custom-width
        // path caps measurement at screenWidthDp, which excludes OEM cutout edges.
        properties = DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = false)) {
        FitLogTheme(workout = true) {
            val view = LocalView.current
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window
                if (fullscreen && activity != null) {
                    val display = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay.getRealMetrics(display)
                    window?.setLayout(display.widthPixels, display.heightPixels)
                } else window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window?.decorView?.setPadding(0, 0, 0, 0)
            }
            DisposableEffect(fullscreen, view) {
                val window = (view.parent as? DialogWindowProvider)?.window
                val oldFlags = window?.attributes?.flags
                if (fullscreen) {
                    // Some OEM floating windows still inset both short edges even
                    // with a cutout policy. Let the opaque video window cover them.
                    window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                }
                val oldCutout = if (Build.VERSION.SDK_INT >= 28) window?.attributes?.layoutInDisplayCutoutMode else null
                if (fullscreen && Build.VERSION.SDK_INT >= 28 && window != null) window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                val controller = window?.let { WindowCompat.getInsetsController(it, view) }
                window?.statusBarColor = android.graphics.Color.BLACK
                window?.navigationBarColor = android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= 29) {
                    window?.isNavigationBarContrastEnforced = false
                    window?.isStatusBarContrastEnforced = false
                }
                controller?.isAppearanceLightStatusBars = false
                controller?.isAppearanceLightNavigationBars = false
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
                onDispose {
                    // A replacement dialog owns its own insets. Showing bars on the
                    // departing window races its fullscreen replacement during rotation.
                    if (oldFlags != null && window != null) window.attributes = window.attributes.apply { flags = oldFlags }
                    if (Build.VERSION.SDK_INT >= 28 && oldCutout != null && window != null) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = oldCutout }
                }
            }
            BackHandler { if (fullscreen) fullscreen = false else dismiss() }
            Column(Modifier.fillMaxSize().background(Night)
                .then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
                if (!fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(dismiss) { Text("返回训练", color = NightText) }
                    Text(exercise, Modifier.weight(1f), color = NightText, style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AndroidView(factory = { PlayerView(it).apply {
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setShowNextButton(false); setShowPreviousButton(false)
                        controllerShowTimeoutMs = 4000
                    } }, update = { it.player = player; it.keepScreenOn = playing },
                        onRelease = { it.player = null; it.keepScreenOn = false }, modifier = Modifier.fillMaxSize())
                    if (resolving) CircularProgressIndicator(color = GreenAccent)
                    error?.let { message ->
                        Column(Modifier.fillMaxWidth().background(Night).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(message, color = NightText)
                            AppButton({ if (player.mediaItemCount > 0) position = player.currentPosition.coerceAtLeast(0); retry++ }) { Text("重试播放") }
                            AppTextButton(::openSource) { Text("打开原视频", color = NightMuted) }
                        }
                    }
                    AppTextButton({ fullscreen = !fullscreen }, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Text(if (fullscreen) "收起全屏" else "全屏", color = NightText)
                    }
                }
                if (!fullscreen) Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(media?.title ?: "动作讲解", color = NightText, style = MaterialTheme.typography.titleMedium)
                    media?.author?.takeIf { it.isNotBlank() }?.let { Text("B站 UP · $it", color = NightMuted, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppTextButton({
                            val speeds = listOf(.75f, 1f, 1.25f, 1.5f, 2f)
                            speed = speeds[(speeds.indexOf(speed) + 1) % speeds.size]; player.setPlaybackSpeed(speed)
                        }, enabled = media != null) { Text("倍速 ${speed}×", color = GreenAccent) }
                        Spacer(Modifier.weight(1f))
                        AppTextButton(::openSource) { Text("原视频 ↗", color = NightMuted) }
                    }
                    media?.takeIf { it.parts.size > 1 }?.let { video ->
                        video.parts.forEach { item ->
                            AppTextButton({ if (item.number != video.part) { part = item.number; position = 0 } }, enabled = !resolving,
                                modifier = Modifier.fillMaxWidth()) {
                                Text("${item.number} · ${item.title}", color = if (item.number == video.part) GreenAccent else NightMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

private fun Context.playbackActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun tutorialSource(context: android.content.Context, media: TutorialMedia): MediaSource {
    val http = DefaultHttpDataSource.Factory().setUserAgent(BilibiliVideoClient.AGENT)
        .setDefaultRequestProperties(mapOf("Referer" to media.source))
        .setConnectTimeoutMs(10000).setReadTimeoutMs(10000)
    val base = DefaultDataSource.Factory(context.applicationContext, http)
    val factory = ProgressiveMediaSource.Factory(DataSource.Factory {
        val delegate = base.createDataSource()
        object : DataSource by delegate {
            override fun open(dataSpec: DataSpec): Long {
                val candidates = listOf(dataSpec.uri.toString()) + media.alternatives[dataSpec.uri.toString()].orEmpty()
                var failure: java.io.IOException? = null
                for (candidate in candidates) {
                    try { return delegate.open(dataSpec.buildUpon().setUri(candidate).build()) }
                    catch (error: java.io.IOException) { failure = error; runCatching { delegate.close() } }
                }
                throw requireNotNull(failure)
            }
        }
    })
    fun source(url: String) = factory.createMediaSource(MediaItem.fromUri(url))
    return if (media.audio != null) MergingMediaSource(source(media.videos.first()), source(media.audio))
    else if (media.videos.size == 1) source(media.videos.single())
    else ConcatenatingMediaSource(*media.videos.map(::source).toTypedArray())
}
