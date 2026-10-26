package com.fitlog.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate

/** Compact keeps every set; detail gives each set its own readable row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingAchievementCard(summary: SessionSummary, modifier: Modifier = Modifier,
    checkProgress: Float = 1f, checkPlaced: (Rect) -> Unit = {}, framed: Boolean = true,
    detailed: Boolean = false) {
    val date = LocalDate.ofEpochDay(summary.dateEpochDay)
    val strength = summary.sets > summary.cardioRounds
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(if (framed) 20.dp else 0.dp),
        color = if (framed) CardFill else Paper) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LiftingLoop", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("${date.monthValue}月${date.dayOfMonth}日 · ${WeekNames[date.dayOfWeek.value - 1]}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(summary.title, Modifier.weight(1f), color = Ink, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                CompletionCheck(checkProgress, Modifier.size(44.dp).onGloballyPositioned { checkPlaced(it.boundsInRoot()) }, Green)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AchievementMetric(timerLabel(summary.seconds), "训练时长", Modifier.weight(1f))
                AchievementMetric(if (strength) number(summary.volume) else number(summary.cardioDistance),
                    if (strength) "训练容量 · kg" else "有氧距离 · 米", Modifier.weight(1f))
            }
            Text("${summary.exercises} 个动作 · ${summary.sets} ${if (summary.cardioRounds > 0) "段 / 组" else "组"}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (summary.cardioRounds > 0) Text("有氧 ${cardioTime(summary.cardioSeconds)} · ${number(summary.cardioDistance)} 米", color = Green, style = MaterialTheme.typography.bodySmall)
            val actions = WorkoutShareLayout.actions(summary.completed)
            if (actions.isNotEmpty()) {
                HorizontalDivider(color = Line)
                Text("动作与逐组记录", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                actions.forEach { action ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(action.name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${action.rows.sumOf { it.size }} ${if (action.cardio) "段" else "组"}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (detailed) {
                            action.rows.flatten().forEach { set ->
                                Row(Modifier.fillMaxWidth().background(FieldFill, RoundedCornerShape(10.dp)).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SetNumber(set.ordinal)
                                    Text(if (action.cardio) WorkoutShareLayout.setText(set) else "${number(set.weight)} kg × ${set.reps} 次", color = Ink, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            action.rows.flatten().forEach { set ->
                                Row(Modifier.background(FieldFill, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SetNumber(set.ordinal)
                                    Text(WorkoutShareLayout.setText(set), color = Ink, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                if (actions.any { !it.cardio }) Text("力量 kg × 次数", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SetNumber(ordinal: Int) {
    Box(Modifier.size(22.dp).background(GreenTint, CircleShape), contentAlignment = Alignment.Center) {
        Text(ordinal.toString(), color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AchievementMetric(value: String, caption: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
        Text(caption, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
