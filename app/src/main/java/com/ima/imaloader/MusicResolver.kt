package com.ima.imaloader

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

data class Seed(val track: String, val artist: String, val durationSec: Long?, val rawTitle: String)
data class Candidate(val id: String, val title: String, val uploader: String?, val durationSec: Long?, val score: Int)
data class MusicMatch(
    val sourceUrl: String,
    val title: String,
    val artist: String,
    val album: String?,
    val year: Int?,
    val coverUrl: String?,
    val officialCover: Boolean,
    val infoJsonPath: String,
)

class MusicResolver(private val context: Context, private val engine: DownloadEngine) {

    /** Qué canción es, según el video que pegó el usuario */
    fun seedFrom(info: MediaInfo): Seed {
        val j = runCatching { JSONObject(info.rawJson ?: "{}") }.getOrDefault(JSONObject())
        val channel = (j.optString("channel").ifBlank { null } ?: info.artist)
            ?.removeSuffix(" - Topic")
            ?.replace(Regex("""(?i)\s*(vevo|official|oficial)$"""), "")
            ?.trim()
        val (a, t) = TextMatch.splitArtistTitle(TextMatch.cleanTitle(info.title))
        val track = j.optString("track").ifBlank { null } ?: t
        val artist = j.optString("artist").ifBlank { null }?.substringBefore(",")?.trim()
            ?: a
            ?: channel.orEmpty()
        return Seed(track, artist, info.durationSec, info.title)
    }

    fun candidateFromUrl(url: String, info: MediaInfo): Candidate? {
        val id = QuickPreview.extractYoutubeId(url)
            ?: Regex("""[?&]v=([\w-]{11})""").find(url)?.groupValues?.get(1)
            ?: info.id.takeIf { it.length == 11 }
            ?: return null
        return Candidate(id, info.title, info.artist, info.durationSec, 100)
    }

    /** Busca la canción oficial: primero YouTube Music, luego canales "Topic" */
    suspend fun candidates(seed: Seed): List<Candidate> = withContext(Dispatchers.IO) {
        val q = "${seed.artist} ${seed.track}".trim()
        if (q.isBlank()) return@withContext emptyList()

        var list = search("https://music.youtube.com/search?q=${URLEncoder.encode(q, "UTF-8")}#songs")
        Log.d("IMA_MUSIC", "YT Music: ${list.size} resultados")

        if (list.isEmpty()) {
            val rawSearch = search("ytsearch8:$q")
            val topics = rawSearch.filter { it.uploader?.endsWith("- Topic") == true }
            list = topics.ifEmpty { rawSearch }
            Log.d("IMA_MUSIC", "Topic/Search fallback: ${list.size} resultados")
        }

        list.asSequence().mapNotNull { c -> score(c, seed).takeIf { it >= 0 }?.let { c.copy(score = it) } }
            .filter { it.score >= 55 }
            .sortedByDescending { it.score }
            .take(3)
            .toList()
            .also {
                Log.d("IMA_MUSIC", "candidatas: " + it.joinToString { c -> "${c.title}(${c.score})" })
            }
    }

    private fun search(target: String): List<Candidate> {
        val req = YoutubeDLRequest(target).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-cache-dir")
            addOption("--playlist-end", "8")
            addOption("--socket-timeout", "10")
        }
        val out = runCatching { YoutubeDL.getInstance().execute(req).out }.getOrNull() ?: return emptyList()
        val entries = runCatching { JSONObject(out).optJSONArray("entries") }.getOrNull() ?: return emptyList()

        return (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id").takeIf { it.length == 11 } ?: return@mapNotNull null
            Candidate(
                id = id,
                title = e.optString("title"),
                uploader = e.optString("uploader").ifBlank { e.optString("channel") }.ifBlank { null },
                durationSec = e.optLong("duration").takeIf { it > 0 },
                score = 0,
            )
        }
    }

    /** 0–100, o -1 si claramente no es la misma canción */
    private fun score(c: Candidate, s: Seed): Int {
        val t = TextMatch.similarity(TextMatch.cleanTitle(c.title), s.track)
        if (t < 0.6) return -1
        val diff = if ((c.durationSec != null) && (s.durationSec != null)) {
            abs(c.durationSec - s.durationSec)
        } else null
        if ((diff != null) && (diff > 45L)) return -1

        val a = c.uploader?.let { up ->
            val cleanUp = up.removeSuffix(" - Topic").trim()
            maxOf(
                TextMatch.similarity(cleanUp, s.artist),
                if (s.artist.isNotBlank() &&
                    TextMatch.normalize("${c.title} $cleanUp").contains(TextMatch.normalize(s.artist))
                ) 0.8 else 0.0
            )
        } ?: 0.5

        val d = when {
            diff == null -> 0.5
            diff <= 3L -> 1.0
            diff <= 10L -> 0.75
            diff <= 25L -> 0.4
            else -> 0.0
        }

        var sc = (55.0 * t) + (15.0 * a) + (30.0 * d)
        if (TextMatch.variantTags(c.title) != TextMatch.variantTags(s.rawTitle)) {
            sc -= 30.0
        }
        return sc.roundToInt()
    }

    /** Analiza la candidata, la completa con iTunes y prepara el archivo JSON de metadatos */
    suspend fun build(c: Candidate): MusicMatch? = withContext(Dispatchers.IO) {
        val url = "https://music.youtube.com/watch?v=${c.id}"
        val info = try {
            engine.fetchInfo(url) // Quitamos forceRefresh=true temporalmente a no ser que Engine lo soporte.
        } catch (e: DownloadException) {
            Log.d("IMA_MUSIC", "no se pudo analizar ${c.id}: ${e.kind}")
            return@withContext null
        }

        val j = JSONObject(info.rawJson ?: return@withContext null)
        val ytTrack = j.optString("track").ifBlank { null }
            ?: TextMatch.splitArtistTitle(TextMatch.cleanTitle(info.title)).second
        val ytArtist = j.optString("artist").ifBlank { null }
            ?: info.artist?.removeSuffix(" - Topic").orEmpty()

        val hit = ItunesClient.find(
            track = ytTrack,
            artist = ytArtist.substringBefore(",").trim(),
            durationSec = info.durationSec ?: c.durationSec
        )
        Log.d(
            "IMA_MUSIC",
            "iTunes: ${hit?.let { "${it.artist} - ${it.title} (${it.album})" } ?: "sin coincidencia"}"
        )

        val title = hit?.title ?: ytTrack
        val artist = hit?.artist ?: ytArtist
        val album = hit?.album ?: j.optString("album").ifBlank { null }
        val year = hit?.year ?: j.optInt("release_year").takeIf { it > 0 }

        // Datos que yt-dlp escribirá en el archivo final
        j.put("track", title)
        j.put("title", title)
        if (artist.isNotBlank()) j.put("artist", artist)
        j.remove("artists")
        j.remove("creator")
        j.put("description", "")
        j.put("synopsis", "")
        album?.let { j.put("album", it) }
        j.put("album_artist", hit?.albumArtist ?: artist)
        hit?.genre?.let { j.put("genre", it) }
        hit?.trackNo?.let { j.put("track_number", it) }
        hit?.discNo?.let { j.put("disc_number", it) }
        year?.let { j.put("release_year", it) }
        hit?.releaseDate?.let {
            j.put("release_date", it)
            j.put("upload_date", it)
        }

        // Portada oficial cuadrada 1200x1200 de iTunes
        val cover = hit?.coverUrl?.takeIf { ItunesClient.headOk(it) }
            ?: hit?.coverUrl?.replace("1200x1200bb", "600x600bb")?.takeIf { ItunesClient.headOk(it) }

        if (cover != null) {
            j.put("thumbnail", cover)
            j.put(
                "thumbnails",
                JSONArray().put(
                    JSONObject()
                        .put("url", cover)
                        .put("id", "cover")
                        .put("preference", 100)
                        .put("width", 1200)
                        .put("height", 1200)
                )
            )
        }

        val dir = File(context.cacheDir, "music").apply { mkdirs() }
        dir.listFiles()
            ?.filter { (System.currentTimeMillis() - it.lastModified()) > 1_800_000L }
            ?.forEach { it.delete() }

        val f = File(dir, "${c.id}.json").also { it.writeText(j.toString()) }

        MusicMatch(
            sourceUrl = url,
            title = title,
            artist = artist,
            album = album,
            year = year,
            coverUrl = cover ?: info.thumbnail,
            officialCover = cover != null,
            infoJsonPath = f.absolutePath,
        )
    }

    companion object {
        fun deleteJsonIfExists(path: String?) {
            if (!path.isNullOrBlank()) {
                runCatching { File(path).delete() }
            }
        }
    }
}