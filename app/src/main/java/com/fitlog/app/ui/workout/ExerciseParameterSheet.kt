package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.ExerciseSpec
import com.fitlog.app.domain.number
import com.fitlog.app.domain.WorkoutParameterInput
import com.fitlog.app.domain.PlanSets
import com.fitlog.app.data.ExerciseJson
import com.fitlog.app.ui.components.PrimaryAction
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class Parameter(val label: String, val min: Double, val max: Double, val step: Double) {
    SETS("组数", 1.0, 20.0, 1.0), WEIGHT("重量 kg", 0.0, 2000.0, .5),
    REPS("每组次数", 1.0, 100.0, 1.0), REST("休息 秒", 0.0, 600.0, 15.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseParameterSheet(original: ExerciseSpec, dismiss: () -> Unit, save: (ExerciseSpec) -> Unit, initialSet: Int = 0) {
    val reducedMotion = LocalGlassEnvironment.current.reduceMotion
    if (original.isCardio) {
        var updated by remember { mutableStateOf<ExerciseSpec?>(original) }
        AppModalBottomSheet(onDismissRequest = dismiss, containerColor = Paper,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(original.name, style = MaterialTheme.typography.titleLarge)
                CardioInputFields(original, editMuscles = false) { updated = it }
                PrimaryAction("完成", updated != null) { updated?.let { save(it.normalized()) } }
            }
        }
        return
    }
    var muscles by remember { mutableStateOf(original.muscleGroups.joinToString("、")) }
    var targetsJson by rememberSaveable(original.name) { mutableStateOf(ExerciseJson.encodeTargets(PlanSets.explicit(original).setTargets)) }
    var selected by rememberSaveable(original.name, initialSet) { mutableIntStateOf(initialSet.coerceIn(0, original.sets - 1)) }
    val targets = ExerciseJson.decodeTargets(targetsJson)
    val group = selected.coerceIn(0, targets.lastIndex)
    val target = targets[group]
    val groupScroll = rememberLazyListState(initialFirstVisibleItemIndex = group)
    LaunchedEffect(group, targets.size, reducedMotion) {
        if (reducedMotion) groupScroll.scrollToItem(group) else groupScroll.animateScrollToItem(group)
    }
    fun draft() = original.copy(sets = targets.size, setTargets = targets)
    fun updateTarget(weight: Double = target.weight, reps: Int = target.reps, rest: Int = target.restSeconds) {
        targetsJson = ExerciseJson.encodeTargets(PlanSets.update(draft(), group, com.fitlog.app.domain.SetTarget(weight, reps, rest)).setTargets)
    }
    var keypad by rememberSaveable { mutableStateOf<Parameter?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(original.name, style = MaterialTheme.typography.titleLarge)
            Text("选择组别，分别调整重量、次数和组后休息。", color = Muted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            LazyRow(state = groupScroll, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(targets) { i, _ ->
                    AppFilterChip(group == i, { selected = i }, label = { Text("第 ${i + 1} 组") })
                }
            }
            key(group) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ParameterWheel(Parameter.SETS, targets.size.toDouble(), Modifier.weight(1f),
                        onChange = {
                            targetsJson = ExerciseJson.encodeTargets(PlanSets.resize(draft(), it.toInt()).setTargets)
                            selected = selected.coerceAtMost(it.toInt() - 1)
                        }, onTap = { keypad = Parameter.SETS })
                    ParameterWheel(Parameter.WEIGHT, target.weight, Modifier.weight(1f),
                        label = if (original.isBodyweight) "额外负重 kg" else "重量 kg",
                        onChange = { updateTarget(weight = it) }, onTap = { keypad = Parameter.WEIGHT })
                    ParameterWheel(Parameter.REPS, target.reps.toDouble(), Modifier.weight(1f),
                        label = "本组次数", onChange = { updateTarget(reps = it.toInt()) }, onTap = { keypad = Parameter.REPS })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                AppTextButton(onClick = { keypad = Parameter.REST }) { Text("本组后休息 · ${target.restSeconds} 秒", color = Muted) }
                AppTextButton(onClick = {
                    val updated = PlanSets.remove(draft(), group)
                    targetsJson = ExerciseJson.encodeTargets(updated.setTargets)
                    selected = group.coerceAtMost(updated.sets - 1)
                }, enabled = targets.size > 1) { Text("删除本组", color = Muted) }
            }
            MuscleGroupInput(original, true) { muscles = it }
            PrimaryAction("完成") { save(PlanSets.explicit(draft().copy(muscleGroup = muscles))) }
        }
    }
    keypad?.let { field ->
        val current = when (field) { Parameter.SETS -> targets.size.toDouble(); Parameter.WEIGHT -> target.weight
            Parameter.REPS -> target.reps.toDouble(); Parameter.REST -> target.restSeconds.toDouble() }
        ParameterKeypad(field, current, dismiss = { keypad = null },
            label = if (field == Parameter.WEIGHT && original.isBodyweight) "额外负重 kg" else field.label) { value ->
            when (field) {
                Parameter.SETS -> {
                    targetsJson = ExerciseJson.encodeTargets(PlanSets.resize(draft(), value.toInt()).setTargets)
                    selected = selected.coerceAtMost(value.toInt() - 1)
                }
                Parameter.WEIGHT -> updateTarget(weight = value)
                Parameter.REPS -> updateTarget(reps = value.toInt())
                Parameter.REST -> updateTarget(rest = value.toInt()) }
            keypad = null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParameterWheel(field: Parameter, value: Double, modifier: Modifier,
    label: String = field.label,
    onChange: (Double) -> Unit, onTap: () -> Unit) {
    val reducedMotion = LocalGlassEnvironment.current.reduceMotion
    val base = remember(field) { List(((field.max - field.min) / field.step).toInt() + 1) { field.min + it * field.step } }
    val extraValues = remember(field) { mutableStateListOf<Double>() }
    val values = remember(base, value, extraValues.toList()) {
        if (extraValues.isEmpty() && value in base) base else (base + extraValues + value).distinct().sorted()
    }
    SideEffect { if (value !in base && value !in extraValues) extraValues.add(value) }
    val initial = values.indexOf(value).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    val latestChange by rememberUpdatedState(onChange)
    val latestValues by rememberUpdatedState(values)
    val selectedIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: initial
        }
    }
    LaunchedEffect(value, values) {
        if (!state.isScrollInProgress && values.getOrNull(state.firstVisibleItemIndex) != value) state.scrollToItem(initial)
    }
    LaunchedEffect(state) {
        snapshotFlow {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index
        }.collect { index ->
            index?.let { latestValues.getOrNull(it)?.let(latestChange) }
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(44.dp).background(GreenTint, RoundedCornerShape(12.dp)))
            LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 44.dp)) {
                itemsIndexed(values, key = { _, item -> item }) { index, item ->
                    val selected = selectedIndex == index
                    Box(Modifier.fillMaxWidth().height(44.dp)
                        .semantics { contentDescription = "$label ${number(item)}，点击输入" }
                        .clickable { if (selected) onTap() else scope.launch { if (reducedMotion) state.scrollToItem(index) else state.animateScrollToItem(index) } },
                        contentAlignment = Alignment.Center) {
                        val longNumber = number(item).length > 5
                        Text(number(item), color = if (selected) Green else Muted.copy(alpha = .45f),
                            fontSize = if (selected) { if (longNumber) 20.sp else 26.sp } else { if (longNumber) 14.sp else 18.sp },
                            textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ParameterKeypad(field: Parameter, current: Double, dismiss: () -> Unit, label: String = field.label,
    startWithWheel: Boolean = false, confirm: (Double) -> Unit) {
    var input by rememberSaveable(field) { mutableStateOf(number(current)) }
    var replace by rememberSaveable(field) { mutableStateOf(true) }
    var wheelMode by rememberSaveable(field) { mutableStateOf(startWithWheel) }
    val parsed = WorkoutParameterInput.parse(input, field.min, field.max, field != Parameter.WEIGHT)
    val valid = parsed != null
    AppAlertDialog(onDismissRequest = dismiss, title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                if (wheelMode) {
                    ParameterWheel(field, parsed ?: current, Modifier.fillMaxWidth(), label = label,
                        onChange = { input = number(it); replace = true }, onTap = { wheelMode = false })
                    Text("上下滑动调整 · 点击数字手动输入", color = Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                } else {
                Text(input.ifBlank { "0" }, color = Green, style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
                Text("${number(field.min)} – ${number(field.max)}${if (field == Parameter.WEIGHT) " kg" else ""}",
                    color = if (valid) Muted else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            AppButton(onClick = {
                                if (key == "⌫") { input = input.dropLast(1); replace = false }
                                else {
                                    val base = if (replace) "" else input
                                    val next = if (key == "." && base.isEmpty()) "0." else base + key
                                    if (next.length <= 7 && next.count { it == '.' } <= 1 && next.substringAfter('.', "").length <= 2) {
                                        input = next; replace = false
                                    }
                                }
                            }, enabled = key != "." || field == Parameter.WEIGHT,
                                modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = GreenTint, contentColor = Green)) {
                                Text(key, fontSize = 20.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (startWithWheel) AppTextButton(onClick = { wheelMode = true }, enabled = valid) { Text("切回滚轮") }
                }
            }
        }, confirmButton = { AppTextButton(onClick = { parsed?.let(confirm) }, enabled = valid) { Text("确定") } },
        dismissButton = { AppTextButton(onClick = dismiss) { Text("取消") } })
}

