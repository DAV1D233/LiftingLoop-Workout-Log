package com.fitlog.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.ui.components.AppTextButton
import com.fitlog.app.ui.glass.LocalGlassEnvironment
import com.fitlog.app.ui.theme.*
import kotlin.math.roundToInt

/** One bounded, two-position switch. Horizontal drag never intercepts vertical page scrolling. */
@Composable
fun AppSlidingSwitch(second: Boolean, change: (Boolean) -> Unit,
    firstLabel: String, secondLabel: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(22.dp)
    val reduced = LocalGlassEnvironment.current.reduceMotion
    BoxWithConstraints(modifier.heightIn(min = 48.dp).clip(shape).background(FieldFill)) {
        val half = (maxWidth - 8.dp) / 2
        val halfPx = with(LocalDensity.current) { half.toPx().coerceAtLeast(1f) }
        var dragging by remember { mutableStateOf(false) }
        var fraction by remember { mutableFloatStateOf(if (second) 1f else 0f) }
        val position by animateFloatAsState(if (dragging) fraction else if (second) 1f else 0f,
            if (reduced || dragging) snap() else spring(dampingRatio = .82f, stiffness = 550f), label = "calendar metric thumb")
        Box(Modifier.matchParentSize().padding(4.dp)) {
            Surface(Modifier.fillMaxHeight().width(half).offset { IntOffset((halfPx * position).roundToInt(), 0) },
                shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 0.dp) {}
        }
        Row(Modifier.fillMaxWidth().selectableGroup()
            .onPreviewKeyEvent {
                if (enabled && it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) { change(false); true }
                else if (enabled && it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) { change(true); true }
                else false
            }
            .draggable(rememberDraggableState { delta -> fraction = (fraction + delta / halfPx).coerceIn(0f, 1f) },
                Orientation.Horizontal, enabled = enabled, onDragStarted = { dragging = true; fraction = if (second) 1f else 0f },
                onDragStopped = { velocity ->
                    val next = if (velocity > 400f) true else if (velocity < -400f) false else fraction >= .5f
                    dragging = false
                    change(next)
                }).padding(horizontal = 4.dp)) {
            listOf(firstLabel, secondLabel).forEachIndexed { index, label ->
                val active = second == (index == 1)
                AppTextButton(onClick = { change(index == 1) }, enabled = enabled, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { selected = active; role = Role.Tab }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) Green else Muted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

