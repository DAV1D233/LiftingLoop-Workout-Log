package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.fitlog.app.domain.*
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun WorkoutSessionScreen(nav: NavHostController, vm: AppViewModel) {
    val active by vm.session.collectAsState()
    val summary by vm.summary.collectAsState()
    val busy by vm.busy.collectAsState()
    val undoLabel by vm.undoLabel.collectAsState()
    val now by vm.now.collectAsState()
    var finish by remember { mutableStateOf(false) }
    var adjustment by remember { mutableStateOf<String?>(null) }
    var resultDetails by remember { mutableStateOf(false) }
    var exerciseDetails by remember { mutableStateOf(false) }
    var openTutorial by remember { mutableStateOf(false) }
    fun showExercise(tutorial: Boolean = false) { openTutorial = tutorial; exerciseDetails = true }
    var editBodyWeight by remember { mutableStateOf(false) }
    var cardioComplete by remember { mutableStateOf(false) }
    var cardioElapsed by remember { mutableIntStateOf(0) }
    fun home() { vm.dismissSummary(); nav.popBackStack(Routes.HOME, false) }
    var lastRingBounds by remember { mutableStateOf(Rect.Zero) }
    var lastRingProgress by remember { mutableFloatStateOf(0f) }
    val result = summary
    if (result != null) {
        BackHandler { if (resultDetails) resultDetails = false else home() }
        WorkoutCompletionMotion(result, lastRingBounds, lastRingProgress, ::home, { resultDetails = true })
        if (resultDetails) CompletedTrainingSheet(result) { resultDetails = false }
        return
    }
    val session = active
    if (session == null) {
        Column(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Text("没有进行中的训练", style = MaterialTheme.typography.headlineMedium)
            AppTextButton(onClick = ::home) { Text("回到首页") }
        }
        return
    }
    SideEffect { lastRingProgress = session.progress }
    BackHandler { if (!busy) finish = true }
    val current = session.current
    val remaining = session.restSeconds(now)
    val resting = remaining > 0
    val pulse = remember { Animatable(1f) }
    val reduced = LocalGlassEnvironment.current.reduceMotion
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var previous by remember(session.key) { mutableStateOf(session) }
    var handoffIndex by remember(session.key) { mutableStateOf<Int?>(null) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    val introduction = if (foreground && session.endedAt == null)
        handoffIndex?.takeIf { it == session.index } ?: session.index.takeIf { ExerciseHandoffPolicy.shouldShow(previous, session) }
        else null
    val changing = introduction != null
    LaunchedEffect(session, foreground) {
        val old = previous
        previous = session
        if (!foreground || handoffIndex?.let { it != session.index } == true || session.endedAt != null) handoffIndex = null
        if (foreground && ExerciseHandoffPolicy.shouldShow(old, session)) handoffIndex = session.index
    }
    var observedCount by remember(session.key) { mutableIntStateOf(session.completed.size) }
    LaunchedEffect(session.completed.size, reduced) {
        val increased = session.completed.size > observedCount
        observedCount = session.completed.size
        if (reduced) pulse.snapTo(1f)
        else {
            if (increased) pulse.animateTo(1.055f, tween(90))
            pulse.animateTo(1f, spring(dampingRatio = .72f))
        }
    }
    if (current.isCardio || session.pendingCardioReview != null) {
        CardioTrainingScreen(session, now, busy, vm, ::home, { tutorial -> showExercise(tutorial) }, { lastRingBounds = it })
        if (exerciseDetails) CurrentExerciseSheet(session, busy, { exerciseDetails = false }, vm, openTutorial)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing)
        .onGloballyPositioned { viewport = it.boundsInRoot() }) {
        val compact = maxHeight < 700.dp
        val gap = if (compact) 8.dp else 16.dp
        Column(Modifier.fillMaxSize().then(if (changing) Modifier.clearAndSetSemantics { } else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SessionTopButton("回退", !busy && undoLabel != null) { vm.undo() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = NightMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(timeLabel(session.durationSeconds(now)), color = NightMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "训练时长 ${timeLabel(session.durationSeconds(now))}" })
                }
                SessionTopButton("结束", !busy) { finish = true }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = gap), contentAlignment = Alignment.Center) {
                SessionRings(session, remaining, session.restProgress(now), session.segmentSeconds(now), Modifier.sizeIn(maxWidth = 224.dp, maxHeight = 224.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .onGloballyPositioned { lastRingBounds = it.boundsInRoot() }
                    .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value })
            }
            Eyebrow(if (resting) "休息中 / 下一组" else if (session.endedAt != null) "本次训练" else "当前动作", NightMuted)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).motionCombinedClickable(enabled = !busy,
                onClick = { showExercise() }, onLongClickLabel = if (current.isCardio) "修改有氧目标和间歇" else "修改当前动作组数、次数和重量",
                onLongClick = { showExercise() }), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(current.name, fontSize = if (compact) 28.sp else 32.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = NightText, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    .onGloballyPositioned { titleBounds = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (changing) 0f else 1f },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (current.isCardio) {
                Text(current.cardioTarget.overview(current.reps), color = NightText, fontSize = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("第 ${(session.currentDone + 1).coerceAtMost(current.sets)} / ${current.sets} 轮", color = NightMuted, fontSize = 12.sp)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Parameter(number(session.currentWeight), "kg", !busy && session.endedAt == null,
                    longAction = { showExercise() }) { adjustment = "weight" }
                Text("×", fontSize = 24.sp, color = NightMuted)
                Parameter("${session.currentTarget.reps}", "次", !busy && session.endedAt == null,
                    longAction = { showExercise() }) { adjustment = "reps" }
            }
            }
            if (current.isBodyweight) {
                if (session.bodyWeightKg != null) Text("体重 ${number(session.bodyWeightKg)} kg ＋ 额外负重 ${number(session.currentTarget.weight)} kg  ✎",
                    color = NightMuted, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy && session.endedAt == null) { adjustment = "weight" }.padding(vertical = 10.dp))
                else AppTextButton(onClick = { editBodyWeight = true }, enabled = !busy) { Text("填写体重，让自重动作自动带入") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(if (resting && current.isCardio) "下一段 · ${current.cardioTarget.overview(current.reps)}"
                    else if (resting) "下一组 · ${number(session.currentWeight)} kg × ${session.currentTarget.reps}"
                    else "长按动作设置参数", color = NightMuted, fontSize = 11.sp, maxLines = 1)
                AppTextButton(onClick = { showExercise(tutorial = true) }, enabled = !busy) {
                    Text("讲解 ↗", color = NightMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(gap))
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed && !reduced) .96f else 1f, if (reduced) snap() else spring(), label = "completePress")
            val ended = session.endedAt != null
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (resting) AppOutlinedButton(onClick = { vm.adjustRest(-15) }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("−15 秒", color = NightText, fontSize = 12.sp)
            }
            Box(Modifier.size(if (compact) 88.dp else 104.dp)
                .clickable(interaction, indication = null, enabled = !busy && !changing) {
                    when { ended -> vm.finish(); resting -> vm.skipRest()
                        current.isCardio -> { cardioElapsed = session.segmentSeconds(now); cardioComplete = true }
                        else -> vm.completeSet() }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(4.dp, CircleShape)
                    .background(if (resting && !ended) RestSurface else Lime, CircleShape))
                Text(when { busy -> "保存中"; ended -> "保存训练"; resting -> "跳过休息"; current.isCardio -> "完成本段"; else -> "完成本组" },
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (resting && !ended) RestText else Night)
            }
            if (resting) AppOutlinedButton(onClick = { vm.adjustRest(15) }, enabled = !busy && remaining < 600,
                modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("＋15 秒", color = NightText, fontSize = 12.sp)
            }
            }
            Spacer(Modifier.height(gap))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                session.exercises.forEachIndexed { i, spec ->
                    val done = session.completed.count { it.exerciseIndex == i } >= spec.sets
                    Box(Modifier.padding(horizontal = 4.dp).size(if (i == session.index) 10.dp else 8.dp).background(
                        when { done -> Blue; i == session.index -> NightText; else -> NightSurface }, CircleShape))
                }
            }
            Text("${session.completed.size} / ${session.totalSets} ${if (session.exercises.any { it.isCardio }) "段/组" else "组"}",
                color = NightMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        introduction?.let { index ->
            key(session.key, index, session.completed.size) {
                ExerciseHandoff(current.name, titleBounds.translate(-viewport.topLeft), if (compact) 28f else 32f,
                    reduced, finished = { handoffIndex = null })
            }
        }
    }
    if (finish) AppAlertDialog(onDismissRequest = { finish = false }, title = { Text("结束本次训练？") },
        text = { Text(if (session.completed.isEmpty()) "尚未完成任何组，结束后不生成记录。" else "保存已完成的 ${session.completed.size} 组，查看训练总结。") },
        confirmButton = { AppTextButton(onClick = { finish = false; vm.finish(done = { if (vm.summary.value == null) home() }) }, enabled = !busy) { Text("保存并结束") } },
        dismissButton = { Row {
            AppTextButton(onClick = { finish = false }, enabled = !busy) { Text("继续") }
            AppTextButton(onClick = { finish = false; vm.discard(::home) }, enabled = !busy) { Text("放弃") }
        } })
    adjustment?.let { kind ->
        val target = if (resting) "下一组" else "当前组"
        if (kind == "weight") WeightAdjustmentDialog(
            title = target + if (current.isBodyweight) "额外负重" else "重量",
            weight = session.currentTarget.weight, enabled = !busy,
            adjust = { vm.adjust(weight = it) }, setWeight = vm::setCurrentWeight,
            dismiss = { adjustment = null })
        else AppAlertDialog(onDismissRequest = { adjustment = null }, title = { Text(target + "次数") },
            text = { StepControl("次", "${session.currentTarget.reps}",
                minus = { vm.adjust(reps = -1) }, plus = { vm.adjust(reps = 1) }, enabled = !busy) },
            confirmButton = { AppTextButton(onClick = { adjustment = null }) { Text("完成") } })
    }
    if (exerciseDetails) CurrentExerciseSheet(session, busy, { exerciseDetails = false }, vm, openTutorial)
    if (cardioComplete) CardioCompletionDialog(current, cardioElapsed, busy, { cardioComplete = false }) { seconds, meters, reps ->
        vm.completeSet(seconds, meters, reps)
        cardioComplete = false
    }
    if (editBodyWeight) BodyWeightDialog(vm) { editBodyWeight = false }
}

@Composable
private fun SessionTopButton(label: String, enabled: Boolean, action: () -> Unit) {
    AppOutlinedButton(onClick = action, modifier = Modifier.size(56.dp), enabled = enabled,
        shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = NightSurface, contentColor = NightText)) {
        Text(label, color = if (enabled) NightText else NightMuted.copy(alpha = .35f), fontSize = 13.sp)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Parameter(value: String, unit: String, enabled: Boolean, longAction: () -> Unit, action: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.motionPress(source, enabled).clip(RoundedCornerShape(12.dp))
        .combinedClickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = action, onLongClickLabel = "修改当前动作组数、次数和重量",
        onLongClick = longAction).padding(6.dp), verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = NightText)
        Text(unit, Modifier.padding(start = 4.dp, bottom = 4.dp), fontSize = 14.sp, color = NightMuted)
    }
}

@Composable
private fun SessionRings(session: WorkoutSession, rest: Int, restProgress: Float, elapsed: Int, modifier: Modifier) {
    val reduced = LocalGlassEnvironment.current.reduceMotion
    val total by animateFloatAsState(session.progress, tween(if (reduced) 0 else 480, easing = FastOutSlowInEasing), label = "total")
    val recovery by animateFloatAsState(restProgress, tween(if (reduced) 0 else 250, easing = LinearEasing), label = "recovery")
    // Create the continuous animation only while the water is visible.
    val waterPhase = if (rest > 0 && !LocalGlassEnvironment.current.reduceMotion) {
        rememberInfiniteTransition(label = "waterMotion").animateFloat(0f, (PI * 2).toFloat(),
            infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "waterPhase")
    } else remember { mutableFloatStateOf(0f) }
    var wasResting by remember(session.key) { mutableStateOf(rest > 0) }
    var announceGroup by remember(session.key) { mutableStateOf(false) }
    val readyAlpha = remember(session.key) { Animatable(1f) }
    val justFinishedRest = wasResting && rest == 0 && session.endedAt == null
    LaunchedEffect(rest > 0, session.index, session.currentDone, session.endedAt, reduced) {
        val leavingRest = wasResting && rest == 0 && session.endedAt == null
        wasResting = rest > 0
        announceGroup = false
        if (leavingRest) {
            announceGroup = true
            if (!reduced) {
                readyAlpha.snapTo(0f)
                readyAlpha.animateTo(1f, tween(120))
                delay(90)
                readyAlpha.animateTo(.12f, tween(120))
                delay(90)
                readyAlpha.animateTo(1f, tween(120))
            } else readyAlpha.snapTo(1f)
            delay(700)
            announceGroup = false
        } else readyAlpha.snapTo(1f)
    }
    val activeAlpha = if (reduced) 1f else if (justFinishedRest) 0f else readyAlpha.value
    val colors = List(session.current.sets) { i ->
        val completedColor by animateColorAsState(if (i < session.currentDone) Blue else NightSurface,
            tween(if (reduced) 0 else 220), label = "group-$i")
        if (i == session.currentDone && rest == 0 && session.endedAt == null) lerp(NightSurface, NightText, activeAlpha)
        else if (i == session.currentDone) NightSurface else completedColor
    }
    BoxWithConstraints(modifier.semantics { contentDescription = "${session.completed.size} / ${session.totalSets} 组完成" }, contentAlignment = Alignment.Center) {
        val centerWidth = minOf(maxWidth, maxHeight) * .48f
        val countSize = (maxWidth.value * .134f).coerceIn(16f, 30f).sp
        val labelSize = (maxWidth.value * .049f).coerceIn(8f, 11f).sp
        Canvas(Modifier.fillMaxSize()) {
            val outerStroke = size.minDimension * (13f / 224f)
            val inset = outerStroke / 2
            val outerSize = Size(size.width - outerStroke, size.height - outerStroke)
            drawArc(NightSurface, -90f, 360f, false, Offset(inset, inset), outerSize, style = Stroke(outerStroke, cap = StrokeCap.Round))
            if (total > 0) drawArc(Lime, -90f, 360f * total, false, Offset(inset, inset), outerSize,
                style = Stroke(outerStroke, cap = StrokeCap.Round))
            val innerInset = size.minDimension * (31f / 224f)
            val sweep = 360f / colors.size
            colors.forEachIndexed { i, color ->
                drawArc(color, -90f + i * sweep + 3f, sweep - 6f, false,
                    Offset(innerInset, innerInset), Size(size.width - 2 * innerInset, size.height - 2 * innerInset),
                    style = Stroke(size.minDimension * (16f / 224f), cap = StrokeCap.Butt))
            }
            if (rest > 0) {
                drawRecoveryWater(recovery, waterPhase.value)
            }
        }
        if (rest in 1..3) {
            // The water circle is 54% of the ring diameter. Keep the digit inside
            // an inscribed square, including at large system font scales.
            val diameter = minOf(maxWidth, maxHeight) * .54f
            val countdownSize = with(LocalDensity.current) { (diameter * .56f).toSp() }
            Box(Modifier.size(diameter * .68f), contentAlignment = Alignment.Center) {
                Text(rest.toString(), color = NightText,
                    style = TextStyle(fontSize = countdownSize, lineHeight = countdownSize,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.semantics { contentDescription = "休息剩余 $rest 秒" })
            }
        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val centerText = if (rest > 0) timerLabel(rest) else if (session.endedAt != null) "${(session.progress * 100).toInt()}%"
                else if (announceGroup || !session.current.isCardio) "第 ${(session.currentDone + 1).coerceAtMost(session.current.sets)} ${if (session.current.isCardio) "段" else "组"}"
                else timerLabel(elapsed)
            val measured = rememberTextMeasurer().measure(centerText, TextStyle(fontSize = countSize,
                fontWeight = FontWeight.Medium), softWrap = false).size.width
            val fit = (with(LocalDensity.current) { centerWidth.toPx() } / measured.coerceAtLeast(1)).coerceAtMost(1f)
            Text(centerText, fontSize = countSize * fit, fontWeight = FontWeight.Medium, color = NightText,
                maxLines = 1, softWrap = false)
            Text(if (rest > 0) "${if (session.current.isCardio) "段" else "组"}间休息" else if (session.current.isCardio && !announceGroup) "本段已用时" else "共 ${session.current.sets} ${if (session.current.isCardio) "段" else "组"}", color = NightMuted, fontSize = labelSize, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun DrawScope.drawRecoveryWater(progress: Float, phase: Float) {
    val radius = size.minDimension * .27f
    val left = center.x - radius
    val right = center.x + radius
    val top = center.y - radius
    val bottom = center.y + radius
    val level = progress.coerceIn(0f, 1f)
    val waterY = bottom - radius * 2 * level
    // Keep the first and last moments within the circle without an abrupt wave jump.
    val amplitude = radius * .045f * minOf(1f, level * 10, (1 - level) * 10)
    val circle = Path().apply { addOval(Rect(left, top, right, bottom)) }
    fun surface(y: Float, height: Float, shift: Float): Path = Path().apply {
        for (step in 0..64) {
            val fraction = step / 64f
            val angle = fraction * (PI * 2).toFloat()
            val wave = sin(angle + shift) + .25f * sin(angle * 2 - shift)
            val x = left + radius * 2 * fraction
            if (step == 0) moveTo(x, y + wave * height) else lineTo(x, y + wave * height)
        }
    }
    fun filled(wave: Path) = Path().apply {
        addPath(wave); lineTo(right, bottom); lineTo(left, bottom); close()
    }
    clipPath(circle) {
        if (level > 0) {
            val back = surface(waterY - amplitude * .3f, amplitude * .7f, -phase + 1.6f)
            drawPath(filled(back), Blue.copy(alpha = .24f))
            val front = surface(waterY, amplitude, phase)
            val water = filled(front)
            drawPath(water, Brush.verticalGradient(listOf(Lime.copy(alpha = .43f),
                GreenAccent.copy(alpha = .32f), Blue.copy(alpha = .26f)), top, bottom))
            clipPath(water) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .13f), Color.Transparent),
                    Offset(center.x - radius * .4f, waterY), radius * 1.2f), radius * 1.5f)
            }
            drawPath(front, Lime.copy(alpha = .55f), style = Stroke(radius * .013f, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun CompletionScreen(summary: SessionSummary, home: () -> Unit, history: () -> Unit,
    modifier: Modifier = Modifier, checkProgress: Float = 1f, checkPlaced: (Rect) -> Unit = {}) {
    Column(modifier.fillMaxSize().background(Night).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CompletionCheck(checkProgress, Modifier.size(84.dp)
                .onGloballyPositioned { checkPlaced(it.boundsInRoot()) })
            Text(if (summary.complete) "目标达成" else "训练已记录", color = NightText,
                fontSize = 32.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 28.dp))
            Text(summary.title, color = NightMuted, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp))
            Text("${summary.exercises} 个动作 · ${summary.sets} ${if (summary.cardioRounds > 0) "段 / 组" else "组"} · ${timeLabel(summary.seconds)}",
                color = NightMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp))
            Text("训练记录已保存", color = NightMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))
        }
        Spacer(Modifier.height(12.dp))
        AppButton(history, Modifier.fillMaxWidth().heightIn(min = 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GreenAccent, contentColor = Night)) {
            Text("查看运动详情")
        }
        AppTextButton(home, Modifier.fillMaxWidth()) { Text("回到首页", color = NightMuted) }
    }
}
