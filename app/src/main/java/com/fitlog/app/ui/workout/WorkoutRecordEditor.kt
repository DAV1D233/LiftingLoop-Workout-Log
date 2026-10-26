package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutRecordEditor(record: WorkoutRecord, vm: AppViewModel, saved: () -> Unit, back: () -> Unit) {
    val busy by vm.busy.collectAsState()
    val original = remember(record.id) { WorkoutLogEditing.parse(record.summary, record.totalSets) }
    var name by rememberSaveable(record.id) { mutableStateOf(record.planName) }
    var epochDay by rememberSaveable(record.id) { mutableLongStateOf(record.dateEpochDay) }
    var minutes by rememberSaveable(record.id) { mutableStateOf((record.durationSeconds / 60).toString()) }
    var seconds by rememberSaveable(record.id) { mutableStateOf((record.durationSeconds % 60).toString()) }
    var raw by rememberSaveable(record.id) { mutableStateOf(WorkoutRecordDraft.fromSets(original.orEmpty())) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmingSave by rememberSaveable(record.id) { mutableStateOf(false) }
    val rows = raw.chunked(WorkoutRecordDraft.WIDTH)
    val sets = WorkoutRecordDraft.decode(raw)
    val groups = WorkoutRecordDraft.groups(raw)
    val min = minutes.toLongOrNull()?.takeIf { it in 0..(Int.MAX_VALUE.toLong() / 60) }
    val sec = seconds.toIntOrNull()?.takeIf { it in 0..59 }
    val duration = if (min != null && sec != null) (min * 60 + sec).takeIf { it <= Int.MAX_VALUE }?.toInt() else null
    val validSets = original == null || (sets.isNotEmpty() && sets.all { it.valid() })
    val valid = name.isNotBlank() && duration != null && validSets
    fun update(row: Int, field: Int, value: String) { raw = WorkoutRecordDraft.update(raw, row, field, value) }
    AppModalBottomSheet(onDismissRequest = { if (!busy) back() }, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("编辑训练记录", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                AppTextButton(onClick = back, enabled = !busy) { Text("取消", color = Muted) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("训练名称", color = Muted, style = MaterialTheme.typography.labelSmall)
                            BasicTextField(name, { name = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .semantics { contentDescription = "训练名称" }, enabled = !busy, singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(color = if (name.isBlank()) MaterialTheme.colorScheme.error else Ink),
                                cursorBrush = SolidColor(Green))
                        }
                        AppParameterSurface(onClick = { choosingDate = true }, enabled = !busy, color = Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("训练日期", color = Muted, style = MaterialTheme.typography.labelSmall)
                                    Text(dateLabel(LocalDate.ofEpochDay(epochDay)), style = MaterialTheme.typography.bodyLarge)
                                }
                                Icon(Icons.Default.DateRange, "选择训练日期", tint = Green, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("训练时长", color = Muted, style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecordNumber(minutes, { minutes = it }, "训练时长，分钟", min == null, !busy, Modifier.width(72.dp), fill = CardFill, compact = true)
                                Text("分", color = Muted)
                                RecordNumber(seconds, { seconds = it }, "训练时长，秒，0至59", sec == null, !busy, Modifier.width(56.dp), fill = CardFill, compact = true)
                                Text("秒", color = Muted)
                            }
                        }
                    }
                }
                if (original == null) item {
                    Text("这条旧记录无法逐组解析，保留原始动作明细，可修改名称、日期和时长。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(record.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                } else {
                    itemsIndexed(groups, key = { _, indices -> rows[indices.first()][8] }) { ordinal, indices ->
                        val first = rows[indices.first()]
                        val groupId = first[8]
                        val cardio = sets[indices.first()].isCardio
                        var more by remember { mutableStateOf(false) }
                        AppCard(color = CardFill, shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextField(first[0], { raw = WorkoutRecordDraft.rename(raw, groupId, it) }, Modifier.weight(1f),
                                        enabled = !busy, singleLine = true, label = { Text("动作 ${(ordinal + 1).toString().padStart(2, '0')}${if (cardio) " · 有氧" else ""}") },
                                        textStyle = MaterialTheme.typography.titleMedium, colors = recordFieldColors(), isError = first[0].isBlank())
                                    Box {
                                        AppIconButton(onClick = { more = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "动作操作", tint = Muted) }
                                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                            DropdownMenuItem(text = { Text("删除这个动作") }, onClick = {
                                                more = false; raw = WorkoutRecordDraft.removeAction(raw, groupId)
                                            }, enabled = !busy)
                                        }
                                    }
                                }
                                if (!cardio) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("组", Modifier.width(28.dp), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.labelSmall)
                                        Text("重量 kg", Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.width(18.dp))
                                        Text("次数", Modifier.weight(1f), textAlign = TextAlign.Center, color = Muted, style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.width(48.dp))
                                    }
                                }
                                indices.forEachIndexed { groupOrdinal, index ->
                                    val row = rows[index]
                                    val value = sets[index]
                                    if (cardio) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("第 ${groupOrdinal + 1} 段", Modifier.weight(1f), color = Green, style = MaterialTheme.typography.labelLarge)
                                                RecordRemove("移除第 ${groupOrdinal + 1} 段", !busy) { raw = WorkoutRecordDraft.remove(raw, index) }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text("实际时长 / 秒", color = Muted, style = MaterialTheme.typography.labelSmall)
                                                    RecordNumber(row[4], { update(index, 4, it) }, "第 ${groupOrdinal + 1} 段实际时长，秒", value.durationSeconds !in 0..86400, !busy)
                                                }
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Text("实际距离 / 米", color = Muted, style = MaterialTheme.typography.labelSmall)
                                                    RecordNumber(row[5], { update(index, 5, it) }, "第 ${groupOrdinal + 1} 段实际距离，米", !value.distanceMeters.isFinite() || value.distanceMeters !in 0.0..1000000.0, !busy, decimal = true)
                                                }
                                            }
                                            TextField(row[2], { update(index, 2, it) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                                                label = { Text("次数 · 非计次可为 0") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = recordFieldColors(Paper), shape = RoundedCornerShape(12.dp), isError = value.reps !in 0..100)
                                            if (value.stroke.isNotBlank() || value.poolLengthMeters > 0) {
                                                TextField(row[6], { update(index, 6, it) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                                                    label = { Text("泳姿") }, colors = recordFieldColors(Paper), shape = RoundedCornerShape(12.dp))
                                                TextField(row[7], { update(index, 7, it) }, Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                                                    label = { Text("池长 / 米") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    colors = recordFieldColors(Paper), shape = RoundedCornerShape(12.dp),
                                                    isError = !value.poolLengthMeters.isFinite() || value.poolLengthMeters !in 0.0..1000.0)
                                            }
                                            if (groupOrdinal < indices.lastIndex) HorizontalDivider(color = Line, modifier = Modifier.padding(top = 6.dp))
                                        }
                                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.width(28.dp).height(30.dp).background(GreenTint, RoundedCornerShape(7.dp))
                                            .semantics { contentDescription = "第 ${groupOrdinal + 1} 组" }, contentAlignment = Alignment.Center) {
                                            Text("${groupOrdinal + 1}", color = Green, style = MaterialTheme.typography.labelLarge)
                                        }
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            RecordNumber(row[1], { update(index, 1, it) }, "第 ${groupOrdinal + 1} 组实际重量，公斤",
                                                !value.weight.isFinite() || value.weight !in 0.0..2500.0, !busy, Modifier.widthIn(max = 68.dp), decimal = true)
                                        }
                                        Text("×", Modifier.width(18.dp), textAlign = TextAlign.Center, color = Muted)
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            RecordNumber(row[2], { update(index, 2, it) }, "第 ${groupOrdinal + 1} 组次数，1至100",
                                                value.reps !in 1..100, !busy, Modifier.widthIn(max = 68.dp))
                                        }
                                        RecordRemove("移除第 ${groupOrdinal + 1} 组", !busy) { raw = WorkoutRecordDraft.remove(raw, index) }
                                    }
                                }
                                AppTextButton(onClick = { raw = WorkoutRecordDraft.appendSet(raw, groupId) }, enabled = !busy) {
                                    Text(if (cardio) "＋ 添加一段" else "＋ 添加一组")
                                }
                            }
                        }
                    }
                    item {
                        AppOutlinedButton(onClick = { adding = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加动作") }
                        if (sets.isEmpty()) Text("至少保留一组或一段记录", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        if (validSets && sets.any { !it.isCardio }) Text("力量容量 ${number(WorkoutLogEditing.volume(sets))} kg", color = Green,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                        if (validSets && sets.any { it.isCardio }) Text("有氧 ${cardioTime(sets.filter { it.isCardio }.sumOf { it.durationSeconds })} · ${number(sets.filter { it.isCardio }.sumOf { it.distanceMeters })} 米",
                            color = Green, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                PrimaryAction(if (busy) "保存中…" else "保存修改", valid && !busy) {
                    confirmingSave = true
                }
            }
        }
    }
    if (confirmingSave) AppAlertDialog(onDismissRequest = { if (!busy) confirmingSave = false },
        title = { Text("确认保存修改？") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name.trim(), style = MaterialTheme.typography.titleMedium)
                Text(dateLabel(LocalDate.ofEpochDay(epochDay)), color = Muted)
                Text(timeLabel(duration ?: 0) + if (original != null) " · ${sets.size} 组/段" else "", color = Muted)
                Text("确认后将更新这次历史记录。", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }, confirmButton = {
            AppTextButton(onClick = {
                vm.updateWorkout(record, name, LocalDate.ofEpochDay(epochDay), duration!!, if (original != null) sets else null) {
                    confirmingSave = false; saved()
                }
            }, enabled = valid && !busy) { Text(if (busy) "保存中…" else "确认修改") }
        }, dismissButton = { AppTextButton(onClick = { confirmingSave = false }, enabled = !busy) { Text("继续编辑") } })
    if (choosingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86400000L)
        AppDatePickerDialog(onDismissRequest = { choosingDate = false }, confirmButton = {
            AppTextButton(onClick = { picker.selectedDateMillis?.let { epochDay = Math.floorDiv(it, 86400000L) }; choosingDate = false },
                enabled = picker.selectedDateMillis != null && !busy) { Text("确定") }
        }, dismissButton = { AppTextButton(onClick = { choosingDate = false }) { Text("取消") } }) { DatePicker(picker, colors = DatePickerDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, todayDateBorderColor = androidx.compose.ui.graphics.Color.Transparent)) }
    }
    if (adding) {
        var actionName by rememberSaveable { mutableStateOf("") }
        var cardio by rememberSaveable { mutableStateOf(false) }
        AppAlertDialog(onDismissRequest = { adding = false }, title = { Text("添加动作") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(actionName, { actionName = it }, label = { Text("动作名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppFilterChip(!cardio, { cardio = false }, label = { Text("力量") })
                    AppFilterChip(cardio, { cardio = true }, label = { Text("有氧") })
                }
            }
        }, confirmButton = { AppTextButton(onClick = {
            raw = WorkoutRecordDraft.appendAction(raw, actionName.trim(), cardio); adding = false
        }, enabled = actionName.isNotBlank() && !busy) { Text("添加") } },
            dismissButton = { AppTextButton(onClick = { adding = false }) { Text("取消") } })
    }
}

@Composable
private fun recordFieldColors(fill: Color = CardFill) = TextFieldDefaults.colors(
    focusedContainerColor = fill, unfocusedContainerColor = fill, disabledContainerColor = fill,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent)

@Composable
private fun RecordNumber(value: String, change: (String) -> Unit, label: String, error: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, decimal: Boolean = false, fill: Color = FieldFill, compact: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(value, { change(it.take(16)) }, modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 48.dp)
        .background(fill, shape).border(1.dp, if (error) MaterialTheme.colorScheme.error else CardOutline, shape)
        .semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = (if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium).copy(color = Ink, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        cursorBrush = SolidColor(Green), decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 12.dp), contentAlignment = Alignment.Center) { inner() }
        })
}

@Composable
private fun RecordRemove(label: String, enabled: Boolean, remove: () -> Unit) {
    AppIconButton(onClick = remove, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.Delete, label, tint = Muted, modifier = Modifier.size(16.dp))
    }
}

