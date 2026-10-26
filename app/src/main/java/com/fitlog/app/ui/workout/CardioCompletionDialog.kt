package com.fitlog.app.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardioCompletionDialog(spec: ExerciseSpec, elapsed: Int, busy: Boolean, dismiss: () -> Unit,
    reviewRounds: Int = 1, initialDistance: Double = spec.cardioTarget.distanceMeters,
    initialReps: Int = spec.reps, confirmLabel: String = "保存记录", dismissLabel: String = "继续训练",
    complete: (Int, Double, Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf((elapsed / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((elapsed % 60).toString()) }
    val kilometers = spec.name in listOf("跑步", "骑行", "快走")
    val unit = if (kilometers) 1000.0 else 1.0
    var distance by rememberSaveable { mutableStateOf(if (initialDistance > 0) number(initialDistance / unit) else "") }
    var reps by rememberSaveable { mutableStateOf(initialReps.toString()) }
    val min = minutes.toIntOrNull()?.takeIf { it in 0..(1440 * reviewRounds) }
    val sec = seconds.toIntOrNull()?.takeIf { it in 0..59 }
    val duration = if (min != null && sec != null) (min * 60 + sec).takeIf { it <= 86400 * reviewRounds } else null
    val meters = if (distance.isBlank()) 0.0 else distance.replace(',', '.').toDoubleOrNull()?.times(unit)
        ?.takeIf { it.isFinite() && it in 0.0..(1000000.0 * reviewRounds) }
    val count = if (spec.cardioTarget.mode == "计次") reps.toIntOrNull()?.takeIf { it in 1..(100 * reviewRounds) } else 0
    AppModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(spec.name, style = MaterialTheme.typography.headlineMedium)
            Text(if (spec.isInterval) "完成 $reviewRounds 轮 · 确认实际数据" else "确认实际完成的数据", color = Muted)
            AppCard(color = CardFill) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("有效运动时间", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardioNumber(minutes, { minutes = it }, "分钟", !busy, duration == null, Modifier.weight(1f))
                        CardioNumber(seconds, { seconds = it }, "秒", !busy, duration == null, Modifier.weight(1f))
                    }
                    CardioNumber(distance, { distance = it }, "实际距离 / ${if (kilometers) "公里" else "米"}（可不填）", !busy,
                        meters == null, decimal = true)
                    if (spec.cardioTarget.mode == "计次") CardioNumber(reps, { reps = it }, "实际总次数", !busy, count == null)
                    if (meters != null && spec.cardioTarget.poolLengthMeters > 0) {
                        StepControl("单程趟数", number(meters / spec.cardioTarget.poolLengthMeters),
                            { distance = number((meters - spec.cardioTarget.poolLengthMeters).coerceAtLeast(0.0) / unit) },
                            { distance = number((meters + spec.cardioTarget.poolLengthMeters).coerceAtMost(1000000.0 * reviewRounds) / unit) }, !busy)
                    }
                    if (meters != null && duration != null) cardioPerformance(spec.name, duration, meters,
                        spec.cardioTarget.poolLengthMeters > 0 || spec.name.contains("泳"))?.let { Text(it, color = Green) }
                }
            }
            PrimaryAction(if (busy) "保存中…" else confirmLabel,
                !busy && duration != null && meters != null && count != null) { complete(duration!!, meters!!, count!!) }
            AppTextButton(dismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(dismissLabel) }
        }
    }
}
