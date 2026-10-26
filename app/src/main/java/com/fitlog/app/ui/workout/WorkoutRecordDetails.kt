package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlog.app.data.WorkoutRecord
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutRecordDetails(record: WorkoutRecord, vm: AppViewModel, dismiss: () -> Unit) {
    var editing by rememberSaveable(record.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(record.id) { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val actions = remember(record.summary, record.totalSets) {
        WorkoutLogEditing.parse(record.summary, record.totalSets)?.let { sets ->
            WorkoutRecordDraft.groups(WorkoutRecordDraft.fromSets(sets)).map { group -> group.map { sets[it] } }
        }
    }
    if (editing) {
        WorkoutRecordEditor(record, vm, dismiss, { editing = false })
    } else AppModalBottomSheet(onDismissRequest = dismiss, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Eyebrow(dateLabel(LocalDate.ofEpochDay(record.dateEpochDay)), Green)
            Text(record.planName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
            Text("${record.countLabel()} · ${timeLabel(record.durationSeconds)}", color = Muted)
            if (record.cardioRounds > 0) Text(record.cardioLabel(), color = Green, modifier = Modifier.padding(top = 6.dp))
            if (record.totalSets > record.cardioRounds) TrainingVolume.resolve(record.volumeKg, record.summary, record.totalSets)?.let {
                Text("训练容量 ${number(it)} kg", color = Green, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AppTextButton(onClick = { editing = true }, enabled = !busy) { Text("编辑记录") }
                AppTextButton(onClick = { deleting = true }, enabled = !busy) { Text("删除记录", color = Muted) }
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (actions != null) actions.forEachIndexed { index, sets -> WorkoutActionCard(index, sets) }
                else AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
                    // Preserve older free-text records without inventing missing set values.
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("训练详情", color = Ink, style = MaterialTheme.typography.titleLarge)
                        Text(record.summary, color = Ink, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (deleting) AppAlertDialog(onDismissRequest = { if (!busy) deleting = false }, title = { Text("删除这次训练？") },
        text = { Text("${record.planName} · ${dateLabel(LocalDate.ofEpochDay(record.dateEpochDay))}") },
        confirmButton = { AppTextButton(onClick = { vm.deleteWorkout(record); deleting = false; dismiss() }, enabled = !busy) { Text("删除") } },
        dismissButton = { AppTextButton(onClick = { deleting = false }, enabled = !busy) { Text("取消") } })
}


