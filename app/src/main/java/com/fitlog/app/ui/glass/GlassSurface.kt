package com.fitlog.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fitlog.app.domain.GlassQuality
import com.fitlog.app.domain.GlassRestriction
import kotlin.math.ceil

@Composable
fun GlassSurface(modifier: Modifier = Modifier, style: GlassStyle = GlassStyle.STANDARD,
    shape: Shape = RoundedCornerShape(24.dp), tint: Color? = null, pressed: Boolean = false,
    protectIcon: Boolean = true,
    content: @Composable BoxScope.() -> Unit) {
    Box(modifier.glassSurface(style, shape, tint, pressed, protectIcon), content = content)
}

/** Only the material receives the lens. Child text/icons are painted afterwards, unchanged. */
fun Modifier.glassSurface(style: GlassStyle = GlassStyle.STANDARD,
    shape: Shape = RoundedCornerShape(24.dp), tint: Color? = null, pressed: Boolean = false,
    protectIcon: Boolean = true): Modifier = composed {
    val environment = LocalGlassEnvironment.current
    val source = LocalGlassBackdrop.current
    val snapshot = LocalGlassSnapshot.current
    val view = LocalView.current
    val lensLayer = rememberGraphicsLayer()
    val glowLayer = rememberGraphicsLayer()
    val drawGuard = remember(source, environment.quality) { GlassDrawGuard() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val renderer = remember(environment.quality) {
        if (Build.VERSION.SDK_INT >= 33 && environment.quality == GlassQuality.REFRACTIVE)
            runCatching { GlassShader() }.getOrElse {
                Log.e("FitLogGlass", "Lens initialization failed for one control; using blur fallback", it)
                null
            } else null
    }
    val highlight = remember(environment.quality) {
        if (Build.VERSION.SDK_INT >= 33 && environment.quality != GlassQuality.SOLID)
            runCatching { GlassHighlight() }.getOrNull() else null
    }
    val materialTint = remember(environment.quality) {
        if (Build.VERSION.SDK_INT >= 33 && environment.quality != GlassQuality.SOLID)
            runCatching { GlassTint() }.getOrNull() else null
    }
    val base = tint ?: if (environment.dark) Color(0xFF202A23) else Color.White
    val materialDark = base.luminance() < .35f
    onGloballyPositioned { origin = screenOffset(view) + it.positionInRoot() }
        .graphicsLayer {
            this.shape = shape
            shadowElevation = if (environment.quality == GlassQuality.SOLID) 0f else style.shadowDp * density
            ambientShadowColor = Color.Black.copy(alpha = if (style == GlassStyle.DOCK) .16f else .09f)
            spotShadowColor = Color.Black.copy(alpha = if (style == GlassStyle.DOCK) .22f else .12f)
        }
        .drawWithCache {
            // Compose 1.7 reuses CanvasDrawScope during layer recording. Passing the live
            // scope as Density can make drawParams.density point to itself and overflow.
            val layerDensity = Density(density, fontScale)
            val layerDirection = layoutDirection
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val radius = when (outline) {
                is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                else -> minOf(size.width, size.height) / 2f
            }
            val optical = environment.quality != GlassQuality.SOLID && !drawGuard.failed && source?.failed != true
            val staticPolish = environment.quality == GlassQuality.SOLID &&
                environment.restriction != GlassRestriction.TRANSPARENCY
            val clear = style == GlassStyle.DOCK
            val hasGlow = clear && !materialDark
            val glowPadding = 8.dp.toPx()
            val glowSize = IntSize(ceil(size.width + glowPadding * 2).toInt(), ceil(size.height + glowPadding * 2).toInt())
            val glowBlur = if (Build.VERSION.SDK_INT >= 31 && optical && hasGlow)
                runCatching { RenderEffect.createBlurEffect(3.dp.toPx(), 3.dp.toPx(), Shader.TileMode.CLAMP).asComposeRenderEffect() }.getOrNull() else null
            val glowStrength = if (clear) 1f else .5f
            val glowBrush = Brush.sweepGradient(listOf(Color.White.copy(alpha = .8f * glowStrength), base.copy(alpha = .25f * glowStrength),
                Color.White.copy(alpha = .6f * glowStrength), base.copy(alpha = .35f * glowStrength), Color.White.copy(alpha = .8f * glowStrength)), Offset(size.width / 2f, size.height / 2f))
            val inset = minOf(GlassTextEdgeDp * density, minOf(size.width, size.height) / 3f)
            onDrawWithContent {
            if (source?.recording == true) return@onDrawWithContent
            if (size.width <= 0f || size.height <= 0f) {
                drawContent()
                return@onDrawWithContent
            }
            drawGuard.render(effect = {
            // A local blurred rim produces a soft halo even on a plain white page. It never
            // samples or blurs icon/text content, and does not run a continuous animation.
            if (optical && hasGlow) {
                glowLayer.record(layerDensity, layerDirection, glowSize) {
                    translate(glowPadding, glowPadding) {
                        drawOutline(outline, glowBrush, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    }
                }
                glowLayer.renderEffect = glowBlur
                translate(-glowPadding, -glowPadding) { drawLayer(glowLayer) }
            }
            val usable = optical && source != null && source.ready && !source.failed &&
                (view === source.view || snapshot != null)
            if (usable) {
                val halo = ceil((style.blurDp * 3f + style.refractionDp * 1.12f + 2f) * density)
                    val expanded = IntSize(ceil(size.width + halo * 2).toInt(), ceil(size.height + halo * 2).toInt())
                    lensLayer.record(layerDensity, layerDirection, expanded) {
                        translate(halo, halo) { drawBackdrop(source!!, snapshot, origin) }
                    }
                    lensLayer.renderEffect = if (Build.VERSION.SDK_INT >= 33 && renderer != null) {
                        renderer.effect(size.width, size.height, halo, radius, style, density, pressed).asComposeRenderEffect()
                    } else if (Build.VERSION.SDK_INT >= 31) {
                        RenderEffect.createBlurEffect(style.blurDp * density, style.blurDp * density, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    } else null
                    clipPath(path) { translate(-halo, -halo) { drawLayer(lensLayer) } }
            }
            clipPath(path) {
                if (style == GlassStyle.CONTROL) {
                    // Keep the foreground legible; a narrow transparent optical lip
                    // exposes refraction without adding a broad inset shadow.
                    val core = if (materialDark) GlassControlDarkAlpha else GlassControlLightAlpha
                    val edge = minOf(3.dp.toPx(), size.minDimension / 4f)
                    if (usable && Build.VERSION.SDK_INT >= 33 && materialTint != null)
                        drawRect(materialTint.brush(size.width, size.height, radius, edge, base, style.tintAlpha, core))
                    else if (usable) drawRect(Brush.verticalGradient(0f to base.copy(alpha = style.tintAlpha),
                        (edge / size.height) to base.copy(alpha = core),
                        (1f - edge / size.height) to base.copy(alpha = core), 1f to base.copy(alpha = style.tintAlpha)))
                    else drawRect(base)
                } else if (!clear && usable && source?.failed == false) {
                    if (Build.VERSION.SDK_INT >= 33 && materialTint != null)
                        drawRect(materialTint.brush(size.width, size.height, radius, inset, base, style.tintAlpha))
                    else {
                        val core = style.tintAlpha + (1f - style.tintAlpha) * GlassTextCoreAlpha
                        drawRect(Brush.verticalGradient(0f to base.copy(alpha = style.tintAlpha),
                            (inset / size.height) to base.copy(alpha = core), (1f - inset / size.height) to base.copy(alpha = core), 1f to base.copy(alpha = style.tintAlpha)))
                    }
                } else drawRect(base.copy(alpha = if (usable && source?.failed == false) style.tintAlpha else 1f))
                // Protect the text core independently of unknown background luminance. The optical
                // edge remains translucent; content is never blurred or used as shader input.
                if (clear && protectIcon && usable && source?.failed == false) {
                    // Keep the 28dp icon (including its 1.18 selection scale) readable while
                    // preserving a clear curved lens edge instead of an opaque inner disc.
                    if (materialDark && Build.VERSION.SDK_INT >= 33 && materialTint != null)
                        drawRect(materialTint.brush(size.width, size.height, radius, 3.dp.toPx(), base, style.tintAlpha, DockDarkCoreAlpha))
                    else drawRect(Brush.radialGradient(0f to base.copy(alpha = if (materialDark) DockDarkCoreAlpha else DockIconCoreAlpha),
                        DockIconCoreRadius to base.copy(alpha = if (materialDark) DockDarkCoreAlpha else DockIconCoreAlpha), 1f to Color.Transparent,
                        center = center, radius = size.minDimension * .48f))
                }
                if (optical || staticPolish) {
                    val gloss = if (materialDark) { if (pressed) .04f else .025f } else if (pressed) .16f else .08f
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = gloss), Color.Transparent),
                        Offset.Zero, Offset(size.width, size.height)))
                }
            }
            // The dark contact rim stays visible on white; directional specular light defines
            // the curved glass edge. Solid accessibility fallback has no glow or refraction.
            drawOutline(outline, if (materialDark) Color.White.copy(alpha = .24f) else Color.Black.copy(alpha = if (clear) .20f else .12f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(if (clear) 1.dp.toPx() else .7.dp.toPx()))
            if (optical || staticPolish) clipPath(path) {
                val light = if (optical && Build.VERSION.SDK_INT >= 33 && highlight != null)
                    highlight.brush(size.width, size.height, radius, pressed)
                    else Brush.linearGradient(listOf(Color.White, Color.Transparent, Color.White), Offset.Zero, Offset(size.width, size.height))
                drawOutline(outline, light, alpha = if (materialDark) .55f else .9f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(if (clear) 1.6.dp.toPx() else if (style == GlassStyle.CONTROL) 1.4.dp.toPx() else .9.dp.toPx()))
            }
            }, fallback = {
                // Paint a readable, complete material in the same frame. Never leave a partly
                // drawn lens, and never wrap app text/icons or business callbacks in this catch.
                drawOutline(outline, base)
                drawOutline(outline, if (materialDark) Color.White.copy(alpha = .24f) else Color.Black.copy(alpha = .20f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx()))
            }, report = { error ->
                Log.e("FitLogGlass", "Optional glass drawing failed; using solid material", error)
            })
            drawContent()
            }
        }
}
