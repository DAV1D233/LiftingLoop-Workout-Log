package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fitlog.app.data.ExerciseJson
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.PrimaryAction
import com.fitlog.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomExerciseSheet(vm: AppViewModel, names: Set<String>, dismiss: () -> Unit,
    initial: ExerciseSpec? = null, added: (ExerciseSpec) -> Unit) {
    val busy by vm.busy.collectAsState()
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.typeLabel ?: "器械") }
    var icon by rememberSaveable { mutableStateOf(initial?.iconPath.orEmpty()) }
    var video by rememberSaveable { mutableStateOf(initial?.videoUrl.orEmpty()) }
    var parameters by remember { mutableStateOf<ExerciseSpec?>(initial ?: ExerciseSpec("自定义动作")) }
    val duplicate = name.trim() in names && name.trim() != initial?.name
    val videoValid = ExerciseVideoLink.valid(video)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importExerciseIcon(uri) { icon = it }
    }
    val original = (initial ?: if (kind == "有氧") ExerciseSpec("自定义动作", sets = 1, reps = 1, restSeconds = 0,
        cardioTarget = CardioTarget("自由", 0, training = "continuous")) else ExerciseSpec("自定义动作"))
        .copy(isBodyweight = kind == "自重", isCardio = kind == "有氧")
    AppModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initial == null) "新增动作" else "编辑动作", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseIcon(original.copy(iconPath = icon), Modifier.size(64.dp))
                AppTextButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("选择图标") }
                AppTextButton(onClick = { icon = "" }, enabled = !busy && icon.isNotEmpty()) { Text("使用默认") }
            }
            OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("动作名称") }, isError = duplicate, enabled = !busy, shape = RoundedCornerShape(18.dp),
                supportingText = { if (duplicate) Text("动作库中已有这个名称") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("自重", "器械", "有氧").forEach { type ->
                    AppFilterChip(kind == type, { kind = type }, enabled = !busy, label = { Text(type) })
                }
            }
            key(kind) { CustomPlanParameters(original, !busy) { parameters = it } }
            OutlinedTextField(video, { video = it.trim().take(2048) }, Modifier.fillMaxWidth(),
                label = { Text("B站动作讲解链接（可选）") }, singleLine = true, isError = !videoValid, enabled = !busy,
                supportingText = { Text(if (videoValid) "粘贴 https://www.bilibili.com/video/… 或 https://b23.tv/…"
                    else "请粘贴 B站视频地址或 b23.tv 短链接") })
            PrimaryAction(if (busy) "保存中…" else if (initial != null) "保存修改" else "保存并添加动作", !busy && name.isNotBlank() && !duplicate && videoValid && parameters != null) {
                parameters?.let { values ->
                    val spec = values.copy(name = name.trim(), isBodyweight = kind == "自重", isCardio = kind == "有氧",
                        iconPath = icon, videoUrl = video.trim())
                    vm.saveCustomExercise(spec, initial?.name) { added(spec); dismiss() }
                }
            }
            AppTextButton(onClick = dismiss, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消") }
        }
    }
}

@Composable
private fun CustomPlanParameters(original: ExerciseSpec, enabled: Boolean, onValue: (ExerciseSpec?) -> Unit) {
    if (original.isCardio) {
        CardioInputFields(original, enabled = enabled, onValue = onValue)
        return
    }
    var json by rememberSaveable { mutableStateOf(ExerciseJson.encode(listOf(PlanSets.explicit(original)))) }
    var muscles by rememberSaveable { mutableStateOf(original.muscleGroups.joinToString("、")) }
    val spec = ExerciseJson.decode(json).first().copy(muscleGroup = muscles)
    SideEffect { onValue(spec) }
    AppCard(color = CardFill, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("训练参数", style = MaterialTheme.typography.titleLarge)
            PlanSetRows(spec, enabled) { json = ExerciseJson.encode(listOf(it)) }
        }
    }
    MuscleGroupInput(original, enabled) { muscles = it }
}


