package com.ima.imaloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

object InstagramResolver {

    private const val EMBED_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val BOT_UA = "TelegramBot (like TwitterBot)"

    private val SHORTCODE_REGEX = Regex("""instagram\.com/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""")
    private val VIDEO_URL_JSON_REGEX = Regex("""\\?"video_url\\?"\s*:\s*\\?"(https:[^"\\]+)\\?"""")
    private val EMBED_VIDEO_TAG_REGEX = Regex("""<video[^>]+src="([^"]+)"""")
    private val OG_VIDEO_REGEX = Regex("""<meta\s+property="og:video(?::secure_url)?"\s+content="([^"]+)"""")
    private val OG_IMAGE_REGEX = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")
    private val OG_TITLE_REGEX = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")

    fun extractShortcode(url: String): String? =
        SHORTCODE_REGEX.find(url)?.groupValues?.getOrNull(1)

    /** Extrae el MediaInfo y la URL directa del MP4 sin iniciar sesión */
    suspend fun resolveAnonymous(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val code = extractShortcode(url) ?: return@withContext null

        // Intento 1: Endpoint oficial /embed/captioned/ de Instagram (no pide login en posts públicos)
        fetchFromEmbed(code, url)?.let { return@withContext it }

        // Intento 2: Espejo OpenGraph para Reels cuando el IP está limitado por GraphQL
        fetchFromOpenGraphProxy(code, url)?.let { return@withContext it }

        null
    }

    private fun fetchFromEmbed(shortcode: String, url: String): MediaInfo? = runCatching {
        val embedUrl = "https://www.instagram.com/p/$shortcode/embed/captioned/"
        val html = httpGet(embedUrl, EMBED_UA) ?: return null

        val rawVideoUrl = VIDEO_URL_JSON_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?: EMBED_VIDEO_TAG_REGEX.find(html)?.groupValues?.getOrNull(1)
            ?: return null

        // Decode twice for deep escapes in Instagram's JSON like https\u00253A\u00252F\u00252F
        val cleanVideoUrl = decodeDeepEscapes(decodeEscapes(rawVideoUrl))
        if (!cleanVideoUrl.startsWith("http")) return null

        val thumb = OG_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { decodeDeepEscapes(decodeEscapes(it)) }
        buildDirectMediaInfo(shortcode, "Instagram Reel $shortcode", thumb, cleanVideoUrl, url)
    }.getOrNull()

    private fun fetchFromOpenGraphProxy(shortcode: String, url: String): MediaInfo? = runCatching {
        val mirrors = listOf(
            "https://d.ddinstagram.com/reel/$shortcode/",
            "https://ddinstagram.com/reel/$shortcode/",
            "https://vxinstagram.com/reel/$shortcode",
            "https://www.ddinstagram.com/reel/$shortcode",
            "https://o.ddinstagram.com/reel/$shortcode"
        )
        for (mirror in mirrors) {
            val html = httpGet(mirror, BOT_UA) ?: continue
            val videoUrl = OG_VIDEO_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?.let { decodeDeepEscapes(decodeEscapes(it)) }
                ?.takeIf { it.startsWith("http") }
            if (videoUrl == null) continue
            
            val thumb = OG_IMAGE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { decodeDeepEscapes(decodeEscapes(it)) }
            val title = OG_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?.takeIf { it.isNotBlank() } ?: "Instagram Video $shortcode"
            return@runCatching buildDirectMediaInfo(shortcode, title, thumb, videoUrl, url)
        }
        null
    }.getOrNull()

    private fun buildDirectMediaInfo(
        shortcode: String,
        title: String,
        thumbnail: String?,
        directVideoUrl: String,
        originalUrl: String
    ): MediaInfo {
        val headSize = fetchContentLength(directVideoUrl)
        val format = MediaFormat(
            id = "ig_direct_mp4",
            ext = "mp4",
            width = 1080,
            height = 1920,
            fps = 30.0,
            vcodec = "h264",
            acodec = "mp4a.40.2",
            tbr = null,
            abr = 128.0,
            sizeBytes = headSize,
            sizeApprox = true,
        )
        return MediaInfo(
            id = shortcode,
            title = title,
            artist = "Instagram",
            album = null,
            releaseYear = null,
            thumbnail = thumbnail,
            durationSec = null,
            webpageUrl = originalUrl,
            formats = listOf(format),
            directUrl = directVideoUrl,
            originalUrl = originalUrl
        )
    }

    private fun httpGet(url: String, ua: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
        }
        val body = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else null
        conn.disconnect()
        body
    }.getOrNull()

    private fun fetchContentLength(url: String): Long? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            connectTimeout = 6_000
            readTimeout = 6_000
        }
        val len = conn.contentLengthLong.takeIf { it > 0L }
        conn.disconnect()
        len
    }.getOrNull()

    private fun decodeEscapes(raw: String): String =
        raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u0025", "%")
            .replace("&amp;", "&")

    private fun decodeDeepEscapes(raw: String): String = runCatching {
        URLDecoder.decode(raw, "UTF-8")
    }.getOrDefault(raw)
}