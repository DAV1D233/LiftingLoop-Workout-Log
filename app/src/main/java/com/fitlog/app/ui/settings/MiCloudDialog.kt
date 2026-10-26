package com.fitlog.app.ui.settings

import com.fitlog.app.ui.components.*

import com.fitlog.app.ui.glass.*

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitlog.app.data.mijia.MiCloudClient
import com.fitlog.app.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MiCloudDialog(vm: AppViewModel, dismiss: () -> Unit) {
    val state by vm.mijia.values.collectAsState()
    var login by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf("cn") }
    AppAlertDialog(onDismissRequest = dismiss, title = { Text("米家体重同步") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("S200 · yunmai.scales.ms106\n同步米家已上传的体重，用于之后的自重训练。")
                Text(state.status, color = MaterialTheme.colorScheme.primary)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.connected || state.needsLogin) {
                    Text("选择与米家一致的地区，在小米页面完成登录。LiftingLoop 不保存密码。")
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        AppOutlinedButton(onClick = { expanded = true }, enabled = !state.busy) { Text("地区：${MiCloudClient.regions[region]}") }
                        DropdownMenu(expanded, { expanded = false }) {
                            MiCloudClient.regions.forEach { (code, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { region = code; expanded = false }) }
                        }
                    }
                }
                if (state.connected && !state.needsLogin) {
                    Text("选择你的成员", style = MaterialTheme.typography.titleSmall)
                    state.profiles.forEach { member ->
                        AppOutlinedButton(onClick = { vm.selectMiMember(member.key) }, enabled = !state.busy && state.selected != member.key,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("${if (state.selected == member.key) "✓ " else ""}${member.name} · ${member.scale}")
                        }
                    }
                    AppTextButton(onClick = { vm.refreshMiMembers() }, enabled = !state.busy) { Text("刷新设备与成员") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("打开应用时自动同步", modifier = Modifier.weight(1f).padding(top = 12.dp))
                        AppSwitch(state.auto, { vm.setMiAuto(it) }, enabled = !state.busy)
                    }
                    Text("应用在前台时每 5 分钟检查一次，回到应用时也会检查。请先通过米家或蓝牙网关上传称重结果。",
                        style = MaterialTheme.typography.bodySmall)
                    if (state.measuredAt > 0) Text("已检查的最新称重：" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(state.measuredAt)), style = MaterialTheme.typography.bodySmall)
                    AppButton(onClick = { vm.syncMiWeight() }, enabled = !state.busy && state.selected != null) { Text("立即同步") }
                }
                Text("实验性同步。读取失败会保留当前体重；更新的手动体重优先保留，进行中的训练沿用原体重。",
                    style = MaterialTheme.typography.bodySmall)
                if (state.connected) AppTextButton(onClick = { vm.disconnectMi() }, enabled = !state.busy) { Text("断开米家") }
            }
        },
        confirmButton = {
            if (!state.connected || state.needsLogin) AppTextButton(onClick = { login = true }, enabled = !state.busy) { Text("登录小米账号") }
            else AppTextButton(onClick = dismiss) { Text("完成") }
        }, dismissButton = { AppTextButton(onClick = dismiss) { Text("关闭") } })
    if (login) MiLoginDialog({ login = false }) { uid, pass ->
        login = false
        vm.connectMi(uid, pass, region)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiLoginDialog(dismiss: () -> Unit, connected: (String, String) -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("正在打开小米账号登录页…") }
    var submitted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var pageReady by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var loadId by remember { mutableIntStateOf(0) }
    var browserId by remember { mutableIntStateOf(0) }
    var disposed by remember { mutableStateOf(false) }
    var clearingCookies by remember { mutableStateOf(false) }
    var cookiesReady by remember { mutableStateOf(false) }
    val released = remember { mutableSetOf<WebView>() }
    fun release(view: WebView) {
        if (web === view) web = null
        if (released.add(view)) { view.stopLoading(); view.destroy() }
    }
    fun fail(message: String) {
        if (disposed) return
        loading = false; pageReady = false; pageError = message
        status = "小米登录页未能正常打开。"
    }
    fun retry() {
        if (submitted) return
        web?.stopLoading()
        loading = true; pageReady = false; pageError = null
        status = "正在重新打开小米账号登录页…"
        loadId++
        // Recreate a renderer that may have failed, without clearing login cookies again.
        browserId++
    }
    fun finish() {
        if (submitted || !pageReady || pageError != null) return
        val cookies = MiCloudClient.cookies(CookieManager.getInstance().getCookie("https://account.xiaomi.com").orEmpty())
        val uid = cookies["userId"]
        val pass = cookies["passToken"]
        if (!uid.isNullOrBlank() && !pass.isNullOrBlank()) {
            submitted = true
            connected(uid, pass)
        } else status = "尚未取得登录状态，请完成小米页面的登录或验证后重试。"
    }
    LaunchedEffect(loadId, loading) {
        if (loading) {
            delay(25000)
            if (loading && !disposed) fail("加载超过 25 秒。请检查网络，或更新 Android System WebView / Chrome 后重新打开登录页。")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            disposed = true
            // AndroidView releases its own renderer; never keep account pass tokens after closing.
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(onClick = dismiss) { Text("取消") }
                    Text("小米账号", style = MaterialTheme.typography.titleMedium)
                    AppTextButton(onClick = { finish() }, enabled = pageReady && pageError == null && !submitted) { Text("完成登录") }
                }
                Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    key(browserId) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                            try {
                                WebView(context).apply {
                                    web = this
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val blocked = !MiCloudClient.trustedLoginUrl(request.url.toString())
                                            if (blocked && request.isForMainFrame) fail("无法在应用内打开该登录跳转，请重新打开小米登录页。")
                                            return blocked
                                        }
                                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                            if (disposed || web !== view) return
                                            if (!MiCloudClient.trustedLoginUrl(url)) {
                                                view.stopLoading(); fail("已阻止非小米登录地址"); return
                                            }
                                            loadId++; loading = true; pageReady = false; pageError = null
                                            status = "正在加载小米账号登录页…"
                                        }
                                        override fun onPageCommitVisible(view: WebView, url: String) {
                                            if (!disposed && web === view && pageError == null && MiCloudClient.trustedLoginUrl(url)) {
                                                loading = false; pageReady = true
                                                status = "请在下方小米页面登录，完成后点击“完成登录”。"
                                            }
                                        }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                            if (web === view && request.isForMainFrame)
                                                fail("无法连接小米登录页（错误 ${error.errorCode}）。请检查网络后重试。")
                                        }
                                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                            if (web === view && request.isForMainFrame)
                                                fail("小米登录服务暂时无法访问（HTTP ${errorResponse.statusCode}），请稍后重试。")
                                        }
                                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                            handler.cancel()
                                            if (web === view) fail("小米登录页的安全连接失败。请检查手机日期和网络后重试。")
                                        }
                                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                            if (web === view) fail("网页组件已停止运行，请重新打开登录页。")
                                            (view.parent as? ViewGroup)?.removeView(view)
                                            release(view)
                                            return true
                                        }
                                    }
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, false)
                                    if (cookiesReady) loadUrl(MiCloudClient.LOGIN_URL)
                                    else if (!clearingCookies) {
                                        clearingCookies = true
                                        cookieManager.removeAllCookies {
                                            if (!disposed) {
                                                cookiesReady = true
                                                web?.loadUrl(MiCloudClient.LOGIN_URL)
                                            }
                                        }
                                    }
                                }
                            } catch (_: RuntimeException) {
                                web?.let(::release)
                                fail("无法启动网页组件，请启用或更新 Android System WebView / Chrome 后重试。")
                                TextView(context)
                            }
                        }, onRelease = { view ->
                            if (view is WebView) release(view)
                        })
                    }
                    pageError?.let { message ->
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("登录页加载失败", style = MaterialTheme.typography.titleLarge)
                                Text(message, modifier = Modifier.padding(vertical = 16.dp))
                                AppButton(onClick = ::retry) { Text("重新打开小米登录页") }
                            }
                        }
                    }
                }
                AppTextButton(onClick = ::retry, enabled = !submitted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("重新打开登录页") }
            }
        }
    }
}
