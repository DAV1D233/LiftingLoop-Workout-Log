package com.fitlog.app.ui.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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

/** Shared two-position selector: same interaction as the training cycle switch. */
@Composable
internal fun CalendarMetricSwitch(cardio: Boolean, change: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    com.fitlog.app.ui.components.AppSlidingSwitch(cardio, change, "力量容量", "有氧时长", modifier)
}

/** Diagonal outward arrows expand details; inward arrows return to the compact view. */
@Composable
internal fun CalendarDetailIcon(expand: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
            Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), strokeWidth = 1.8f * unit, cap = StrokeCap.Round)
        line(14f, 10f, 20f, 4f)
        line(10f, 14f, 4f, 20f)
        if (expand) {
            line(14f, 4f, 20f, 4f); line(20f, 4f, 20f, 10f)
            line(4f, 14f, 4f, 20f); line(4f, 20f, 10f, 20f)
        } else {
            line(14f, 4f, 14f, 10f); line(14f, 10f, 20f, 10f)
            line(4f, 14f, 10f, 14f); line(10f, 14f, 10f, 20f)
        }
    }
}
