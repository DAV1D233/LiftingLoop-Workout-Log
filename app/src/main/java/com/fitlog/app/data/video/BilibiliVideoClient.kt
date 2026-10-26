package com.fitlog.app.data.video

import com.fitlog.app.domain.BiliVideoLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

data class TutorialPart(val number: Int, val title: String, val cid: Long)
data class TutorialMedia(val title: String, val author: String, val source: String,
    val parts: List<TutorialPart>, val part: Int, val videos: List<String>, val audio: String? = null,
    val alternatives: Map<String, List<String>> = emptyMap())

/** Anonymous, read-only playback. No login cookies, scraping proxy or downloaded video cache. */
class BilibiliVideoClient internal constructor(private val jsonOverride: (suspend (String, String) -> JSONObject)? = null) {
    companion object {
        const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val API = "https://api.bilibili.com"
    }

    suspend fun resolve(link: String, selectedPart: Int? = null): TutorialMedia = withContext(Dispatchers.IO) {
        require(BiliVideoLink.trustedPage(link)) { "视频链接无效，请检查 BV 号或 B站分享链接" }
        val full = if (URI(link).host.equals("b23.tv", true)) expand(link) else link
        val ref = BiliVideoLink.parse(full) ?: error("这个链接不是可播放的 B站视频")
        val info = apiJson("$API/x/web-interface/view?${ref.parameter}=${ref.apiId}", ref.source)
        val pages = info.optJSONArray("pages") ?: JSONArray()
        check(pages.length() <= 1000) { "视频分集数量过多，请打开原视频观看" }
        val parts = (0 until pages.length()).map { index ->
            val p = pages.getJSONObject(index)
            TutorialPart(index + 1, p.optString("part", "第 ${index + 1} 集"), p.getLong("cid"))
        }.ifEmpty { listOf(TutorialPart(1, info.optString("title"), info.getLong("cid"))) }
        val part = selectedPart ?: ref.page
        val target = parts.firstOrNull { it.number == part && it.cid > 0 } ?: error("视频中没有第 $part 集")
        val source = "https://www.bilibili.com/video/${info.optString("bvid").takeIf { Regex("BV[A-Za-z0-9]{10}").matches(it) } ?: ref.id}/?p=$part"
        val play = apiJson("$API/x/player/playurl?${ref.parameter}=${ref.apiId}&cid=${target.cid}&qn=64&fnval=16&fnver=0&fourk=0", source)
        check(!play.optBoolean("is_preview") && play.optInt("is_preview") != 1) { "该视频仅允许试看，请打开原视频查看访问要求" }
        val dash = play.optJSONObject("dash")
        val alternatives = mutableMapOf<String, List<String>>()
        fun JSONObject.address(): String? = mediaAddresses().let { addresses ->
            addresses.firstOrNull()?.also { alternatives[it] = addresses.drop(1) }
        }
        val video = dash?.optJSONArray("video").objects()
            .filter { it.optString("codecs").startsWith("avc1") && it.optInt("id") <= 64 }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("id") }.thenByDescending { it.optLong("bandwidth") })
            .firstNotNullOfOrNull { it.address() }
        val audio = dash?.optJSONArray("audio").objects()
            .filter { it.optString("codecs").startsWith("mp4a") }
            .sortedByDescending { it.optLong("bandwidth") }.firstNotNullOfOrNull { it.address() }
        val videos = if (video != null && audio != null) listOf(video) else play.optJSONArray("durl").objects()
            .sortedBy { it.optInt("order") }.map { BiliVideoLink.mediaUrl(it.optString("url")) ?: error("未取得安全的视频地址") }
        check(videos.isNotEmpty()) { "B站暂未提供可直接播放的视频流，请重试或打开原视频" }
        TutorialMedia(info.optString("title", "动作讲解"), info.optJSONObject("owner")?.optString("name").orEmpty(),
            source, parts, part, videos, if (video != null && audio != null) audio else null, alternatives)
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.mediaAddresses(): List<String> = sequenceOf(optString("baseUrl"), optString("base_url"))
        .plus(sequenceOf("backupUrl", "backup_url").flatMap { key -> optJSONArray(key).let { a ->
            if (a == null) emptySequence() else (0 until a.length()).asSequence().map { a.optString(it) }
        } })
        .mapNotNull(BiliVideoLink::mediaUrl).distinct().toList()

    private suspend fun expand(link: String): String {
        var current = link.trim()
        repeat(5) {
            coroutineContext.ensureActive()
            check(BiliVideoLink.trustedPage(current)) { "分享链接跳转到了不支持的网站" }
            if (!URI(current).host.equals("b23.tv", true)) return current
            val c = connect(current, "https://www.bilibili.com/")
            try {
                check(c.responseCode in listOf(301, 302, 303, 307, 308)) { "短链接暂时无法解析，请使用完整 BV 视频链接" }
                current = URI(current).resolve(c.getHeaderField("Location") ?: error("分享链接没有跳转地址")).toString()
            } finally { c.disconnect() }
        }
        error("分享链接跳转次数过多，请使用完整 BV 视频链接")
    }

    private fun connect(url: String, referer: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000; readTimeout = 10000; instanceFollowRedirects = false
        setRequestProperty("User-Agent", AGENT)
        setRequestProperty("Referer", referer)
        setRequestProperty("Accept-Encoding", "identity")
    }

    private suspend fun apiJson(url: String, referer: String): JSONObject = jsonOverride?.invoke(url, referer) ?: json(url, referer)

    private suspend fun json(url: String, referer: String): JSONObject {
        coroutineContext.ensureActive()
        val c = connect(url, referer)
        try {
            val status = c.responseCode
            check(status in 200..299) { if (status == 403 || status == 412) "B站暂时限制了本次访问，请稍后重试或打开原视频" else "视频服务连接失败（$status）" }
            val bytes = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    check(bytes.size() + count <= 2 * 1024 * 1024) { "视频信息响应过大" }
                    bytes.write(buffer, 0, count)
                }
            }
            val reply = JSONObject(bytes.toString("UTF-8"))
            check(reply.optInt("code", -1) == 0) { when (reply.optInt("code")) {
                -404, 62002 -> "视频不存在或已下架"
                -101, -10403, 10403 -> "这个视频需要登录或访问权限，请打开原视频"
                -352, -412 -> "B站暂时限制了本次访问，请稍后重试或打开原视频"
                else -> "视频暂时无法播放（${reply.optInt("code")}）"
            } }
            return reply.optJSONObject("data") ?: error("视频服务未返回播放信息")
        } finally { c.disconnect() }
    }
}
