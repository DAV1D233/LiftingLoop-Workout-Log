package com.fitlog.app.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.fitlog.app.domain.GlassQuality
import com.fitlog.app.domain.GlassRestriction

enum class GlassStyle(val tintAlpha: Float, val blurDp: Float, val refractionDp: Float, val edgeDp: Float, val shadowDp: Float) {
    LIGHT(.30f, 2f, 2f, 3f, 1f), STANDARD(.46f, 3f, 4f, 4f, 2f), EMPHASIZED(.60f, 5f, 5f, 5f, 3f),
    /** Backdrop-inspired interactive lens; no broad inner shadow or glow. */
    CONTROL(.42f, 2f, 10f, 8f, 1.5f),
    /** Icon-only floating controls: clear lens edge, protected icon core, visible optical rim. */
    DOCK(.18f, 2f, 12f, 12f, 4f)
}

internal const val DockIconCoreAlpha = .78f
internal const val DockDarkCoreAlpha = .98f
internal const val DockIconCoreRadius = .82f
// A narrow optical lip avoids the old broad, recessed-looking opacity ramp.
internal const val GlassTextEdgeDp = 1.5f
internal const val GlassCardAlpha = .95f
internal const val GlassControlLightAlpha = .97f
internal const val GlassControlDarkAlpha = .92f
internal const val GlassTextCoreAlpha = .98f

data class GlassEnvironment(val quality: GlassQuality = GlassQuality.SOLID,
    val reduceMotion: Boolean = false, val dark: Boolean = false,
    val restriction: GlassRestriction = GlassRestriction.NONE)

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }
