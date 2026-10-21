package com.ima.imaloader

import java.net.URI

enum class Platform(
    val hosts: List<String>,
    val cookieUrl: String? = null,
    val cookieDomain: String? = null,
    val loginUrl: String? = null,
    val sessionCookie: String? = null,
) {
    YOUTUBE(
        hosts = listOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        cookieUrl = "https://www.youtube.com",
        cookieDomain = ".youtube.com",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com",
        sessionCookie = "SAPISID"
    ),
    TIKTOK(
        hosts = listOf("tiktok.com"),
        cookieUrl = "https://www.tiktok.com",
        cookieDomain = ".tiktok.com",
        loginUrl = "https://www.tiktok.com/login",
        sessionCookie = "sessionid"
    ),
    INSTAGRAM(
        hosts = listOf("instagram.com", "instagr.am"),
        cookieUrl = "https://www.instagram.com",
        cookieDomain = ".instagram.com",
        loginUrl = "https://www.instagram.com/accounts/login/",
        sessionCookie = "sessionid"
    ),
    FACEBOOK(
        hosts = listOf("facebook.com", "fb.watch", "fb.com"),
        cookieUrl = "https://www.facebook.com",
        cookieDomain = ".facebook.com",
        loginUrl = "https://m.facebook.com/login/",
        sessionCookie = "c_user"
    ),
    X(
        hosts = listOf("x.com", "twitter.com", "t.co"),
        cookieUrl = "https://x.com",
        cookieDomain = ".x.com",
        loginUrl = "https://x.com/i/flow/login",
        sessionCookie = "auth_token"
    ),
    OTHER(emptyList()); // Vimeo, Reddit, SoundCloud, Twitch, etc.

    val label get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private val URL_REGEX = Regex("""https?://\S+""")

        fun extractUrl(text: String): String =
            URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', ';', '"', '\'') ?: text.trim()

        fun detect(text: String): Platform {
            val url = extractUrl(text)
            val host = runCatching { URI(url).host }.getOrNull()
                ?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return OTHER
            return entries.firstOrNull { p ->
                p.hosts.any { host == it || host.endsWith(".$it") }
            } ?: OTHER
        }
    }
}
