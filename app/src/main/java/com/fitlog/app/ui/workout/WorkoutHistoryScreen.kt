package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.domain.TrainingVolume
import com.fitlog.app.domain.WorkoutOverview
import com.fitlog.app.domain.cardioTime
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun WorkoutRecord.historyVolume() = TrainingVolume.resolve(volumeKg, summary, totalSets)
internal fun capacityLabel(value: Double) = String.format(Locale.CHINA, "%,.0f", value)
internal fun WorkoutRecord.countLabel() = listOfNotNull(
    (totalSets - cardioRounds).takeIf { it > 0 }?.let { "$it 组" }, cardioRounds.takeIf { it > 0 }?.let { "$it 段有氧" }).joinToString(" · ")
internal fun WorkoutRecord.cardioLabel() = "有氧 ${cardioTime(cardioSeconds)}" + if (cardioDistanceMeters > 0) " · ${capacityLabel(cardioDistanceMeters)} 米" else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(nav: NavHostController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(selectedMonth)
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WorkoutRecord?>(null) }
    val records = remember(data.workouts, month) {
        data.workouts.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }.sortedByDescending { it.createdAt }
    }
    val strengthRecords = records.filter { it.totalSets > it.cardioRounds }
    val knownVolumes = strengthRecords.mapNotNull { it.historyVolume() }
    val unknownVolumes = strengthRecords.size - knownVolumes.size
    PageScaffold("训练历史", { nav.popBackStack() }, actions = {
        AppTextButton(onClick = { nav.navigate(Routes.CALENDAR) }) { Text("日历") }
    }) { padding ->
        key(selectedMonth) {
            LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppTextButton(onClick = { pickingMonth = true }, contentPadding = PaddingValues(end = 8.dp)) {
                            Text("${month.year} 年 ${month.monthValue} 月", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Icon(Icons.Default.KeyboardArrowDown, "切换月份", Modifier.padding(start = 6.dp).size(20.dp), tint = Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("当月累计", fontSize = 11.sp, color = Muted)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HistoryMetric("${records.size}", "训练次数", Modifier.weight(1f))
                        HistoryMetric("${records.sumOf { it.durationSeconds.toLong() } / 60}", "时长 / 分钟", Modifier.weight(1f))
                        HistoryMetric(if (knownVolumes.isEmpty() && records.isNotEmpty()) "—" else capacityLabel(knownVolumes.sum()),
                            if (unknownVolumes > 0) "已知力量容量 / kg" else "力量容量 / kg", Modifier.weight(1.25f))
                    }
                    if (records.any { it.cardioRounds > 0 }) Text("有氧 ${cardioTime(records.sumOf { it.cardioSeconds })} · ${capacityLabel(records.sumOf { it.cardioDistanceMeters })} 米",
                        color = Green, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    if (unknownVolumes > 0) Text("${unknownVolumes} 次旧记录缺少容量明细", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
                    HorizontalDivider(color = Line)
                }
                if (data.ready && records.isEmpty()) item {
                    Column(Modifier.padding(vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("这个月还没有训练记录", style = MaterialTheme.typography.titleLarge)
                        Text("点击上方月份，回看其他月份的训练。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        AppTextButton(onClick = { nav.navigate(Routes.FREE) }) { Text("开始自由训练 →") }
                    }
                }
                records.groupBy { it.dateEpochDay }.toSortedMap(compareByDescending { it }).forEach { (day, daily) ->
                    item(key = "day-$day") {
                        WorkoutDayCard(daily) { detail = it }
                    }
                }
            }
        }
    }
    if (pickingMonth) TrainingMonthPicker(month, { pickingMonth = false }) {
        selectedMonth = it.toString()
        pickingMonth = false
    }
    detail?.let { record -> WorkoutRecordDetails(record, vm) { detail = null } }
}

@Composable
internal fun WorkoutDayCard(records: List<WorkoutRecord>, onDetail: (WorkoutRecord) -> Unit) {
    if (records.isEmpty()) return
    val daily = records.sortedByDescending { it.createdAt }
    val date = LocalDate.ofEpochDay(daily.first().dateEpochDay)
    val strengthRecords = daily.filter { it.totalSets > it.cardioRounds }
    val volumes = strengthRecords.mapNotNull { it.historyVolume() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${date.monthValue}月${date.dayOfMonth}日 · ${WeekNames[date.dayOfWeek.value - 1]}",
                color = Color(0xFF111111), fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${daily.size} 次训练", Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (strengthRecords.isEmpty()) cardioTime(daily.sumOf { it.cardioSeconds })
                        else if (volumes.isEmpty()) "容量未知" else "${capacityLabel(volumes.sum())} kg", color = Green, fontSize = 13.sp)
                    if (volumes.size < strengthRecords.size && volumes.isNotEmpty()) Text("已知力量容量", color = Muted, fontSize = 11.sp)
                    if (daily.sumOf { it.cardioDistanceMeters } > 0) Text("${capacityLabel(daily.sumOf { it.cardioDistanceMeters })} 米", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (daily.size > 1) {
            val groupShape = RoundedCornerShape(20.dp)
            AppCard(modifier = Modifier.fillMaxWidth(),
                color = FieldFill, shape = groupShape) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    daily.forEach { record ->
                        key(record.id) { HistorySessionCard(record) { onDetail(record) } }
                    }
                }
            }
        } else {
            HistorySessionCard(daily.first()) { onDetail(daily.first()) }
        }
    }
}

@Composable
private fun HistorySessionCard(record: WorkoutRecord, onDetail: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    AppCard(modifier = Modifier.fillMaxWidth(), color = CardFill, shape = shape) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            HistorySession(record, onDetail)
        }
    }
}

@Composable
private fun HistoryMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HistorySession(record: WorkoutRecord, onDetail: () -> Unit) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val actions = remember(record.summary) { WorkoutOverview.actions(record.summary) }
    val volume = record.historyVolume()
    Column {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).motionClickable(onClickLabel = if (expanded) "收起动作明细" else "展开动作明细") { expanded = !expanded }
            .padding(vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.planName, Modifier.weight(1f), color = Color(0xFF111111),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 11.sp, color = Muted)
                Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null, Modifier.size(18.dp), tint = Muted)
            }
            Text(if (actions.isEmpty()) "点击查看训练明细" else actions.joinToString(" · ") { it.name },
                fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${record.countLabel()} · ${timeLabel(record.durationSeconds)}", fontSize = 12.sp) }
                Text(if (record.totalSets == record.cardioRounds) {
                    if (record.cardioDistanceMeters > 0) "${capacityLabel(record.cardioDistanceMeters)} 米" else cardioTime(record.cardioSeconds)
                } else if (volume == null) "容量未知" else "${capacityLabel(volume)} kg", fontSize = 12.sp, color = Green)
            }
            if (record.cardioRounds > 0) Text(record.cardioLabel(), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        val detailsDuration = if (LocalGlassEnvironment.current.reduceMotion) 0 else 220
        AnimatedVisibility(expanded,
            enter = expandVertically(tween(detailsDuration)) + fadeIn(tween(detailsDuration)),
            exit = shrinkVertically(tween(detailsDuration)) + fadeOut(tween(detailsDuration))) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                actions.forEach { action ->
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(action.name, fontSize = 12.sp)
                        Text(action.overview, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                AppTextButton(onClick = onDetail, contentPadding = PaddingValues(0.dp)) { Text("查看 / 编辑 →", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
internal fun TrainingMonthPicker(selected: YearMonth, dismiss: () -> Unit, choose: (YearMonth) -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(selected.year) }
    AppAlertDialog(onDismissRequest = dismiss, containerColor = Paper, title = { Text("选择月份") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AppTextButton(onClick = { year-- }, enabled = year > 1) { Text("‹", fontSize = 24.sp) }
                Text("$year 年", style = MaterialTheme.typography.titleMedium)
                AppTextButton(onClick = { year++ }, enabled = year < 9999) { Text("›", fontSize = 24.sp) }
            }
            (0..3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { col ->
                        val candidate = YearMonth.of(year, row * 3 + col)
                        AppTextButton(onClick = { choose(candidate) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = if (candidate == selected) GreenTint else Color.Transparent)) {
                            Text("${candidate.monthValue} 月")
                        }
                    }
                }
            }
        }
    }, confirmButton = { AppTextButton(onClick = { choose(YearMonth.now()) }) { Text("回到本月") } },
        dismissButton = { AppTextButton(onClick = dismiss) { Text("取消") } })
}



