package com.fitlog.app.ui.home

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlog.app.data.planLabel
import com.fitlog.app.domain.number
import com.fitlog.app.domain.MissedTraining
import com.fitlog.app.domain.ScheduleCatchUp
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.ui.workout.CompletedTrainingSheet
import com.fitlog.app.ui.workout.WorkoutRecordDetails
import com.fitlog.app.ui.workout.completedSummary
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(nav: NavHostController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val prefs by vm.preferences.collectAsState()
    val session by vm.session.collectAsState()
    val today by vm.today.collectAsState()
    val busy by vm.busy.collectAsState()

    val day = today.toEpochDay()
    val workouts = data.workouts.filter { it.dateEpochDay == day }
    val schedule = prefs.scheduleFor(today)
    val label = planLabel(schedule, data.plans)
    val recordedCompletion by produceState(false, schedule, day, data.workouts, prefs.scheduleCompletedDay, prefs.scheduleCompletedKey) {
        value = false
        try { value = vm.todayPlanCompleted(schedule, today) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { value = false }
    }
    val completedToday = recordedCompletion || (prefs.scheduleCompletedDay == day && prefs.scheduleCompletedKey == schedule)
    val monthCount = data.workouts.count { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == YearMonth.from(today) }
    val weekStart = day - (today.dayOfWeek.value - 1)
    val weekTrainingDays = data.workouts.asSequence().map { it.dateEpochDay }
        .filter { it in weekStart..day }.distinct().count()
    var showRest by remember { mutableStateOf(false) }
    var completedRecord by remember { mutableStateOf<WorkoutRecord?>(null) }
    var missedTraining by remember { mutableStateOf<MissedTraining?>(null) }

    var showWeight by remember { mutableStateOf(false) }
    var dockFocused by remember { mutableStateOf(false) }
    val dockDrag = remember { DockDragController() }
    val contentAlpha by animateFloatAsState(if (dockFocused) .12f else 1f, tween(if (!dockFocused || LocalGlassEnvironment.current.reduceMotion) 0 else 120), label = "home focus")

    fun todayAction() {
        if (session != null) nav.navigate(Routes.preview("active")) { launchSingleTop = true }
        else if (completedToday) {
            val sessionKey = vm.settings.completedScheduleSession(day, schedule)
            completedRecord = workouts.firstOrNull { sessionKey != null && it.sessionKey == sessionKey }
                ?: workouts.filter { it.planName == label }.maxByOrNull { it.createdAt }
            if (completedRecord == null) vm.message("没有找到这次训练记录")
        }
        else if (vm.missedTraining() != null) missedTraining = vm.missedTraining()
        else when (schedule) {
            "unset" -> nav.navigate(Routes.PLANS)
            "rest" -> showRest = true
            else -> nav.navigate(Routes.preview(ScheduleCatchUp.route(schedule, day, false))) { launchSingleTop = true }
        }
    }
    val dockItems = mapOf(
        DockSection.WORKOUT to listOf(
            DockItem("运动日历", HomeSymbol.CALENDAR) { nav.navigate(Routes.CALENDAR) },
            DockItem("自由训练", HomeSymbol.FREE) { nav.navigate(Routes.FREE) },
            DockItem(if (session != null) "继续训练" else if (completedToday) "今日已完成" else "今日计划", if (completedToday && session == null) HomeSymbol.CALENDAR else HomeSymbol.PLAY, ::todayAction)),
        DockSection.FOOD to listOf(
            DockItem("记录饮食", HomeSymbol.EDIT) { nav.navigate(Routes.FOOD) },
            DockItem("饮食日历", HomeSymbol.CALENDAR) { nav.navigate(Routes.FOOD_HISTORY) }),
        DockSection.ADD to listOf(
            DockItem("训练计划", HomeSymbol.BOOK) { nav.navigate(Routes.PLAN_LIBRARY) },
            DockItem("训练周期", HomeSymbol.CALENDAR) { nav.navigate(Routes.PLANS) },
            DockItem("记录体重", HomeSymbol.WEIGHT) { showWeight = true },
            DockItem("动作管理", HomeSymbol.FREE) { nav.navigate(Routes.EXERCISES) }))
    BoxWithConstraints(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing)
        .dockSelectionGesture(dockDrag)) {
        val compact = maxHeight < 660.dp
        val contentHeight = (maxHeight - 152.dp).coerceAtLeast(130.dp)
        // The Dock floats over the page's white bottom margin. Record that actual plain
        // background independently; glass header controls must not enter the source tree.
        Canvas(Modifier.matchParentSize().glassBackdropSource()) { drawRect(Paper) }
        Box(Modifier.matchParentSize()) {
        Column(Modifier.fillMaxWidth().heightIn(max = contentHeight)
            .graphicsLayer { alpha = if (dockFocused) contentAlpha else 1f }.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LiftingLoop", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                AppIconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "设置", tint = Ink, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).motionClickable(enabled = data.ready && !busy, onClick = ::todayAction)
                .padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("今日训练", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(when {
                    !data.ready -> "加载中"
                    session != null -> session!!.title
                    schedule == "unset" -> "未安排训练"
                    schedule == "rest" -> "恢复日"
                    else -> label
                }, style = MaterialTheme.typography.headlineLarge)
                Text(when {
                    session != null -> "继续上次训练 ↗"
                    completedToday -> "今日计划已完成 · 查看训练详情 ↗"
                    schedule == "unset" -> "安排训练周期 ↗"
                    schedule == "rest" -> "查看休息日安排 ↗"
                    else -> "查看今日动作 ↗"
                }, color = Green, style = MaterialTheme.typography.bodyMedium)
                if (workouts.isNotEmpty()) Text("今日已完成 ${workouts.size} 次训练", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = Ink.copy(alpha = .24f), thickness = .5.dp)
            Row(Modifier.fillMaxWidth()) {
                SwissMetric("${monthCount.toString().padStart(2, '0')}", "本月训练 / 次", Modifier.weight(1f))
                SwissMetric(weekTrainingDays.toString().padStart(2, '0'), "本周训练 / 天", Modifier.weight(1f))
            }
            WeekStrip(today, data) { date ->
                nav.currentBackStackEntry?.savedStateHandle?.set("calendarDay", date.toEpochDay())
                nav.navigate(Routes.CALENDAR)
            }
            Spacer(Modifier.height(4.dp))
        }
        }
        HomeDock(dockItems, enabled = data.ready && !busy, hint = !prefs.hintSeen, availableHeight = maxHeight, gestures = dockDrag,
            cue = vm::cue, opened = { vm.settings.dismissHint() }, focusChanged = { dockFocused = it })
    }
    if (showRest) AppAlertDialog(onDismissRequest = { showRest = false },
        title = { Text("今日休息") }, text = { Text("当前训练周期将今天设为休息日。") },
        confirmButton = { AppTextButton(onClick = { showRest = false; nav.navigate(Routes.FREE) }) { Text("自由训练") } },
        dismissButton = { AppTextButton(onClick = { showRest = false }) { Text("关闭") } })
    if (showWeight) BodyWeightDialog(vm) { showWeight = false }
    completedRecord?.let { record ->
        val summary = remember(record) { record.completedSummary() }
        if (summary != null) CompletedTrainingSheet(summary) { completedRecord = null }
        else WorkoutRecordDetails(record, vm) { completedRecord = null }
    }
    missedTraining?.let { missed ->
        MissedTrainingSheet(vm, missed, schedule, { missedTraining = null }) { resume ->
            missedTraining = null
            nav.navigate(Routes.preview(ScheduleCatchUp.route(if (resume) missed.key else schedule, day, resume))) { launchSingleTop = true }
        }
    }
}

@Composable
private fun SwissMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WeekStrip(today: LocalDate, data: LogData, onClick: (LocalDate) -> Unit) {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val sunday = monday.plusDays(6)
    val workouts = data.workouts.filter { it.dateEpochDay in monday.toEpochDay()..sunday.toEpochDay() }
    val foods = data.foods.filter { it.dateEpochDay in monday.toEpochDay()..sunday.toEpochDay() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("本周", color = Muted, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                val date = monday.plusDays(i.toLong())
                val count = workouts.count { it.dateEpochDay == date.toEpochDay() }
                val hasFood = foods.any { it.dateEpochDay == date.toEpochDay() }
                val current = date == today
                Column(Modifier.weight(1f).semantics {
                    contentDescription = "${date.monthValue}月${date.dayOfMonth}日，${WeekNames[i]}，${if (current) "今天，" else ""}$count 次训练，${if (hasFood) "有" else "无"}饮食记录"
                }.clip(RoundedCornerShape(12.dp)).motionClickable(onClickLabel = "查看当天记录") { onClick(date) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(WeekNames[i].removePrefix("周"), color = Muted, style = MaterialTheme.typography.labelSmall)
                    Surface(color = if (current) Ink else Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text("${date.dayOfMonth}", color = if (current) Color.White else Ink, style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                    Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(9.dp, 3.dp).background(if (count > 0) GreenAccent else Line))
                        Box(Modifier.size(9.dp, 3.dp).background(if (hasFood) OrangeAccent else Line))
                    }
                }
            }
        }
    }
}



