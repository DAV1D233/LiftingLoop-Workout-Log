package com.fitlog.app.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.glass.LocalGlassEnvironment
import com.fitlog.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Runs only after the summary exists (the record has already been committed).
 * All decoration is pointer-transparent; leaving the composition cancels the coroutine.
 */
@Composable
internal fun WorkoutCompletionMotion(summary: SessionSummary, origin: Rect, fraction: Float,
    home: () -> Unit, history: () -> Unit) {
    val enabled = !LocalGlassEnvironment.current.reduceMotion
    // A rotation or toggling the preference must not replay a celebration of the same record.
    var played by rememberSaveable(summary.title, summary.seconds, summary.sets) { mutableStateOf(false) }
    val progress = remember(summary) { Animatable(if (played || !enabled) 1f else 0f) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var checkBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(summary, enabled) {
        if (!enabled || played) progress.snapTo(1f)
        else {
            played = true
            progress.animateTo(1f, tween(1500))
        }
    }
    val p = if (enabled) progress.value else 1f
    val reveal = ((p - .26f) / .36f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(Night).onGloballyPositioned { rootBounds = it.boundsInRoot() }) {
        CompletionScreen(summary, home, history,
            modifier = Modifier.graphicsLayer {
                alpha = reveal
                translationY = 16 * density * (1f - reveal)
            }, checkProgress = ((p - .48f) / .42f).coerceIn(0f, 1f),
            checkPlaced = { checkBounds = it })
        if (enabled && p < 1f) CompletionBurst(p, origin.translate(-rootBounds.topLeft),
            checkBounds.translate(-rootBounds.topLeft), fraction,
            seed = summary.title.hashCode() + summary.seconds, complete = summary.complete)
    }
}

private data class Confetti(val angle: Float, val travel: Float, val spin: Float, val length: Float)

@Composable
private fun BoxScope.CompletionBurst(progress: Float, origin: Rect, destination: Rect,
    fraction: Float, seed: Int, complete: Boolean) {
    val pieces = remember(seed, complete) {
        val random = Random(seed)
        List(if (complete) 36 else 18) {
            Confetti(random.nextFloat() * (PI * 2).toFloat(), .6f + random.nextFloat() * .7f,
                random.nextFloat() * 540f - 270f, 5f + random.nextFloat() * 4f)
        }
    }
    Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
        val p = progress
        val start = if (origin.width > 1 && origin.height > 1) origin.center else Offset(size.width / 2, size.height * .35f)
        val target = if (destination.width > 1) destination.center else Offset(size.width / 2, size.height * .3f)
        val initialRadius = if (origin.width > 1) origin.width / 2 else 112 * density
        val travel = FastOutSlowInEasing.transform(((p - .24f) / .48f).coerceIn(0f, 1f))
        val center = start + (target - start) * travel
        val expanded = initialRadius * (1f + .28f * (p / .24f).coerceIn(0f, 1f))
        val targetRadius = if (destination.width > 1) destination.width / 2 else 32 * density
        val radius = expanded + (targetRadius - expanded) * travel
        val opacity = (1f - (p - .56f) / .22f).coerceIn(0f, 1f)
        if (opacity > 0) {
            val glow = sin((p / .82f).coerceIn(0f, 1f) * PI).toFloat() * .28f
            val glowRadius = radius * 1.55f
            drawCircle(Brush.radialGradient(listOf(GreenAccent.copy(alpha = glow), Color.Transparent),
                center, glowRadius), glowRadius, center)
            val stroke = (7f - travel * 3f) * density
            drawCircle(NightSurface.copy(alpha = opacity), radius, center, style = Stroke(stroke))
            drawArc(GreenAccent.copy(alpha = opacity), -90f, 360f * fraction.coerceIn(0f, 1f), false,
                topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val flight = ((p - .18f) / .82f).coerceIn(0f, 1f)
        if (flight > 0) {
            val fade = (1f - flight * flight).coerceIn(0f, 1f)
            val reach = size.width * .68f
            val colors = listOf(GreenAccent, OrangeAccent, Blue, NightText)
            pieces.forEachIndexed { index, piece ->
                val distance = reach * piece.travel * flight
                val point = start + Offset(cos(piece.angle) * distance,
                    sin(piece.angle) * distance * .68f - size.height * .14f * flight + size.height * .3f * flight * flight)
                rotate(piece.spin * flight, point) {
                    drawRoundRect(colors[index % colors.size].copy(alpha = fade * .9f),
                        topLeft = point, size = Size(3 * density, piece.length * density),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(density))
                }
            }
        }
    }
}

/** The two strokes are drawn in sequence; never a static glyph that simply fades in. */
@Composable
internal fun CompletionCheck(progress: Float, modifier: Modifier = Modifier, foreground: Color = GreenAccent) {
    Canvas(modifier.semantics { contentDescription = "训练记录已保存" }) {
        val p = progress.coerceIn(0f, 1f)
        drawCircle(GreenAccent.copy(alpha = .12f * p))
        val a = Offset(size.width * .27f, size.height * .5f)
        val b = Offset(size.width * .44f, size.height * .67f)
        val c = Offset(size.width * .75f, size.height * .34f)
        val first = (p / .4f).coerceIn(0f, 1f)
        val second = ((p - .4f) / .6f).coerceIn(0f, 1f)
        val stroke = size.minDimension * .052f
        if (first > 0) drawLine(foreground, a, a + (b - a) * first, strokeWidth = stroke, cap = StrokeCap.Round)
        if (second > 0) drawLine(foreground, b, b + (c - b) * second, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
