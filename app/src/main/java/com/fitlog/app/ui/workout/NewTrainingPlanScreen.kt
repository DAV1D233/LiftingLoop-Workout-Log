package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import com.fitlog.app.ui.components.AppCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fitlog.app.R
import com.fitlog.app.data.ExerciseJson
import com.fitlog.app.data.PresetData
import com.fitlog.app.domain.ExerciseSpec
import com.fitlog.app.domain.number
import com.fitlog.app.domain.trainingCount
import com.fitlog.app.domain.PlanSets
import com.fitlog.app.domain.isInterval
import com.fitlog.app.feedback.FeedbackCue
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.Routes
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*
import kotlinx.coroutines.launch

private val PlanDraftSaver = Saver<List<ExerciseSpec>, String>(
    save = { ExerciseJson.encode(it) }, restore = { ExerciseJson.decode(it) })
private val ExerciseNamesSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) }, restore = { it.toList() })

@Composable
fun NewTrainingPlanScreen(nav: NavHostController, vm: AppViewModel, attachToSchedule: Boolean = false, editPlanId: Long? = null) {
    var planName by rememberSaveable(editPlanId) { mutableStateOf("") }
    var missingName by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var chosen by rememberSaveable(editPlanId, stateSaver = PlanDraftSaver) { mutableStateOf(emptyList<ExerciseSpec>()) }
    val exerciseLibrary by vm.exerciseLibrary.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("全部") }
    var equipment by rememberSaveable { mutableStateOf("全部类型") }
    var overview by rememberSaveable(editPlanId) { mutableStateOf(editPlanId != null) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf<String?>(null) }
    var editSet by rememberSaveable { mutableIntStateOf(0) }
    val libraryScroll = rememberLazyListState()
    val overviewScroll = rememberLazyListState()
    val busy by vm.busy.collectAsState()
    var loaded by rememberSaveable(editPlanId) { mutableStateOf(editPlanId == null) }
    var loadFailed by rememberSaveable(editPlanId) { mutableStateOf(false) }
    LaunchedEffect(editPlanId) {
        if (editPlanId == null || loaded) return@LaunchedEffect
        try {
            val plan = vm.trainingPreview("plan:$editPlanId")
            if (plan == null) { loadFailed = true; vm.message("计划已不存在，请返回重新选择") }
            else { planName = plan.title; chosen = plan.exercises.map(PlanSets::explicit) }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { loadFailed = true; vm.message("计划读取失败，请返回后重试") }
        loaded = true
    }
    fun goBack() {
        if (busy) return
        when {
            editPlanId != null && overview -> nav.popBackStack()
            overview -> overview = false
            editPlanId != null -> overview = true
            else -> nav.popBackStack()
        }
    }
    val library = exerciseLibrary.map { (if (it.isCardio) "有氧" else it.muscleGroups.firstOrNull() ?: "自定义") to it }
    fun equipmentLabel(spec: ExerciseSpec) = spec.typeLabel
    val visible = library.filter { (category, spec) ->
        (group == "全部" || (group == "有氧" && spec.isCardio) || group in spec.muscleGroups || (group == "自定义" && spec.muscleGroups.isEmpty())) && spec.name.contains(search.trim(), ignoreCase = true) &&
            (equipment == "全部类型" || equipmentLabel(spec) == equipment)
    }
    LaunchedEffect(group, equipment, search) { libraryScroll.scrollToItem(0) }
    BackHandler(overview || busy || editPlanId != null) { goBack() }
    fun toggle(spec: ExerciseSpec) {
        if (busy) return
        chosen = if (chosen.any { it.name == spec.name }) chosen.filterNot { it.name == spec.name } else chosen + PlanSets.explicit(vm.rememberedExercise(spec))
        vm.cue(FeedbackCue.SELECT)
    }
    PageScaffold(if (overview) { if (editPlanId != null) "编辑训练计划" else "计划概览" } else if (editPlanId != null) "添加动作" else "新建训练计划",
        back = ::goBack,
        actions = {
            AppTextButton(onClick = { if (overview) overview = false else showCustom = true }, enabled = !busy && loaded && !loadFailed) {
                Text(if (overview) "继续添加" else "＋ 自定义")
            }
        }, bottom = {
            if (overview) PrimaryAction(if (busy) "保存中…" else if (editPlanId != null) "保存修改" else "保存训练计划",
                enabled = chosen.isNotEmpty() && !busy && loaded && !loadFailed) {
                if (planName.isBlank()) {
                    missingName = true
                    vm.message("请先填写训练计划名称")
                    scope.launch {
                        overviewScroll.scrollToItem(0)
                        withFrameNanos { }
                        nameFocus.requestFocus()
                        keyboard?.show()
                    }
                    return@PrimaryAction
                }
                vm.savePlan(planName.trim(), chosen.map(PlanSets::explicit), planId = editPlanId) { id ->
                    if (editPlanId != null || nav.previousBackStackEntry?.destination?.route in listOf(Routes.PLAN_LIBRARY, Routes.PLAN_PICKER)) {
                        nav.popBackStack()
                    } else if (attachToSchedule) {
                        nav.previousBackStackEntry?.savedStateHandle?.set("createdSchedulePlan", "plan:$id")
                        nav.popBackStack()
                    } else nav.navigate(Routes.PLAN_LIBRARY) {
                        popUpTo("editor?mode={mode}") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            } else Surface(color = GreenAccent, contentColor = Ink, shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Column(Modifier.weight(1f)) {
                        Text("已选 ${chosen.size} 个动作", style = MaterialTheme.typography.titleMedium)
                        Text(if (chosen.isEmpty()) "挑选喜欢的动作" else trainingCount(chosen),
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .8f))
                    }
                    AppButton(onClick = { overview = true }, enabled = chosen.isNotEmpty() && !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenTint, contentColor = Green)) { Text("查看概览") }
                }
            }
        }) { padding ->
        if (!loaded || loadFailed) {
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                if (!loaded) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在读取训练计划…", Modifier.padding(top = 16.dp)) }
                else Text("无法读取训练计划，请返回后重新选择。", color = Muted)
            }
        } else if (overview) {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), state = overviewScroll,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(planName, { planName = it; if (it.isNotBlank()) missingName = false },
                        Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(nameFocus),
                        label = { Text("计划名称") }, placeholder = { Text("为这份计划取个名字") }, singleLine = true,
                        enabled = !busy, shape = RoundedCornerShape(18.dp), isError = missingName,
                        supportingText = { if (missingName) Text("请填写训练计划名称后保存") })
                    Text("${chosen.size} 个动作 · ${trainingCount(chosen)}", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 12.dp))
                    Text("点击数字调整，保存后随时开始。", color = Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                }
                if (chosen.isEmpty()) item {
                    EmptyState("还没有动作", "返回动作库挑选喜欢的动作。", "挑选动作") { overview = false }
                }
                items(chosen, key = { it.name }) { spec ->
                    val index = chosen.indexOfFirst { it.name == spec.name }
                    var more by remember { mutableStateOf(false) }
                    AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}".padStart(2, '0'), color = Green, modifier = Modifier.padding(end = 12.dp))
                                Text(spec.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Box {
                                    AppIconButton(onClick = { more = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "动作选项") }
                                    DropdownMenu(more, { more = false }) {
                                        DropdownMenuItem(text = { Text("上移") }, enabled = index > 0,
                                            onClick = { chosen = chosen.toMutableList().apply { add(index - 1, removeAt(index)) }; more = false })
                                        DropdownMenuItem(text = { Text("下移") }, enabled = index < chosen.lastIndex,
                                            onClick = { chosen = chosen.toMutableList().apply { add(index + 1, removeAt(index)) }; more = false })
                                        DropdownMenuItem(text = { Text("移除动作") }, onClick = { toggle(spec); more = false })
                                    }
                                }
                            }
                            if (spec.isCardio) AppParameterSurface(onClick = { editSet = 0; editName = spec.name },
                                enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = FieldFill,
                                shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (spec.isInterval) "间歇训练 · ${spec.sets} 轮" else "连续运动", color = Green, style = MaterialTheme.typography.titleMedium)
                                    Text(spec.cardioTarget.overview(spec.reps), color = Ink)
                                    if (spec.isInterval) Text("轮间休息 ${spec.restSeconds} 秒", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text("修改运动设置 →", color = Green, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (!spec.isCardio) PlanSetRows(spec, !busy) { updated ->
                                chosen = chosen.map { if (it.name == spec.name) updated else it }
                            }
                            Text(spec.muscleGroups.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp))
                            if (spec.isBodyweight) Text("自重会自动使用开始训练时的体重", color = Muted,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))

                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("搜索动作") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(18.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("全部") + (library.flatMap { it.second.muscleGroups } + listOf("有氧", "自定义")).distinct()).forEach { category ->
                            AppFilterChip(group == category, { group = category }, label = { Text(category) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenTint, selectedLabelColor = Green))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("全部类型", "自重", "器械", "有氧").forEach { kind ->
                            AppFilterChip(equipment == kind, { equipment = kind }, label = { Text(kind) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenTint, selectedLabelColor = Green))
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), state = libraryScroll, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (visible.isEmpty()) item { Text("没有找到动作，试试其他部位、类型或关键词，也可添加自定义动作。", color = Muted, modifier = Modifier.padding(vertical = 24.dp)) }
                    visible.groupBy { if (group == "全部") it.first else group }.forEach { (category, entries) ->
                    item(key = "group:$category") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(category, style = MaterialTheme.typography.titleMedium)
                            HorizontalDivider(Modifier.weight(1f), color = Line)
                            Text("${entries.size} 个动作", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(entries, key = { it.second.name }) { (_, spec) ->
                        val selected = chosen.any { it.name == spec.name }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(if (selected) GreenTint else CardFill)

                            .toggleable(selected, enabled = !busy, role = Role.Checkbox, onValueChange = { toggle(spec) })
                            .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExerciseIcon(spec, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(spec.name, style = MaterialTheme.typography.titleMedium)
                                Text((listOf(equipmentLabel(spec)) + spec.muscleGroups).joinToString(" · "),
                                    color = Muted, style = MaterialTheme.typography.labelMedium)
                            }
                            Box(Modifier.size(36.dp).background(if (selected) Green else FieldFill, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (selected) Icons.Default.Check else Icons.Default.Add,
                                    if (selected) "已选，点击移除" else "添加动作", tint = if (selected) Color.White else Green,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    chosen.firstOrNull { it.name == editName }?.let { spec ->
        ExerciseParameterSheet(spec, initialSet = editSet, dismiss = { editName = null }, save = { updated ->
            chosen = chosen.map { if (it.name == updated.name) updated else it }
            editName = null
        })
    }
    if (showCustom) CustomExerciseSheet(vm, library.map { it.second.name }.toSet(), { showCustom = false }) { spec ->
        chosen = chosen + PlanSets.explicit(spec)
        group = if (spec.isCardio) "有氧" else spec.muscleGroups.firstOrNull() ?: "自定义"; search = ""; equipment = "全部类型"
    }
}

