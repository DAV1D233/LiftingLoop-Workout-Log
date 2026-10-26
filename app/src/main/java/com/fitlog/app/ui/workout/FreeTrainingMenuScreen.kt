package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.components.AppCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fitlog.app.ui.Routes
import com.fitlog.app.ui.components.PageScaffold
import com.fitlog.app.ui.theme.*

@Composable
fun FreeTrainingMenuScreen(nav: NavHostController) {
    PageScaffold("自由训练", { nav.popBackStack() }) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Triple("从现有训练计划开始", "挑选一份计划，直接开始训练", Routes.PLAN_PICKER),
                Triple("新建训练计划", "挑选动作、调整参数并保存", Routes.PLAN_EDITOR)).forEachIndexed { index, (title, detail, route) ->
                AppCard(onClick = { nav.navigate(route) }, shape = RoundedCornerShape(24.dp), color = CardFill) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(if (index == 0) Icons.AutoMirrored.Filled.List else Icons.Default.Add, null, tint = Green)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                        Text("→", color = Green)
                    }
                }
            }
        }
    }
}
