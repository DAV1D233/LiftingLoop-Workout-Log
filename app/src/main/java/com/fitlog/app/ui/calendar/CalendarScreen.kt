package com.fitlog.app.ui.calendar

import com.fitlog.app.ui.glass.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import com.fitlog.app.domain.number
import com.fitlog.app.domain.foodNumber
import com.fitlog.app.domain.TrainingVolume
import com.fitlog.app.domain.WorkoutOverview
import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.ui.workout.WorkoutDayCard
import com.fitlog.app.ui.workout.WorkoutRecordDetails
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val VolumeColors = listOf(Color.Transparent, GreenTint, lerp(Paper, GreenAccent, .45f), GreenAccent, Green)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(nav: NavHostController, vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val entryDay = remember {
        nav.previousBackStackEntry?.savedStateHandle?.remove<Long>("calendarDay")?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    }
    var selectedRaw by rememberSaveable { mutableStateOf(entryDay.toString()) }
    var monthRaw by rememberSaveable { mutableStateOf(YearMonth.from(entryDay).toString()) }
    var compact by rememberSaveable { mutableStateOf(false) }
    var cardioHeat by rememberSaveable { mutableStateOf(false) }
    var detailed by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("workout") }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var workoutDetail by remember { mutableStateOf<WorkoutRecord?>(null) }
    val selected = LocalDate.parse(selectedRaw)
    val month = YearMonth.parse(monthRaw)
    val collapse by animateFloatAsState(if (compact) 1f else 0f,
        animationSpec = tween(if (LocalGlassEnvironment.current.reduceMotion) 0 else 420, easing = FastOutSlowInEasing), label = "monthToWeek")
    val weekStart = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(6)
    val monthStart = month.atDay(1)
    val gridStart = monthStart.minusDays((monthStart.dayOfWeek.value - 1).toLong())
    val rows = (monthStart.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
    val selectedRow = (ChronoUnit.DAYS.between(gridStart, weekStart) / 7).toInt().coerceIn(0, rows - 1)
    val workouts = data.workouts.filter { it.dateEpochDay == selected.toEpochDay() }
    val foods = data.foods.filter { it.dateEpochDay == selected.toEpochDay() }
    val monthWorkouts = data.workouts.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    val monthFoods = data.foods.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    val volumes = remember(data.workouts) { data.workouts.associate { it.id to TrainingVolume.resolve(it.volumeKg, it.summary, it.totalSets) } }
    val actions = remember(data.workouts) { data.workouts.associate { it.id to WorkoutOverview.actions(it.summary) } }
    val recordsByDay = remember(data.workouts) { data.workouts.groupBy { it.dateEpochDay } }
    // Match the first record shown for the day; its actions must not be mixed with later plans.
    val previews = remember(recordsByDay, actions) {
        recordsByDay.mapValues { (_, records) ->
            val first = records.first()
            DayTrainingPreview(first.planName, actions[first.id].orEmpty().map { it.name }.take(5))
        }
    }
    val dailyVolumes = remember(data.workouts, volumes) {
        data.workouts.groupBy { it.dateEpochDay }.mapValues { (_, records) -> records.mapNotNull { volumes[it.id] }.sum() }
    }
    val missingVolumes = remember(data.workouts, volumes) {
        data.workouts.filter { volumes[it.id] == null }.groupingBy { it.dateEpochDay }.eachCount()
    }
    val strengthLabels = remember(recordsByDay, dailyVolumes, missingVolumes) {
        recordsByDay.keys.associateWith { day ->
            if ((missingVolumes[day] ?: 0) > 0) "—" else number(dailyVolumes[day] ?: 0.0)
        }
    }
    val dailyCardio = remember(data.workouts) { data.workouts.groupBy { it.dateEpochDay }.mapValues { (_, records) -> records.sumOf { it.cardioSeconds }.toDouble() } }
    val cardioLabels = remember(dailyCardio) { dailyCardio.mapValues { timerLabel(it.value.toInt()) } }
    val capacityLabels = if (cardioHeat) cardioLabels else strengthLabels
    val heatValues = if (cardioHeat) dailyCardio else dailyVolumes
    val peak = heatValues.filterKeys { YearMonth.from(LocalDate.ofEpochDay(it)) == month }.values.maxOrNull() ?: 0.0
    val selectedVolume = dailyVolumes[selected.toEpochDay()] ?: 0.0
    val missing = missingVolumes[selected.toEpochDay()] ?: 0
    fun select(date: LocalDate) { selectedRaw = date.toString(); monthRaw = YearMonth.from(date).toString() }
    PageScaffold("运动日历", { nav.popBackStack() }, actions = {
        AppTextButton(onClick = { compact = !compact; monthRaw = YearMonth.from(selected).toString() }) { Text(if (compact) "月视图" else "周视图") }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(onClick = {
                    if (compact) select(selected.minusWeeks(1)) else monthRaw = month.minusMonths(1).toString()
                }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (compact) "上一周" else "上个月") }
                Text(if (compact) "${weekStart.monthValue}月${weekStart.dayOfMonth}日 – ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日"
                    else "${month.year} / ${month.monthValue.toString().padStart(2, '0')}", Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                AppIconButton(onClick = {
                    if (compact) select(selected.plusWeeks(1)) else monthRaw = month.plusMonths(1).toString()
                }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (compact) "下一周" else "下个月") }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { WeekNames.forEach { Text(it.removePrefix("周"), Modifier.weight(1f),
                    color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            // Measure the actual names at each cell's width. Long names wrap completely;
            // each week takes its tallest day, and the collapse uses those same row heights.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val planStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
                val actionStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
                val capacityStyle = actionStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                val cellWidth = constraints.maxWidth / 7
                val weekHeights = remember(gridStart, rows, previews, detailed, capacityLabels, cellWidth, density.density, density.fontScale,
                    planStyle, actionStyle, capacityStyle, textMeasurer) {
                    val planWidth = (cellWidth - with(density) { 6.dp.roundToPx() }).coerceAtLeast(1)
                    val actionWidth = (cellWidth - with(density) { 2.dp.roundToPx() }).coerceAtLeast(1)
                    List(rows) { row ->
                        (0..6).maxOf { col ->
                            val dateKey = gridStart.plusDays((row * 7 + col).toLong()).toEpochDay()
                            val preview = previews[dateKey]
                            var height = 52.dp // 38dp date + 7dp padding above and below.
                            if (preview != null && preview.planName.isNotBlank()) {
                                val measured = textMeasurer.measure(preview.planName, planStyle, constraints = Constraints(maxWidth = planWidth))
                                height += with(density) { measured.size.height.toDp() } + 7.dp
                            }
                            if (detailed && preview != null && preview.actions.isNotEmpty()) {
                                val measured = textMeasurer.measure(preview.actions.joinToString("\n"), actionStyle,
                                    constraints = Constraints(maxWidth = actionWidth))
                                height += with(density) { measured.size.height.toDp() } + 3.dp
                            }
                            if (capacityLabels[dateKey] != null) {
                                val measured = textMeasurer.measure(capacityLabels.getValue(dateKey), capacityStyle,
                                    constraints = Constraints(maxWidth = actionWidth))
                                height += with(density) { measured.size.height.toDp() } + 3.dp
                            }
                            maxOf(height, with(density) { (48f + 16f * fontScale).dp })
                        }
                    }
                }
                val fullHeight = weekHeights.fold(0.dp) { sum, height -> sum + height }
                val precedingHeight = weekHeights.take(selectedRow).fold(0.dp) { sum, height -> sum + height }
                val rowOffset = with(density) { precedingHeight.toPx() }
                Box(Modifier.fillMaxWidth().height(fullHeight + (weekHeights[selectedRow] - fullHeight) * collapse)
                    .clipToBounds()) {
                    Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                        .offset { IntOffset(0, (-rowOffset * collapse).roundToInt()) }) {
                        repeat(rows) { row ->
                            Row(Modifier.fillMaxWidth().height(weekHeights[row])) {
                                repeat(7) { col ->
                                    val date = gridStart.plusDays((row * 7 + col).toLong())
                                    val preview = previews[date.toEpochDay()]
                                    DayCell(date, date == selected, data, Modifier.weight(1f),
                                        { select(date); compact = true }, outsideMonth = YearMonth.from(date) != month,
                                        volume = heatValues[date.toEpochDay()] ?: 0.0,
                                        measureLabel = if (cardioHeat) "有氧时长" else "力量容量", measureUnit = if (cardioHeat) "秒" else "kg",
                                        missingVolume = (missingVolumes[date.toEpochDay()] ?: 0) > 0,
                                        intensity = TrainingVolume.level(heatValues[date.toEpochDay()] ?: 0.0, peak),
                                        planName = preview?.planName.orEmpty(), actionNames = if (detailed) preview?.actions.orEmpty() else emptyList(),
                                        planStyle = planStyle, actionStyle = actionStyle, capacityStyle = capacityStyle,
                                        capacityLabel = capacityLabels[date.toEpochDay()].orEmpty())
                                }
                            }
                        }
                    }
                }
            }
            val controlsHeight = with(LocalDensity.current) { (28f + 24f * fontScale).dp }
            Box(Modifier.fillMaxWidth().heightIn(min = controlsHeight)) {
                Row(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarMetricSwitch(cardioHeat, { cardioHeat = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    AppIconButton(onClick = { detailed = !detailed },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Green),
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = if (detailed) "切换为简洁视图" else "切换为详细视图"
                            stateDescription = if (detailed) "详细模式" else "简洁模式"
                        }) { CalendarDetailIcon(expand = !detailed) }
                }
            }
            Box(Modifier.fillMaxWidth().height(24.dp * (1f - collapse)).clipToBounds()) {
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (cardioHeat) "有氧时长" else "力量容量", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text("低", color = Muted, fontSize = 10.sp)
                        VolumeColors.drop(1).forEach { color -> Box(Modifier.size(12.dp).background(color, CircleShape)) }
                        Text("高", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(84.dp * (1f - collapse)).clipToBounds()) {
                Row(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                    .padding(top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Metric("${monthWorkouts.size}", "本月训练")
                    Metric("${monthFoods.map { it.dateEpochDay }.distinct().size}", "饮食记录天数", color = Orange)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Line)
            if (tab != "workout" || workouts.isEmpty()) {
                Text(dateLabel(selected), color = Color(0xFF111111),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSegment(tab == "workout", { tab = "workout" }, label = { Text("运动") })
                AppSegment(tab == "food", { tab = "food" }, label = { Text("饮食") })
            }
            if (tab == "workout") {
                if (workouts.isNotEmpty()) {
                    key(selectedRaw) { WorkoutDayCard(workouts) { workoutDetail = it } }
                    if (missing > 0) Text("${missing} 条旧记录缺少可计算的重量和次数，未计入容量。", color = Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (workouts.isEmpty()) Text("这天还没有训练记录。", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
                AppTextButton(onClick = { nav.navigate(Routes.HISTORY) }, contentPadding = PaddingValues(0.dp)) { Text("查看所有训练 →") }
            } else {
                Text("共 ${foodNumber(foods.sumOf { it.calories })} kcal", color = Orange, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 12.dp))
                if (foods.isEmpty()) Text("这天还没有饮食记录。", color = Muted)
                foods.forEach { food -> ActionRow(food.name, "${foodNumber(food.calories)} kcal",
                    { detail = food.name to ("热量 ${foodNumber(food.calories)} kcal\n蛋白质 ${food.proteinGrams?.let(::foodNumber) ?: "—"} g\n碳水 ${food.carbsGrams?.let(::foodNumber) ?: "—"} g\n脂肪 ${food.fatGrams?.let(::foodNumber) ?: "—"} g") }, color = Orange) }
                AppTextButton(onClick = {
                    nav.currentBackStackEntry?.savedStateHandle?.set("dietDay", selected.toEpochDay())
                    nav.navigate(Routes.FOOD_HISTORY)
                }, contentPadding = PaddingValues(0.dp)) { Text("饮食日历 →") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    workoutDetail?.let { record -> WorkoutRecordDetails(record, vm) { workoutDetail = null } }
    detail?.let { item ->
        AppModalBottomSheet(onDismissRequest = { detail = null }, containerColor = Paper) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text(item.first, style = MaterialTheme.typography.headlineMedium)
                Text(item.second, Modifier.padding(top = 18.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private data class DayTrainingPreview(val planName: String, val actions: List<String>)

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, data: LogData, modifier: Modifier, select: () -> Unit,
    outsideMonth: Boolean = false, volume: Double = 0.0, missingVolume: Boolean = false, intensity: Int = 0,
    planName: String = "", actionNames: List<String> = emptyList(),
    planStyle: TextStyle, actionStyle: TextStyle, capacityStyle: TextStyle, capacityLabel: String = "",
    measureLabel: String = "力量容量", measureUnit: String = "kg") {
    val workout = data.workouts.any { it.dateEpochDay == date.toEpochDay() }
    val food = data.foods.any { it.dateEpochDay == date.toEpochDay() }
    Column(modifier.fillMaxHeight().graphicsLayer { alpha = if (outsideMonth) .35f else 1f }.clip(RoundedCornerShape(12.dp)).motionClickable(onClick = select)
        .semantics { contentDescription = dateLabel(date) + (if (workout) "，$measureLabel ${number(volume)} $measureUnit${if (missingVolume) "，部分记录容量未知" else ""}" else "") +
            (if (planName.isNotBlank()) "，计划 $planName" else "") +
            (if (actionNames.isNotEmpty()) "，动作 ${actionNames.joinToString("、")}" else "") + (if (food) "，有饮食" else "") }
        .padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        val level = if (outsideMonth) 0 else intensity
        Box(Modifier.size(38.dp).background(if (selected) Green else if (date == LocalDate.now()) FieldFill else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp).background(if (selected) Green else VolumeColors[level], CircleShape), contentAlignment = Alignment.Center) {
                Text("${date.dayOfMonth}", color = if (selected || level == 4) Color.White else if (outsideMonth) Muted else Ink, fontSize = 14.sp)
            }
        }
        if (planName.isNotBlank()) Text(planName,
            Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                .background(GreenTint, RoundedCornerShape(5.dp)).padding(horizontal = 2.dp, vertical = 2.dp),
            color = Green, style = planStyle, softWrap = true,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionNames.isNotEmpty()) Text(actionNames.joinToString("\n"),
            Modifier.fillMaxWidth().padding(horizontal = 1.dp), color = Muted, style = actionStyle,
            softWrap = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (capacityLabel.isNotEmpty()) Text(capacityLabel,
            Modifier.fillMaxWidth().padding(horizontal = 1.dp), color = Green, style = capacityStyle,
            softWrap = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}



