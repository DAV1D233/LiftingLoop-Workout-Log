package com.fitlog.app.ui.workout

import com.fitlog.app.domain.isInterval

import com.fitlog.app.ui.glass.*

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.fitlog.app.domain.number
import com.fitlog.app.domain.BodyWeight
import com.fitlog.app.domain.trainingCount
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.components.AppCard
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun TrainingPreviewScreen(nav: NavHostController, vm: AppViewModel, key: String) {
    var preview by remember(key) { mutableStateOf<TrainingPreview?>(null) }
    var loaded by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    val busy by vm.busy.collectAsState()
    val today by vm.today.collectAsState()
    val prefs by vm.preferences.collectAsState()
    val active by vm.session.collectAsState()
    var editWeight by remember { mutableStateOf(false) }
    val bodyWeight = if (key == "active") active?.bodyWeightKg ?: prefs.bodyWeightKg else prefs.bodyWeightKg
    val needsWeight = preview?.exercises?.any { it.isBodyweight } == true && bodyWeight == null &&
        !(key == "active" && active?.endedAt != null)
    LaunchedEffect(key) {
        try { preview = vm.trainingPreview(key) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        loaded = true
    }
    PageScaffold("训练准备", { nav.popBackStack() }, bottom = {
        PrimaryAction(if (needsWeight) "请先填写体重" else if (key == "active") "确认进入 · 继续训练" else "确认进入 · 开始训练", loaded && preview != null && !busy) {
            if (needsWeight) { editWeight = true; return@PrimaryAction }
            nav.navigate(Routes.countdown(key)) { launchSingleTop = true }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)) {
            item {
                Text(dateLabel(today), color = Muted)
                Text(preview?.title ?: if (!loaded) "正在读取计划…" else "无法读取计划",
                    style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                if (preview != null) {
                    Text("${preview!!.exercises.size} 个动作 · ${trainingCount(preview!!.exercises)}", color = Green)
                    if (preview!!.exercises.any { it.isBodyweight }) ActionRow("自重训练体重",
                        bodyWeight?.let { "${number(it)} kg · 体重＋额外负重" } ?: "尚未填写 · 开始前请录入体重",
                        { editWeight = true }, if (key == "active" && active?.bodyWeightKg != null) "下次更新" else if (bodyWeight != null) "修改" else "填写")
                    SectionHeading("本次训练动作", "确认后，倒计时 3 秒进入训练。")
                } else if (loaded) {
                    Text(if (failed) "读取失败，请返回后重试。" else "计划已不存在，请返回重新选择。", color = Muted)
                }
            }
            itemsIndexed(preview?.exercises.orEmpty()) { index, exercise ->
                AppCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val image = exerciseIllustration(exercise.name)
                    if (image != null) Image(painterResource(image), null, Modifier.size(64.dp))
                    else Text("${index + 1}".padStart(2, '0'), color = Muted, fontSize = 22.sp, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                        if (exercise.isCardio) {
                            Text((if (exercise.isInterval) "${exercise.sets} 轮 · " else "连续运动 · ") + exercise.cardioTarget.overview(exercise.reps), color = Green,
                                style = MaterialTheme.typography.bodyMedium)
                            if (exercise.isInterval) Text("轮间休息 ${exercise.restSeconds} 秒", color = Muted, fontSize = 12.sp)
                        } else if (exercise.setTargets.isNotEmpty()) {
                            Text("${exercise.sets} 组 · 逐组设置", color = Muted)
                            exercise.setTargets.forEachIndexed { i, target ->
                                val weight = BodyWeight.effectiveWeight(exercise.copy(weight = target.weight), bodyWeight)
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = GreenTint, contentColor = Green, shape = CircleShape) {
                                        Box(Modifier.size(24.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)), contentAlignment = Alignment.Center) {
                                            Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Text("${number(weight)} kg × ${target.reps} · 休息 ${target.restSeconds} 秒",
                                        modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp)
                                }
                            }
                        } else {
                        Text("${exercise.sets} 组 × ${exercise.reps} 次" +
                            if (exercise.isBodyweight) {
                                bodyWeight?.let { " · ${number(BodyWeight.effectiveWeight(exercise, it))} kg（自重）" } ?: " · 自重"
                            } else if (exercise.weight > 0) " · ${number(exercise.weight)} kg" else "",
                            color = Muted, modifier = Modifier.padding(top = 4.dp))
                        Text("组间休息 ${exercise.restSeconds} 秒", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                }
            }
        }
    }
    if (editWeight) BodyWeightDialog(vm) { editWeight = false }
}

@Composable
fun TrainingCountdownScreen(nav: NavHostController, vm: AppViewModel, key: String) {
    val busy by vm.busy.collectAsState()
    val active by vm.session.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var cancelled by remember { mutableStateOf(false) }
    var skipRequested by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(3) }
    var attempt by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(.6f) }
    val countdownSound = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull() }
    DisposableEffect(countdownSound) {
        onDispose {
            countdownSound?.stopTone()
            countdownSound?.release()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) countdownSound?.stopTone()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun cancel() {
        if (!busy && !cancelled) {
            cancelled = true
            countdownSound?.stopTone()
            nav.popBackStack()
        }
    }
    fun enter() {
        if (cancelled || !resumed) return
        nav.navigate(Routes.SESSION) {
            popUpTo(Routes.COUNTDOWN) { inclusive = true }
            launchSingleTop = true
        }
    }
    BackHandler { cancel() }
    val reducedMotion by rememberUpdatedState(LocalGlassEnvironment.current.reduceMotion)
    LaunchedEffect(resumed, attempt, cancelled, skipRequested) {
        if (!resumed || cancelled) return@LaunchedEffect
        if (!skipRequested) for (digit in 3 downTo 1) {
            count = digit
            countdownSound?.startTone(ToneGenerator.TONE_PROP_BEEP, 110)
            if (reducedMotion) { scale.snapTo(1f); delay(650) }
            else {
                scale.snapTo(.6f)
                scale.animateTo(1.15f, tween(650, easing = FastOutSlowInEasing))
            }
            delay(350)
        }
        if (!cancelled) {
            count = 0
            if (!skipRequested) {
                countdownSound?.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                delay(180)
            }
            if (active != null) vm.beginPlan("active", ::enter)
            else if (key.isNotEmpty()) vm.beginPlan(key, ::enter)
        }
    }
    Box(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow("准备开始", NightMuted)
            Text(active?.title ?: "训练倒计时", color = NightMuted, modifier = Modifier.padding(top = 16.dp))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                if (count > 0) Text("$count", color = Lime, fontSize = 144.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.graphicsLayer { scaleX = if (reducedMotion) 1f else scale.value; scaleY = scaleX })
                else Text(if (busy) "正在准备…" else "准备就绪", color = NightText)
            }
            if (count > 0) AppTextButton(onClick = {
                // Changing the effect key cancels the in-flight numeral animation/delay.
                if (!skipRequested && !cancelled && resumed && !busy) {
                    countdownSound?.stopTone()
                    count = 0
                    skipRequested = true
                }
            }, enabled = !busy && resumed && !cancelled && !skipRequested) {
                Text("跳过倒计时", color = Lime)
            }
            if (count == 0 && !busy) AppTextButton(onClick = { skipRequested = false; attempt++ }) { Text("重新开始", color = Lime) }
        }
        AppOutlinedButton(onClick = ::cancel, enabled = !busy,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = 54.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NightText)) { Text("取消") }
    }
}
