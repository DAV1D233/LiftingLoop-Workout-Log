package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MuscleGroupInput(original: ExerciseSpec, enabled: Boolean, onValue: (String) -> Unit) {
    var selected by rememberSaveable(original.name) { mutableStateOf(original.muscleGroups.filter { it in MuscleGroups.choices }) }
    var extra by rememberSaveable(original.name) { mutableStateOf(original.muscleGroups.filterNot { it in MuscleGroups.choices }.joinToString("、")) }
    SideEffect { onValue((selected + MuscleGroups.parse(extra)).distinct().joinToString("、")) }
    Column {
        Text("锻炼部位 · 可多选", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MuscleGroups.choices.forEach { group ->
                AppFilterChip(group in selected, { selected = if (group in selected) selected - group else selected + group },
                    enabled = enabled, label = { Text(group) })
            }
        }
        OutlinedTextField(extra, { extra = it.take(160) }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
            label = { Text("其他肌群（选填）") }, supportingText = { Text("多个部位用、分隔；筛选任一部位都能找到动作") })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CardioInputFields(original: ExerciseSpec, minimum: Int = 1, enabled: Boolean = true,
    editMuscles: Boolean = true, onValue: (ExerciseSpec?) -> Unit) {
    var interval by rememberSaveable(original.name) { mutableStateOf(original.isInterval) }
    var mode by rememberSaveable(original.name) { mutableStateOf(original.cardioTarget.mode) }
    var rounds by rememberSaveable(original.name) { mutableStateOf(original.sets.toString()) }
    var minutes by rememberSaveable(original.name) { mutableStateOf((original.cardioTarget.durationSeconds / 60).toString()) }
    var seconds by rememberSaveable(original.name) { mutableStateOf((original.cardioTarget.durationSeconds % 60).toString()) }
    val kilometers = original.name in listOf("跑步", "骑行", "快走") && !original.isInterval
    var meters by rememberSaveable(original.name) { mutableStateOf(if (original.cardioTarget.distanceMeters > 0)
        number(original.cardioTarget.distanceMeters / if (kilometers) 1000 else 1) else "") }
    var reps by rememberSaveable(original.name) { mutableStateOf(original.reps.toString()) }
    var rest by rememberSaveable(original.name) { mutableStateOf(original.restSeconds.toString()) }
    var swimming by rememberSaveable(original.name) { mutableStateOf(original.name.contains("泳") || original.cardioTarget.poolLengthMeters > 0) }
    var pool by rememberSaveable(original.name) { mutableStateOf(number(original.cardioTarget.poolLengthMeters.takeIf { it > 0 } ?: 25.0)) }
    var stroke by rememberSaveable(original.name) { mutableStateOf(original.cardioTarget.stroke.ifBlank { "自由泳" }) }
    var muscles by remember { mutableStateOf(original.muscleGroups.joinToString("、")) }
    var more by rememberSaveable { mutableStateOf(false) }
    val roundValue = if (interval) rounds.toIntOrNull()?.takeIf { it in minimum..20 } else 1.takeIf { minimum <= 1 }
    val minValue = minutes.toIntOrNull()?.takeIf { it in 0..1440 }
    val secValue = seconds.toIntOrNull()?.takeIf { it in 0..59 }
    val duration = if (mode != "计时") 0 else if (minValue != null && secValue != null)
        (minValue * 60 + secValue).takeIf { it in 1..86400 } else null
    val distance = if (meters.isBlank()) 0.0 else meters.replace(',', '.').toDoubleOrNull()
        ?.times(if (kilometers) 1000 else 1)?.takeIf { it.isFinite() && it in 0.0..1000000.0 }
    val count = if (mode == "计次") reps.toIntOrNull()?.takeIf { it in 1..100 } else original.reps
    val pause = if (interval) rest.toIntOrNull()?.takeIf { it in 0..600 } else 0
    val poolValue = pool.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 1000 }
    val valid = roundValue != null && pause != null && duration != null && distance != null && count != null &&
        (mode != "距离" || distance > 0) && (!swimming || poolValue != null)
    val updated = if (valid) original.copy(sets = roundValue!!, restSeconds = pause!!, weight = 0.0, isBodyweight = false,
        reps = count!!, setTargets = emptyList(), muscleGroup = muscles,
        cardioTarget = CardioTarget(mode, duration!!, distance!!, if (swimming) poolValue!! else 0.0,
            if (swimming) stroke else "", if (interval) "interval" else "continuous").normalized()) else null
    SideEffect { onValue(updated) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppSlidingSwitch(interval, {
            interval = it
            mode = if (it && mode !in listOf("计时", "计次")) "计时" else if (!it && mode == "计次") "自由" else mode
            if (it && rounds.toIntOrNull().let { n -> n == null || n < minimum }) rounds = minimum.coerceAtLeast(3).toString()
        }, "连续运动", "间歇训练", Modifier.fillMaxWidth(), enabled = enabled && minimum <= 1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (interval) listOf("计时" to "按时间", "计次" to "按次数", "距离" to "按距离")
                else listOf("自由" to "自由计时", "计时" to "目标时长", "距离" to "目标距离")).forEach { (value, label) ->
                AppFilterChip(mode == value, { if (mode != value && value != "距离") meters = ""; mode = value }, enabled = enabled, label = { Text(label) })
            }
        }
        if (mode == "计时") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (interval) listOf(20, 30, 45, 60) else listOf(600, 1200, 1800)).forEach { total ->
                    AppFilterChip(duration == total, { minutes = (total / 60).toString(); seconds = (total % 60).toString() },
                        enabled = enabled, label = { Text(if (interval) "$total 秒" else "${total / 60} 分") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioNumber(minutes, { minutes = it }, if (interval) "每轮运动 · 分" else "目标 · 分", enabled, duration == null, Modifier.weight(1f))
                CardioNumber(seconds, { seconds = it }, "秒", enabled, duration == null, Modifier.weight(1f))
            }
        }
        if (mode == "距离") CardioNumber(meters, { meters = it }, "目标距离 / ${if (kilometers) "公里" else "米"}",
            enabled, distance == null || distance <= 0, decimal = true)
        if (interval) {
            if (mode == "计次") CardioNumber(reps, { reps = it }, "每轮次数", enabled, count == null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioNumber(rounds, { rounds = it }, "轮数", enabled, roundValue == null, Modifier.weight(1f))
                CardioNumber(rest, { rest = it }, "轮间休息 / 秒", enabled, pause == null, Modifier.weight(1f))
            }
            Text(if (mode == "计时") "运动与休息自动切换，完成后统一确认数据" else "每完成一轮点击一次，轮间休息自动计时",
                style = MaterialTheme.typography.bodySmall, color = com.fitlog.app.ui.theme.Muted)
        }
        if (swimming) {
            Text("泳池设置", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50).forEach { length -> AppFilterChip(poolValue == length.toDouble(), { pool = "$length" },
                    enabled = enabled, label = { Text("$length 米池") }) }
            }
            CardioNumber(pool, { pool = it }, "泳池长度 / 米", enabled, poolValue == null, decimal = true)
            OutlinedTextField(stroke, { stroke = it.take(30) }, Modifier.fillMaxWidth(), enabled = enabled, singleLine = true, label = { Text("泳姿") })
            if (distance != null && distance > 0 && poolValue != null) Text("${number(distance / poolValue)} 趟（单程）", style = MaterialTheme.typography.bodySmall)
        }
        AppTextButton({ more = !more }, enabled = enabled) { Text(if (more) "收起更多设置" else "更多设置") }
        if (more) {
            if (mode != "距离") CardioNumber(meters, { meters = it }, "${if (interval) "每轮" else "目标"}距离（可不填）/ ${if (kilometers) "公里" else "米"}", enabled, distance == null, decimal = true)
            AppFilterChip(swimming, { swimming = !swimming }, enabled = enabled, label = { Text("泳池训练") })
            if (editMuscles) MuscleGroupInput(original, enabled) { muscles = it }
        }
    }
}

@Composable
internal fun CardioNumber(value: String, change: (String) -> Unit, label: String, enabled: Boolean, error: Boolean,
    modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(value, { change(it.take(10)) }, modifier.fillMaxWidth(), enabled = enabled, isError = error, singleLine = true,
        label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}
