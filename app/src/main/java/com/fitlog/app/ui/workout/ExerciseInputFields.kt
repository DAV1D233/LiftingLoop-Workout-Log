package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*

@Composable
internal fun ExerciseInputFields(original: ExerciseSpec, minimumSets: Int = 1, enabled: Boolean = true,
    completedSets: Int = 0, alwaysPerSet: Boolean = false,
    onValue: (ExerciseSpec?) -> Unit) {
    if (original.isCardio) { CardioInputFields(original, minimumSets, enabled, onValue = onValue); return }
    var muscles by remember { mutableStateOf(original.muscleGroups.joinToString("、")) }
    var sets by rememberSaveable(original.name) { mutableStateOf(original.sets.toString()) }
    var reps by rememberSaveable(original.name) { mutableStateOf(original.reps.toString()) }
    var weight by rememberSaveable(original.name) { mutableStateOf(number(original.weight)) }
    var rest by rememberSaveable(original.name) { mutableStateOf(original.restSeconds.toString()) }
    val s = WorkoutParameterInput.parse(sets, minimumSets.toDouble(), 20.0, true)?.toInt()
    val r = WorkoutParameterInput.parse(reps, 1.0, 100.0, true)?.toInt()
    val w = WorkoutParameterInput.parse(weight, 0.0, 2000.0, false)
    val t = WorkoutParameterInput.parse(rest, 0.0, 600.0, true)?.toInt()
    var targets by remember { mutableStateOf<List<SetTarget>?>(original.setTargets) }
    val updated = if (s != null && r != null && w != null && t != null && targets != null)
        original.copy(sets = s, reps = r, weight = w, restSeconds = t, setTargets = targets!!, muscleGroup = muscles) else null
    SideEffect { onValue(updated) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            NumericField(sets, { sets = it }, "组数", "$minimumSets–20", s == null, enabled, Modifier.weight(1f))
            Text("×")
            NumericField(weight, { weight = it }, if (alwaysPerSet) { if (original.isBodyweight) "默认额外负重" else "默认重量 kg" } else if (original.isBodyweight) "额外负重" else "重量 kg",
                "0–2000", w == null, enabled, Modifier.weight(1f), true)
            Text("×")
            NumericField(reps, { reps = it }, if (alwaysPerSet) "默认次数" else "每组次数", "1–100", r == null, enabled, Modifier.weight(1f))
        }
        NumericField(rest, { rest = it }, "组间休息 秒", "0–600", t == null, enabled, Modifier.fillMaxWidth())
        PerSetFields(original.copy(sets = s ?: original.sets, weight = w ?: original.weight,
            reps = r ?: original.reps, restSeconds = t ?: original.restSeconds), completedSets, enabled, alwaysPerSet) { targets = it }
        MuscleGroupInput(original, enabled) { muscles = it }
    }
}

@Composable
internal fun PerSetFields(spec: ExerciseSpec, completedSets: Int = 0, enabled: Boolean = true, alwaysPerSet: Boolean = false,
    onValue: (List<SetTarget>?) -> Unit) {
    var separate by rememberSaveable(spec.name) { mutableStateOf(spec.setTargets.isNotEmpty()) }
    var raw by rememberSaveable(spec.name) { mutableStateOf(spec.setTargets.joinToString(";") {
        "${number(it.weight)}|${it.reps}|${it.restSeconds}"
    }) }
    val stored = if (raw.isBlank()) emptyList() else raw.split(';')
    val rows = List(spec.sets) { i -> stored.getOrNull(i)?.split('|')?.takeIf { it.size == 3 }
        ?: spec.targetAt(i).let { listOf(number(it.weight), "${it.reps}", "${it.restSeconds}") } }
    val values = rows.map { row ->
        val w = WorkoutParameterInput.parse(row[0], 0.0, 2000.0, false)
        val r = WorkoutParameterInput.parse(row[1], 1.0, 100.0, true)
        val t = WorkoutParameterInput.parse(row[2], 0.0, 600.0, true)
        if (w != null && r != null && t != null) SetTarget(w, r.toInt(), t.toInt()) else null
    }
    SideEffect { onValue(if (!alwaysPerSet && !separate) emptyList() else if (values.all { it != null }) values.filterNotNull() else null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!alwaysPerSet) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("逐组设置（递减组）", Modifier.weight(1f))
            AppSwitch(separate, { separate = it }, enabled = enabled)
        }
        if (alwaysPerSet || separate) {
            Text("以下逐组参数优先；连续递减时将组后休息设为 0 秒。", style = MaterialTheme.typography.bodySmall)
            rows.forEachIndexed { i, row ->
                fun change(column: Int, value: String) {
                    val next = rows.map { it.toMutableList() }.toMutableList()
                    next[i][column] = value
                    raw = next.joinToString(";") { it.joinToString("|") }
                }
                Text("第 ${i + 1} 组${if (i < completedSets) " · 已完成" else ""}", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(row[0], { change(0, it) }, if (spec.isBodyweight) "额外负重 kg" else "重量 kg",
                        "0–2000", WorkoutParameterInput.parse(row[0], 0.0, 2000.0, false) == null,
                        enabled && i >= completedSets, Modifier.weight(1f), true)
                    NumericField(row[1], { change(1, it) }, "次数", "1–100",
                        WorkoutParameterInput.parse(row[1], 1.0, 100.0, true) == null,
                        enabled && i >= completedSets, Modifier.weight(1f))
                    NumericField(row[2], { change(2, it) }, "组后休息 秒", "0–600",
                        WorkoutParameterInput.parse(row[2], 0.0, 600.0, true) == null,
                        enabled && i >= completedSets, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NumericField(value: String, change: (String) -> Unit, label: String, hint: String,
    error: Boolean, enabled: Boolean, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(value, { change(it.replace(',', '.').take(7)) }, modifier,
        label = { Text(label) }, supportingText = { Text(hint) }, singleLine = true, isError = error,
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}
