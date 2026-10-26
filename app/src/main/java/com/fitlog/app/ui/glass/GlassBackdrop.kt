package com.fitlog.app.ui.glass

import android.view.View
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import com.fitlog.app.domain.GlassQuality
import kotlin.math.roundToInt

internal class GlassBackdrop(val layer: GraphicsLayer, val view: View) {
    var origin = Offset.Zero
    var recording = false // Draw-pass state, never Compose state: no draw/recomposition feedback loop.
    var ready = false
    var failed by mutableStateOf(false)
    var activeSource: Any? = null
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }
internal val LocalGlassSnapshot = staticCompositionLocalOf<ImageBitmap?> { null }

internal fun screenOffset(view: View): Offset {
    val xy = IntArray(2)
    view.getLocationOnScreen(xy)
    return Offset(xy[0].toFloat(), xy[1].toFloat())
}

@Composable
fun GlassBackdropHost(content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val view = LocalView.current
    val backdrop = remember(layer, view) { GlassBackdrop(layer, view) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** Attach only to opaque page content, never an ancestor of GlassSurface. The source is explicit
 * rather than recording the entire NavHost: cached Compose render nodes must not sample themselves.
 */
fun Modifier.glassBackdropSource(): Modifier = composed {
    val source = LocalGlassBackdrop.current
    val environment = LocalGlassEnvironment.current
    val view = LocalView.current
    val token = remember { Any() }
    DisposableEffect(source, view) {
        if (source?.view === view) { source.activeSource = token; source.ready = false; source.failed = false }
        onDispose { if (source?.activeSource === token) { source.activeSource = null; source.ready = false } }
    }
    onGloballyPositioned {
        if (source?.activeSource === token) source.origin = screenOffset(view) + it.positionInRoot()
    }.drawWithContent {
        if (source != null && source.activeSource === token && environment.quality != GlassQuality.SOLID && !source.failed) {
            val layerDensity = androidx.compose.ui.unit.Density(density, fontScale)
            val layerDirection = layoutDirection
            val layerSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            source.recording = true
            try {
                source.layer.record(layerDensity, layerDirection, layerSize) { this@drawWithContent.drawContent() }
                source.ready = true
                // Sources contain only plain content. Never draw the same graphics-layer
                // subtree twice with different recording flags: Compose caches its nodes.
                drawLayer(source.layer)
            } catch (error: RuntimeException) {
                source.failed = true
                source.recording = false
                Log.e("FitLogGlass", "Backdrop recording failed for this page; using readable fallback", error)
                drawContent()
            }
            finally { source.recording = false }
        } else drawContent()
    }
}

/** A modal takes one bounded, downsampled in-memory backdrop, not a live cross-window render node. */
@Composable
internal fun GlassModalSnapshot(content: @Composable () -> Unit) {
    val source = LocalGlassBackdrop.current
    val environment = LocalGlassEnvironment.current
    val copy = rememberGraphicsLayer()
    var image by remember(source) { mutableStateOf<ImageBitmap?>(null) }
    val configuration = LocalConfiguration.current
    LaunchedEffect(source, environment.quality, configuration.screenWidthDp, configuration.screenHeightDp) {
        image = null
        if (source == null || environment.quality == GlassQuality.SOLID || source.failed) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!source.ready) return@LaunchedEffect
        val size = source.layer.size
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val scale = minOf(1f, 1024f / maxOf(size.width, size.height))
        try {
            copy.record(androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                IntSize((size.width * scale).roundToInt().coerceAtLeast(1), (size.height * scale).roundToInt().coerceAtLeast(1))) {
                withTransform({ scale(scale, scale, pivot = Offset.Zero) }) { drawLayer(source.layer) }
            }
            image = copy.toImageBitmap()
        } catch (_: RuntimeException) {
            image = null
        } catch (_: OutOfMemoryError) { image = null }
    }
    CompositionLocalProvider(LocalGlassSnapshot provides image, content = content)
    DisposableEffect(source) { onDispose { image = null } }
}

internal fun DrawScope.drawBackdrop(source: GlassBackdrop, snapshot: ImageBitmap?, origin: Offset) {
    val offset = source.origin - origin
    translate(offset.x, offset.y) {
        if (snapshot == null) drawLayer(source.layer)
        else drawImage(snapshot, dstSize = source.layer.size)
    }
}
