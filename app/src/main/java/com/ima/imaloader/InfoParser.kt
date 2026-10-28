package com.ima.imaloader

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(k: String): String? =
    if (has(k) && !isNull(k)) optString(k).ifBlank { null } else null

private fun JSONObject.lng(k: String): Long? =
    if (has(k) && !isNull(k)) optLong(k, -1L).takeIf { it > 0L } else null

private fun JSONObject.int(k: String): Int? =
    if (has(k) && !isNull(k)) optInt(k, -1).takeIf { it > 0 } else null

private fun JSONObject.dbl(k: String): Double? =
    if (has(k) && !isNull(k)) optDouble(k, Double.NaN).takeIf { !it.isNaN() && it > 0.0 } else null

object InfoParser {
    fun parse(json: String, requestedUrl: String = ""): MediaInfo {
        val root = JSONObject(json)
        val o = selectMediaObject(root)
        val arr = o.optJSONArray("formats") ?: JSONArray()
        val formats = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { parseFormat(it) }
        }

        val id = o.str("id") ?: root.optString("id")
        val webUrl = o.str("webpage_url") ?: root.str("webpage_url") ?: requestedUrl
        val isYtMusic = "music.youtube.com" in requestedUrl.lowercase() ||
            "music.youtube.com" in webUrl.lowercase() ||
            o.str("album") != null

        val rawArtist = o.str("artist") ?: o.str("creator") ?: o.str("uploader") ?: root.str("uploader")
        val cleanArtist = rawArtist
            ?.removeSuffix(" - Topic")
            ?.removeSuffix(" - Tema")
            ?.trim()

        val cleanTitle = o.str("track") ?: o.str("title") ?: root.str("title") ?: "Multimedia"
        val releaseYear = o.int("release_year")?.toString()
            ?: o.str("release_date")?.take(4)
            ?: o.str("upload_date")?.take(4)

        val hdThumb = pickBestThumbnail(o) ?: pickBestThumbnail(root)
        val subsObj = o.optJSONObject("subtitles") ?: o.optJSONObject("automatic_captions")
        val hasSubs = subsObj != null && subsObj.length() > 0

        return MediaInfo(
            id = id,
            title = cleanTitle,
            artist = cleanArtist,
            album = o.str("album"),
            releaseYear = releaseYear,
            thumbnail = hdThumb,
            durationSec = o.lng("duration"),
            webpageUrl = webUrl,
            isYoutubeMusic = isYtMusic,
            hasSubtitles = hasSubs,
            formats = formats,
            rawJson = o.toString(),
            originalUrl = requestedUrl // Add back to fix compilation
        )
    }

    private fun pickBestThumbnail(o: JSONObject): String? {
        val thumbs = o.optJSONArray("thumbnails")
        var bestUrl: String? = null
        var bestScore = -1L

        if (thumbs != null) {
            for (i in 0 until thumbs.length()) {
                val t = thumbs.optJSONObject(i) ?: continue
                val url = t.str("url") ?: continue
                val w = t.lng("width") ?: 0L
                val h = t.lng("height") ?: 0L
                val pref = t.int("preference")?.toLong() ?: 0L
                val formatBonus = if (".webp" in url.lowercase()) 0L else 50_000L
                val score = (w * h) + (pref * 10_000L) + formatBonus
                if (score >= bestScore) {
                    bestScore = score
                    bestUrl = url
                }
            }
        }

        val chosen = bestUrl ?: o.str("thumbnail") ?: return null
        return if ("ytimg.com" in chosen && "?sqp=" in chosen) {
            chosen.substringBefore("?sqp=")
        } else {
            chosen
        }
    }

    private fun selectMediaObject(root: JSONObject): JSONObject {
        if (root.optJSONArray("formats") != null) return root
        val entries = root.optJSONArray("entries") ?: return root
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val fmt = entry.optJSONArray("formats")
            if (fmt != null && fmt.length() > 0) return entry
        }
        return entries.optJSONObject(0) ?: root
    }

    private fun parseFormat(f: JSONObject): MediaFormat? {
        if (f.str("ext") == "mhtml" || f.str("format_note") == "storyboard") return null
        val v = f.str("vcodec")?.takeUnless { it == "none" }
        val a = f.str("acodec")?.takeUnless { it == "none" }
        if (v == null && a == null) return null

        val exactSize = f.lng("filesize")
        val approxSize = f.lng("filesize_approx")

        return MediaFormat(
            id = f.optString("format_id"),
            ext = f.str("ext"),
            width = f.int("width"),
            height = f.int("height"),
            fps = f.dbl("fps"),
            vcodec = v,
            acodec = a,
            tbr = f.dbl("tbr"),
            abr = f.dbl("abr"),
            sizeBytes = exactSize ?: approxSize,
            sizeApprox = exactSize == null && approxSize != null,
        )
    }
}