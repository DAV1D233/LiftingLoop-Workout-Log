package com.fitlog.app.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.GlassQuality
import com.fitlog.app.domain.GlassPolicy
import android.os.Build
import com.fitlog.app.ui.theme.FitLogTheme

/** Developer-only fixture within the app: check edge displacement against straight grid lines.
 * Layoutlib may fall back; actual refraction acceptance requires a hardware Android 13+ device.
 * This composable is not registered in navigation and is absent from release sources.
 */
@Preview(name = "Glass optics / API 33", apiLevel = 33, widthDp = 440, heightDp = 240)
@Preview(name = "Opaque fallback / API 26", apiLevel = 26, widthDp = 440, heightDp = 240)
@Composable
private fun GlassOpticsPreview() {
    FitLogTheme {
        CompositionLocalProvider(LocalGlassEnvironment provides GlassEnvironment(
            GlassPolicy.quality(Build.VERSION.SDK_INT, true, false, false, false, false, false))) {
            GlassBackdropHost {
                Canvas(Modifier.fillMaxSize().glassBackdropSource()) {
                    drawRect(Color.White)
                    val step = 12.dp.toPx()
                    repeat((size.width / step).toInt() + 1) { x ->
                        repeat((size.height / step).toInt() + 1) { y ->
                            if ((x + y) % 2 == 0) drawRect(Color.Black, Offset(x * step, y * step), Size(step, step))
                        }
                    }
                }
                Row(Modifier.align(Alignment.Center).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    GlassStyle.entries.forEach { style ->
                        GlassSurface(Modifier.size(88.dp), style, CircleShape) {
                            Text(style.name.take(1), Modifier.align(Alignment.Center), color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
