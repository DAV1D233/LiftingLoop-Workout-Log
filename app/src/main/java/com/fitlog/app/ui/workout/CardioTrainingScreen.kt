package com.fitlog.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

@Composable
internal fun CardioTrainingScreen(session: WorkoutSession, now: Long, busy: Boolean, vm: AppViewModel,
    home: () -> Unit, settings: (Boolean) -> Unit, ringBounds: (Rect) -> Unit) {
    val review = session.pendingCardioReview
    val spec = session.exercises[review ?: session.index]
    val logs = session.completed.filter { it.exerciseIndex == (review ?: session.index) }
    val paused = session.pausedAt != null
    val rest = session.restSeconds(now)
    val resting = rest > 0
    val elapsed = if (review != null) logs.sumOf { it.durationSeconds } else session.segmentSeconds(now)
    val timed = spec.cardioTarget.mode == "计时"
    val remaining = (spec.cardioTarget.durationSeconds - elapsed).coerceAtLeast(0)
    val timer = when { review != null -> elapsed; resting -> rest; timed && remaining > 0 -> remaining; else -> elapsed }
    val progress = when {
        review != null -> 1f
        resting -> session.restProgress(now)
        timed -> (elapsed.toFloat() / spec.cardioTarget.durationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> 0f
    }
    var confirming by remember { mutableStateOf(false) }
    var resumeOnCancel by remember { mutableStateOf(false) }
    var reviewing by rememberSaveable(session.key, review) { mutableStateOf(review != null) }
    fun requestEnd() {
        if (session.endedAt != null && review == null) { vm.finish(); return }
        resumeOnCancel = !paused && review == null
        if (resumeOnCancel) vm.pauseCardio()
        confirming = true
    }
    fun cancelEnd() {
        confirming = false
        if (resumeOnCancel && session.pausedAt != null && review == null) vm.pauseCardio()
    }
    BackHandler { if (!busy) requestEnd() }
    Column(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton({ requestEnd() }, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = NightText) }
            Text(if (spec.isInterval) "间歇训练" else "连续运动", color = NightMuted,
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            AppTextButton({ requestEnd() }, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "结束本次训练" }) { Text("结束", color = NightText) }
        }
        Text(spec.name, color = NightText, style = MaterialTheme.typography.headlineMedium)
        Text(when {
            review != null -> "已完成 · 确认记录后保存"
            session.endedAt != null -> if (busy) "正在保存" else "记录待保存"
            paused -> "已暂停"
            resting -> "轮间休息"
            spec.isInterval -> "运动中 · 第 ${session.currentDone + 1} / ${spec.sets} 轮"
            timed && remaining == 0 -> "目标时长已达成"
            timed -> "距离目标还剩"
            else -> "已运动"
        }, color = if (resting) Blue else GreenAccent, style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth * .88f, 290.dp)
            val finalCountdown = resting && rest in 1..3
            val digits = with(LocalDensity.current) { (diameter * if (finalCountdown) .25f else .16f).toSp() }
            Box(Modifier.size(diameter).onGloballyPositioned { ringBounds(it.boundsInRoot()) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 9.dp.toPx()
                    val inset = stroke / 2
                    val extent = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(NightSurface, -90f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), extent, style = Stroke(stroke))
                    if (progress > 0) drawArc(if (resting) Blue else GreenAccent, -90f, 360f * progress, false,
                        androidx.compose.ui.geometry.Offset(inset, inset), extent, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (finalCountdown) "$rest" else cardioClock(timer), color = NightText, fontSize = digits, fontWeight = FontWeight.Medium,
                        maxLines = 1, softWrap = false, modifier = Modifier.semantics { contentDescription = "有氧计时 ${cardioClock(timer)}" })
                    if (!finalCountdown) Text(if (resting) "休息剩余" else if (review != null) "有效运动时间" else if (timed && remaining > 0) "剩余时间" else "有效运动时间",
                        color = NightMuted, fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cardioClock(if (review != null) elapsed else logs.sumOf { it.durationSeconds } + if (resting) 0 else elapsed),
                    color = NightText, style = MaterialTheme.typography.titleLarge)
                Text("累计运动", color = NightMuted, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (spec.isInterval) "${logs.size} / ${spec.sets}" else if (spec.cardioTarget.mode == "计时")
                    cardioClock(spec.cardioTarget.durationSeconds) else if (spec.cardioTarget.distanceMeters > 0)
                    "${number(spec.cardioTarget.distanceMeters)} 米" else "自由计时", color = NightText, style = MaterialTheme.typography.titleLarge)
                Text(if (spec.isInterval) "完成轮数" else "运动目标", color = NightMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (spec.cardioTarget.mode == "计次" && review == null) Text("每轮 ${spec.reps} 次", color = NightText, style = MaterialTheme.typography.titleLarge)
        if (spec.cardioTarget.poolLengthMeters > 0) Text("${spec.cardioTarget.stroke} · ${number(spec.cardioTarget.poolLengthMeters)} 米池", color = NightMuted)
        if (review == null && session.endedAt != null) {
            AppButton({ vm.finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), enabled = !busy) {
                Text(if (busy) "保存中…" else "重试保存")
            }
        } else if (review == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton({ vm.pauseCardio() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Night), shape = RoundedCornerShape(24.dp)) {
                    Text(if (paused) "继续运动" else "暂停")
                }
                AppOutlinedButton({ vm.finishCardio() }, enabled = !busy && (!resting || session.currentDone > 0),
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NightSurface, contentColor = NightText)) { Text("结束本项") }
            }
            if (spec.isInterval || resting) AppOutlinedButton({ if (resting) vm.skipRest() else vm.completeCardioRound(session.key, session.index, session.currentDone) },
                enabled = !busy && !paused, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (resting) RestSurface else NightSurface, contentColor = NightText)) {
                Text(if (resting) "跳过休息" else if (spec.cardioTarget.mode != "计时") "完成一轮" else "提前完成本轮")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                AppTextButton({ if (!paused) vm.pauseCardio(); settings(false) }, enabled = !busy) { Text("运动设置", color = NightMuted) }
                AppTextButton({ if (!paused) vm.pauseCardio(); settings(true) }, enabled = !busy) { Text("动作讲解", color = NightMuted) }
            }
        } else AppButton({ reviewing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), enabled = !busy) { Text("确认运动记录") }
    }
    if (confirming) AppAlertDialog(onDismissRequest = { if (!busy) cancelEnd() }, title = { Text("结束本次训练？") },
        text = { Text("确认当前项目的数据后保存训练。") },
        confirmButton = { AppTextButton({ confirming = false; vm.finishCardio(wholeWorkout = true); reviewing = true }, enabled = !busy) { Text("保存并结束") } },
        dismissButton = { Row {
            AppTextButton({ cancelEnd() }, enabled = !busy) { Text("继续训练") }
            AppTextButton({ confirming = false; vm.discard(home) }, enabled = !busy) { Text("放弃") }
        } })
    if (review != null && reviewing && !confirming) key(session.key, review) {
        CardioCompletionDialog(spec, logs.sumOf { it.durationSeconds }, busy, { reviewing = false },
            reviewRounds = logs.size.coerceAtLeast(1), initialDistance = logs.sumOf { it.distanceMeters },
            initialReps = logs.sumOf { if (it.cardioMode == "计次") it.reps else 0 },
            confirmLabel = if (session.endedAt != null) "保存训练" else "保存并继续", dismissLabel = "稍后确认") { seconds, meters, reps ->
            vm.confirmCardio(seconds, meters, reps)
        }
    }
}

internal fun cardioClock(seconds: Int): String = if (seconds >= 3600)
    "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
