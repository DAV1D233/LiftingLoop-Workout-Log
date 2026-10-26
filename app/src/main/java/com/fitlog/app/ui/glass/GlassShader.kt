package com.fitlog.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Native Android adapter for Backdrop's Apache-2.0 lens equations.
 * Upstream code and modification notice are retained in BackdropShaders.kt.
 */
@RequiresApi(33)
internal class GlassShader {
    private val shader = RuntimeShader(RoundedRectRefractionShaderString)
    private var key: List<Any>? = null
    private var cached: RenderEffect? = null
    fun effect(width: Float, height: Float, halo: Float, radius: Float, style: GlassStyle,
        density: Float, pressed: Boolean): RenderEffect {
        val next = listOf(width, height, halo, radius, style, density, pressed)
        if (next == key) return cached!!
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("offset", -halo, -halo)
        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
        shader.setFloatUniform("refractionHeight", style.edgeDp * density)
        shader.setFloatUniform("refractionAmount", -style.refractionDp * density * if (pressed) 1.12f else 1f)
        shader.setFloatUniform("depthEffect", 0f)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = RenderEffect.createBlurEffect(style.blurDp * density, style.blurDp * density, Shader.TileMode.CLAMP)
        return RenderEffect.createChainEffect(lens, blur).also { key = next; cached = it }
    }
}

/** Directional specular rim, independent of the backdrop and foreground text. */
@RequiresApi(33)
internal class GlassHighlight {
    private val shader = RuntimeShader(DefaultHighlightShaderString)
    private val brush = ShaderBrush(shader)
    fun brush(width: Float, height: Float, radius: Float, pressed: Boolean): ShaderBrush {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
        shader.setColorUniform("color", android.graphics.Color.WHITE)
        shader.setFloatUniform("angle", if (pressed) -1.83f else -2.36f)
        shader.setFloatUniform("falloff", 2.5f)
        return brush
    }
}

@RequiresApi(33)
internal class GlassTint {
    private val shader = RuntimeShader(GlassMaterialTintShaderString)
    private val brush = ShaderBrush(shader)
    fun brush(width: Float, height: Float, radius: Float, edge: Float, tint: Color, alpha: Float,
        coreAlpha: Float = GlassTextCoreAlpha): ShaderBrush {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
        shader.setColorUniform("color", tint.toArgb())
        shader.setFloatUniform("edgeWidth", edge)
        shader.setFloatUniform("edgeAlpha", alpha)
        shader.setFloatUniform("coreAlpha", coreAlpha)
        return brush
    }
}
