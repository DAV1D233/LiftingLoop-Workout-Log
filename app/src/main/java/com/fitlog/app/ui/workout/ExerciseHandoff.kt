package com.fitlog.app.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.ui.theme.Night
import com.fitlog.app.ui.theme.NightMuted
import com.fitlog.app.ui.theme.NightText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Uses the actual action title rectangle; no device-specific landing position is guessed. */
@Composable
internal fun BoxScope.ExerciseHandoff(name: String, destination: Rect, titleSize: Float,
    reducedMotion: Boolean, finished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val latestFinished by rememberUpdatedState(finished)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(if (reducedMotion) 1400L else 1550L)
        if (!reducedMotion) progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        latestFinished()
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.matchParentSize()
        .semantics { paneTitle = "下一个动作"; liveRegion = LiveRegionMode.Polite }
        .focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.Escape)) {
                latestFinished(); true
            } else false
        }.focusable()
        .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "跳过动作提示") { latestFinished() }) {
        val p = progress.value
        Box(Modifier.matchParentSize().background(Night.copy(alpha = 1f - p)))
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val target = destination.takeIf { it.width > 0 && it.height > 0 }
            ?: Rect(24.dp.value * density.density, height * .45f, width - 24.dp.value * density.density, height * .45f + 48.dp.value * density.density)
        val centerX = width / 2f + (target.center.x - width / 2f) * p
        val centerY = height * .46f + (target.center.y - height * .46f) * p
        val w = with(density) { target.width.toDp() }
        val h = with(density) { target.height.toDp() }
        val introScale = minOf(1.18f, (width - with(density) { 32.dp.toPx() }) / target.width.coerceAtLeast(1f)).coerceAtLeast(1f)
        Text("下一个动作", color = NightMuted, fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopCenter).offset {
                IntOffset(0, (height * .46f - target.height * .59f - with(density) { 32.sp.toPx() + 12.dp.toPx() }).roundToInt())
            }
                .graphicsLayer { alpha = (1f - p * 2.5f).coerceIn(0f, 1f) })
        Box(Modifier.offset { IntOffset((centerX - target.width / 2).roundToInt(), (centerY - target.height / 2).roundToInt()) }
            .requiredSize(w, h).graphicsLayer { scaleX = 1f + (introScale - 1f) * (1f - p); scaleY = scaleX }, contentAlignment = Alignment.Center) {
            Text(name, fontSize = titleSize.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = NightText, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
