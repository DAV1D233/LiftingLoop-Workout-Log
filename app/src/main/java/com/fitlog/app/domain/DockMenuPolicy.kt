package com.fitlog.app.domain

import kotlin.math.floor

/** Relative vertical selection is independent of the finger's horizontal screen position. */
object DockMenuPolicy {
    fun advance(cursor: Float, upward: Float, rowHeight: Float, count: Int): Float {
        if (count <= 0 || !cursor.isFinite() || !upward.isFinite() || !rowHeight.isFinite() || rowHeight <= 0f) return -1f
        // Clamp each increment, not accumulated travel: reversing at the top responds immediately.
        return (cursor + upward / rowHeight).coerceIn(-1f, (count - 1).toFloat())
    }
    fun selected(cursor: Float, count: Int): Int = if (count <= 0 || !cursor.isFinite()) -1
        else floor(cursor + .5f).coerceIn(-1f, (count - 1).toFloat()).toInt()
}
