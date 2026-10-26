package com.fitlog.app.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitlog.app.ui.SessionSummary
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

/** An opaque, bounded window keeps the training page out of the record's safe areas. */
@Composable
internal fun CompletedTrainingSheet(summary: SessionSummary, dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FitLogTheme {
            val view = LocalView.current
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = true
                        isAppearanceLightNavigationBars = true
                    }
                }
            }
            BackHandler(onBack = dismiss)
            Column(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(dismiss) { Text("返回") }
                    Spacer(Modifier.weight(1f))
                    WorkoutShareButton(summary, compact = true)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    TrainingAchievementCard(summary, detailed = true)
                }
            }
        }
    }
}
