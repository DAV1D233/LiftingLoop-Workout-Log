package com.fitlog.app.domain

/** Material strength and device capability are intentionally separate. */
enum class GlassQuality { SOLID, FROSTED, REFRACTIVE }

enum class GlassRestriction { NONE, TRANSPARENCY, SOFTWARE, MEMORY, BATTERY, THERMAL, UNSUPPORTED, FAILED }

object GlassPolicy {
    fun restriction(api: Int, hardware: Boolean, lowRam: Boolean, powerSave: Boolean,
        severeThermal: Boolean, reduceTransparency: Boolean, failed: Boolean): GlassRestriction = when {
        reduceTransparency -> GlassRestriction.TRANSPARENCY
        !hardware -> GlassRestriction.SOFTWARE
        lowRam -> GlassRestriction.MEMORY
        powerSave -> GlassRestriction.BATTERY
        severeThermal -> GlassRestriction.THERMAL
        failed -> GlassRestriction.FAILED
        api < 31 -> GlassRestriction.UNSUPPORTED
        else -> GlassRestriction.NONE
    }
    fun quality(api: Int, hardware: Boolean, lowRam: Boolean, powerSave: Boolean,
        severeThermal: Boolean, reduceTransparency: Boolean, failed: Boolean): GlassQuality = when {
        reduceTransparency || !hardware || lowRam || powerSave || severeThermal || failed -> GlassQuality.SOLID
        api >= 33 -> GlassQuality.REFRACTIVE
        api >= 31 -> GlassQuality.FROSTED
        else -> GlassQuality.SOLID
    }
    fun selectedAfterKey(current: Int, count: Int, up: Boolean): Int {
        if (count <= 0) return -1
        // The first action is the bottom row, closest to the thumb.
        return if (current < 0) { if (up) 0 else count - 1 }
        else (current + if (up) 1 else -1).coerceIn(0, count - 1)
    }
    fun confirms(released: Boolean, selected: Int, count: Int, enabled: Boolean, closing: Boolean) =
        released && enabled && !closing && selected in 0 until count
}
