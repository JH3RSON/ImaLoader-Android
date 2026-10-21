package com.ima.imaloader.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class YouTubeSearchMode(val label: String) {
    YOUTUBE("YouTube"),
    YOUTUBE_MUSIC("YouTube Music")
}

data class YouTubeSearchResult(
    val id: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val duration: String? = null,
    val isMusic: Boolean = false,
    val canonicalUrl: String = "https://www.youtube.com/watch?v=$id"
)

object YouTubeSearchService {

    private const val TAG = "YouTubeSearchService"
    
    // Servidores de respaldo públicos para Invidious / Piped (Búsqueda nativa de YouTube sin API Key requerida)
    private val PIPED_SERVERS = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacydev.net",
        "https://pipedapi.drgns.space"
    )

    private val INVIDIOUS_SERVERS = listOf(
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://invidious.drgns.space"
    )

    /**
     * Busca contenido en YouTube o YouTube Music.
     * @param query Término de búsqueda (ej. "God of War 3 gameplay", "Billie Jean")
     * @param mode Modalidad seleccionada (YOUTUBE o YOUTUBE_MUSIC)
     * @param apiKey Clave opcional de YouTube Data API v3 si se configura
     */
    suspend fun search(
        query: String,
        mode: YouTubeSearchMode,
        apiKey: String? = null
    ): List<YouTubeSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // Si se proporciona una API Key oficial, intentar primero YouTube Data API v3
        if (!apiKey.isNullOrBlank()) {
            runCatching {
                searchViaYouTubeDataApi(trimmed, mode, apiKey)
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        }

        // Intento 1: Búsqueda nativa (Scraping de ytInitialData) - Muy rápida y confiable
        runCatching {
            searchViaYouTubeHtml(trimmed, mode)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        // Intento 2: Búsqueda vía API Piped (Especializada en YouTube y YouTube Music)
        runCatching {
            searchViaPiped(trimmed, mode)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        // Intento 2: Búsqueda vía API Invidious
        runCatching {
            searchViaInvidious(trimmed, mode)
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        emptyList()
    }

    /* ------------------------------------------------------------------------
     * 0. Búsqueda nativa HTML (ytInitialData) - No requiere API Key ni instancias de terceros
     * ------------------------------------------------------------------------ */
    private fun searchViaYouTubeHtml(query: String, mode: YouTubeSearchMode): List<YouTubeSearchResult> {
        val finalQuery = if (mode == YouTubeSearchMode.YOUTUBE_MUSIC) "$query song" else query
        val encodedQuery = URLEncoder.encode(finalQuery, "UTF-8")
        val urlStr = "https://www.youtube.com/results?search_query=$encodedQuery"
        
        val html = httpGet(urlStr) ?: return emptyList()
        val match = Regex("""ytInitialData\s*=\s*(\{.+?\});\s*(?:var\s+|window|</script>)""", setOf(RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: Regex("""var\s+ytInitialData\s*=\s*(\{.+?\});""", setOf(RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: return emptyList()
        val jsonStr = match.groupValues[1]
        
        val json = JSONObject(jsonStr)
        val contents = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        val results = mutableListOf<YouTubeSearchResult>()
        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
            for (j in 0 until section.length()) {
                val item = section.optJSONObject(j) ?: continue
                val video = item.optJSONObject("videoRenderer") ?: continue
                
                val videoId = video.optString("videoId")
                if (videoId.length != 11) continue
                
                val title = video.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Sin título"
                val owner = video.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
                val lengthText = video.optJSONObject("lengthText")?.optString("simpleText")
                val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                
                results.add(
                    YouTubeSearchResult(
                        id = videoId,
                        title = title,
                        channelTitle = owner,
                        thumbnailUrl = thumbnail,
                        duration = lengthText,
                        isMusic = mode == YouTubeSearchMode.YOUTUBE_MUSIC
                    )
                )
            }
        }
        return results
    }

    /* ------------------------------------------------------------------------
     * 1. Búsqueda con Piped API (Soporta YouTube y YouTube Music)
     * ------------------------------------------------------------------------ */
    private fun searchViaPiped(query: String, mode: YouTubeSearchMode): List<YouTubeSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val filter = if (mode == YouTubeSearchMode.YOUTUBE_MUSIC) "music_songs" else "videos"

        for (baseUrl in PIPED_SERVERS) {
            try {
                val endpoint = if (mode == YouTubeSearchMode.YOUTUBE_MUSIC) {
                    "$baseUrl/music/search?q=$encodedQuery"
                } else {
                    "$baseUrl/search?q=$encodedQuery&filter=$filter"
                }

                val jsonStr = httpGet(endpoint) ?: continue
                val json = JSONObject(jsonStr)

                val items = json.optJSONArray("items") ?: continue

                val results = mutableListOf<YouTubeSearchResult>()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val url = item.optString("url", "")
                    val id = parseVideoIdFromPipedUrl(url) ?: item.optString("id").takeIf { it.length == 11 } ?: continue
                    
                    val title = item.optString("title", "Sin título")
                    val uploader = item.optString("uploaderName", item.optString("artist", "YouTube"))
                    val thumbnail = item.optString("thumbnail", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
                    val durationSec = item.optLong("duration", -1L)
                    val durationStr = if (durationSec > 0) formatSeconds(durationSec) else null

                    results.add(
                        YouTubeSearchResult(
                            id = id,
                            title = title,
                            channelTitle = uploader,
                            thumbnailUrl = thumbnail,
                            duration = durationStr,
                            isMusic = mode == YouTubeSearchMode.YOUTUBE_MUSIC
                        )
                    )
                }

                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                Log.w(TAG, "Piped server failed: $baseUrl, ${e.message}")
            }
        }
        return emptyList()
    }

    /* ------------------------------------------------------------------------
     * 2. Búsqueda con Invidious API
     * ------------------------------------------------------------------------ */
    private fun searchViaInvidious(query: String, mode: YouTubeSearchMode): List<YouTubeSearchResult> {
        val encodedQuery = URLEncoder.encode(
            if (mode == YouTubeSearchMode.YOUTUBE_MUSIC) "$query music" else query,
            "UTF-8"
        )

        for (baseUrl in INVIDIOUS_SERVERS) {
            try {
                val endpoint = "$baseUrl/api/v1/search?q=$encodedQuery&type=video"
                val jsonStr = httpGet(endpoint) ?: continue
                val jsonArray = JSONArray(jsonStr)

                val results = mutableListOf<YouTubeSearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val id = item.optString("videoId", "")
                    if (id.length != 11) continue

                    val title = item.optString("title", "Sin título")
                    val author = item.optString("author", "YouTube")
                    val durationSec = item.optLong("lengthSeconds", -1L)
                    val durationStr = if (durationSec > 0) formatSeconds(durationSec) else null
                    val thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                    results.add(
                        YouTubeSearchResult(
                            id = id,
                            title = title,
                            channelTitle = author,
                            thumbnailUrl = thumbnail,
                            duration = durationStr,
                            isMusic = mode == YouTubeSearchMode.YOUTUBE_MUSIC
                        )
                    )
                }

                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                Log.w(TAG, "Invidious server failed: $baseUrl, ${e.message}")
            }
        }
        return emptyList()
    }

    /* ------------------------------------------------------------------------
     * 3. Búsqueda con YouTube Data API v3 Oficial
     * ------------------------------------------------------------------------ */
    private fun searchViaYouTubeDataApi(query: String, mode: YouTubeSearchMode, apiKey: String): List<YouTubeSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val categoryParam = if (mode == YouTubeSearchMode.YOUTUBE_MUSIC) "&videoCategoryId=10" else ""
        val urlStr = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=20&type=video&q=$encodedQuery$categoryParam&key=$apiKey"

        val jsonStr = httpGet(urlStr) ?: return emptyList()
        val json = JSONObject(jsonStr)
        val items = json.optJSONArray("items") ?: return emptyList()

        val results = mutableListOf<YouTubeSearchResult>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val idObj = item.optJSONObject("id") ?: continue
            val videoId = idObj.optString("videoId", "")
            if (videoId.length != 11) continue

            val snippet = item.optJSONObject("snippet") ?: continue
            val title = snippet.optString("title", "Sin título")
            val channelTitle = snippet.optString("channelTitle", "YouTube")
            val thumbnails = snippet.optJSONObject("thumbnails")
            val thumbUrl = thumbnails?.optJSONObject("medium")?.optString("url")
                ?: thumbnails?.optJSONObject("default")?.optString("url")
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            results.add(
                YouTubeSearchResult(
                    id = videoId,
                    title = title,
                    channelTitle = channelTitle,
                    thumbnailUrl = thumbUrl,
                    isMusic = mode == YouTubeSearchMode.YOUTUBE_MUSIC
                )
            )
        }
        return results
    }

    /* ------------------------------------------------------------------------
     * Utilidades auxiliares
     * ------------------------------------------------------------------------ */
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8,application/json")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "httpGet failed for url: $urlStr", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseVideoIdFromPipedUrl(url: String): String? {
        if (url.startsWith("/watch?v=")) return url.substringAfter("/watch?v=").take(11)
        if (url.length == 11) return url
        return null
    }

    private fun formatSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m >= 60) {
            val h = m / 60
            val remM = m % 60
            String.format(Locale.US, "%d:%02d:%02d", h, remM, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}
