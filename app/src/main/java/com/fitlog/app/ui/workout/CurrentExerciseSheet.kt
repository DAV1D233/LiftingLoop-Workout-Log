package com.fitlog.app.ui.workout

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitlog.app.domain.*
import com.fitlog.app.ui.AppViewModel
import com.fitlog.app.ui.components.PrimaryAction
import com.fitlog.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrentExerciseSheet(session: WorkoutSession, busy: Boolean, dismiss: () -> Unit, vm: AppViewModel,
    openTutorial: Boolean = false) {
    val context = LocalContext.current
    val current = session.current
    val editable = session.endedAt == null
    var parameters by remember(session.key, session.index) { mutableStateOf<ExerciseSpec?>(current) }
    val minimum = (session.currentDone + if (current.isCardio) 1 else 0).coerceAtLeast(1)
    val tutorial = ExerciseTutorials.forExercise(current.name)
    var playingTutorial by rememberSaveable(session.key, session.index) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var locatedTutorial by remember(session.key, session.index, openTutorial) { mutableStateOf(false) }
    LaunchedEffect(openTutorial, scroll.maxValue) {
        if (openTutorial && !locatedTutorial && scroll.maxValue > 0) {
            scroll.scrollTo(scroll.maxValue)
            locatedTutorial = true
        }
    }
    val tutorialUrl = current.videoUrl.takeIf { it.isNotBlank() && ExerciseVideoLink.valid(it) } ?: tutorial?.url
    fun openVideo() {
        if (tutorialUrl != null) { playingTutorial = true; return }
        val url = "https://search.bilibili.com/all?keyword=${Uri.encode(current.name + " 动作教学") }"
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { vm.message("未找到可打开视频的应用，请安装浏览器或哔哩哔哩。") }
    }
    FitLogTheme {
    AppModalBottomSheet(onDismissRequest = dismiss, containerColor = Paper,
        contentColor = Ink, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(scroll).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(current.name, style = MaterialTheme.typography.headlineMedium)
            Text("本次训练 · 已完成 ${session.currentDone} ${if (current.isCardio) "段" else "组"}", color = Muted,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            if (editable) {
                AppCard(color = CardFill, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (current.isCardio) CardioInputFields(current, minimum, !busy, editMuscles = false) { parameters = it }
                        else PlanSetRows(parameters ?: current, !busy, completedSets = session.currentDone) { parameters = it }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (current.isBodyweight) Text("体重 ${session.bodyWeightKg?.let(::number) ?: "未填写"} kg ＋ 额外负重",
                    color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                PrimaryAction("保存动作设置", !busy && parameters != null && parameters != current, Green) {
                    parameters?.let { vm.editCurrentExercise(session.key, session.index, it, dismiss) }
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = Muted.copy(alpha = .2f))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExerciseIcon(current, Modifier.size(72.dp))
                Column(Modifier.weight(1f)) {
                    Text("动作讲解", style = MaterialTheme.typography.titleMedium)
                    Text(if (current.videoUrl.isNotBlank()) "自定义 B站讲解视频" else tutorial?.title ?: "查找这个动作的讲解视频", color = Ink,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(if (current.videoUrl.isNotBlank()) "使用你添加的视频链接" else tutorial?.let { "B站 UP · ${it.uploader}" } ?: "此动作暂未匹配固定视频", color = Muted,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
            AppOutlinedButton(onClick = ::openVideo, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 54.dp)) {
                Text(if (tutorialUrl != null) "▶ 观看动作讲解" else "去 B站搜索此动作")
            }
            AppTextButton(onClick = dismiss, modifier = Modifier.padding(top = 8.dp)) { Text("返回训练", color = Muted) }
        }
    }
    if (playingTutorial && tutorialUrl != null) TutorialVideoDialog(tutorialUrl, current.name, { playingTutorial = false })
}
}
