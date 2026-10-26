package com.fitlog.app.domain

import kotlin.math.abs

data class MenuPoint(val x: Float, val y: Float)

object GestureTargets {
    /** Hit boxes cover the enlarged pills. Gaps between menu rows remain cancellable. */
    fun index(x: Float, y: Float, centers: List<MenuPoint>, halfWidth: Float, halfHeight: Float): Int =
        centers.indices.filter { abs(centers[it].x - x) <= halfWidth && abs(centers[it].y - y) <= halfHeight }
            .minByOrNull { val dx = centers[it].x - x; val dy = centers[it].y - y; dx * dx + dy * dy } ?: -1
}
