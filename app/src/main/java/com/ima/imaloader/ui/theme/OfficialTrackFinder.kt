package com.ima.imaloader.ui.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/** Datos del link que pegó el usuario (extraídos por tu motor actual). */
data class SourceInfo(
    val title: String,
    val uploader: String? = null,
    val durationSec: Int? = null,
    val artist: String? = null,
    val track: String? = null
)

data class OfficialTrack(
    val videoId: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSec: Int?,
    val coverUrl: String?,
    val score: Double
) {
    /** Link oficial listo para pasar a tu selector de calidades / motor. */
    val url: String get() = "https://music.youtube.com/watch?v=$videoId"
    val artistDisplay: String get() = artists.joinToString(", ")
}

sealed interface OfficialResult {
    data class Found(val track: OfficialTrack) : OfficialResult
    object NotFound : OfficialResult
    data class Error(val message: String) : OfficialResult
}

object OfficialTrackFinder {

    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private const val CLIENT_VERSION = "1.20250310.01.00"
    private const val OFFICIAL_TYPE = "MUSIC_VIDEO_TYPE_ATV"

    private const val MIN_TITLE_SIM = 0.62
    private const val MIN_ARTIST_SIM = 0.45 // Bloquea resubidas de randoms con otro nombre de artista
    private const val MIN_SCORE = 0.64
    private const val GREAT_SCORE = 0.86
    private const val MAX_DURATION_GAP = 65

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    // Caché LRU en memoria para respuesta instantánea (0ms) si se consulta el mismo video
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, OfficialTrack>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OfficialTrack>?) = size > 40
        }
    )

    private val UNWANTED = listOf(
        "karaoke", "instrumental", "slowed", "sped up", "speed up", "reverb",
        "nightcore", "8d", "remix", "cover", "tribute", "live", "en vivo",
        "acoustic", "acustico", "acustica", "lofi", "lo fi", "mashup", "bass boosted"
    )

    private val NOISE_WORDS = setOf(
        "official", "oficial", "video", "videoclip", "clip", "lyric", "lyrics",
        "letra", "letras", "audio", "subtitulado", "subtitulada", "subtitulos",
        "sub", "subs", "espanol", "spanish", "english", "ingles", "traducida",
        "traduccion", "traducido", "hd", "4k", "hq", "visualizer", "mv",
        "topic", "vevo", "full", "al", "en", "con", "by", "de", "del"
    )

    private val TYPE_LABELS = setOf("Song", "Video", "Single", "EP", "Album", "Canción", "Álbum")

    private val BRACKETS = Regex("""[(\[{（【][^)\]}）】]*[)\]}）】]""")
    private val FEAT_TAIL = Regex("""(?i)\b(feat|ft|featuring|prod)\b\.?\s.*$""")
    private val DASH = Regex("""\s+[-–—~:]\s+""")
    private val MARKS = Regex("""\p{Mn}+""")
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val DURATION = Regex("""\d{1,2}(:\d{2}){1,2}""")
    private val COVER_SIZE = Regex("""=w\d+-h\d+.*$""")
    private val OFFICIAL_SUFFIX = Regex("""(?i)\s*(-\s*topic|vevo|official|oficial)\s*$""")

    suspend fun find(src: SourceInfo): OfficialResult = withContext(Dispatchers.IO) {
        val cacheKey = "${src.title.trim()}|${src.uploader.orEmpty()}|${src.durationSec ?: 0}"
        cache[cacheKey]?.let { return@withContext OfficialResult.Found(it) }

        try {
            val p = parseSource(src)
            if (p.queries.isEmpty()) return@withContext OfficialResult.NotFound

            val pool = LinkedHashMap<String, Candidate>()
            var best: Pair<Candidate, Double>? = null
            var lastError: Exception? = null

            for (q in p.queries) {
                try {
                    search(q).forEachIndexed { rank, c ->
                        pool.putIfAbsent(c.videoId, c.copy(rank = rank))
                    }
                } catch (e: IOException) {
                    lastError = e
                    continue
                }
                best = pickBest(pool.values, p)
                if (best != null && best.second >= GREAT_SCORE) break
            }

            val chosen = best
            when {
                chosen != null -> {
                    val track = chosen.first.toTrack(chosen.second)
                    cache[cacheKey] = track
                    OfficialResult.Found(track)
                }
                pool.isEmpty() && lastError != null ->
                    OfficialResult.Error(lastError.message ?: "No se pudo conectar con YouTube Music")
                else -> OfficialResult.NotFound
            }
        } catch (e: Exception) {
            OfficialResult.Error(e.message ?: "Error al buscar en YouTube Music")
        }
    }

    private fun pickBest(all: Collection<Candidate>, p: Parsed): Pair<Candidate, Double>? =
        all.asSequence()
            .filter { it.videoType == OFFICIAL_TYPE }
            .filter { !it.album.isNullOrBlank() } // Toda pista oficial ATV real pertenece a un álbum/single
            .filter { gapOk(it.durationSec, p.durationSec) }
            .map { it to score(it, p) }
            .filter { it.second >= MIN_SCORE }
            .maxByOrNull { it.second }

    private fun gapOk(a: Int?, b: Int?): Boolean =
        a == null || b == null || abs(a - b) <= MAX_DURATION_GAP

    private fun score(c: Candidate, p: Parsed): Double {
        // 1) Descartar truco típico de resubidores: meter "Artista - Canción" dentro del título de YTM
        if (DASH.containsMatchIn(c.title) && !DASH.containsMatchIn(p.knownTrack.orEmpty())) {
            val splitCand = c.title.split(DASH)
            if (splitCand.size >= 2 && c.artists.none { similarity(norm(it), norm(splitCand[0])) >= 0.7 }) {
                return 0.0
            }
        }

        // 2) Similitud del título
        val candCore = stripNoise(core(c.title)).ifEmpty { core(c.title) }
        val titleSim = p.titleOptions.maxOfOrNull { similarity(candCore, it) } ?: 0.0
        if (titleSim < MIN_TITLE_SIM) return 0.0

        // 3) Filtrar versiones no deseadas (remix, slowed, cover, etc.) si no estaban en el link original
        val candNormPadded = " ${norm("${c.title} ${c.album.orEmpty()}")} "
        if (UNWANTED.any { candNormPadded.contains(" $it ") && !p.fullPadded.contains(" $it ") }) {
            return 0.0
        }

        // 4) Validación estricta del Artista Oficial
        val artistScore = c.artists.maxOfOrNull { artist ->
            val aNorm = norm(artist)
            val aTokens = tokens(aNorm)
            val tokenCov = coverage(aTokens, p.blob)
            val optSim = p.artistOptions.maxOfOrNull { similarity(aNorm, it) } ?: 0.0
            // Evitar que el nombre del artista coincida solo porque se llama igual que la canción
            val isJustSongTitle = similarity(aNorm, candCore) >= 0.85 && !p.artistOptions.any { similarity(aNorm, it) >= 0.85 }
            if (isJustSongTitle) 0.0 else maxOf(tokenCov, optSim)
        } ?: 0.0

        // Si el link original tenía indicios del artista (ej. "Artista - Canción" o metadato artist),
        // exigimos que el artista de YouTube Music coincida sí o sí.
        if (p.hasExplicitArtist && artistScore < MIN_ARTIST_SIM) return 0.0

        // 5) Duración más tolerante a intros/outros de videos con subtítulos (hasta 15s sin castigo severo)
        val durScore = if (c.durationSec == null || p.durationSec == null) {
            0.7
        } else {
            when (abs(c.durationSec - p.durationSec)) {
                in 0..5 -> 1.0
                in 6..15 -> 0.88
                in 16..30 -> 0.60
                in 31..50 -> 0.25
                else -> 0.0
            }
        }

        // 6) Peso de autoridad en YouTube Music (las oficiales reales suelen salir en el top 1-3)
        val rankBonus = 0.14 / (1.0 + c.rank * 0.7)

        return if (p.hasExplicitArtist) {
            0.40 * titleSim + 0.38 * artistScore + 0.15 * durScore + rankBonus
        } else {
            // Si el video de YT solo decía el nombre de la canción sin artista, confiamos más en el ranking de YTM
            0.50 * titleSim + 0.20 * artistScore + 0.18 * durScore + (rankBonus * 1.4)
        }
    }

    private class Parsed(
        val titleOptions: List<String>,
        val artistOptions: List<String>,
        val blob: Set<String>,
        val fullPadded: String,
        val durationSec: Int?,
        val hasExplicitArtist: Boolean,
        val knownTrack: String?,
        val queries: List<String>
    )

    private fun parseSource(s: SourceInfo): Parsed {
        val head = s.title.substringBefore('|').substringBefore('/')
        val noBrackets = head.replace(BRACKETS, " ").replace(FEAT_TAIL, " ").trim()
        val parts = noBrackets.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }

        val titleOpts = LinkedHashSet<String>()
        val artistOpts = LinkedHashSet<String>()

        fun addTitle(x: String?) {
            if (x.isNullOrBlank()) return
            val raw = core(x)
            val stripped = stripNoise(raw)
            if (stripped.isNotEmpty()) titleOpts += stripped
            if (raw.isNotEmpty()) titleOpts += raw
        }

        fun addArtist(x: String?) {
            if (x.isNullOrBlank()) return
            val cleaned = stripNoise(norm(OFFICIAL_SUFFIX.replace(x, "")))
            if (cleaned.isNotEmpty()) artistOpts += cleaned
        }

        addTitle(s.track)
        addArtist(s.artist)

        val officialUploader = s.uploader
            ?.takeIf { OFFICIAL_SUFFIX.containsMatchIn(it) }
            ?.let { OFFICIAL_SUFFIX.replace(it, "").trim() }
        addArtist(officialUploader)

        if (parts.size >= 2) {
            // En YouTube puede venir "Artista - Canción" o "Canción - Artista"
            addArtist(parts[0])
            addArtist(parts[1])
            addTitle(parts[1])
            addTitle(parts[0])
        } else {
            addTitle(noBrackets)
        }
        // Respaldo por si el título real tenía paréntesis legítimos
        addTitle(head.replace(FEAT_TAIL, " "))

        val cleanFullTitle = stripNoise(core(noBrackets)).ifEmpty { core(noBrackets) }
        val blob = tokens(norm("${s.title} ${s.uploader.orEmpty()} ${s.artist.orEmpty()}"))
        val fullPadded = " ${norm("${s.title} ${s.track.orEmpty()}")} "

        val hasExplicitArtist = !s.artist.isNullOrBlank() || parts.size >= 2 || !officialUploader.isNullOrBlank()

        // Construcción de queries limpias (sin "sub español", "lyrics", etc.)
        val queries = ArrayList<String>()
        if (!s.artist.isNullOrBlank() && !s.track.isNullOrBlank()) {
            queries += "${s.artist} ${s.track}"
        }
        if (parts.size >= 2) {
            val p0 = stripNoise(core(parts[0])).ifEmpty { core(parts[0]) }
            val p1 = stripNoise(core(parts[1])).ifEmpty { core(parts[1]) }
            if (p0.isNotEmpty() && p1.isNotEmpty()) queries += "$p0 $p1"
        } else if (!officialUploader.isNullOrBlank() && cleanFullTitle.isNotEmpty()) {
            queries += "$officialUploader $cleanFullTitle"
        }
        if (cleanFullTitle.isNotEmpty()) queries += cleanFullTitle
        queries += noBrackets

        return Parsed(
            titleOptions = titleOpts.toList(),
            artistOptions = artistOpts.toList(),
            blob = blob,
            fullPadded = fullPadded,
            durationSec = s.durationSec,
            hasExplicitArtist = hasExplicitArtist,
            knownTrack = s.track,
            queries = queries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
        )
    }

    private fun search(query: String): List<Candidate> {
        val body = JSONObject()
            .put(
                "context", JSONObject().put(
                    "client", JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", "en")
                        .put("gl", Locale.getDefault().country.ifEmpty { "US" })
                )
            )
            .put("query", query)
            .put("params", SONGS_FILTER)

        val conn = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) {
                throw IOException("YouTube Music respondió HTTP ${conn.responseCode}")
            }

            val inputStream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            val text = inputStream.bufferedReader().use { it.readText() }
            return parseResults(JSONObject(text))
        } finally {
            conn.disconnect()
        }
    }

    private data class Candidate(
        val videoId: String,
        val title: String,
        val artists: List<String>,
        val album: String?,
        val durationSec: Int?,
        val coverUrl: String?,
        val videoType: String,
        val rank: Int = 0
    ) {
        fun toTrack(score: Double) =
            OfficialTrack(videoId, title, artists, album, durationSec, coverUrl, score)
    }

    private fun parseResults(root: JSONObject): List<Candidate> {
        val items = ArrayList<JSONObject>()
        collect(root, "musicResponsiveListItemRenderer", items)
        return items.mapNotNull { runCatching { parseItem(it) }.getOrNull() }
    }

    private fun parseItem(item: JSONObject): Candidate? {
        val type = deepString(item, "musicVideoType") ?: return null
        val videoId = item.optJSONObject("playlistItemData")
            ?.optString("videoId")?.takeIf { it.isNotEmpty() }
            ?: deepString(item, "videoId")
            ?: return null
        val cols = item.optJSONArray("flexColumns") ?: return null

        fun runs(i: Int): JSONArray? = cols.optJSONObject(i)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")

        val title = runs(0)?.optJSONObject(0)?.optString("text").orEmpty()
        if (title.isEmpty()) return null

        val artists = ArrayList<String>()
        val loose = ArrayList<String>()
        var album: String? = null
        var duration: Int? = null

        val r = runs(1)
        if (r != null) {
            for (i in 0 until r.length()) {
                val run = r.optJSONObject(i) ?: continue
                val text = run.optString("text")
                if (text.isBlank() || text.trim() == "•" || text in TYPE_LABELS) continue
                if (DURATION.matches(text.trim())) {
                    duration = toSeconds(text.trim())
                } else {
                    when (deepString(run, "pageType")) {
                        "MUSIC_PAGE_TYPE_ARTIST" -> artists += text
                        "MUSIC_PAGE_TYPE_ALBUM" -> album = text
                        else -> loose += text
                    }
                }
            }
        }
        if (artists.isEmpty() && loose.isNotEmpty()) artists += loose.first()

        val thumbs = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val cover = thumbs?.let { t -> t.optJSONObject(t.length() - 1) }
            ?.optString("url")
            ?.takeIf { it.isNotEmpty() }
            ?.replace(COVER_SIZE, "=w1200-h1200-l90-rj")

        return Candidate(videoId, title, artists, album, duration, cover, type)
    }

    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) out += v else collect(v, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun deepString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.opt(key)
                if (direct is String && direct.isNotEmpty()) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    deepString(node.opt(keys.next()), key)?.let { return it }
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                deepString(node.opt(i), key)?.let { return it }
            }
        }
        return null
    }

    private fun toSeconds(t: String): Int =
        t.split(':').map { it.toInt() }.fold(0) { acc, n -> acc * 60 + n }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NON_WORD, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun core(s: String): String = norm(s.replace(BRACKETS, " ").replace(FEAT_TAIL, " "))

    private fun stripNoise(n: String): String =
        n.split(' ').filter { it.isNotEmpty() && it !in NOISE_WORDS }.joinToString(" ")

    private fun tokens(n: String): Set<String> =
        if (n.isEmpty()) emptySet() else n.split(' ').filter { it.isNotEmpty() }.toSet()

    private fun coverage(a: Set<String>, blob: Set<String>): Double =
        if (a.isEmpty()) 0.0 else a.count { it in blob }.toDouble() / a.size

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val ta = tokens(a)
        val tb = tokens(b)
        val dice = if (ta.isEmpty() || tb.isEmpty()) 0.0 else 2.0 * ta.intersect(tb).size / (ta.size + tb.size)
        val lev = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        return maxOf(dice, lev)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}