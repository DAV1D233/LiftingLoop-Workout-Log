package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import com.fitlog.app.data.planLabel
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.Routes
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

private val ScheduleDraftSaver = Saver<List<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toList() })

@Composable
fun TrainingScheduleScreen(nav: NavHostController, vm: AppViewModel, entry: NavBackStackEntry) {
    val prefs by vm.preferences.collectAsState()
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    val busy by vm.busy.collectAsState()
    var mode by rememberSaveable { mutableStateOf(if (prefs.cycle.isEmpty() && prefs.week.all { it.isBlank() }) "cycle" else prefs.scheduleMode) }
    var cycle by rememberSaveable(stateSaver = ScheduleDraftSaver) { mutableStateOf(prefs.cycle) }
    var week by rememberSaveable(stateSaver = ScheduleDraftSaver) { mutableStateOf(prefs.week) }
    var start by rememberSaveable { mutableLongStateOf(if (prefs.cycle.isEmpty()) today.toEpochDay() else prefs.cycleStartEpochDay) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var overview by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val days = if (mode == "cycle") cycle else week
    val index = selected.coerceIn(0, if (mode == "cycle") cycle.size else 6)
    val pending = mode == "cycle" && index == cycle.size
    val current = days.getOrElse(index) { "" }
    val title = if (mode == "cycle") "Day ${index + 1}" else WeekNames[index]
    val ready = if (mode == "cycle") cycle.isNotEmpty() else week.any { it.isNotBlank() }
    val choices = data.plans.map { "plan:${it.id}" to it.name }
    val exerciseNames by produceState<Map<String, List<String>>>(emptyMap(), data.plans) {
        try {
            value = choices.associate { (key, _) -> key to vm.scheduleExerciseNames(key) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { vm.message("动作列表加载失败，请重新进入训练周期。") }
    }
    fun assign(key: String) {
        if (mode == "cycle") {
            if (pending) { cycle = cycle + key; selected = cycle.size }
            else cycle = cycle.toMutableList().apply { this[index] = key }
        } else {
            week = week.toMutableList().apply { this[index] = key }
            selected = (index + 1).coerceAtMost(6)
        }
        choosing = false
    }
    val created by remember(entry) { entry.savedStateHandle.getStateFlow("createdSchedulePlan", "") }.collectAsState()
    val circles = rememberLazyListState()
    val reducedMotion = LocalGlassEnvironment.current.reduceMotion
    LaunchedEffect(index, mode, days.size, reducedMotion) {
        val first = circles.firstVisibleItemIndex
        val target = when {
            mode == "week" -> 0
            index < first -> index
            index >= first + 7 -> (index - 6).coerceAtLeast(0)
            else -> null
        }
        if (target != null) {
            if (reducedMotion || mode == "week") circles.scrollToItem(target)
            else circles.animateScrollToItem(target)
        }
    }
    LaunchedEffect(created) {
        if (created.isNotBlank()) {
            assign(created)
            entry.savedStateHandle.set("createdSchedulePlan", "")
        }
    }
    BackHandler(overview || busy) { if (!busy) overview = false }
    PageScaffold(if (overview) "训练周期概览" else "训练周期",
        back = { if (!busy) { if (overview) overview = false else nav.popBackStack() } },
        bottom = {
            PrimaryAction(if (busy) "保存中…" else if (overview) "保存并应用周期" else "周期概览 · ${days.size} 天",
                enabled = ready && !busy) {
                if (!overview) overview = true
                else vm.saveSchedule(mode, week, cycle, start) {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME); launchSingleTop = true }
                }
            }
        }) { padding ->
        if (overview) {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(if (mode == "cycle") "${cycle.size} 天一个周期" else "每周一个周期", style = MaterialTheme.typography.headlineMedium)
                    Text("${days.count { it.isNotBlank() && it != "rest" }} 个训练日 · ${days.count { it.isBlank() || it == "rest" }} 个休息日",
                        color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    if (mode == "cycle") {
                        Text("Day 1 从 ${dateLabel(LocalDate.ofEpochDay(start))} 开始", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (start != today.toEpochDay()) AppTextButton(onClick = { start = today.toEpochDay() }, enabled = !busy) { Text("改为今天从 Day 1 开始") }
                    } else Text("每周一到周日重复；未设置的日期按休息处理。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    ScheduleCalendarPreview(mode, week, cycle, start, today, busy,
                        label = { key -> planLabel(key, data.plans) })
                }
                item {
                    SectionHeading(if (mode == "week") "整周详细安排" else "周期详细安排", "点击任一天调整训练内容")
                }
                itemsIndexed(days) { i, key ->
                    AppCard(onClick = { selected = i; overview = false }, enabled = !busy,
                        color = CardFill, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ScheduleCircle(if (mode == "cycle") "Day ${i + 1}" else WeekNames[i], key == "rest" || key.isBlank(), false)
                            Column(Modifier.weight(1f)) {
                                Text(planLabel(key.ifBlank { "rest" }, data.plans), style = MaterialTheme.typography.titleMedium)
                                Text(if (key == "rest" || key.isBlank()) "恢复与休息" else exerciseNames[key].orEmpty().joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("→", color = Muted)
                        }
                    }
                }
            }
        } else Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSlidingSwitch(second = mode == "week", change = { weekly ->
                    val nextMode = if (weekly) "week" else "cycle"
                    if (mode != nextMode) { mode = nextMode; selected = 0 }
                }, firstLabel = "按天循环", secondLabel = "按周循环",
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(), enabled = !busy)
            }
            Text(if (mode == "cycle") "按顺序添加训练日或休息日，超过 7 天可左右滑动。" else "分别安排周一到周日，每周自动循环。",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val dayWidth = (maxWidth - 32.dp) / 7
                val circleSize = (dayWidth - 4.dp).coerceAtMost(52.dp)
            LazyRow(state = circles, modifier = Modifier, contentPadding = PaddingValues(horizontal = 16.dp),
                userScrollEnabled = mode == "cycle") {
                itemsIndexed(days) { i, key ->
                    Column(Modifier.width(dayWidth).heightIn(min = 88.dp).semantics { this.selected = index == i && !pending }
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !busy) { selected = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ScheduleCircle(if (mode == "cycle") "${i + 1}" else WeekNames[i], key == "rest" || key.isBlank(),
                            index == i && !pending, circleSize, enabled = !busy, onClick = { selected = i })
                        Text(planLabel(key, data.plans), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (index == i && !pending && key != "rest" && key.isNotBlank()) Green else Muted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp))
                    }
                }
                if (mode == "cycle") item {
                    Column(Modifier.width(dayWidth).heightIn(min = 88.dp).clickable(remember { MutableInteractionSource() }, indication = null, enabled = !busy) { selected = cycle.size }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(circleSize).padding(4.dp).fillMaxSize().clip(CircleShape).background(if (pending) GreenTint else FieldFill)
                            .clickable(enabled = !busy) { selected = cycle.size }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "添加一天", tint = if (pending) Green else Muted)
                        }
                        Text("添加", color = if (pending) Green else Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.headlineMedium)
                                    Text(if (current.isBlank()) "这一天练什么？" else planLabel(current, data.plans), color = Muted,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                                if (mode == "cycle" && !pending) AppIconButton(onClick = {
                                    cycle = cycle.filterIndexed { i, _ -> i != index }; selected = index.coerceAtMost(cycle.size)
                                }, enabled = !busy) { Icon(Icons.Default.Delete, "删除这一天", tint = Muted) }
                            }
                            if (current in listOf("push", "pull", "legs")) Text(
                                "这是旧版内置模板，不会跟随已保存计划更新。请更换为下方训练计划，之后名称和动作会同步更新。",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            exerciseNames[current].orEmpty().forEach { name ->
                                Text(name, color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            AppButton(onClick = { choosing = true }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenTint, contentColor = Green)) {
                                Text(if (current.isBlank() || current == "rest") "设置训练内容" else "更换训练内容")
                            }
                            AppOutlinedButton(onClick = { assign("rest") }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) {
                                Text(if (pending) "添加休息日" else "设为休息日")
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosing) AppAlertDialog(onDismissRequest = { choosing = false }, title = { Text("$title · 训练内容") },
        text = {
            Column {
            Text("与训练计划保持同步；修改计划后，周期会使用最新的名称和动作。", color = Muted,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
            if (choices.isEmpty()) Text("还没有训练计划，可以先新建一个。", color = Muted)
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(choices, key = { it.first }) { (key, label) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (current == key) GreenTint else CardFill).clickable { assign(key) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == key, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp), color = Ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        exerciseNames[key].orEmpty().forEach { name ->
                            Text(name, Modifier.padding(start = 8.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } }, confirmButton = { AppTextButton(onClick = { choosing = false; nav.navigate(Routes.SCHEDULE_DAY_EDITOR) }) { Text("自选动作，新建这一天") } },
        dismissButton = { AppTextButton(onClick = { choosing = false }) { Text("取消") } })
}

@Composable
private fun ScheduleCircle(label: String, rest: Boolean, selected: Boolean, diameter: Dp = 72.dp,
    enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val scale by animateFloatAsState(if (diameter < 60.dp && !selected) .84f else 1f,
        animationSpec = tween(if (LocalGlassEnvironment.current.reduceMotion) 0 else 180, easing = FastOutSlowInEasing), label = "selectedDaySize")
    Box(Modifier.size(diameter).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        .background(if (selected) Green else if (rest) FieldFill else GreenTint), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Paper else if (rest) Muted else Green,
            style = if (diameter < 60.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
    }
}


