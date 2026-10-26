package com.fitlog.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlog.app.ui.theme.*

@Composable
fun AppActionButton(label: String, enabled: Boolean, color: Color, action: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .35f
    AppButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(
            containerColor = if (dark) Lime else if (color == Orange) OrangeAccent else GreenAccent,
            contentColor = if (dark) Night else Ink,
            disabledContainerColor = if (dark) NightSurface else FieldFill,
            disabledContentColor = if (dark) NightMuted else Muted)) { Text(label) }
}

@Composable
fun AppSegment(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .35f
    val source = remember { MutableInteractionSource() }
    Surface(modifier.motionPress(source, enabled).semantics { this.selected = selected }, shape = RoundedCornerShape(20.dp),
        color = motionColor(if (selected) { if (dark) NightSurface else GreenTint } else if (dark) NightSurface else FieldFill),
        contentColor = if (selected) { if (dark) Lime else Green } else MaterialTheme.colorScheme.onSurface,
        border = null, shadowElevation = 0.dp) {
        Box(Modifier.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Tab, onClick = onClick)
            .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { label() }
    }
}

/** The dialog's own surface determines its theme, not the screen behind it. */
@Composable
fun AppAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface) {
    FitLogTheme(workout = containerColor.luminance() < .35f) {
        AlertDialog(onDismissRequest, confirmButton, modifier, dismissButton = dismissButton,
            title = title, text = text, containerColor = containerColor, tonalElevation = 0.dp)
    }
}

/** Size the content inside this sheet. Fractional height on the sheet's outer modifier
 * constrains the anchor calculation and can leave a transparent strip at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = if (containerColor.luminance() < .35f) NightText else Ink,
    content: @Composable ColumnScope.() -> Unit) {
    FitLogTheme(workout = containerColor.luminance() < .35f) {
        ModalBottomSheet(onDismissRequest, modifier, sheetState, containerColor = containerColor,
            contentColor = contentColor, tonalElevation = 0.dp, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    DatePickerDialog(onDismissRequest, confirmButton, dismissButton = dismissButton,
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        tonalElevation = 0.dp, content = { content() })
}
