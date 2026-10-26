package com.fitlog.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.fitlog.app.feedback.FeedbackCue
import com.fitlog.app.ui.theme.*
import com.fitlog.app.ui.glass.*
import com.fitlog.app.domain.GlassPolicy
import com.fitlog.app.domain.DockMenuPolicy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

internal enum class DockSection(val label: String) { WORKOUT("锻炼"), FOOD("饮食"), ADD("添加") }
internal data class DockItem(val title: String, val symbol: HomeSymbol, val action: () -> Unit)

/** Anchored lists and gesture hit regions share root coordinates, including system insets. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BoxScope.HomeDock(items: Map<DockSection, List<DockItem>>, enabled: Boolean,
    hint: Boolean, availableHeight: Dp, gestures: DockDragController, cue: (FeedbackCue) -> Unit, opened: () -> Unit,
    focusChanged: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf<DockSection?>(null) }
    var presented by remember { mutableStateOf<DockSection?>(null) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var pressedSection by remember { mutableStateOf<DockSection?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var menuViewport by remember { mutableStateOf(Rect.Zero) }
    val anchors = remember { mutableStateMapOf<DockSection, Rect>() }
    val materialScales = remember { mutableStateMapOf<DockSection, Float>() }
    val rows = remember { mutableStateMapOf<Int, Rect>() }
    val rowHeights = remember { mutableStateMapOf<Int, Int>() }
    val requesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }
    val scope = rememberCoroutineScope()
    val animation = remember { Animatable(0f) }
    val density = LocalDensity.current
    val reduced = LocalGlassEnvironment.current.reduceMotion
    val menuFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var selectionScroll by remember { mutableStateOf<Job?>(null) }
    val dismissInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(menu, reduced) {
        val opening = menu
        if (opening != null) {
            if (presented != opening) {
                animation.snapTo(0f)
                withFrameNanos { }
                if (menu != opening) return@LaunchedEffect
                presented = opening
            }
            runCatching { menuFocus.requestFocus() }
            // Animatable resumes at its current value when a close/open reverses.
            animation.animateTo(1f, if (reduced) snap() else tween(
                (420 * (1f - animation.value)).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing))
        } else {
            val closing = presented ?: return@LaunchedEffect
            animation.animateTo(0f, if (reduced) snap() else tween(
                (280 * animation.value).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing))
            if (menu == null && presented == closing) {
                presented = null
                menuViewport = Rect.Zero
                rows.clear(); rowHeights.clear(); requesters.clear()
            }
        }
    }
    val focus = (rows[selectedIndex]?.center ?: anchors[menu]?.center?.minus(Offset(0f, with(density) { 90.dp.toPx() })) ?: Offset.Zero) - overlayOrigin
    val spotlight by animateOffsetAsState(focus, tween(if (reduced || menu == null) 0 else 140), label = "spotlight")
    fun select(index: Int) {
        if (index != selectedIndex) {
            selectedIndex = index
            if (index >= 0) {
                cue(FeedbackCue.SELECT)
                selectionScroll?.cancel()
                val section = menu
                selectionScroll = scope.launch {
                    if (menu == section) requesters[index]?.bringIntoView()
                }
            }
        }
    }
    fun close(confirm: Boolean) {
        val section = menu ?: return
        val action = if (GlassPolicy.confirms(confirm, selectedIndex, items[section].orEmpty().size, enabled, false))
            items[section]?.getOrNull(selectedIndex)?.action else null
        // Release interaction synchronously. The retained menu is visual-only
        // while the same timeline runs backwards; cleanup never waits on motion.
        menu = null; selectedIndex = -1
        selectionScroll?.cancel(); selectionScroll = null
        focusChanged(false)
        focusManager.clearFocus()
        if (action != null) { cue(FeedbackCue.CONFIRM); action() }
    }
    var dragCursor by remember { mutableFloatStateOf(-1f) }
    var lastTravel by remember { mutableFloatStateOf(0f) }
    var holdPreviousY by remember { mutableStateOf<Float?>(null) }
    fun moveSelection(upward: Float) {
        // Selection distance follows layout height, never the animated/scaled visual bounds.
        val rowHeight = rowHeights.values.filter { it > 0 }.average().takeIf { it.isFinite() && it > 0 }?.toFloat()
            ?: with(density) { 52.dp.toPx() }
        dragCursor = DockMenuPolicy.advance(dragCursor, upward, rowHeight, items[menu].orEmpty().size)
        select(DockMenuPolicy.selected(dragCursor, items[menu].orEmpty().size))
    }
    fun open(section: DockSection) {
        if (!enabled || menu == section) return
        selectionScroll?.cancel(); selectionScroll = null
        holdPreviousY = null; dragCursor = -1f
        if (presented != section) {
            rows.clear(); rowHeights.clear(); requesters.clear(); menuViewport = Rect.Zero
        }
        selectedIndex = -1; menu = section
        focusChanged(true); opened(); cue(FeedbackCue.HOLD)
    }
    fun hover(local: Offset) {
        // A hold can move before the first presentation frame has measured its rows.
        // Selection already has an anchor and a 52dp fallback; do not drop that travel.
        if (menu == null) return
        val point = overlayOrigin + local
        val anchor = anchors[menu]
        if (anchor == null) return
        if (point.y >= anchor.top) {
            select(-1); dragCursor = -1f; holdPreviousY = null
        } else {
            moveSelection((holdPreviousY ?: anchor.center.y) - point.y)
            holdPreviousY = point.y
        }
    }
    BackHandler(menu != null) { close(false) }
    SideEffect {
        gestures.enabled = enabled
        gestures.active = menu != null && enabled
        gestures.sectionAt = { point -> anchors.entries.firstOrNull { it.value.contains(point + overlayOrigin) }?.key }
        gestures.tap = { section -> if (menu == section) close(false) else open(section) }
        gestures.hold = ::open
        gestures.hover = ::hover
        gestures.press = { pressedSection = it }
        gestures.begin = { dragCursor = selectedIndex.toFloat(); lastTravel = 0f }
        gestures.travel = { upward -> moveSelection(upward - lastTravel); lastTravel = upward }
        gestures.end = ::close
    }
    DisposableEffect(gestures) { onDispose {
        gestures.active = false
        gestures.enabled = false
        gestures.sectionAt = { null }; gestures.tap = {}; gestures.hold = {}; gestures.hover = {}; gestures.press = {}
        gestures.begin = {}; gestures.travel = {}; gestures.end = {}
    } }
    Box(Modifier.matchParentSize().onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft }) {
        if (menu != null) Canvas(Modifier.matchParentSize().clickable(
            interactionSource = dismissInteraction, indication = null, onClickLabel = "收起菜单") { close(false) }) {
            drawRect(Brush.radialGradient(
                0f to Paper.copy(alpha = .50f), .48f to Paper.copy(alpha = .82f),
                1f to Paper.copy(alpha = .97f), center = spotlight, radius = 280.dp.toPx()))
        }
        val visibleSection = menu ?: presented
        if (visibleSection != null) Canvas(Modifier.matchParentSize()) {
            anchors[visibleSection]?.let { anchor ->
                if (presented == visibleSection && menuViewport.width > 0f) {
                    val scale = materialScales[visibleSection] ?: 1f
                    val bottom = Offset(anchor.center.x, anchor.center.y - anchor.height * scale / 2f) - overlayOrigin
                    val topY = menuViewport.top - overlayOrigin.y
                    if (topY < bottom.y) drawLine(Ink.copy(alpha = .30f), bottom,
                        Offset(bottom.x, bottom.y + (topY - bottom.y) * animation.value), strokeWidth = .5.dp.toPx())
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            visibleSection?.let { section ->
                key(section) {
                    val listScroll = rememberScrollState()
                    // Use the already-measured Dock row, never the new menu's initially-zero width.
                    val anchorX = ((anchors[section]?.center?.x ?: dockBounds.left) - dockBounds.left)
                        .coerceIn(0f, dockBounds.width.coerceAtLeast(0f))
                    val opensLeft = section == DockSection.ADD
                    val inset = with(density) { anchorX.toDp() }
                    val trailingInset = with(density) { (dockBounds.width - anchorX).coerceAtLeast(0f).toDp() }
                    Box(Modifier.fillMaxWidth().onGloballyPositioned { if (visibleSection == section) menuViewport = it.boundsInRoot() }
                        .focusRequester(menuFocus).onPreviewKeyEvent { event ->
                            if (menu != section || event.type != KeyEventType.KeyDown) false else when (event.key) {
                                Key.DirectionUp, Key.DirectionDown -> {
                                    select(GlassPolicy.selectedAfterKey(selectedIndex, items[section].orEmpty().size, event.key == Key.DirectionUp)); true
                                }
                                Key.Enter, Key.NumPadEnter, Key.Spacebar -> { close(true); true }
                                Key.Escape -> { close(false); true }
                                else -> false
                            }
                        }.focusable(enabled = menu == section)
                        .then(if (menu != section) Modifier.clearAndSetSemantics { } else Modifier)
                        .graphicsLayer { alpha = if (presented == section) 1f else 0f }) {
                        Column(Modifier.fillMaxWidth().padding(start = if (opensLeft) 0.dp else inset,
                            end = if (opensLeft) trailingInset else 0.dp)
                            .heightIn(max = (availableHeight - 144.dp).coerceAtLeast(96.dp))
                            // Retain the scroll offset during reversal without a scroll gesture.
                            .verticalScroll(listScroll, enabled = false)
                            .padding(start = if (opensLeft) 0.dp else 12.dp, end = if (opensLeft) 12.dp else 0.dp)) {
                            items[section].orEmpty().withIndex().reversed().forEach { (index, item) ->
                                val requester = remember { BringIntoViewRequester() }
                                requesters[index] = requester
                                val selected = selectedIndex == index
                                val selectedScale by animateFloatAsState(if (selected) 1.16f else 1f,
                                    if (reduced) snap() else spring(dampingRatio = .8f, stiffness = 500f), label = "dock selection")
                                // Only the icon, label and 8dp padding are a tap target. The
                                // full-width list is positioning space; blank taps reach the scrim.
                                Row(Modifier.align(if (opensLeft) Alignment.End else Alignment.Start)
                                    .widthIn(min = 48.dp).heightIn(min = 52.dp).bringIntoViewRequester(requester)
                                    .onSizeChanged { if (menu == section) rowHeights[index] = it.height }
                                    .onGloballyPositioned { if (menu == section) rows[index] = it.boundsInRoot() }
                                    .graphicsLayer {
                                        val reveal = if (reduced) animation.value else (animation.value * 1.3f - index * .1f).coerceIn(0f, 1f)
                                        alpha = reveal
                                        scaleX = selectedScale * (.92f + .08f * reveal); scaleY = scaleX
                                        translationY = with(density) { (36.dp + 52.dp * index).toPx() } * (1f - reveal)
                                        translationX = with(density) { 12.dp.toPx() } * (if (opensLeft) 1f else -1f) * (1f - reveal)
                                        transformOrigin = TransformOrigin(if (opensLeft) 1f else 0f, .5f)
                                    }.semantics { this.selected = selected }
                                    .then(if (menu == section) Modifier.clickable(
                                        enabled = enabled && presented == section, role = Role.Button) { select(index); close(true) }
                                        else Modifier)
                                    .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (!opensLeft) HomeIcon(item.symbol, if (section == DockSection.FOOD) Orange else Green, Modifier.size(24.dp))
                                    Text(item.title, style = MaterialTheme.typography.titleMedium,
                                        color = if (section == DockSection.FOOD) Orange else if (selected) Green else Ink, modifier = Modifier.weight(1f, fill = false),
                                        textAlign = if (opensLeft) TextAlign.End else TextAlign.Start)
                                    if (opensLeft) HomeIcon(item.symbol, if (section == DockSection.FOOD) Orange else Green, Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (hint && visibleSection == null) Text("点击展开 · 长按上滑选择，松手进入", color = Muted,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth().onGloballyPositioned { dockBounds = it.boundsInRoot() }, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.glassSurface(GlassStyle.DOCK, shape = RoundedCornerShape(40.dp), protectIcon = false).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DockSection.WORKOUT, DockSection.FOOD).forEach { section ->
                        DockCircle(section, enabled, menu == section, pressedSection == section, anchors,
                            tap = { if (menu == section) close(false) else open(section) }, hold = { open(section) },
                            dimmed = menu != null && menu != section,
                            materialChanged = { materialScales[section] = it })
                    }
                }
                Spacer(Modifier.weight(1f))
                DockCircle(DockSection.ADD, enabled, menu == DockSection.ADD, pressedSection == DockSection.ADD, anchors,
                    tap = { if (menu == DockSection.ADD) close(false) else open(DockSection.ADD) }, hold = { open(DockSection.ADD) },
                    dimmed = menu != null && menu != DockSection.ADD,
                    materialChanged = { materialScales[DockSection.ADD] = it })
            }
        }
    }
}

@Composable
private fun DockCircle(section: DockSection, enabled: Boolean, active: Boolean, pressed: Boolean,
    anchors: MutableMap<DockSection, Rect>, tap: () -> Unit, hold: () -> Unit,
    dimmed: Boolean, materialChanged: (Float) -> Unit) {
    val reduced = LocalGlassEnvironment.current.reduceMotion
    val scale by animateFloatAsState(if (active) 1.18f else 1f,
        if (reduced) snap() else spring(dampingRatio = .8f), label = "dock icon")
    val rotation by animateFloatAsState(if (section == DockSection.ADD && active) 45f else 0f,
        if (reduced) snap() else spring(dampingRatio = .8f, stiffness = 450f), label = "dock add rotation")
    val materialScale by animateFloatAsState(if (reduced) 1f else if (pressed) .96f else if (active) 1.08f else 1f,
        if (reduced) snap() else spring(dampingRatio = .8f), label = "dock material")
    val emphasis by animateFloatAsState(if (dimmed) .28f else 1f, tween(if (reduced || !dimmed) 0 else 120), label = "dock dim")
    SideEffect { materialChanged(materialScale) }
    Box(Modifier.size(64.dp).onGloballyPositioned { anchors[section] = it.boundsInRoot() }
        .semantics { role = Role.Button; contentDescription = section.label
            stateDescription = if (active) "已展开，再次点击收起" else "未展开"; if (!enabled) disabled()
            onClick("${if (active) "收起" else "展开"}${section.label}菜单") { if (enabled) tap(); enabled }
            onLongClick("展开快捷菜单") { if (enabled) hold(); enabled }
        }
        .onKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyUp && event.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) { tap(); true } else false
        }.focusable(enabled)
        .graphicsLayer { alpha = if (enabled) { if (dimmed) emphasis else 1f } else .45f },
        contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { scaleX = materialScale; scaleY = materialScale }
            .glassSurface(GlassStyle.DOCK, CircleShape,
                when (section) {
                    DockSection.FOOD -> DockOrangeTint
                    DockSection.ADD -> DockAddTint
                    DockSection.WORKOUT -> DockGreenTint
                }, pressed))
        HomeIcon(when(section) { DockSection.WORKOUT -> HomeSymbol.WORKOUT; DockSection.FOOD -> HomeSymbol.FOOD; DockSection.ADD -> HomeSymbol.ADD },
            when (section) {
                DockSection.FOOD -> DockOrangeInk
                DockSection.ADD -> Color.White
                DockSection.WORKOUT -> DockGreenInk
            },
            Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation })
    }
}

// Slightly richer tints for the three home controls; keep other glass surfaces unchanged.
private val DockAddTint = Color.Black

