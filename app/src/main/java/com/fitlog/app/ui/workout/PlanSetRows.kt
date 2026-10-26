package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.theme.*

/** Shared plan/custom editor. Ordinals describe order; only actual parameters are interactive. */
@Composable
internal fun PlanSetRows(spec: ExerciseSpec, enabled: Boolean = true, completedSets: Int = 0, change: (ExerciseSpec) -> Unit) {
    var editing by rememberSaveable(spec.name) { mutableStateOf<Parameter?>(null) }
    var selected by rememberSaveable(spec.name) { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(spec.sets) { index ->
            val target = spec.targetAt(index)
            val editable = enabled && index >= completedSets
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}".padStart(2, '0'), color = Muted,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                    listOf(Parameter.WEIGHT, Parameter.REPS).forEach { field ->
                        AppParameterSurface(onClick = { selected = index; editing = field }, enabled = editable,
                            modifier = Modifier.weight(1f), color = FieldFill, shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (field == Parameter.REPS) "次数" else if (spec.isBodyweight) "额外负重 kg" else "重量 kg",
                                    color = Muted, style = MaterialTheme.typography.labelSmall)
                                Text(if (field == Parameter.WEIGHT) number(target.weight) else "${target.reps}",
                                    color = Green, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    AppIconButton(onClick = { change(PlanSets.remove(spec, index)) }, enabled = editable && spec.sets > 1) {
                        Icon(Icons.Default.Delete, "删除第 ${index + 1} 组", tint = Muted)
                    }
                }
                AppTextButton(onClick = { selected = index; editing = Parameter.REST }, enabled = editable,
                    contentPadding = PaddingValues(start = 32.dp)) {
                    Text(if (index < completedSets) "已完成 · 休息 ${target.restSeconds} 秒" else "组后休息 · ${target.restSeconds} 秒", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        AppTextButton(onClick = { change(PlanSets.resize(spec, spec.sets + 1)) }, enabled = enabled && spec.sets < 20) {
            Text("＋ 添加一组")
        }
    }
    editing?.let { field ->
        val index = selected.coerceIn(0, spec.sets - 1)
        val target = spec.targetAt(index)
        val current = when (field) {
            Parameter.WEIGHT -> target.weight
            Parameter.REPS -> target.reps.toDouble()
            else -> target.restSeconds.toDouble()
        }
        ParameterKeypad(field, current, dismiss = { editing = null },
            startWithWheel = field == Parameter.WEIGHT || field == Parameter.REPS,
            label = "第 ${index + 1} 组 · " + when (field) {
                Parameter.WEIGHT -> if (spec.isBodyweight) "额外负重 kg" else "重量 kg"
                Parameter.REPS -> "次数"
                else -> "组后休息 秒"
            }) { value ->
            change(PlanSets.update(spec, index, when (field) {
                Parameter.WEIGHT -> target.copy(weight = value)
                Parameter.REPS -> target.copy(reps = value.toInt())
                else -> target.copy(restSeconds = value.toInt())
            }))
            editing = null
        }
    }
}
