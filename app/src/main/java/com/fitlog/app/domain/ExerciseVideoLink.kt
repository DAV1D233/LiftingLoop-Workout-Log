package com.fitlog.app.domain

import java.net.URI

object ExerciseVideoLink {
    fun valid(value: String): Boolean {
        if (value.isBlank()) return true
        return runCatching {
            val uri = URI(value.trim())
            val host = uri.host?.lowercase() ?: return false
            uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
                ((host == "b23.tv" && uri.path.length > 1) ||
                    ((host == "bilibili.com" || host.endsWith(".bilibili.com")) && uri.path.startsWith("/video/")))
        }.getOrDefault(false)
    }
}
