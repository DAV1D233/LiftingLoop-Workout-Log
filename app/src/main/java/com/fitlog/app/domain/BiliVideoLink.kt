package com.fitlog.app.domain

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class BiliVideoReference(val id: String, val page: Int) {
    val parameter: String get() = if (id.startsWith("BV")) "bvid" else "aid"
    val apiId: String get() = id.removePrefix("av")
    val source: String get() = "https://www.bilibili.com/video/$id/?p=$page"
}

/** Public video links only. Short links must first be resolved through trusted redirects. */
object BiliVideoLink {
    fun parse(value: String): BiliVideoReference? = runCatching {
        if (value.isBlank() || !ExerciseVideoLink.valid(value)) return null
        val uri = URI(value.trim())
        if (uri.host.equals("b23.tv", true)) return null
        val id = uri.path.removePrefix("/video/").trimEnd('/')
        if (!Regex("BV[A-Za-z0-9]{10}|av[0-9]{1,19}").matches(id)) return null
        if (id.startsWith("av") && (id.removePrefix("av").toLongOrNull() ?: 0) <= 0) return null
        val pages = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .filter { URLDecoder.decode(it[0], "UTF-8") == "p" }
        if (pages.size > 1) return null
        val page = if (pages.isEmpty()) 1 else pages.single().getOrNull(1)?.toIntOrNull() ?: return null
        BiliVideoReference(id, page).takeIf { page in 1..1000 }
    }.getOrNull()

    fun trustedPage(value: String): Boolean = value.isNotBlank() && ExerciseVideoLink.valid(value)

    /** CDN addresses come from Bilibili's API, never from a user-supplied media URL. */
    fun mediaUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (uri.scheme !in listOf("https", "http") || uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) return null
        if (listOf("bilivideo.com", "bilivideo.cn", "bilivideo.net", "akamaized.net").none { host == it || host.endsWith(".$it") }) return null
        if (uri.rawFragment != null) return null
        if (uri.scheme == "http") "https://$host${uri.rawPath}${uri.rawQuery?.let { "?$it" }.orEmpty()}" else value
    }.getOrNull()
}
