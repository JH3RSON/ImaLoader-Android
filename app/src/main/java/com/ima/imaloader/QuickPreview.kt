package com.ima.imaloader

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Vista previa instantánea (<150ms) mientras yt-dlp termina de extraer todos los streams */
data class QuickMediaPreview(
    val url: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val platform: Platform,
    val isYoutubeMusic: Boolean,
)

object QuickPreview {

    // Caché en memoria RAM (máximo 10 links) para que reabrir un enlace sea instantáneo (0 ms)
    private val infoRamCache = LruCache<String, MediaInfo>(10)

    private val YT_ID_REGEX = Regex(
        """(?:youtube\.com/(?:watch\?.*v=|shorts/|live/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})"""
    )

    fun getCachedInfo(url: String): MediaInfo? = infoRamCache.get(url.trim())

    fun putCachedInfo(url: String, info: MediaInfo) {
        infoRamCache.put(url.trim(), info)
    }

    fun extractYoutubeId(url: String): String? =
        YT_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

    /** Convierte un link normal de YouTube en su equivalente de YouTube Music para portadas 1:1 */
    fun toYoutubeMusicUrl(url: String): String? {
        val id = extractYoutubeId(url) ?: return null
        return "https://music.youtube.com/watch?v=$id"
    }

    fun isStandardYoutubeVideo(url: String): Boolean {
        val low = url.lowercase()
        return Platform.detect(url) == Platform.YOUTUBE &&
            "music.youtube.com" !in low &&
            extractYoutubeId(url) != null
    }

    /** Devuelve portada HD y título en ~120ms mientras yt-dlp analiza los códecs en paralelo */
    suspend fun fetchInstant(rawText: String): QuickMediaPreview? = withContext(Dispatchers.IO) {
        val cleanUrl = Platform.extractUrl(rawText)
        if (!cleanUrl.startsWith("http")) return@withContext null

        val platform = Platform.detect(cleanUrl)
        val isYtMusic = "music.youtube.com" in cleanUrl.lowercase()
        val ytId = extractYoutubeId(cleanUrl)

        // Si ya está en caché RAM, devolvemos directo
        getCachedInfo(cleanUrl)?.let { cached ->
            return@withContext QuickMediaPreview(
                url = cleanUrl,
                title = cached.title,
                author = cached.artist,
                thumbnailUrl = cached.thumbnail,
                platform = platform,
                isYoutubeMusic = cached.isYoutubeMusic,
            )
        }

        // En YouTube construimos la miniatura HD al instante y consultamos oEmbed (~120ms)
        if (platform == Platform.YOUTUBE && ytId != null) {
            val instantThumb = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
            val oembed = fetchOEmbed("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$ytId&format=json")
            val title = oembed?.optString("title")?.takeIf { it.isNotBlank() } ?: "Cargando información..."
            val author = oembed?.optString("author_name")
                ?.removeSuffix(" - Topic")
                ?.takeIf { it.isNotBlank() }
            val thumb = oembed?.optString("thumbnail_url")?.takeIf { it.isNotBlank() } ?: instantThumb
            return@withContext QuickMediaPreview(
                url = cleanUrl,
                title = title,
                author = author,
                thumbnailUrl = thumb,
                platform = platform,
                isYoutubeMusic = isYtMusic,
            )
        }

        if (platform == Platform.TIKTOK) {
            val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
            val oembed = fetchOEmbed("https://www.tiktok.com/oembed?url=$encoded")
            if (oembed != null) {
                return@withContext QuickMediaPreview(
                    url = cleanUrl,
                    title = oembed.optString("title", "Video de TikTok"),
                    author = oembed.optString("author_name"),
                    thumbnailUrl = oembed.optString("thumbnail_url"),
                    platform = platform,
                    isYoutubeMusic = false,
                )
            }
        }

        QuickMediaPreview(
            url = cleanUrl,
            title = "Analizando enlace de ${platform.label}...",
            author = platform.label,
            thumbnailUrl = null,
            platform = platform,
            isYoutubeMusic = false,
        )
    }

    private fun fetchOEmbed(endpoint: String): JSONObject? = runCatching {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2_500
            readTimeout = 2_500
        }
        val body = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else null
        conn.disconnect()
        body?.let { JSONObject(it) }
    }.getOrNull()
}