package com.fitlog.app.domain

/** Decorative movement never overrides accessibility or a backgrounded window. */
object MotionPolicy {
    fun enabled(lively: Boolean, reduced: Boolean, systemOff: Boolean, foreground: Boolean = true): Boolean =
        lively && !reduced && !systemOff && foreground
}
