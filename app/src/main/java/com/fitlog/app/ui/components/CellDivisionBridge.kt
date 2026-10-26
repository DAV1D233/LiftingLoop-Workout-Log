package com.fitlog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.sqrt

/** Circular arcs tangent to both circles form a rounded, filled neck at the hovered target. */
@Composable
fun CellDivisionBridge(anchor: Offset, finger: Offset?, sourceRadius: Float,
    targetRadius: Float, reach: Float, color: Color, alpha: Float, targetColor: Color = color) {
    Canvas(Modifier.fillMaxSize()) {
        val tip = finger ?: return@Canvas
        val delta = tip - anchor
        val distance = delta.getDistance()
        if (reach <= 0f || distance < sourceRadius * .7f || distance > reach * 1.3f) return@Canvas
        val direction = delta / distance
        val normal = Offset(-direction.y, direction.x)
        val dropRadius = targetRadius
        // During overlap the circles themselves already convey the separation.
        if (distance <= sourceRadius + dropRadius) return@Canvas
        val neckAlpha = ((1.3f - distance / reach) / .18f).coerceIn(0f, 1f)
        if (neckAlpha == 0f) return@Canvas
        // Intersect the two expanded circles to find a connector circle tangent to both.
        val gap = distance - sourceRadius - dropRadius
        val arcRadius = maxOf(dropRadius * .60f, gap * .80f)
        val sourceDistance = sourceRadius + arcRadius
        val targetDistance = dropRadius + arcRadius
        val centerX = (sourceDistance * sourceDistance - targetDistance * targetDistance + distance * distance) / (2f * distance)
        val centerY = sqrt((sourceDistance * sourceDistance - centerX * centerX).coerceAtLeast(0f))
        val topCenter = anchor + direction * centerX + normal * centerY
        val bottomCenter = anchor + direction * centerX - normal * centerY
        val topBase = anchor + (topCenter - anchor) * (sourceRadius / sourceDistance)
        val topEnd = tip + (topCenter - tip) * (dropRadius / targetDistance)
        val bottomBase = anchor + (bottomCenter - anchor) * (sourceRadius / sourceDistance)
        val bottomEnd = tip + (bottomCenter - tip) * (dropRadius / targetDistance)
        fun Path.connectorArc(center: Offset, from: Offset, to: Offset) {
            val startAngle = Math.toDegrees(atan2((from - center).y, (from - center).x).toDouble()).toFloat()
            val endAngle = Math.toDegrees(atan2((to - center).y, (to - center).x).toDouble()).toFloat()
            val sweep = (endAngle - startAngle + 360f) % 360f
            val extent = Offset(arcRadius, arcRadius)
            arcTo(Rect(center - extent, center + extent), startAngle, sweep, false)
        }
        val path = Path().apply {
            moveTo(topBase.x, topBase.y)
            connectorArc(topCenter, topBase, topEnd)
            lineTo(bottomEnd.x, bottomEnd.y)
            connectorArc(bottomCenter, bottomEnd, bottomBase)
            close()
        }
        val separationAlpha = ((distance - sourceRadius - dropRadius) / (12.dp.toPx())).coerceIn(0f, 1f)
        val circles = Path().apply {
            addOval(Rect(anchor - Offset(sourceRadius, sourceRadius), anchor + Offset(sourceRadius, sourceRadius)))
            addOval(Rect(tip - Offset(dropRadius, dropRadius), tip + Offset(dropRadius, dropRadius)))
        }
        clipPath(circles, ClipOp.Difference) {
            drawPath(path, Brush.linearGradient(listOf(color, targetColor),
                start = anchor + direction * sourceRadius, end = tip - direction * dropRadius),
                alpha = alpha * neckAlpha * separationAlpha)
        }
    }
}
