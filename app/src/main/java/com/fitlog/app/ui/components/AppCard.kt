package com.fitlog.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.fitlog.app.ui.theme.CardFill
import com.fitlog.app.ui.theme.Ink
import com.fitlog.app.ui.theme.NightText

@Composable
fun AppCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp),
    color: Color = CardFill, enabled: Boolean = true, onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit) {
    val surfaceColor = motionColor(color)
    val source = remember { MutableInteractionSource() }
    val foreground = if (color.luminance() < .35f) NightText else Ink
    if (onClick == null) Surface(modifier, shape, surfaceColor, contentColor = foreground, shadowElevation = 0.dp, content = content)
    else Surface(onClick, modifier.motionPress(source, enabled, .985f), enabled, shape, surfaceColor, contentColor = foreground, shadowElevation = 0.dp, interactionSource = source, content = content)
}
