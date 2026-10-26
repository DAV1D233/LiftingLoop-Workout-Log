package com.fitlog.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.fitlog.app.ui.theme.*

// Controls follow the actual Material surface theme, including white sheets opened
// from a dark workout. The home Dock is the only user of optical glass surfaces.
@Composable
private fun darkSurface() = MaterialTheme.colorScheme.surface.luminance() < .35f

@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = if (darkSurface()) Lime else GreenAccent,
        contentColor = if (darkSurface()) Night else Ink,
        disabledContainerColor = if (darkSurface()) NightSurface else FieldFill,
        disabledContentColor = if (darkSurface()) NightMuted else Muted),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Button(onClick, modifier.motionPress(source, enabled), enabled, shape, colors,
        null, null, contentPadding, source, content)
}

/** Text links, explanations and dialog actions intentionally have no filled frame or shadow. */
@Composable
fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp), colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TextButton(onClick, modifier.motionPress(source, enabled, .985f), enabled, shape, colors,
        null, null, contentPadding, source, content)
}

@Composable
fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(containerColor = if (darkSurface()) NightSurface else FieldFill),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(onClick, modifier.motionPress(source, enabled), enabled, shape,
        if (colors.containerColor.alpha == 0f) colors.copy(containerColor = if (darkSurface()) NightSurface else FieldFill) else colors,
        null, null, contentPadding, source, content)
}

@Composable
fun AppIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    IconButton(onClick, modifier.motionPress(source, enabled, .94f).clip(CircleShape), enabled, colors, source, content)
}

@Composable
fun AppTonalIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (darkSurface()) NightSurface else GreenTint,
        contentColor = if (darkSurface()) Lime else Green),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Surface(modifier.motionPress(source, enabled, .94f), shape = shape, color = Color.Transparent, shadowElevation = 0.dp) {
        FilledTonalIconButton(onClick, Modifier, enabled, shape, colors, source, content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = if (darkSurface()) NightSurface else FieldFill,
        selectedContainerColor = if (darkSurface()) NightSurface else GreenTint,
        selectedLabelColor = if (darkSurface()) Lime else Green),
    interactionSource: MutableInteractionSource? = null) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FilterChip(selected, onClick, label, modifier.motionPress(source, enabled), enabled, leadingIcon, trailingIcon, shape,
        colors.copy(containerColor = if (darkSurface()) NightSurface else FieldFill),
        null, null, source)
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedTrackColor = GreenAccent, checkedThumbColor = Paper,
        uncheckedTrackColor = if (darkSurface()) NightSurface else FieldFill,
        uncheckedThumbColor = if (darkSurface()) NightMuted else Muted), interactionSource: MutableInteractionSource? = null) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Switch(checked, onCheckedChange, modifier.motionPress(source, enabled).clip(RoundedCornerShape(50)), thumbContent, enabled, colors.copy(checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent, disabledCheckedBorderColor = Color.Transparent, disabledUncheckedBorderColor = Color.Transparent), source)
}

@Composable
fun AppParameterSurface(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp), color: Color = FieldFill, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(onClick, modifier.motionPress(source, enabled), enabled, shape, color, contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp, interactionSource = source, content = content)
}
