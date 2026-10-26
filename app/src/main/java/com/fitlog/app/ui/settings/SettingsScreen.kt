package com.fitlog.app.ui.settings

import com.fitlog.app.ui.glass.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fitlog.app.BuildConfig
import com.fitlog.app.data.planLabel
import com.fitlog.app.domain.number
import com.fitlog.app.feedback.FeedbackCue
import com.fitlog.app.ui.*
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

@Composable
fun SettingsScreen(nav: NavHostController, vm: AppViewModel) {
    val prefs by vm.preferences.collectAsState()
    val data by vm.data.collectAsState()
    val mi by vm.mijia.values.collectAsState()
    var editWeight by remember { mutableStateOf(false) }
    var scaleInfo by remember { mutableStateOf(false) }
    PageScaffold("设置", { nav.popBackStack() }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            SectionHeading("身体数据")
            ActionRow("我的体重", prefs.bodyWeightKg?.let { "${number(it)} kg · 用于自重训练" } ?: "填写体重，自重动作自动带入",
                { editWeight = true }, "编辑")
            ActionRow("小米 S200", mi.status, { scaleInfo = true }, if (mi.connected) "同步" else "连接")
            SectionHeading("反馈")
            FeedbackSwitch("触觉反馈", "选择、完成与目标达成时的节奏震动", prefs.haptics) { vm.settings.feedback(haptics = it) }
            FeedbackSwitch("声音反馈", "确认、休息结束与训练完成的提示音", prefs.sounds) { vm.settings.feedback(sounds = it) }
            AppTextButton(onClick = { vm.cue(FeedbackCue.FINISH) }, contentPadding = PaddingValues(0.dp)) { Text("体验完成反馈 →") }
            SectionHeading("动效与辅助功能")
            FeedbackSwitch("生动动效", if (prefs.reduceMotion) "减少动态效果已开启，当前动效受其限制" else "按钮回弹、页面转场、训练完成柔光彩带与勾选绘制；关闭即停用", prefs.livelyMotion) {
                vm.settings.appearance(livelyMotion = it)
            }
            Text(glassStatusLabel(), color = Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp))
            FeedbackSwitch("减少动态效果", "优先停用生动动效、玻璃弹性和追光；系统关闭动画时也自动减少", prefs.reduceMotion) {
                vm.settings.appearance(reduceMotion = it)
            }
            FeedbackSwitch("降低透明度", "使用清晰的实体材质，关闭背景折射和模糊", prefs.reduceTransparency) {
                vm.settings.appearance(reduceTransparency = it)
            }
            SectionHeading("训练")
            ActionRow("训练周期", if (prefs.scheduleMode == "cycle") "${prefs.cycle.size} 天一个周期 · 训练与休息按顺序进行" else "每周一个周期 · 按星期安排训练",
                { nav.navigate(Routes.PLANS) }, "编辑")
            Box(Modifier.padding(top = 28.dp, bottom = 32.dp)) {
                ActionRow("LiftingLoop", BuildConfig.VERSION_NAME, { nav.navigate(Routes.ABOUT) }, "关于")
            }
        }
    }
    if (editWeight) BodyWeightDialog(vm) { editWeight = false }
    if (scaleInfo) MiCloudDialog(vm) { scaleInfo = false }
}

@Composable
private fun FeedbackSwitch(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        AppSwitch(checked, change)
    }
    HorizontalDivider(color = Line)
}
