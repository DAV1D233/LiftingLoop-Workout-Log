package com.fitlog.app.ui.components

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.BodyWeight
import com.fitlog.app.domain.number
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.settings.MiCloudDialog
import com.fitlog.app.ui.theme.*
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightDialog(vm: AppViewModel, dismiss: () -> Unit) {
    val prefs by vm.preferences.collectAsState()
    val busy by vm.busy.collectAsState()
    val scale by vm.mijia.values.collectAsState()
    var text by rememberSaveable { mutableStateOf(prefs.bodyWeightKg?.let(::number).orEmpty()) }
    var lastWeight by rememberSaveable { mutableStateOf(prefs.bodyWeightKg) }
    var scaleInfo by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Automatic synchronization may refresh an untouched field, never a manual draft.
    LaunchedEffect(prefs.bodyWeightKg) {
        if (text == lastWeight?.let(::number).orEmpty()) text = prefs.bodyWeightKg?.let(::number).orEmpty()
        lastWeight = prefs.bodyWeightKg
    }
    val parsed = BodyWeight.parse(text)
    val editable = !busy && !scale.busy
    fun adjust(amount: Double) {
        parsed?.let { weight ->
            text = number(BigDecimal.valueOf(weight).add(BigDecimal.valueOf(amount))
                .toDouble().coerceIn(1.0, 500.0))
        }
    }
    if (!scaleInfo) AppModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, containerColor = Paper,
        sheetState = sheet) {
        WindowBackgroundBlur(enabled = !prefs.reduceTransparency)
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("我的体重", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                    color = Ink, fontWeight = FontWeight.Bold)
                AppIconButton(onClick = dismiss, enabled = !busy) { Icon(Icons.Default.Close, "关闭体重面板") }
            }
            AppCard(shape = RoundedCornerShape(12.dp), color = CardFill) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("当前体重", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = maxWidth < 300.dp
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            WeightChange(.1, -1, compact, editable && parsed != null && parsed > 1.0) { adjust(-.1) }
                            WeightChange(.5, -1, compact, editable && parsed != null && parsed > 1.0) { adjust(-.5) }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val measurer = rememberTextMeasurer()
                                    val density = LocalDensity.current
                                    val style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium)
                                    val measured = measurer.measure(text.ifEmpty { "填写" }, style, softWrap = false).size.width
                                    val fit = (with(density) { maxWidth.toPx() } / measured.coerceAtLeast(1)).coerceAtMost(1f)
                                    BasicTextField(value = text, onValueChange = { text = it.replace(',', '.').take(12) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "体重，千克，点击输入" },
                                        enabled = editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        cursorBrush = SolidColor(Green),
                                        textStyle = style.copy(color = Ink, fontSize = 40.sp * fit, textAlign = TextAlign.Center),
                                        decorationBox = { field -> Box(contentAlignment = Alignment.Center) {
                                            if (text.isEmpty()) Text("填写", color = Muted, fontSize = 40.sp * fit)
                                            field()
                                        } })
                                }
                                Text("kg", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            WeightChange(.5, 1, compact, editable && parsed != null && parsed < 500.0) { adjust(.5) }
                            WeightChange(.1, 1, compact, editable && parsed != null && parsed < 500.0) { adjust(.1) }
                        }
                    }
                    Text(if (text.isNotBlank() && parsed == null) "请输入 1–500 kg 的有效体重" else "点击数字可手动输入",
                        color = if (text.isNotBlank() && parsed == null) MaterialTheme.colorScheme.error else Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            AppCard(shape = RoundedCornerShape(12.dp), color = CardFill) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlinedButton(onClick = {
                        val before = vm.preferences.value.bodyWeightKg
                        scope.launch {
                            vm.mijia.sync()
                            val updated = vm.preferences.value.bodyWeightKg
                            if (updated != before) { text = updated?.let(::number).orEmpty(); lastWeight = updated }
                        }
                    }, enabled = editable,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (scale.busy) "正在同步…" else "同步体重秤数据")
                    }
                    Text(scale.status,
                        color = Muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    AppTextButton(onClick = { scaleInfo = true }, enabled = editable, contentPadding = PaddingValues(0.dp)) {
                        Text("体重秤详细设置 ↗", color = Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("自重动作自动使用体重，额外负重另外记录。", color = Muted, style = MaterialTheme.typography.bodySmall)
            PrimaryAction(if (busy) "保存中…" else "保存体重", enabled = editable && parsed != null) {
                parsed?.let { vm.saveBodyWeight(it, dismiss) }
            }
        }
    }
    if (scaleInfo) MiCloudDialog(vm) {
        scaleInfo = false
    }
}


@Composable
private fun WeightChange(amount: Double, direction: Int, compact: Boolean, enabled: Boolean, action: () -> Unit) {
    val diameter = if (amount == .5) { if (compact) 40.dp else 44.dp } else { if (compact) 32.dp else 36.dp }
    Column(Modifier.width(if (compact) 44.dp else 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AppTonalIconButton(onClick = action, enabled = enabled, shape = CircleShape,
            modifier = Modifier.size(diameter).semantics { contentDescription = "${if (direction < 0) "减少" else "增加"} ${number(amount)} 千克" }) {
            if (direction < 0) {
                val tint = LocalContentColor.current
                Canvas(Modifier.size(18.dp)) {
                    drawLine(tint, Offset(2.dp.toPx(), center.y), Offset(size.width - 2.dp.toPx(), center.y), strokeWidth = 2.dp.toPx())
                }
            } else Icon(Icons.Default.Add, null, Modifier.size(20.dp))
        }
        Text("${number(amount)} kg", color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
