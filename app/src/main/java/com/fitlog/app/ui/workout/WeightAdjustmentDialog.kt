package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.number
import kotlin.math.floor
import kotlin.math.sqrt

@Composable
fun WeightAdjustmentDialog(title: String, weight: Double, enabled: Boolean,
    adjust: (Double) -> Unit, setWeight: (Double) -> Unit, dismiss: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = input.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2000.0 }
    val focus = remember { FocusRequester() }
    val displayWeight = if (editing) parsed ?: weight else weight
    fun applyInput() {
        if (enabled && parsed != null) { setWeight(parsed); editing = false }
    }
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
    AppAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            WeightDumbbell(displayWeight)
            if (editing) {
                OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("重量 / kg") }, singleLine = true, enabled = enabled,
                    isError = input.isNotEmpty() && parsed == null,
                    supportingText = { Text("可输入 0–2000 kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyInput() }))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppTextButton(onClick = { editing = false }) { Text("取消输入") }
                    AppTextButton(onClick = { applyInput() }, enabled = enabled && parsed != null) { Text("应用") }
                }
            } else {
                AppTextButton(onClick = { input = number(weight); editing = true }, enabled = enabled) {
                    Text(number(weight), fontSize = 32.sp)
                    Text(" kg", fontSize = 14.sp)
                }
                Text("点击数字手动输入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                listOf(2.5, 0.5).forEach { step ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppOutlinedButton(onClick = { adjust(-step) }, enabled = enabled && weight > 0,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("−${number(step)}") }
                        AppOutlinedButton(onClick = { adjust(step) }, enabled = enabled && weight < 2000,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("+${number(step)}") }
                    }
                }
            }
        }
    }, confirmButton = {
        AppTextButton(onClick = { if (editing) { if (parsed != null && enabled) { setWeight(parsed); dismiss() } } else dismiss() },
            enabled = !editing || (enabled && parsed != null)) { Text("完成") }
    })
}

@Composable
private fun WeightDumbbell(weight: Double) {
    val plates = remember(weight) {
        var remaining = weight / 2
        buildList {
            listOf(20.0, 10.0, 5.0, 2.5, 1.25, .5, .25).forEach { denomination ->
                val count = floor((remaining + .000001) / denomination).toInt()
                repeat(count) { add(denomination) }
                remaining -= count * denomination
            }
            if (remaining > .000001) add(remaining)
        }
    }
    val heightScale by animateFloatAsState((weight / 40).coerceIn(0.0, 1.0).toFloat(),
        if (LocalGlassEnvironment.current.reduceMotion) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(), label = "dumbbellWeight")
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = .86f)
    val visible = plates.take(8)
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics {
        contentDescription = "${number(weight)} kg 哑铃重量示意，每侧 ${plates.size} 片"
    }) {
        val cy = size.height / 2
        val center = size.width / 2
        val graphicWidth = minOf(size.width * .88f, 240.dp.toPx())
        val gripWidth = minOf(48.dp.toPx(), graphicWidth * .24f)
        val gripHalf = gripWidth / 2
        val gap = 4.dp.toPx()
        val innerGap = 5.dp.toPx()
        val availablePerSide = (graphicWidth - gripWidth) / 2 - innerGap
        val plateWidth = minOf(22.dp.toPx(),
            (availablePerSide - (visible.size - 1).coerceAtLeast(0) * gap) / visible.size.coerceAtLeast(1))
        val handleHeight = 14.dp.toPx()
        drawRoundRect(ink, Offset(center - gripHalf, cy - handleHeight / 2),
            Size(gripWidth, handleHeight), CornerRadius(1.dp.toPx()))
        val largest = visible.firstOrNull() ?: 1.0
        visible.forEachIndexed { i, plate ->
            val h = (72 + 8 * heightScale).dp.toPx() * (.4f + .6f * sqrt(plate / largest).toFloat())
            val distance = gripHalf + innerGap + i * (plateWidth + gap)
            for (side in listOf(-1, 1)) {
                val x = if (side > 0) center + distance else center - distance - plateWidth
                drawRoundRect(ink, Offset(x, cy - h / 2), Size(plateWidth, h), CornerRadius(plateWidth * .4f))
            }
        }
    }
}

