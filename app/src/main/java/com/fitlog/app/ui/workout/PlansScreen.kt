package com.fitlog.app.ui.workout

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import com.fitlog.app.data.WorkoutPlan
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.components.AppAlertDialog
import com.fitlog.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlansScreen(nav: NavHostController, vm: AppViewModel, selecting: Boolean = false,
    scheduleEntry: NavBackStackEntry? = null, managing: Boolean = false) {
    if (!selecting && !managing) {
        TrainingScheduleScreen(nav, vm, requireNotNull(scheduleEntry))
        return
    }
    val data by vm.data.collectAsState()
    val busy by vm.busy.collectAsState()
    val active by vm.session.collectAsState()
    var options by remember { mutableStateOf<WorkoutPlan?>(null) }
    var deleting by remember { mutableStateOf<WorkoutPlan?>(null) }
    PageScaffold(if (selecting) "选择训练计划" else "训练计划", { nav.popBackStack() }, actions = {
        AppIconButton(onClick = { nav.navigate(Routes.PLAN_EDITOR) }, enabled = !busy) {
            Icon(Icons.Default.Add, "添加训练计划", tint = Green)
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            item {
                Text("长按训练计划可编辑或删除", color = Muted, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp))
                if (selecting && active != null) ActionRow("继续上次训练", active!!.title,
                    { nav.navigate(Routes.preview("active")) }, trailing = "进行中")
            }
            items(data.plans, key = { it.id }) { plan ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).motionCombinedClickable(enabled = !busy,
                    onClick = {
                        if (selecting) nav.navigate(Routes.preview(if (active != null) "active" else "plan:${plan.id}"))
                        else nav.navigate(Routes.editPlan(plan.id))
                    }, onLongClickLabel = "编辑或删除训练计划", onLongClick = { options = plan })
                    .padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, style = MaterialTheme.typography.titleLarge)
                        Text("${plan.exerciseCount} 个动作", color = Muted,
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(if (selecting) "开始 →" else "查看 →", color = Green)
                }
                HorizontalDivider(color = Line)
            }
            if (!data.ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (data.ready && data.plans.isEmpty()) item {
                EmptyState("为自己定一个计划。", "选动作、排顺序，保存后可加入训练周期。", "新建计划") {
                    nav.navigate(Routes.PLAN_EDITOR)
                }
            }
        }
    }
    options?.let { plan ->
        AppAlertDialog(onDismissRequest = { options = null }, title = { Text(plan.name) }, text = {
            Column {
                AppTextButton(onClick = { options = null; nav.navigate(Routes.editPlan(plan.id)) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("编辑训练计划") }
                AppTextButton(onClick = { options = null; deleting = plan }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("删除训练计划", color = Muted) }
            }
        }, confirmButton = { AppTextButton(onClick = { options = null }) { Text("关闭") } })
    }
    deleting?.let { plan -> DeleteDialog(plan.name, { deleting = null }) {
        if (!busy) { vm.deletePlan(plan); deleting = null }
    } }
}
