package com.fitlog.app.ui.glass

import androidx.compose.runtime.Composable
import com.fitlog.app.domain.GlassQuality
import com.fitlog.app.domain.GlassRestriction

/** Explain an existing fallback; never silently change accessibility or power settings. */
@Composable
fun glassStatusLabel(): String {
    val environment = LocalGlassEnvironment.current
    val source = LocalGlassBackdrop.current
    val reason = if (environment.restriction == GlassRestriction.NONE && source?.failed == true)
        GlassRestriction.FAILED else environment.restriction
    return when (reason) {
        GlassRestriction.TRANSPARENCY -> "当前为实体显示 · 降低透明度已开启"
        GlassRestriction.SOFTWARE -> "当前使用轻量材质 · 硬件加速尚不可用"
        GlassRestriction.MEMORY -> "当前使用轻量材质 · 设备内存限制"
        GlassRestriction.BATTERY -> "当前使用轻量材质 · 系统省电模式已开启"
        GlassRestriction.THERMAL -> "当前使用轻量材质 · 设备温度过高"
        GlassRestriction.UNSUPPORTED -> "当前使用轻量材质 · 此系统不支持实时玻璃"
        GlassRestriction.FAILED -> "当前页面玻璃暂不可用 · 返回页面后可重试"
        GlassRestriction.NONE -> when (environment.quality) {
            GlassQuality.REFRACTIVE -> "当前模式：背景折射与边缘高光"
            GlassQuality.FROSTED -> "当前模式：模糊与边缘高光"
            GlassQuality.SOLID -> "当前为实体显示"
        }
    }
}
