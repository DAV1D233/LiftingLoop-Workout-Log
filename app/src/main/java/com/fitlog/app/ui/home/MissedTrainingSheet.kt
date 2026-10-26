package com.fitlog.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fitlog.app.data.planLabel
import com.fitlog.app.domain.MissedTraining
import com.fitlog.app.domain.trainingCount
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.TrainingPreview
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedTrainingSheet(vm: AppViewModel, missed: MissedTraining, todayKey: String,
    dismiss: () -> Unit, choose: (Boolean) -> Unit) {
    val prefs by vm.preferences.collectAsState()
    val data by vm.data.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun close(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            try { sheetState.hide(); if (!sheetState.isVisible) action() }
            finally { closing = false }
        }
    }
    var previous by remember(missed.key) { mutableStateOf<TrainingPreview?>(null) }
    var current by remember(todayKey) { mutableStateOf<TrainingPreview?>(null) }
    LaunchedEffect(missed.key, todayKey) {
        try { previous = vm.trainingPreview(missed.key); current = vm.trainingPreview(todayKey) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Plan names stay usable; preparation offers retry. */ }
    }
    AppModalBottomSheet(dismiss, sheetState = sheetState, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (prefs.scheduleMode == "cycle") "按天循环" else "按周循环", color = Muted,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                AppIconButton(onClick = { close(dismiss) }) { Icon(Icons.Default.Close, "暂不开始") }
            }
            Text("上次安排的训练\n还没开始", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("接着上次练，还是按今天的安排？", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            TrainingChoice("接着上次练", previous?.title ?: planLabel(missed.key, data.plans), previous,
                if (prefs.scheduleMode == "cycle") "后续训练依次顺延" else "仅调整今天，之后仍按原周计划进行", true) { close { choose(true) } }
            TrainingChoice("按今天安排练", current?.title ?: planLabel(todayKey, data.plans), current,
                "跳过上次安排，后续计划不变", false) { close { choose(false) } }
            AppTextButton(onClick = { close(dismiss) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("暂不开始", color = Muted) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TrainingChoice(label: String, title: String, preview: TrainingPreview?, detail: String,
    emphasized: Boolean, choose: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (emphasized) GreenTint else CardFill)
        .motionClickable(onClick = choose).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (emphasized) Green else Ink, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (emphasized) Green else Ink, modifier = Modifier.size(20.dp))
        }
        Text(title, color = Ink, style = MaterialTheme.typography.bodyLarge)
        if (preview != null) Text("${preview.exercises.size} 个动作 · ${trainingCount(preview.exercises)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
