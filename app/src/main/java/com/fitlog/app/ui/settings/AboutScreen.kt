package com.fitlog.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitlog.app.BuildConfig
import com.fitlog.app.ui.components.*
import com.fitlog.app.ui.theme.*

@Composable
internal fun AboutScreen(back: () -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    PageScaffold("关于", back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LiftingLoop", color = Ink, style = MaterialTheme.typography.headlineLarge)
                Text(BuildConfig.VERSION_NAME, color = Green, style = MaterialTheme.typography.titleMedium)
                Text("design by DAVID DAI", color = Muted, style = MaterialTheme.typography.bodyLarge)
            }
            AboutSection("设计与开发") {
                Text("AI 辅助开发：OpenAI Codex", color = Ink)
                Text("UI 设计辅助：Autodesk AutoCAD 2022", color = Ink)
                Text("部分代码、界面与文案由 AI 辅助完成。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            AboutSection("免责声明") {
                Text("LiftingLoop 用于个人运动与饮食记录，不提供医疗诊断或治疗建议，也不能替代专业指导。请根据自身情况安排训练；如有不适，请停止运动并寻求专业帮助。", color = Muted)
                Text("食物营养、训练容量及体重秤同步等数据可能存在估算、来源差异或同步延迟，仅供记录参考。请核对关键数据，尤其是自定义与 AI 辅助整理的内容。", color = Muted)
                Text("第三方视频、品牌及图片的权利归原权利人所有。相关内容用于信息展示，不代表官方合作或认可。", color = Muted)
            }
            AboutSection("版本信息") {
                Text("版本 ${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}", color = Muted)
                Text("当前为 Beta 测试版本，可通过截图记录遇到的问题。", color = Muted, style = MaterialTheme.typography.bodySmall)
                AppTextButton({ licenses = !licenses }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (licenses) "收起开源与来源说明 ↑" else "开源与来源说明 →")
                }
                if (licenses) {
                    val notices = remember { context.assets.open("THIRD-PARTY-NOTICES.txt").bufferedReader().use { it.readText() } }
                    Text(notices, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleLarge)
        content()
    }
}
