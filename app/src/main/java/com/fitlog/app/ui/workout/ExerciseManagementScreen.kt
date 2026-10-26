package com.fitlog.app.ui.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fitlog.app.domain.ExerciseSpec
import com.fitlog.app.domain.trainingCount
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

@Composable
fun ExerciseManagementScreen(nav: NavHostController, vm: AppViewModel) {
    val library by vm.exerciseLibrary.collectAsState()
    val busy by vm.busy.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("全部") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseSpec?>(null) }
    var deleting by remember { mutableStateOf<ExerciseSpec?>(null) }
    val visible = library.filter {
        (type == "全部" || it.typeLabel == type) &&
            (it.name.contains(query.trim(), true) || it.muscleGroups.any { group -> group.contains(query.trim(), true) })
    }
    PageScaffold("动作管理", { nav.popBackStack() }, actions = {
        AppIconButton(onClick = { creating = true }, enabled = !busy) { Icon(Icons.Default.Add, "新增动作", tint = Green) }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true, placeholder = { Text("搜索动作或部位") }, shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "器械", "自重", "有氧").forEach { label ->
                    AppFilterChip(type == label, { type = label }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${visible.size} 个动作", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("点击动作可编辑", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visible, key = { it.name }) { spec ->
                    AppCard(onClick = { if (!busy) editing = spec }, color = CardFill, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExerciseIcon(spec, Modifier.size(60.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(spec.name, style = MaterialTheme.typography.titleMedium)
                                    Text((listOf(spec.typeLabel) + spec.muscleGroups).joinToString(" · "), color = Muted,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(trainingCount(listOf(spec)), color = Green, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f))
                                AppIconButton(onClick = { editing = spec }, enabled = !busy) {
                                    Icon(Icons.Default.Edit, "编辑${spec.name}", tint = Green)
                                }
                                AppIconButton(onClick = { deleting = spec }, enabled = !busy) {
                                    Icon(Icons.Default.Delete, "删除${spec.name}", tint = Muted)
                                }
                            }
                        }
                    }
                }
                if (visible.isEmpty()) item {
                    Text(if (query.isBlank() && type == "全部") "动作库为空，点击右上角新增动作" else "没有找到匹配的动作",
                        color = Muted, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
        }
    }
    if (creating) CustomExerciseSheet(vm, library.map { it.name }.toSet(), { creating = false }) {}
    editing?.let { spec -> key(spec.name) {
        CustomExerciseSheet(vm, library.map { it.name }.toSet(), { editing = null }, initial = spec) {}
    } }
    deleting?.let { spec ->
        AppAlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除${spec.name}？") },
            text = { Text("从动作库移除，已有训练计划和历史记录会保留。") },
            confirmButton = { AppTextButton({ vm.deleteExercise(spec) { deleting = null } }, enabled = !busy) { Text("删除") } },
            dismissButton = { AppTextButton({ deleting = null }, enabled = !busy) { Text("取消") } })
    }
}
