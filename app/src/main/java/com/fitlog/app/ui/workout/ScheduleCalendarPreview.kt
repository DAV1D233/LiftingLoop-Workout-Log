package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.SchedulePreviewDay
import com.fitlog.app.domain.TrainingSchedule
import com.fitlog.app.ui.components.WeekNames
import com.fitlog.app.ui.components.dateLabel
import com.fitlog.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun ScheduleCalendarPreview(mode: String, week: List<String>, cycle: List<String>, start: Long,
    today: LocalDate, busy: Boolean, label: (String) -> String) {
    var monthly by rememberSaveable(mode) { mutableStateOf(false) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var picked by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val dates = if (monthly) TrainingSchedule.month(mode, week, cycle, start, month)
        else TrainingSchedule.week(mode, week, cycle, start, today)
    val selected = dates.firstOrNull { it.date.toEpochDay() == picked }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSegment(!monthly, { monthly = false }, enabled = !busy,
                label = { Text(if (mode == "week") "周视图" else "周期安排") })
            AppSegment(monthly, { monthly = true }, enabled = !busy, label = { Text("月视图") })
        }
        if (monthly || mode == "week") AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (monthly) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = !busy && month.year > 1) {
                        Text("‹", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "上个月" })
                    }
                    AppTextButton(onClick = { pickingMonth = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("${month.year} / ${month.monthValue.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium)
                    }
                    AppTextButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = !busy && month.year < 9999) {
                        Text("›", fontSize = 24.sp, modifier = Modifier.semantics { contentDescription = "下个月" })
                    }
                } else Text("本周 · ${dates.first().date.monthValue}/${dates.first().date.dayOfMonth} — ${dates.last().date.monthValue}/${dates.last().date.dayOfMonth}",
                    color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                Row(Modifier.fillMaxWidth()) {
                    WeekNames.forEach { name -> Text(name.removePrefix("周"), Modifier.weight(1f),
                        textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.labelSmall) }
                }
                val blanks = if (monthly) dates.first().date.dayOfWeek.value - 1 else 0
                val cells: List<SchedulePreviewDay?> = List(blanks) { null } + dates
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { col ->
                            val day = row.getOrNull(col)
                            if (day == null) Spacer(Modifier.weight(1f))
                            else SchedulePreviewCell(day, picked == day.date.toEpochDay(), today == day.date,
                                label, !busy, Modifier.weight(1f)) { picked = day.date.toEpochDay() }
                        }
                    }
                }
                HorizontalDivider(color = Line, modifier = Modifier.padding(top = 4.dp))
                val training = dates.count { it.key != "rest" && it.key != "unset" }
                val rest = dates.count { it.key == "rest" }
                val unset = dates.count { it.key == "unset" }
                Text("$training 天训练 · $rest 天休息" + if (unset > 0) " · $unset 天未开始" else "",
                    color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                selected?.let { day ->
                    Text("${dateLabel(day.date)} · ${if (day.key == "unset") "周期尚未开始" else label(day.key)}" +
                        if (mode == "cycle" && day.position >= 0) " · Day ${day.position + 1}" else "",
                        color = Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
        if (monthly) Text("按当前安排预览整月，训练周期会连续重复。浅绿为训练日，灰色为休息日。",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (pickingMonth) TrainingMonthPicker(month, { pickingMonth = false }) {
        monthText = it.toString(); pickingMonth = false
    }
}

@Composable
private fun SchedulePreviewCell(day: SchedulePreviewDay, chosen: Boolean, today: Boolean,
    label: (String) -> String, enabled: Boolean, modifier: Modifier, click: () -> Unit) {
    val unset = day.key == "unset"
    val resting = day.key == "rest"
    val title = if (unset) "未开始" else label(day.key)
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = click)
        .semantics { selected = chosen; contentDescription = "${dateLabel(day.date)}，$title${if (today) "，今天" else ""}" }
        .padding(vertical = 4.dp, horizontal = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = if (chosen) Green else if (today) FieldFill else if (unset) Color.Transparent else if (resting) FieldFill else GreenTint) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Text("${day.date.dayOfMonth}", fontSize = 13.sp, color = if (chosen) Paper else if (unset || resting) Muted else Green)
            }
        }
        Text(title, color = if (unset || resting) Muted else Green, fontSize = 10.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 4.dp))
    }
}


