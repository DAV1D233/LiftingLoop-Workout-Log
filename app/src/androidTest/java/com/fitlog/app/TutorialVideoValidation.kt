package com.fitlog.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fitlog.app.data.video.BilibiliVideoClient
import com.fitlog.app.ui.theme.FitLogTheme
import com.fitlog.app.ui.workout.TutorialVideoDialog
import com.fitlog.app.ui.workout.tutorialSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** No AppViewModel, database, account cookies or user records are created or touched. */
@androidx.annotation.OptIn(UnstableApi::class)
class TutorialVideoValidation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        val root = File(targetContext.cacheDir, "tutorial-validation-${UUID.randomUUID()}").apply { mkdirs() }
        var activity: WorkoutValidationActivity? = null
        var player: ExoPlayer? = null
        var count = 0
        var result = Activity.RESULT_OK
        val priorRotation = android.provider.Settings.System.getInt(targetContext.contentResolver, "user_rotation", 0)
        val priorAutoRotation = android.provider.Settings.System.getInt(targetContext.contentResolver, "accelerometer_rotation", 1)
        fun verify(ok: Boolean, message: String) { count++; check(ok) { message } }
        fun await(label: String, millis: Long = 12000, condition: () -> Boolean) {
            val end = System.currentTimeMillis() + millis
            while (!condition()) { check(System.currentTimeMillis() < end) { "Timeout: $label" }; Thread.sleep(40) }
        }
        fun nodes(text: String): List<AccessibilityNodeInfo> {
            val all = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) {
                node.refresh()
                if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) all.add(node)
                repeat(node.childCount) { node.getChild(it)?.let(::visit) }
            }
            uiAutomation.rootInActiveWindow?.let(::visit)
            return all
        }
        fun click(text: String) {
            var node = nodes(text).firstOrNull()
            while (node != null && !node.isClickable) node = node.parent
            check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $text" }
        }
        fun capture(name: String) { uiAutomation.takeScreenshot()?.let { b ->
            File(root, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle()
        } }
        try {
            val url = "https://www.bilibili.com/video/BV1kx4y1U714/"
            val info = JSONObject("""{"bvid":"BV1kx4y1U714","title":"讲解","owner":{"name":"作者"},"pages":[{"cid":11,"part":"第一集"},{"cid":22,"part":"第二集"}]}""")
            val dash = JSONObject("""{"dash":{"video":[{"id":64,"codecs":"hev1","baseUrl":"https://test.bilivideo.com/hevc"},{"id":80,"codecs":"avc1","baseUrl":"https://test.bilivideo.com/1080"},{"id":32,"codecs":"avc1","baseUrl":"http://test.bilivideo.com/avc"}],"audio":[{"codecs":"mp4a.40.2","bandwidth":10,"baseUrl":"https://test.bilivideo.com/audio"}]}}""")
            val requests = mutableListOf<String>()
            val fixture = BilibiliVideoClient { request, _ -> requests.add(request); if (request.contains("/view?")) info else dash }
            val second = runBlocking { fixture.resolve(url + "?p=2") }
            verify(second.part == 2 && second.author == "作者" && second.parts.size == 2, "Video metadata and query-selected part")
            verify(requests.last().contains("cid=22"), "Second part requests its own cid")
            verify(second.videos.single() == "https://test.bilivideo.com/avc" && second.audio != null, "Prefer compatible AVC plus AAC and upgrade media HTTPS")
            runBlocking { fixture.resolve(url + "?p=2", 1) }
            verify(requests.last().contains("cid=11"), "Explicit part selection replaces original query")
            verify(runCatching { runBlocking { fixture.resolve(url, 9) } }.isFailure, "Missing part fails without playing another part")
            val segments = BilibiliVideoClient { request, _ -> if (request.contains("/view?")) info else JSONObject("""{"durl":[{"order":2,"url":"https://test.bilivideo.com/two"},{"order":1,"url":"https://test.bilivideo.com/one"}]}""") }
            val progressive = runBlocking { segments.resolve(url) }
            verify(progressive.audio == null && progressive.videos.first().endsWith("one") && progressive.videos.size == 2, "Progressive segments preserve playback order")
            val preview = BilibiliVideoClient { request, _ -> if (request.contains("/view?")) info else JSONObject("""{"is_preview":true}""") }
            verify(runCatching { runBlocking { preview.resolve(url) } }.isFailure, "Preview-only video is not presented as complete playback")
            val invalid = BilibiliVideoClient { request, _ -> if (request.contains("/view?")) info else JSONObject("""{"durl":[{"order":1,"url":"https://localhost/video"}]}""") }
            verify(runCatching { runBlocking { invalid.resolve(url) } }.isFailure, "Untrusted media host rejected")

            val host = startActivitySync(Intent(targetContext, WorkoutValidationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WorkoutValidationActivity
            activity = host
            await("window focus") { host.hasWindowFocus() }
            @Suppress("DEPRECATION") val mainInfo = targetContext.packageManager.getActivityInfo(android.content.ComponentName(targetContext, MainActivity::class.java), 0)
            verify(mainInfo.screenOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                "All normal production pages declare portrait orientation")
            verify(mainInfo.configChanges and android.content.pm.ActivityInfo.CONFIG_ORIENTATION != 0 &&
                mainInfo.configChanges and android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE != 0,
                "Playback rotation preserves the Activity and unsaved Compose state")
            uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
            await("normal page stays portrait") { host.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
            verify(host.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                "Device rotation cannot rotate the normal page")
            val dismissed = mutableStateOf(false)
            runOnMainSync { host.setContent { FitLogTheme {
                if (!dismissed.value) TutorialVideoDialog(url, "错误验证", { dismissed.value = true }, resolveVideo = { _, _ -> error("测试视频无法播放") })
                else Text("已返回验证页")
            } } }
            await("error state") { nodes("测试视频无法播放").isNotEmpty() }
            verify(nodes("重试播放").isNotEmpty() && nodes("打开原视频").isNotEmpty(), "Failure offers retry and explicit source fallback")
            click("返回训练")
            await("error dismissal") { dismissed.value }
            verify(dismissed.value, "Failed playback returns without launching a browser")
            runOnMainSync { host.setContent { FitLogTheme {
                TutorialVideoDialog(url, "超时验证", {}, resolveVideo = { _, _ -> withTimeout(1) { delay(50); second } })
            } } }
            await("timeout state") { nodes("读取视频超时").isNotEmpty() }
            verify(nodes("重试播放").isNotEmpty(), "Timeout leaves a visible retry state")

            // Use a verified 1920×1080 tutorial for the landscape regression. The
            // original bench-press fixture is portrait and should remain portrait.
            val landscapeUrl = "https://www.bilibili.com/video/BV1U4411t7VM/"
            val live = runBlocking { withTimeout(30000) { BilibiliVideoClient().resolve(landscapeUrl) } }
            verify(live.title.isNotBlank() && live.videos.isNotEmpty(), "Existing public tutorial resolves on the phone anonymously")
            File(root, "cdn-diagnostics.txt").writeText(buildString {
                appendLine("now=${System.currentTimeMillis() / 1000}")
                (live.videos + listOfNotNull(live.audio)).forEach { primary ->
                    (listOf(primary) + live.alternatives[primary].orEmpty()).forEach { address ->
                        val uri = android.net.Uri.parse(address)
                        val connection = java.net.URL(address).openConnection() as java.net.HttpURLConnection
                        try {
                            connection.connectTimeout = 5000; connection.readTimeout = 5000
                            connection.setRequestProperty("User-Agent", BilibiliVideoClient.AGENT)
                            connection.setRequestProperty("Referer", live.source)
                            connection.setRequestProperty("Range", "bytes=0-1023")
                            appendLine("host=${uri.host}, deadline=${uri.getQueryParameter("deadline")}, status=${connection.responseCode}")
                        } catch (error: Exception) { appendLine("host=${uri.host}, error=${error.javaClass.simpleName}") }
                        finally { connection.disconnect() }
                    }
                }
            })
            val frame = AtomicBoolean(false)
            val failure = AtomicReference<String?>(null)
            runOnMainSync {
                player = ExoPlayer.Builder(targetContext).build().apply {
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frame.set(true) }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error.errorCodeName) }
                    })
                }
                host.setContent { AndroidView(factory = { PlayerView(it).also { view -> view.player = player } }) }
                player!!.setMediaSource(tutorialSource(targetContext, live)); player!!.prepare(); player!!.play()
            }
            await("first decoded frame", 30000) { check(failure.get() == null) { "Decoder: ${failure.get()}" }; frame.get() }
            verify(frame.get(), "Actual tutorial video decoded and rendered by the production MediaSource")
            var position = 0L
            await("playback advances") { runOnMainSync { position = player!!.currentPosition }; position > 1000 }
            verify(position > 1000, "Playback clock advances")
            capture("native-decoded")
            runOnMainSync { player!!.pause(); position = player!!.currentPosition }
            Thread.sleep(400)
            var afterPause = 0L
            runOnMainSync { afterPause = player!!.currentPosition }
            verify(afterPause - position < 100, "Native playback pauses")
            runOnMainSync { player!!.setPlaybackSpeed(1.5f); player!!.seekTo(5000); player!!.play() }
            await("seek playback") { runOnMainSync { position = player!!.currentPosition }; position > 5200 }
            verify(position > 5200, "Seek and speed work with merged audio/video")
            runOnMainSync { player!!.release(); player = null }

            dismissed.value = false
            runOnMainSync { host.setContent { FitLogTheme {
                if (!dismissed.value) TutorialVideoDialog(landscapeUrl, "双杠臂屈伸", { dismissed.value = true }, resolveVideo = { _, _ -> live })
                else Text("已返回验证页")
            } } }
            await("in-app player") { nodes(live.title).isNotEmpty() && nodes("倍速").isNotEmpty() }
            click("倍速")
            waitForIdleSync(); Thread.sleep(400)
            await("speed label") { nodes("1.25").isNotEmpty() }
            verify(nodes("1.25").isNotEmpty(), "In-app speed control")
            Thread.sleep(800); capture("tutorial-player")
            click("全屏")
            await("fullscreen") { nodes("收起全屏").isNotEmpty() }
            await("landscape fullscreen") { host.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            verify(!host.isDestroyed && host.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                "Fullscreen requests landscape without recreating playback host")
            val physicalDisplay = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            host.windowManager.defaultDisplay.getRealMetrics(physicalDisplay)
            val bounds = android.graphics.Rect()
            await("fullscreen window resized to ${physicalDisplay.widthPixels}") {
                uiAutomation.rootInActiveWindow?.getBoundsInScreen(bounds)
                bounds.width() > bounds.height() && bounds.width() >= physicalDisplay.widthPixels - 8
            }
            verify(bounds.width() > bounds.height() && bounds.width() >= physicalDisplay.widthPixels - 8,
                "Fullscreen window spans the landscape display")
            await("landscape controls attached") { nodes("收起全屏").isNotEmpty() && nodes("返回训练").isEmpty() }
            Thread.sleep(800) // Exclude the OS rotation screenshot/animation from visual capture.
            capture("tutorial-landscape-fullscreen")
            verify(nodes("返回训练").isEmpty(), "Fullscreen removes normal chrome")
            ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            await("back exits fullscreen") { nodes("返回训练").isNotEmpty() }
            await("back restores portrait") { host.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
            verify(host.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && !host.isDestroyed,
                "Exit fullscreen restores portrait without losing the host")
            await("portrait playback controls") { nodes("1.25").isNotEmpty() && nodes("返回训练").isNotEmpty() }
            verify(nodes("1.25").isNotEmpty(), "Rotation retains the selected playback speed")
            verify(!dismissed.value, "System Back first exits fullscreen")
            click("返回训练")
            await("return") { dismissed.value && nodes("已返回验证页").isNotEmpty() }
            verify(dismissed.value, "Player closes back to the original page")
            report.putString("stream", "$count tutorial resolver / real decoding / player UI checks passed; no user records touched\n")
            report.putString("rendering", root.path)
        } catch (error: Throwable) {
            if (activity != null) runCatching { capture("failure") }
            report.putString("stream", "FAILED: ${error.stackTraceToString()}\nFixture: ${root.path}\n")
            result = Activity.RESULT_CANCELED
        } finally {
            runOnMainSync { player?.release(); activity?.finish() }
            runCatching {
                uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
                ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system user_rotation $priorRotation")).use { it.readBytes() }
                ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("settings put system accelerometer_rotation $priorAutoRotation")).use { it.readBytes() }
            }
        }
        finish(result, report)
    }
}
