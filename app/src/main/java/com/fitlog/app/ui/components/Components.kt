package com.fitlog.app.ui.components

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.ui.theme.*
import java.time.LocalDate
import java.util.Locale

val WeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
fun dateLabel(day: LocalDate): String = "${day.monthValue}月${day.dayOfMonth}日 · ${WeekNames[day.dayOfWeek.value - 1]}"
fun timerLabel(seconds: Int): String = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
fun timeLabel(seconds: Int): String = if (seconds < 60) "${seconds} 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"

@Composable
fun PageScaffold(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null, content: @Composable (PaddingValues) -> Unit) {
    Box(Modifier.fillMaxSize()) {
    // Safe fallback backdrop: the actual plain page background, excluding all foreground controls.
    Canvas(Modifier.matchParentSize()) { drawRect(Paper) }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                actions()
            }
        }, bottomBar = {
            if (bottom != null) Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp)) { bottom() }
        }, content = content)
    }
}

@Composable
fun Eyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 11.sp, letterSpacing = 1.5.sp, color = color)
}

@Composable
fun SectionHeading(title: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 24.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (detail != null) Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SectionHeading(title: String) = SectionHeading(title, null)

@Composable
fun ActionRow(title: String, caption: String, onClick: () -> Unit, trailing: String? = null,
    color: Color = Green, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    Column {
        Row(Modifier.fillMaxWidth().motionPress(source, enabled, .985f).clip(RoundedCornerShape(12.dp)).clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick).padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (caption.isNotEmpty()) Text(caption, color = Muted, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) Text(trailing, color = color, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color)
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
fun PrimaryAction(label: String, enabled: Boolean = true, color: Color = Green, action: () -> Unit) {
    AppActionButton(label, enabled, color, action)
}

@Composable
fun EmptyState(title: String, detail: String, actionLabel: String, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(detail, color = Muted)
        AppTextButton(action, contentPadding = PaddingValues(0.dp)) { Text(actionLabel + " →") }
    }
}

@Composable
fun StepControl(label: String, value: String, minus: () -> Unit, plus: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppOutlinedButton(minus, Modifier.size(44.dp), enabled, contentPadding = PaddingValues(0.dp)) { Text("−") }
        Box(Modifier.widthIn(min = 76.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        AppOutlinedButton(plus, Modifier.size(44.dp), enabled, contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
}

@Composable
fun DeleteDialog(name: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AppAlertDialog(onDismissRequest = dismiss, title = { Text("删除这条记录？") },
        text = { Text(name) }, confirmButton = { AppTextButton(confirm) { Text("删除") } },
        dismissButton = { AppTextButton(dismiss) { Text("保留") } })
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ink) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = color)
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

