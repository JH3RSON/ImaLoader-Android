package com.ima.imaloader.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SearchMode { VIDEO, MUSIC }

data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val thumb: String,
)

object SearchApi {

    // User-Agent de escritorio coherente con los clientes WEB y WEB_REMIX de Innertube
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private const val KEY_YT = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val KEY_YTM = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    // Filtro estricto de YouTube Music para obtener solo "Canciones" (audios oficiales)
    private const val MUSIC_SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun request(url: String, body: String? = null, origin: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.useCaches = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "es-PE,es;q=0.9,en;q=0.8")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                origin?.let {
                    c.setRequestProperty("Origin", it)
                    c.setRequestProperty("Referer", "$it/")
                }
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP error $code")
            }
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        val url = "https://suggestqueries.google.com/complete/search" +
            "?client=firefox&ds=yt&hl=es&oe=utf-8&q=" + URLEncoder.encode(q.trim(), "UTF-8")
        val arr = JSONArray(request(url)).optJSONArray(1) ?: return@withContext emptyList()
        List(minOf(arr.length(), 8)) { arr.optString(it) }.filter { it.isNotBlank() }
    }

    suspend fun search(q: String, mode: SearchMode): List<SearchItem> =
        withContext(Dispatchers.IO) {
            val cleanQuery = q.trim()
            if (cleanQuery.isEmpty()) return@withContext emptyList()
            if (mode == SearchMode.VIDEO) searchVideos(cleanQuery) else searchSongs(cleanQuery)
        }

    private fun client(name: String, version: String) = JSONObject().put(
        "context",
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", name)
                .put("clientVersion", version)
                .put("hl", "es")
                .put("gl", "PE")
        )
    )

    private fun searchVideos(q: String): List<SearchItem> {
        val body = client("WEB", "2.${today()}.00.00").put("query", q)
        val root = JSONObject(
            request(
                "https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=$KEY_YT",
                body.toString(),
                "https://www.youtube.com"
            )
        )
        return find(root, "videoRenderer").mapNotNull { v ->
            val id = v.optString("videoId")
            val title = v.optJSONObject("title").text()
            if (id.isEmpty() || title.isEmpty()) return@mapNotNull null

            val channel = v.optJSONObject("ownerText").text()
                .ifEmpty { v.optJSONObject("longBylineText").text() }
            val views = v.optJSONObject("shortViewCountText").text()
            val duration = v.optJSONObject("lengthText").text()

            SearchItem(
                id = id,
                title = title,
                subtitle = listOf(channel, views).filter { it.isNotEmpty() }.joinToString(" · "),
                duration = duration,
                thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            )
        }.distinctBy { it.id }
    }

    private fun searchSongs(q: String): List<SearchItem> {
        val body = client("WEB_REMIX", "1.${today()}.01.00")
            .put("query", q)
            .put("params", MUSIC_SONGS)
        val root = JSONObject(
            request(
                "https://music.youtube.com/youtubei/v1/search?prettyPrint=false&key=$KEY_YTM",
                body.toString(),
                "https://music.youtube.com"
            )
        )
        return find(root, "musicResponsiveListItemRenderer").mapNotNull { r ->
            val id = r.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                .ifEmpty {
                    Regex("\"videoId\":\"([\\w-]{11})\"").find(r.toString())?.groupValues?.get(1).orEmpty()
                }
            if (id.isEmpty()) return@mapNotNull null

            val cols = r.optJSONArray("flexColumns") ?: return@mapNotNull null
            fun col(i: Int) = cols.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")

            val title = col(0).text()
            if (title.isEmpty()) return@mapNotNull null

            val parts = col(1).text().split("•").map { it.trim() }.filter { it.isNotEmpty() }
            val dur = parts.lastOrNull()?.takeIf { Regex("""\d+(:\d+)+""").matches(it) }.orEmpty()
            val ignoredTags = setOf("Canción", "Song", "Video", "Vídeo")
            val info = parts.filter { it != dur && it !in ignoredTags }

            val thumbs = r.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            val raw = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
            val cover = raw.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")

            SearchItem(
                id = id,
                title = title,
                subtitle = info.joinToString(" • "),
                duration = dur,
                thumb = cover,
            )
        }.distinctBy { it.id }
    }

    private fun find(node: Any?, key: String, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (node) {
            is JSONObject -> node.keys().forEach { k ->
                val v = node.opt(k)
                if (k == key && v is JSONObject) out.add(v) else find(v, key, out)
            }
            is JSONArray -> for (i in 0 until node.length()) find(node.opt(i), key, out)
        }
        return out
    }

    private fun JSONObject?.text(): String {
        if (this == null) return ""
        val simple = optString("simpleText")
        if (simple.isNotEmpty()) return simple
        val runs = optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
    }
}
