package com.ima.imaloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object UrlNormalizer {
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    // Evita que Instagram y Facebook redirijan los enlaces /share/ a la pared de login
    private const val CRAWLER_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    private val TRACKING = Regex(
        "^(utm_.*|fbclid|igsh|igshid|_r|_t|is_from_webapp|sender_device|share_app_id|u_code|si|feature)$"
    )

    suspend fun normalize(raw: String): String = withContext(Dispatchers.IO) {
        var url = Platform.extractUrl(raw)
        if (Platform.detect(url) == Platform.YOUTUBE) {
            return@withContext clean(url, Platform.YOUTUBE)
        }
        if (isShortLink(url)) {
            url = resolve(url) ?: url
        }
        clean(url, Platform.detect(url))
    }

    private fun isShortLink(url: String): Boolean {
        val u = url.lowercase()
        return listOf(
            "vt.tiktok.com", "vm.tiktok.com", "tiktok.com/t/", "fb.watch",
            "facebook.com/share/", "instagram.com/share/", "//t.co/", "instagr.am"
        ).any { it in u }
    }

    private fun resolve(url: String): String? = runCatching {
        var current = url
        val useCrawler = "instagram.com/share/" in url.lowercase() || "facebook.com/share/" in url.lowercase()
        val ua = if (useCrawler) CRAWLER_UA else BROWSER_UA

        repeat(5) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", ua)
            }
            val code = c.responseCode
            val location = c.getHeaderField("Location")
            val finalUrl = c.url.toString()
            c.disconnect()

            if (code in 300..399 && !location.isNullOrBlank()) {
                val nextUrl = URL(URL(current), location).toString()
                extractNextParam(nextUrl)?.let { return@runCatching it }
                if ("login" in nextUrl.lowercase() || "checkpoint" in nextUrl.lowercase()) {
                    return@runCatching current.takeIf { it != url }
                }
                current = nextUrl
            } else {
                extractNextParam(finalUrl)?.let { return@runCatching it }
                return@runCatching finalUrl.takeIf {
                    "login" !in it.lowercase() && "checkpoint" !in it.lowercase()
                }
            }
        }
        current
    }.getOrNull()

    private fun extractNextParam(url: String): String? {
        val query = url.substringAfter("?", "")
        if (query.isEmpty()) return null
        val nextEncoded = query.split("&")
            .firstOrNull { it.startsWith("next=") }
            ?.substringAfter("next=")
            ?: return null
        return runCatching { URLDecoder.decode(nextEncoded, "UTF-8") }
            .getOrNull()
            ?.takeIf { it.startsWith("http") && "login" !in it.lowercase() }
    }

    private fun clean(url: String, p: Platform): String {
        val base = url.substringBefore("#")
        var path = base.substringBefore("?")
        val query = base.substringAfter("?", "")

        if (p == Platform.INSTAGRAM) {
            // Canonicalizar /reels/ -> /reel/ para que yt-dlp y el embed no fallen
            path = path.replace("/reels/", "/reel/")
        }

        if (query.isEmpty()) return path
        return when (p) {
            Platform.TIKTOK, Platform.INSTAGRAM, Platform.X -> {
                if ("/share/" in path || "/link/" in path) base else path
            }
            else -> {
                val kept = query.split("&").filterNot {
                    TRACKING.matches(it.substringBefore("=").lowercase())
                }
                if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
            }
        }
    }
}