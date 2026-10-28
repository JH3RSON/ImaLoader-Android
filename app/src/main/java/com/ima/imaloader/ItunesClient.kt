package com.ima.imaloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

object ItunesClient {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) Chrome/124.0 Mobile Safari/537.36"

    data class Hit(
        val title: String,
        val artist: String,
        val album: String?,
        val albumArtist: String?,
        val year: Int?,
        val releaseDate: String?,
        val genre: String?,
        val trackNo: Int?,
        val discNo: Int?,
        val coverUrl: String?,
    )

    suspend fun find(track: String, artist: String, durationSec: Long?): Hit? = withContext(Dispatchers.IO) {
        if (track.isBlank()) return@withContext null
        val term = URLEncoder.encode("$artist $track".trim(), "UTF-8")
        val body = get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=10")
            ?: return@withContext null
        val arr = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
            ?: return@withContext null

        var best: JSONObject? = null
        var bestScore = 0.0

        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val name = r.optString("trackName")
            val t = TextMatch.similarity(TextMatch.cleanTitle(name), TextMatch.cleanTitle(track))
            val a = TextMatch.similarity(r.optString("artistName"), artist)
            val ms = r.optLong("trackTimeMillis", 0L)
            val diff = if (durationSec != null && durationSec > 0 && ms > 0) {
                abs((ms / 1000L) - durationSec)
            } else {
                null
            }

            if (t < 0.7) continue
            if (artist.isNotBlank() && a < 0.3) continue
            if (diff != null && diff > 15L) continue
            if (TextMatch.variantTags(name) != TextMatch.variantTags(track)) continue

            val d = when {
                diff == null -> 0.5
                diff <= 3L -> 1.0
                diff <= 8L -> 0.6
                else -> 0.0
            }
            val score = (0.55 * t) + (0.25 * a) + (0.20 * d)
            if (score > bestScore) {
                best = r
                bestScore = score
            }
        }

        best?.takeIf { bestScore >= 0.7 }?.let { r ->
            val date = r.optString("releaseDate").ifBlank { null }
            Hit(
                title = r.optString("trackName"),
                artist = r.optString("artistName"),
                album = r.optString("collectionName").ifBlank { null },
                albumArtist = r.optString("collectionArtistName").ifBlank { null } ?: r.optString("artistName"),
                year = date?.take(4)?.toIntOrNull(),
                releaseDate = date?.take(10)?.replace("-", ""),
                genre = r.optString("primaryGenreName").ifBlank { null },
                trackNo = r.optInt("trackNumber").takeIf { it > 0 },
                discNo = r.optInt("discNumber").takeIf { it > 0 },
                coverUrl = r.optString("artworkUrl100").ifBlank { null }?.replace("100x100bb", "1200x1200bb"),
            )
        }
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.setRequestProperty("User-Agent", UA)
        val body = if (c.responseCode == 200) {
            c.inputStream.bufferedReader().use { it.readText() }
        } else null
        c.disconnect()
        body
    }.getOrNull()

    fun headOk(url: String): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.setRequestProperty("User-Agent", UA)
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    }.getOrDefault(false)
}