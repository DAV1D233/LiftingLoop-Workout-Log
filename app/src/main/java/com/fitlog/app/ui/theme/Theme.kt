package com.fitlog.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color.White
val CardFill = Color(0xFFF3F5F6)
val FieldFill = Color(0xFFE9EEF1)
val CardOutline = Color(0xFF252525)
val Ink = Color(0xFF1D2822)
val Muted = Color(0xFF5F6B62)
val Line = Color(0xFFDDE3E7)
// Accent fills use the chosen palette; smaller text uses darker tonal relatives.
val GreenAccent = Color(0xFF58C7AB)
val OrangeAccent = Color(0xFFF2A03D)
val Green = Color(0xFF23745F)
val GreenTint = lerp(Paper, GreenAccent, .18f)
val Orange = Color(0xFF925009)
val OrangeTint = lerp(Paper, OrangeAccent, .18f)
val DockGreenTint = lerp(Paper, GreenAccent, .55f)
val DockOrangeTint = lerp(Paper, OrangeAccent, .55f)
val DockGreenInk = Color(0xFF163F35)
val DockOrangeInk = Color(0xFF5D3508)
val Night = Color(0xFF101512)
val NightSurface = Color(0xFF202A23)
val RestSurface = Color(0xFF303330)
val RestText = Color(0xFFD2D5D2)
val Lime = GreenAccent
val Blue = Color(0xFF73B6D5)
val NightText = Color(0xFFF2F5ED)
val NightMuted = Color(0xFF9CAC9E)

private val LightPalette = lightColorScheme(primary = Green, onPrimary = Color.White,
    secondary = Orange, onSecondary = Color.White, background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = CardFill, onSurfaceVariant = Muted,
    surfaceContainer = CardFill, surfaceContainerLow = CardFill, surfaceContainerHigh = CardFill,
    surfaceContainerHighest = CardFill, surfaceContainerLowest = Paper,
    outline = Line, outlineVariant = Line, error = Color(0xFFAC3932))
private val WorkoutPalette = darkColorScheme(primary = Lime, onPrimary = Night,
    secondary = Blue, onSecondary = Night, background = Night, onBackground = NightText,
    surface = NightSurface, onSurface = NightText, surfaceVariant = NightSurface,
    onSurfaceVariant = NightMuted, outline = Color(0xFF3C4B40))
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 40.sp, lineHeight = 49.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)
private val Geometry = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))
@Composable
fun FitLogTheme(workout: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (workout) WorkoutPalette else LightPalette,
        typography = Type, shapes = Geometry, content = content)
}

