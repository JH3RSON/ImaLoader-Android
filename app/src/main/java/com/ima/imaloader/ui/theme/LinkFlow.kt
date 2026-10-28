// ═══════════════════════════════════════════════════════════════════════════
//  LinkFlow.kt — Flujo progresivo de análisis, progreso real con miniatura,
//  exportación real a la carpeta pública "Download" y actualizador de yt-dlp.
//  100% adaptado a ImaTemas.kt sin cortar textos (...).
// ═══════════════════════════════════════════════════════════════════════════
package com.ima.imaloader.ui.theme

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  1) DATOS 100% REALES DEL MOTOR (CERO KBPS O RESOLUCIONES INVENTADAS)
// ═══════════════════════════════════════════════════════════════════════════

enum class Kind { AUDIO, VIDEO }

@Immutable
data class ByteSize(val bytes: Long, val approx: Boolean)

@Immutable
data class RealFormat(
    val id: String,
    val ext: String,
    val vcodec: String?,
    val acodec: String?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val height: Int?,
    val fps: Int?,
    val abr: Int?,
    val tbr: Int?,
    val asr: Int?,
    val sizeBytes: Long?,
)

@Immutable
data class MediaInfo(
    val url: String,
    val webpageUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val author: String?,
    val platform: String?,
    val durationSec: Long?,
    val playlistCount: Int?,
    val formats: List<RealFormat>,
    val manualSubs: List<String>,
    val autoSubs: List<String>,
    val esDeYouTubeMusic: Boolean = false,
) {
    val hasSubs: Boolean get() = manualSubs.isNotEmpty() || autoSubs.isNotEmpty()

    val esVideoNormalDeYouTube: Boolean
        get() {
            if (esDeYouTubeMusic) return false
            val u = webpageUrl.lowercase(Locale.ROOT)
            val esYT = u.contains("youtube.com/") || u.contains("youtu.be/")
            val esMusic = u.contains("music.youtube.com")
            val esTopic = (author ?: "").endsWith("- Topic", ignoreCase = true)
            return esYT && !esMusic && !esTopic
        }
}

@Immutable
data class ParsedMusicQuery(
    val artist: String?,
    val songTitle: String,
    val fullQuery: String,
)

fun extraerArtistaYTitulo(autorRaw: String?, tituloRaw: String): ParsedMusicQuery {
    var tituloLimpio = tituloRaw
        .replace(Regex("(?i)[(\\[].*?(official|oficial|video|audio|lyrics|lyric|letra|sub|subtitulad|traducid|traduccion|traducción|hd|4k|mv|remastered|live|en vivo|full song).*?[)\\]]"), "")
        .replace(Regex("(?i)(official video|video oficial|official audio|audio oficial|video musical|lyric video|video con letra|sub español|sub espanol|subtitulado|subtitulada|subtitulos|subtítulos|traducido|traducida|traduccion|traducción|en vivo|live performance|hd|4k|mv)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (tituloLimpio.isBlank()) tituloLimpio = tituloRaw.trim()

    val autorLimpio = (autorRaw ?: "")
        .replace(Regex("(?i)(vevo|official|- topic|oficial|channel|canal|subtitulado|sub|music|musica|música|records)"), "")
        .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    val partes = tituloLimpio.split(Regex("\\s*-\\s*|\\s*–\\s*|\\s*—\\s*"))
    if (partes.size >= 2) {
        val posibleArtista = partes[0].trim()
        val posibleCancion = partes.subList(1, partes.size).joinToString(" - ").trim()
        if (posibleArtista.isNotBlank() && posibleCancion.isNotBlank()) {
            return ParsedMusicQuery(
                artist = posibleArtista,
                songTitle = posibleCancion,
                fullQuery = "$posibleArtista $posibleCancion"
            )
        }
    }

    val autorEsValido = (autorLimpio.length >= 2) &&
            !autorLimpio.equals("youtube", ignoreCase = true) &&
            !autorLimpio.equals("various artists", ignoreCase = true) &&
            !autorLimpio.equals("varios artistas", ignoreCase = true)

    return if (autorEsValido && !tituloLimpio.contains(autorLimpio, ignoreCase = true)) {
        ParsedMusicQuery(
            artist = autorLimpio,
            songTitle = tituloLimpio,
            fullQuery = "$autorLimpio $tituloLimpio"
        )
    } else {
        ParsedMusicQuery(
            artist = null,
            songTitle = tituloLimpio,
            fullQuery = tituloLimpio
        )
    }
}

object YtDlpInfoParser {
    private val AUDIO_ONLY_EXTS = setOf("m4a", "mp3", "opus", "ogg", "aac", "flac", "wav")

    fun parse(
        urlSolicitada: String,
        json: JSONObject,
        forzadoYTMusic: Boolean = false,
        parsedQuery: ParsedMusicQuery? = null
    ): MediaInfo {
        val entries = json.optJSONArray("entries")
        val isSearch = urlSolicitada.startsWith("ytsearch")
        val isList = (json.str("_type") == "playlist" && !isSearch) ||
                (entries != null && !isSearch && !forzadoYTMusic)

        val selectedEntry = if (entries != null && entries.length() > 0) {
            if ((forzadoYTMusic || isSearch) && parsedQuery != null) {
                selectBestOfficialEntry(entries, parsedQuery)
            } else {
                entries.optJSONObject(0)
            }
        } else {
            null
        }

        val src = selectedEntry ?: json

        val webUrl = src.str("webpage_url")
            ?: src.str("url")
            ?: json.str("webpage_url")
            ?: urlSolicitada

        val uploader = src.str("artist")
            ?: src.str("creator")
            ?: src.str("uploader")
            ?: src.str("channel")
            ?: json.str("uploader")
            ?: json.str("channel")

        val isMusicLink = urlSolicitada.contains("music.youtube.com", ignoreCase = true) ||
                webUrl.contains("music.youtube.com", ignoreCase = true)

        val uploaderLower = (uploader ?: "").lowercase(Locale.ROOT)
        val isTopicChannel = uploaderLower.endsWith("- topic") ||
                uploaderLower.endsWith("- tema") ||
                uploaderLower.contains("topic")

        val hasOfficialMusicMetadata = src.str("album") != null ||
                (src.str("artist") != null && src.str("track") != null)

        val esMusicOficial = forzadoYTMusic || isMusicLink || isTopicChannel || hasOfficialMusicMetadata

        return MediaInfo(
            url = webUrl,
            webpageUrl = webUrl,
            title = src.str("track") ?: src.str("title") ?: json.str("title") ?: urlSolicitada,
            thumbnailUrl = thumb(src) ?: thumb(json),
            author = uploader,
            platform = src.str("webpage_url_domain") ?: json.str("webpage_url_domain") ?: src.str("extractor_key"),
            durationSec = if (isList) null else (src.num("duration") ?: json.num("duration"))?.toLong(),
            playlistCount = if (isList) (json.num("playlist_count")?.toInt() ?: entries?.length()) else null,
            formats = parseFormats(src),
            manualSubs = keysOf(src.optJSONObject("subtitles")),
            autoSubs = keysOf(src.optJSONObject("automatic_captions")),
            esDeYouTubeMusic = esMusicOficial
        )
    }

    fun selectBestOfficialEntry(entries: JSONArray, queryInfo: ParsedMusicQuery): JSONObject? {
        if (entries.length() == 0) return null

        val targetSong = cleanStringForMatching(queryInfo.songTitle)
        val targetArtist = queryInfo.artist?.let { cleanStringForMatching(it) }

        var bestTopicEntry: JSONObject? = null
        var bestOfficialEntry: JSONObject? = null
        var maxTopicScore = -1000
        var maxOfficialScore = -1000

        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue

            val uploader = (entry.str("artist") ?: entry.str("creator") ?: entry.str("uploader")
                ?: entry.str("channel") ?: entry.str("playlist_uploader") ?: "").trim()
            val title = (entry.str("track") ?: entry.str("title") ?: "").trim()
            val webUrl = entry.str("webpage_url") ?: entry.str("url") ?: ""

            val uploaderLower = uploader.lowercase(Locale.ROOT)
            val titleLower = title.lowercase(Locale.ROOT)

            val cleanTitle = cleanStringForMatching(title)
            val cleanUploader = cleanStringForMatching(uploader)

            val isFanSub = titleLower.contains("sub español") ||
                    titleLower.contains("sub espanol") ||
                    titleLower.contains("subtitulad") ||
                    titleLower.contains("subtítulo") ||
                    titleLower.contains("subtitulo") ||
                    titleLower.contains("traducid") ||
                    titleLower.contains("traducción") ||
                    titleLower.contains("traduccion") ||
                    titleLower.contains("resubid") ||
                    titleLower.contains("reupload")

            val isTopicChannel = uploaderLower.endsWith("- topic") ||
                    uploaderLower.endsWith("- tema") ||
                    uploaderLower.contains("topic") ||
                    uploaderLower.contains("auto-generated")

            val isOfficialChannel = uploaderLower.contains("vevo") ||
                    uploaderLower.endsWith("official") ||
                    uploaderLower.endsWith("oficial") ||
                    uploaderLower.contains("official artist") ||
                    entry.str("artist") != null

            val isMusicUrl = webUrl.contains("music.youtube.com", ignoreCase = true)
            val hasStructuredAudioMetadata = entry.str("album") != null ||
                    (entry.str("artist") != null && entry.str("track") != null)

            if (isFanSub && !isTopicChannel) {
                continue
            }

            // CRITICAL TITLE MATCHING CHECK: Candidate title MUST match target song!
            val titleMatchesSong = targetSong.isNotBlank() && (
                    cleanTitle.contains(targetSong, ignoreCase = true) ||
                    targetSong.contains(cleanTitle, ignoreCase = true)
            )

            if (!titleMatchesSong) {
                continue // Different song! Disqualify!
            }

            var score = 0

            val isExact = cleanTitle.equals(targetSong, ignoreCase = true)
            score += if (isExact) 100 else 40

            if (!targetArtist.isNullOrBlank()) {
                if (cleanUploader.contains(targetArtist, ignoreCase = true) || cleanTitle.contains(targetArtist, ignoreCase = true)) {
                    score += 50
                }
            }

            if (isTopicChannel) {
                score += 300
            } else if (isMusicUrl) {
                score += 200
            } else if (hasStructuredAudioMetadata) {
                score += 150
            } else if (isOfficialChannel) {
                score += 100
            } else {
                score += 20
            }

            val songLower = targetSong.lowercase(Locale.ROOT)
            if (!songLower.contains("cover") && titleLower.contains("cover")) score -= 200
            if (!songLower.contains("live") && !songLower.contains("en vivo") &&
                (titleLower.contains("live") || titleLower.contains("en vivo"))) score -= 150
            if (!songLower.contains("remix") && titleLower.contains("remix")) score -= 100
            if (!songLower.contains("karaoke") && titleLower.contains("karaoke")) score -= 200

            if (isTopicChannel) {
                if (score > maxTopicScore) {
                    maxTopicScore = score
                    bestTopicEntry = entry
                }
            } else if (isOfficialChannel || hasStructuredAudioMetadata) {
                if (score > maxOfficialScore) {
                    maxOfficialScore = score
                    bestOfficialEntry = entry
                }
            }
        }

        if (bestTopicEntry != null) {
            return bestTopicEntry
        }

        if (bestOfficialEntry != null) {
            return bestOfficialEntry
        }

        return null
    }

    private fun cleanStringForMatching(text: String): String {
        return text
            .replace(Regex("(?i)[(\\[].*?[)\\]]"), "")
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseFormats(src: JSONObject): List<RealFormat> {
        val arr = src.optJSONArray("formats") ?: return emptyList()
        val out = ArrayList<RealFormat>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val id = f.str("format_id") ?: continue
            val ext = f.str("ext") ?: continue
            val height = f.num("height")?.toInt()
            val vRaw = f.str("vcodec")
            val aRaw = f.str("acodec")
            val hasV = when (vRaw) {
                null -> height != null
                "none" -> false
                else -> true
            }
            val hasA = when (aRaw) {
                null -> (hasV && vRaw == null) || (!hasV && ext in AUDIO_ONLY_EXTS)
                "none" -> false
                else -> true
            }
            if (!hasV && !hasA) continue

            val abrReal = f.num("abr")?.roundToInt()?.takeIf { it > 0 }
            val tbrReal = f.num("tbr")?.roundToInt()?.takeIf { it > 0 }

            out += RealFormat(
                id = id,
                ext = ext,
                vcodec = vRaw?.takeIf { it != "none" },
                acodec = aRaw?.takeIf { it != "none" },
                hasVideo = hasV,
                hasAudio = hasA,
                height = height,
                fps = f.num("fps")?.roundToInt(),
                abr = abrReal,
                tbr = tbrReal,
                asr = f.num("asr")?.roundToInt(),
                sizeBytes = (f.num("filesize") ?: f.num("filesize_approx"))?.toLong()?.takeIf { it > 0L }
            )
        }
        return out
    }

    private fun thumb(o: JSONObject?): String? {
        o ?: return null
        val arr = o.optJSONArray("thumbnails")
        if (arr != null && arr.length() > 0) {
            for (i in arr.length() - 1 downTo 0) {
                val u = arr.optJSONObject(i)?.str("url")
                if (u != null) return u
            }
        }
        return o.str("thumbnail")
    }

    private fun keysOf(o: JSONObject?): List<String> {
        o ?: return emptyList()
        val out = mutableListOf<String>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k != "live_chat") out += k
        }
        return out.sorted()
    }
}

private fun JSONObject.str(k: String): String? =
    if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.num(k: String): Double? =
    if (isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }

// ═══════════════════════════════════════════════════════════════════════════
//  2) PISTAS REALES DERIVADAS (OPUS PRIORIZADO Y RESOLUCIONES EXISTENTES)
// ═══════════════════════════════════════════════════════════════════════════

@Immutable
data class VideoRes(
    val height: Int,
    val fps: Int?,
    val bestFormatId: String,
    val containers: List<String>,
    val size: ByteSize?
)

@Immutable
data class AudioTrack(
    val id: String,
    val codec: String,
    val extSalidaNativa: String,
    val esOpus: Boolean,
    val kbps: Int?,
    val sampleRateHz: Int?,
    val size: ByteSize?
)

private fun RealFormat.calcularTamano(durationSec: Long?): ByteSize? {
    sizeBytes?.let { return ByteSize(it, approx = false) }
    val rate = abr ?: tbr ?: return null
    val d = durationSec ?: return null
    if (rate <= 0 || d <= 0) return null
    return ByteSize(rate * 125L * d, approx = true)
}

private fun nombreCodec(codec: String?, ext: String): String = when {
    codec == null -> ext.uppercase(Locale.ROOT)
    codec.startsWith("opus", ignoreCase = true) -> "Opus"
    codec.startsWith("mp4a", ignoreCase = true) -> "AAC (M4A)"
    codec.startsWith("mp3", ignoreCase = true) -> "MP3"
    codec.startsWith("vorbis", ignoreCase = true) -> "Vorbis"
    codec.startsWith("flac", ignoreCase = true) -> "FLAC"
    else -> ext.uppercase(Locale.ROOT)
}

private fun extensionNativaParaAudio(codec: String?, extOriginal: String): String = when {
    codec?.startsWith("opus", ignoreCase = true) == true -> "opus"
    codec?.startsWith("mp4a", ignoreCase = true) == true -> "m4a"
    codec?.startsWith("vorbis", ignoreCase = true) == true -> "ogg"
    extOriginal == "webm" -> "opus"
    else -> extOriginal
}

fun MediaInfo.audioTracks(): List<AudioTrack> {
    val soloAudio = formats.filter { it.hasAudio && !it.hasVideo }
    val fuente = soloAudio.ifEmpty { formats.filter { it.hasAudio } }

    return fuente
        .map { f ->
            val nombre = nombreCodec(f.acodec, f.ext)
            val esOpus = f.acodec?.startsWith("opus", ignoreCase = true) == true || nombre == "Opus"
            val kbpsReal = f.abr ?: if (!f.hasVideo) f.tbr else null
            AudioTrack(
                id = f.id,
                codec = nombre,
                extSalidaNativa = extensionNativaParaAudio(f.acodec, f.ext),
                esOpus = esOpus,
                kbps = kbpsReal,
                sampleRateHz = f.asr,
                size = if (soloAudio.isNotEmpty()) f.calcularTamano(durationSec) else null
            )
        }
        .sortedWith(
            compareByDescending<AudioTrack> { it.kbps ?: 0 }
                .thenByDescending { it.esOpus }
        )
        .distinctBy { Triple(it.codec, it.kbps, it.extSalidaNativa) }
}

fun MediaInfo.bestOpusOrNativeAudio(): AudioTrack? {
    val pistas = audioTracks()
    return pistas.firstOrNull { it.esOpus } ?: pistas.firstOrNull()
}

fun MediaInfo.videoResolutions(): List<VideoRes> {
    val bestAudio = bestOpusOrNativeAudio()
    val ordenContenedores = listOf("mp4", "webm", "mkv")

    // Agrupamos TODOS los formatos de video válidos.
    val rawResolutions = formats.asSequence()
        .filter { it.hasVideo && it.height != null && it.height > 0 }
        .groupBy { it.height!! }
        .map { (altura, lista) ->
            // Elegimos el formato de video más pesado de esta resolución.
            val mejorVideo = lista.maxByOrNull { f ->
                f.sizeBytes ?: f.tbr?.let { (it * 1024L) } ?: 0L
            }!!
            val tamVideo = mejorVideo.calcularTamano(durationSec)
            val tamAudio = if (mejorVideo.hasAudio) null else bestAudio?.size
            val tamTotal = when {
                tamVideo == null -> null
                mejorVideo.hasAudio -> tamVideo
                tamAudio != null -> ByteSize(tamVideo.bytes + tamAudio.bytes, tamVideo.approx || tamAudio.approx)
                else -> tamVideo.copy(approx = true)
            }
            val contenedoresReales = (lista.map { it.ext }.filter { it in ordenContenedores } + "mkv")
                .distinct()
                .sortedBy { ordenContenedores.indexOf(it) }

            VideoRes(
                height = altura,
                fps = lista.mapNotNull { it.fps }.maxOrNull(),
                bestFormatId = mejorVideo.id,
                containers = contenedoresReales,
                size = tamTotal
            )
        }

    // Filtro inteligente para "Resoluciones Falsas" (Upscaled):
    // Si una resolución dice ser más alta (ej: 1920p) pero PESA MENOS que una resolución menor (ej: 1080p),
    // significa que el servidor (ej: TikTok) infló los píxeles artificialmente pero destruyó el bitrate.
    // La verdadera calidad máxima está en el video que pesa más.
    
    val validResolutions = mutableListOf<VideoRes>()
    
    // Ordenamos de menor a mayor altura temporalmente para garantizar que la calidad aumente lógicamente
    val sortedByHeightAsc = rawResolutions.sortedBy { it.height }
    
    var maxSeenBytes = -1L
    
    for (res in sortedByHeightAsc) {
        val currentBytes = res.size?.bytes ?: 0L
        if (currentBytes == 0L) {
            // Si el motor no reporta tamaño, lo incluimos confiando en la altura.
            validResolutions.add(res)
        } else if (currentBytes >= maxSeenBytes * 0.85) {
            // Si esta resolución pesa más (o casi igual, tolerancia del 15%) que la resolución menor anterior, es una resolución real y legítima.
            validResolutions.add(res)
            if (currentBytes > maxSeenBytes) {
                maxSeenBytes = currentBytes
            }
        }
        // Si currentBytes es significativamente MENOR que maxSeenBytes, la descartamos totalmente.
        // Ej: Vimos un 1080p de 13 MB. El siguiente es un 1920p de 8 MB -> ¡Se descarta el 1920p falso!
    }

    // Finalmente, retornamos la lista limpia, ordenada de mejor (más alta/más pesada) a peor.
    return validResolutions.sortedByDescending { it.height }
}

fun MediaInfo.defaultSub(): Pair<String, Boolean>? {
    val dev = Locale.getDefault().language
    fun find(l: List<String>) = l.firstOrNull { it == dev } ?: l.firstOrNull { it.startsWith("$dev-") }
    find(manualSubs)?.let { return it to false }
    find(autoSubs)?.let { return it to true }
    manualSubs.firstOrNull()?.let { return it to false }
    return autoSubs.firstOrNull()?.let { it to true }
}

// ═══════════════════════════════════════════════════════════════════════════
//  3) SELECCIÓN FINAL Y ARGUMENTOS REALES PARA YT-DLP
// ═══════════════════════════════════════════════════════════════════════════

@Immutable
data class DownloadSelection(
    val url: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val kind: Kind,
    val esDescargaDirectaMaxima: Boolean,
    val selectorVideo: String?,
    val selectorAudio: String?,
    val videoHeight: Int?,
    val container: String?,
    val audioTrackId: String?,
    val audioExtReal: String,
    val convertirAMp3: Boolean,
    val realKbps: Int?,
    val estimatedSizeBytes: Long?,
    val metadata: Boolean,
    val embedCover: Boolean,
    val forzarPortadaCuadrada1a1: Boolean,
    val subLang: String?,
    val subAuto: Boolean,
    val saveThumb: Boolean
) {
    /** Etiqueta real resumida para mostrar en la tarjeta de progreso en vivo. */
    fun resumenCalidadReal(): String = if (kind == Kind.AUDIO) {
        buildString {
            append(audioExtReal.uppercase(Locale.ROOT))
            realKbps?.let { append(" • $it kbps") }
        }
    } else {
        buildString {
            append((container ?: "MP4/MKV").uppercase(Locale.ROOT))
            videoHeight?.let { append(" • ${it}p") }
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("url", url)
        obj.put("title", title)
        obj.put("author", author)
        obj.put("thumbnailUrl", thumbnailUrl)
        obj.put("kind", kind.name)
        obj.put("esDescargaDirectaMaxima", esDescargaDirectaMaxima)
        selectorVideo?.let { obj.put("selectorVideo", it) }
        selectorAudio?.let { obj.put("selectorAudio", it) }
        videoHeight?.let { obj.put("videoHeight", it) }
        container?.let { obj.put("container", it) }
        audioTrackId?.let { obj.put("audioTrackId", it) }
        obj.put("audioExtReal", audioExtReal)
        obj.put("convertirAMp3", convertirAMp3)
        realKbps?.let { obj.put("realKbps", it) }
        estimatedSizeBytes?.let { obj.put("estimatedSizeBytes", it) }
        obj.put("metadata", metadata)
        obj.put("embedCover", embedCover)
        obj.put("forzarPortadaCuadrada1a1", forzarPortadaCuadrada1a1)
        subLang?.let { obj.put("subLang", it) }
        obj.put("subAuto", subAuto)
        obj.put("saveThumb", saveThumb)
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): DownloadSelection {
            val obj = JSONObject(json)
            return DownloadSelection(
                url = obj.getString("url"),
                title = obj.getString("title"),
                author = if (obj.has("author")) obj.getString("author") else null,
                thumbnailUrl = if (obj.has("thumbnailUrl")) obj.getString("thumbnailUrl") else null,
                kind = Kind.valueOf(obj.getString("kind")),
                esDescargaDirectaMaxima = obj.getBoolean("esDescargaDirectaMaxima"),
                selectorVideo = if (obj.has("selectorVideo")) obj.getString("selectorVideo") else null,
                selectorAudio = if (obj.has("selectorAudio")) obj.getString("selectorAudio") else null,
                videoHeight = if (obj.has("videoHeight")) obj.getInt("videoHeight") else null,
                container = if (obj.has("container")) obj.getString("container") else null,
                audioTrackId = if (obj.has("audioTrackId")) obj.getString("audioTrackId") else null,
                audioExtReal = obj.getString("audioExtReal"),
                convertirAMp3 = obj.getBoolean("convertirAMp3"),
                realKbps = if (obj.has("realKbps")) obj.getInt("realKbps") else null,
                estimatedSizeBytes = if (obj.has("estimatedSizeBytes")) obj.getLong("estimatedSizeBytes") else null,
                metadata = obj.getBoolean("metadata"),
                embedCover = obj.getBoolean("embedCover"),
                forzarPortadaCuadrada1a1 = obj.getBoolean("forzarPortadaCuadrada1a1"),
                subLang = if (obj.has("subLang")) obj.getString("subLang") else null,
                subAuto = obj.getBoolean("subAuto"),
                saveThumb = obj.getBoolean("saveThumb")
            )
        }
    }
}

fun DownloadSelection.toYtDlpArgs(): List<String> = buildList {
    // --newline y --no-mtime son OBLIGATORIOS para que el progreso sea en tiempo real
    // y el archivo aparezca de primero en la carpeta "Descargas / Recientes" del teléfono.
    add("--newline")
    add("--no-mtime")

    when (kind) {
        Kind.VIDEO -> {
            add("-f")
            if (selectorVideo != null) {
                // Nuevo sistema de calidades reales
                add(selectorVideo)
                container?.let {
                    add("--merge-output-format")
                    add(it)
                }
            } else {
                // Fallback sistema antiguo
                val filtroAltura = videoHeight?.let { "[height<=$it]" } ?: ""
                val targetContainer = container ?: "mp4"

                if (url.contains("tiktok.com", ignoreCase = true)) {
                    add("b$filtroAltura/bv*$filtroAltura+ba/b")
                } else if (targetContainer == "mp4") {
                    add("bv*[vcodec^=avc1]$filtroAltura+ba[ext=m4a]/bv*[ext=mp4]$filtroAltura+ba[ext=m4a]/b[ext=mp4]$filtroAltura/bv*$filtroAltura+ba/b$filtroAltura")
                } else {
                    add("bv*$filtroAltura+ba[acodec^=opus]/bv*$filtroAltura+ba/b$filtroAltura")
                }

                add("--merge-output-format")
                add(targetContainer)
            }
        }

        Kind.AUDIO -> {
            // Se maneja ahora con aplicarMotorAudioPro en MainActivity para el procesamiento de audio avanzado.
        }
    }

    if (metadata) {
        add("--embed-metadata")
        add("--add-metadata")
    }

    if (embedCover) {
        add("--embed-thumbnail")
        add("--convert-thumbnails")
        add("jpg")
        if (forzarPortadaCuadrada1a1) {
            add("--ppa")
            add("ThumbnailsConvertor:-q:v 1 -vf crop=ih:ih")
        }
    }

    subLang?.let {
        add(if (subAuto) "--write-auto-subs" else "--write-subs")
        add("--sub-langs")
        add(it)
    }

    if (saveThumb) {
        add("--write-thumbnail")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  4) MOTOR DE PROGRESO REAL CON PREVISUALIZACIÓN Y GUARDADO EN /DOWNLOAD
// ═══════════════════════════════════════════════════════════════════════════

enum class ActiveDownloadPhase {
    PREPARING,
    DOWNLOADING,
    PROCESSING_FFMPEG,
    COMPLETED,
    FAILED
}

@Immutable
data class LiveDownloadItem(
    val id: String,
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
    val qualityBadge: String,
    val isAudio: Boolean,
    val progress: Float,             // 0f..1f real reportado por yt-dlp
    val downloadedText: String?,     // Ej: "4.2 MB / 8.5 MB" real
    val speedText: String?,          // Ej: "3.4 MB/s" real
    val etaText: String?,            // Ej: "00:02" real
    val statusMessage: String,       // Mensaje completo de la fase actual
    val phase: ActiveDownloadPhase,
    val savedUri: Uri? = null,
    val savedExtension: String? = null,
    val savedBytes: Long? = null
)

/**
 * Analiza las líneas reales que emite yt-dlp en consola:
 * Ej: "[download]  45.2% of   10.50MiB at    3.20MiB/s ETA 00:02"
 * Ej: "[Merger] Merging formats into..." / "[EmbedThumbnail] Adding thumbnail..."
 */
object YtDlpRealProgressParser {
    private val REGEX_PERCENT = Regex("""(\d+(?:\.\d+)?)%""")
    private val REGEX_TOTAL = Regex("""of\s+~?(\d+(?:\.\d+)?)\s*(KiB|MiB|GiB|KB|MB|GB)""", RegexOption.IGNORE_CASE)
    private val REGEX_SPEED = Regex("""at\s+(\d+(?:\.\d+)?\s*(?:KiB|MiB|GiB|KB|MB|GB)/s)""", RegexOption.IGNORE_CASE)
    private val REGEX_ETA = Regex("""ETA\s+(\d+:\d+(?::\d+)?)""")

    fun actualizarConSalidaReal(
        actual: LiveDownloadItem,
        progressFromCallback: Float,
        etaSecondsFromCallback: Long,
        line: String
    ): LiveDownloadItem {
        val limpia = line.trim()

        // Detectar si ya entró a FFmpeg (unir audio/video, incrustar carátula o metadatos)
        if (limpia.startsWith("[Merger]", ignoreCase = true) ||
            limpia.startsWith("[ExtractAudio]", ignoreCase = true) ||
            limpia.startsWith("[EmbedThumbnail]", ignoreCase = true) ||
            limpia.startsWith("[Metadata]", ignoreCase = true) ||
            limpia.startsWith("[ThumbnailsConvertor]", ignoreCase = true)
        ) {
            val msgFase = when {
                limpia.startsWith("[EmbedThumbnail]", ignoreCase = true) -> "Incrustando carátula HD en el archivo…"
                limpia.startsWith("[Metadata]", ignoreCase = true) -> "Escribiendo metadatos oficiales…"
                limpia.startsWith("[Merger]", ignoreCase = true) -> "Uniendo pistas de alta calidad (FFmpeg)…"
                else -> "Procesando archivo final con FFmpeg…"
            }
            return actual.copy(
                progress = 0.99f,
                phase = ActiveDownloadPhase.PROCESSING_FFMPEG,
                statusMessage = msgFase,
                etaText = null
            )
        }

        val pctEnLinea = REGEX_PERCENT.find(limpia)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val pctValido = when {
            pctEnLinea != null && pctEnLinea in 0f..100f -> (pctEnLinea / 100f)
            progressFromCallback in 0f..100f -> (progressFromCallback / 100f)
            else -> actual.progress
        }.coerceIn(0f, 1f)

        val matchTotal = REGEX_TOTAL.find(limpia)
        val textoPesos = if (matchTotal != null) {
            val valorTotal = matchTotal.groupValues[1].toDoubleOrNull() ?: 0.0
            val unidad = matchTotal.groupValues[2]
                .uppercase(Locale.ROOT)
                .replace("KIB", "KB")
                .replace("MIB", "MB")
                .replace("GIB", "GB")
            val descargadoActual = valorTotal * pctValido
            String.format(Locale.US, "%.1f %s / %.1f %s", descargadoActual, unidad, valorTotal, unidad)
        } else {
            actual.downloadedText
        }

        val velocidad = REGEX_SPEED.find(limpia)?.groupValues?.getOrNull(1)
            ?.replace("KiB", "KB", ignoreCase = true)
            ?.replace("MiB", "MB", ignoreCase = true)
            ?.replace("GiB", "GB", ignoreCase = true)
            ?: actual.speedText

        val eta = REGEX_ETA.find(limpia)?.groupValues?.getOrNull(1)
            ?: if (etaSecondsFromCallback > 0) fmtDuration(etaSecondsFromCallback) else actual.etaText

        return actual.copy(
            progress = pctValido,
            downloadedText = textoPesos,
            speedText = velocidad,
            etaText = eta,
            phase = ActiveDownloadPhase.DOWNLOADING,
            statusMessage = "Descargando en tiempo real…"
        )
    }
}

/**
 * Mueve el archivo final generado por yt-dlp a la carpeta pública "Download" del teléfono,
 * lo registra en MediaStore / MediaScanner para que aparezca de inmediato en la app "Archivos"
 * del sistema y borra el archivo temporal interno (0 MB de caché en la app).
 */
object PublicDownloadsExporter {
    suspend fun exportarACarpetaDescargasPublica(
        context: Context,
        archivoGenerado: File,
        esAudio: Boolean = false
    ): Pair<Uri?, Long> = withContext(Dispatchers.IO) {
        if (!archivoGenerado.exists()) return@withContext (null to 0L)
        val peso = archivoGenerado.length()
        val nombreArchivo = archivoGenerado.name
        val ext = archivoGenerado.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (esAudio) "audio/*" else "video/*"

        val dirPublicoBase = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val subcarpeta = if (esAudio) "ImaLoader/Musica" else "ImaLoader/Video"
        val dirFinal = File(dirPublicoBase, subcarpeta)
        if (!dirFinal.exists()) dirFinal.mkdirs()

        // Si el archivo YA está físicamente dentro del directorio final público, solo forzamos el escaneo
        if (archivoGenerado.absolutePath.startsWith(dirFinal.absolutePath)) {
            archivoGenerado.setLastModified(System.currentTimeMillis())
            val scannedUri = escanearArchivoEnSistema(context, archivoGenerado, mime)
            return@withContext (scannedUri to peso)
        }

        // Si yt-dlp + FFmpeg lo armaron en un directorio de trabajo temporal, lo pasamos a /Download/ImaLoader/...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subcarpeta")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
            if (itemUri != null) {
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    archivoGenerado.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
                runCatching { archivoGenerado.delete() }
                return@withContext (itemUri to peso)
            }
        }

        // Respaldo directo en carpeta pública Download/ImaLoader/...
        val destino = File(dirFinal, nombreArchivo)
        archivoGenerado.copyTo(destino, overwrite = true)
        destino.setLastModified(System.currentTimeMillis())
        runCatching { archivoGenerado.delete() }
        val uriFinal = escanearArchivoEnSistema(context, destino, mime)
        return@withContext (uriFinal to peso)
    }

    private suspend fun escanearArchivoEnSistema(
        context: Context,
        file: File,
        mime: String
    ): Uri? = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mime)
        ) { _, uri ->
            if (cont.isActive) {
                val resultado = uri ?: runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                }.getOrNull()
                cont.resume(resultado)
            }
        }
    }
}

/**
 * Controlador global de descargas en curso. Mantiene la lista reactiva de barras de progreso
 * con miniatura, velocidad y porcentaje real.
 */
object ImaLiveDownloadsStore {
    private val _items = MutableStateFlow<List<LiveDownloadItem>>(emptyList())
    val items: StateFlow<List<LiveDownloadItem>> = _items.asStateFlow()

    fun iniciarTarea(selection: DownloadSelection): String {
        val id = "dl_${System.currentTimeMillis()}"
        val estimadoStr = formatearBytesReales(selection.estimatedSizeBytes)?.let { "0.0 MB / $it" }
        val nuevo = LiveDownloadItem(
            id = id,
            title = selection.title,
            author = selection.author,
            thumbnailUrl = selection.thumbnailUrl,
            qualityBadge = selection.resumenCalidadReal(),
            isAudio = (selection.kind == Kind.AUDIO),
            progress = 0f,
            downloadedText = estimadoStr,
            speedText = null,
            etaText = null,
            statusMessage = "Preparando motor yt-dlp…",
            phase = ActiveDownloadPhase.PREPARING
        )
        _items.update { listOf(nuevo) + it }
        return id
    }

    fun actualizarProgresoReal(id: String, progress: Float, etaSec: Long, line: String) {
        _items.update { lista ->
            lista.map { item ->
                if (item.id == id) {
                    YtDlpRealProgressParser.actualizarConSalidaReal(item, progress, etaSec, line)
                } else {
                    item
                }
            }
        }
    }

    fun marcarCompletado(id: String, uriPublica: Uri?, archivoFinal: File?, bytesReales: Long) {
        val extReal = archivoFinal?.extension?.uppercase(Locale.ROOT)
        val tamRealStr = formatearBytesReales(bytesReales)
        _items.update { lista ->
            lista.map { item ->
                if (item.id == id) {
                    item.copy(
                        progress = 1f,
                        downloadedText = tamRealStr ?: item.downloadedText,
                        speedText = null,
                        etaText = null,
                        phase = ActiveDownloadPhase.COMPLETED,
                        statusMessage = "Guardado en la carpeta Descargas de tu teléfono",
                        savedUri = uriPublica,
                        savedExtension = extReal,
                        savedBytes = bytesReales
                    )
                } else {
                    item
                }
            }
        }
    }

    fun marcarError(id: String, mensajeError: String) {
        _items.update { lista ->
            lista.map { item ->
                if (item.id == id) {
                    item.copy(
                        phase = ActiveDownloadPhase.FAILED,
                        speedText = null,
                        etaText = null,
                        statusMessage = mensajeError
                    )
                } else {
                    item
                }
            }
        }
    }

    fun quitarTarjeta(id: String) {
        _items.update { lista -> lista.filterNot { it.id == id } }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  5) CONTROLADOR DEL FLUJO DE ANÁLISIS (CON YOUTUBE MUSIC)
// ═══════════════════════════════════════════════════════════════════════════

@Immutable
data class Draft(
    val kind: Kind = Kind.AUDIO,
    val height: Int? = null, // Usado solo para UI antigua, mantener para compatibilidad
    val selectorVideo: String? = null,
    val selectorAudio: String? = null,
    val container: String? = null,
    val audioTrackId: String? = null,
    val convertirAMp3: Boolean = false,
    val metadata: Boolean = true,
    val embedCover: Boolean = true,
    val subLang: String? = null,
    val subAuto: Boolean = false,
    val saveThumb: Boolean = false
)

sealed interface LinkFlowState {
    object Idle : LinkFlowState
    data class Analyzing(val url: String, val mensaje: String = "Analizando enlace con el motor…") : LinkFlowState
    data class Ready(
        val info: MediaInfo,
        val draft: Draft,
        val calidades: Calidades? = null,
        val videoOriginalRespaldo: MediaInfo? = null,
        val buscandoEnYTMusic: Boolean = false
    ) : LinkFlowState
    data class Failed(val message: String, val urlIntentada: String = "") : LinkFlowState
}

val LinkFlowState.showsSearchBar: Boolean
    get() = this is LinkFlowState.Idle

class LinkFlowController(
    private val scope: CoroutineScope,
    private val fetchInfoJson: suspend (urlOrSearchQuery: String) -> String,
    private val onStartDownload: (DownloadSelection) -> Unit
) {
    private val _state = MutableStateFlow<LinkFlowState>(LinkFlowState.Idle)
    val state: StateFlow<LinkFlowState> = _state.asStateFlow()
    private var job: Job? = null

    fun analyze(rawUrl: String) {
        val url = rawUrl.trim()
        if ((!url.startsWith("http://")) && (!url.startsWith("https://"))) {
            _state.value = LinkFlowState.Failed("Pega un enlace válido que empiece con http:// o https://", url)
            return
        }
        job?.cancel()
        _state.value = LinkFlowState.Analyzing(url)
        job = scope.launch {
            try {
                var localCalidades: Calidades? = null
                val info = withContext(Dispatchers.IO) {
                    val raw = fetchInfoJson(url)
                    val inicio = raw.indexOf('{').coerceAtLeast(0)
                    localCalidades = CalidadesReales.analizar(raw)
                    val jsonLimpio = JSONObject(raw.substring(inicio))
                    YtDlpInfoParser.parse(url, jsonLimpio)
                }
                val bestAudio = info.bestOpusOrNativeAudio()
                val bestVideo = info.videoResolutions().firstOrNull()
                val tipoInicial = if (info.esDeYouTubeMusic || bestVideo == null) Kind.AUDIO else Kind.VIDEO

                val opcionesVideo = localCalidades?.video ?: emptyList()
                val selectorV = if (opcionesVideo.isNotEmpty()) opcionesVideo.first().selector else null
                
                val opcionesAudio = localCalidades?.audio ?: emptyList()
                val selectorA = opcionesAudio.porNivel(NivelAudio.ALTA)?.selector

                _state.value = LinkFlowState.Ready(
                    info = info,
                    calidades = localCalidades,
                    draft = Draft(
                        kind = tipoInicial,
                        height = bestVideo?.height, // Se deja por compatibilidad
                        selectorVideo = selectorV,
                        selectorAudio = selectorA,
                        audioTrackId = bestAudio?.id
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: JSONException) {
                _state.value = LinkFlowState.Failed("El motor devolvió datos incompletos para este enlace.", url)
            } catch (e: Exception) {
                _state.value = LinkFlowState.Failed(e.friendlyMessage(), url)
            }
        }
    }

    fun switchToYouTubeMusic() {
        val actual = _state.value as? LinkFlowState.Ready ?: return
        if (actual.buscandoEnYTMusic) return

        val infoOriginal = actual.info
        job?.cancel()
        _state.value = actual.copy(buscandoEnYTMusic = true)

        job = scope.launch(Dispatchers.IO) {
            try {
                val result = OfficialTrackFinder.find(
                    SourceInfo(
                        title = infoOriginal.title,
                        uploader = infoOriginal.author,
                        durationSec = infoOriginal.durationSec?.toInt(),
                        artist = infoOriginal.author,
                        track = infoOriginal.title
                    )
                )

                when (result) {
                    is OfficialResult.Found -> {
                        val track = result.track
                        val rawFull = fetchInfoJson(track.url)
                        val calidadesYTM = CalidadesReales.analizar(rawFull)
                        val jsonFull = JSONObject(rawFull.substring(rawFull.indexOf('{').coerceAtLeast(0)))
                        
                        val parsedQuery = ParsedMusicQuery(
                            artist = track.artistDisplay,
                            songTitle = track.title,
                            fullQuery = "${track.artistDisplay} ${track.title}"
                        )
                        
                        val nuevaInfo = YtDlpInfoParser.parse(track.url, jsonFull, forzadoYTMusic = true, parsedQuery = parsedQuery)
                        val mejorOpus = nuevaInfo.bestOpusOrNativeAudio()
                        
                        // Overwrite with official metadata visually
                        val finalInfo = nuevaInfo.copy(
                            title = track.title,
                            author = track.artistDisplay,
                            thumbnailUrl = track.coverUrl ?: nuevaInfo.thumbnailUrl,
                            durationSec = track.durationSec?.toLong() ?: nuevaInfo.durationSec
                        )

                        _state.value = LinkFlowState.Ready(
                            info = finalInfo,
                            calidades = calidadesYTM,
                            draft = actual.draft.copy(
                                kind = Kind.AUDIO,
                                selectorAudio = calidadesYTM.audio.porNivel(NivelAudio.ALTA)?.selector,
                                audioTrackId = mejorOpus?.id,
                                convertirAMp3 = false
                            ),
                            videoOriginalRespaldo = infoOriginal,
                            buscandoEnYTMusic = false
                        )
                    }
                    is OfficialResult.Error -> {
                        _state.value = actual.copy(buscandoEnYTMusic = false)
                    }
                    is OfficialResult.NotFound -> {
                        _state.value = actual.copy(buscandoEnYTMusic = false)
                    }
                }
            } catch (_: Exception) {
                _state.value = actual.copy(buscandoEnYTMusic = false)
            }
        }
    }

    fun volverAVideoOriginal() {
        val actual = _state.value as? LinkFlowState.Ready ?: return
        val respaldo = actual.videoOriginalRespaldo ?: return
        val mejorOpus = respaldo.bestOpusOrNativeAudio()
        _state.value = LinkFlowState.Ready(
            info = respaldo,
            draft = actual.draft.copy(audioTrackId = mejorOpus?.id),
            videoOriginalRespaldo = null,
            buscandoEnYTMusic = false
        )
    }

    fun update(change: (Draft) -> Draft) {
        val s = _state.value
        if (s is LinkFlowState.Ready) {
            _state.value = s.copy(draft = change(s.draft))
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = LinkFlowState.Idle
    }

    fun startDirectMaxQualityDownload() {
        val s = _state.value as? LinkFlowState.Ready ?: return
        val info = s.info
        val d = s.draft

        val sel = if (d.kind == Kind.AUDIO) {
            val bestOpus = info.bestOpusOrNativeAudio()
            DownloadSelection(
                url = info.url,
                title = info.title,
                author = info.author,
                thumbnailUrl = info.thumbnailUrl,
                kind = Kind.AUDIO,
                esDescargaDirectaMaxima = true,
                selectorVideo = null,
                selectorAudio = s.calidades?.audio?.porNivel(NivelAudio.ALTA)?.selector,
                videoHeight = null,
                container = null,
                audioTrackId = bestOpus?.id,
                audioExtReal = bestOpus?.extSalidaNativa ?: "opus",
                convertirAMp3 = false,
                realKbps = bestOpus?.kbps,
                estimatedSizeBytes = bestOpus?.size?.bytes,
                metadata = d.metadata,
                embedCover = d.embedCover && info.thumbnailUrl != null,
                forzarPortadaCuadrada1a1 = info.esDeYouTubeMusic,
                subLang = null,
                subAuto = false,
                saveThumb = d.saveThumb && info.thumbnailUrl != null
            )
        } else {
            val bestRes = info.videoResolutions().firstOrNull()
            DownloadSelection(
                url = info.url,
                title = info.title,
                author = info.author,
                thumbnailUrl = info.thumbnailUrl,
                kind = Kind.VIDEO,
                esDescargaDirectaMaxima = true,
                selectorVideo = s.calidades?.video?.firstOrNull()?.selector,
                selectorAudio = null,
                videoHeight = bestRes?.height,
                container = d.container?.takeIf { bestRes != null && it in bestRes.containers },
                audioTrackId = null,
                audioExtReal = "opus",
                convertirAMp3 = false,
                realKbps = null,
                estimatedSizeBytes = bestRes?.size?.bytes,
                metadata = d.metadata,
                embedCover = false,
                forzarPortadaCuadrada1a1 = false,
                subLang = d.subLang,
                subAuto = d.subLang != null && d.subAuto,
                saveThumb = d.saveThumb && info.thumbnailUrl != null
            )
        }

        onStartDownload(sel)
        _state.value = LinkFlowState.Idle
    }

    fun startCustomDownload() {
        val s = _state.value as? LinkFlowState.Ready ?: return
        onStartDownload(s.toCustomSelection())
        _state.value = LinkFlowState.Idle
    }
}

private fun Throwable.friendlyMessage(): String {
    val lines = message.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
    val pick = lines.firstOrNull { it.startsWith("ERROR") } ?: lines.firstOrNull()
    return pick?.removePrefix("ERROR:")?.trim() ?: "No se pudo analizar el enlace."
}

private fun LinkFlowState.Ready.toCustomSelection(): DownloadSelection {
    val d = draft
    val resolutions = info.videoResolutions()
    val chosenRes = resolutions.firstOrNull { it.height == d.height } ?: resolutions.firstOrNull()
    val tracks = info.audioTracks()
    val chosenTrack = tracks.firstOrNull { it.id == d.audioTrackId } ?: info.bestOpusOrNativeAudio()
    val isVideo = d.kind == Kind.VIDEO

    return DownloadSelection(
        url = info.url,
        title = info.title,
        author = info.author,
        thumbnailUrl = info.thumbnailUrl,
        kind = d.kind,
        esDescargaDirectaMaxima = false,
        selectorVideo = d.selectorVideo,
        selectorAudio = d.selectorAudio,
        videoHeight = if (isVideo) chosenRes?.height else null,
        container = if (isVideo && chosenRes != null) d.container?.takeIf { it in chosenRes.containers } else null,
        audioTrackId = if (!isVideo) chosenTrack?.id else null,
        audioExtReal = if (d.convertirAMp3) "mp3" else (chosenTrack?.extSalidaNativa ?: "opus"),
        convertirAMp3 = !isVideo && d.convertirAMp3,
        realKbps = if (!isVideo) chosenTrack?.kbps else null,
        estimatedSizeBytes = if (isVideo) chosenRes?.size?.bytes else chosenTrack?.size?.bytes,
        metadata = d.metadata,
        embedCover = !isVideo && d.embedCover && info.thumbnailUrl != null,
        forzarPortadaCuadrada1a1 = !isVideo && info.esDeYouTubeMusic,
        subLang = if (isVideo) d.subLang else null,
        subAuto = isVideo && d.subLang != null && d.subAuto,
        saveThumb = d.saveThumb && info.thumbnailUrl != null
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  6) INTERFAZ COMPLETA ADAPTADA A IMATEMAS (SIN CORTAR TEXTOS CON "...")
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ImaCompleteLinkSection(
    controller: LinkFlowController,
    enlaceTexto: String,
    onCambioEnlace: (String) -> Unit,
    onPegarDesdePortapapeles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val state by controller.state.collectAsState()
    val descargasActivas by ImaLiveDownloadsStore.items.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = state.showsSearchBar,
            enter = fadeIn(tween(250)) + expandVertically(tween(280)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ImaLinkField(
                    valor = enlaceTexto,
                    onCambio = onCambioEnlace,
                    mostrarIconoEnlace = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ImaButton(
                        texto = "Pegar",
                        onClick = onPegarDesdePortapapeles,
                        modifier = Modifier.weight(1f),
                        rol = ImaRol.FUERTE
                    )
                    ImaButton(
                        texto = "Eliminar",
                        onClick = { onCambioEnlace("") },
                        modifier = Modifier.weight(1f),
                        rol = ImaRol.SUAVE
                    )
                }
                if (p.modo == ImaThemeMode.OSCURO || p.modo == ImaThemeMode.RETRO) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .height(1.dp)
                            .background(p.borde)
                    )
                }
                ImaButton(
                    texto = "Analizar enlace",
                    onClick = { controller.analyze(enlaceTexto) },
                    modifier = Modifier.fillMaxWidth(),
                    rol = ImaRol.PRINCIPAL,
                    mostrarIconoDescarga = true,
                    habilitado = enlaceTexto.isNotBlank()
                )
            }
        }

        // Panel de selección de formatos
        LinkFlowPanel(controller = controller)

        // Barras de progreso en tiempo real con previsualización (miniatura + título + velocidad real)
        if (descargasActivas.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                descargasActivas.forEach { item ->
                    ImaLiveProgressPreviewCard(
                        item = item,
                        onDismiss = { ImaLiveDownloadsStore.quitarTarjeta(item.id) }
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de progreso realista con previsualización del archivo (Miniatura, Título completo,
 * formato real, MB descargados vs totales, velocidad en MB/s y botón para abrir al terminar).
 */
@Composable
fun ImaLiveProgressPreviewCard(
    item: LiveDownloadItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val p = LocalImaPalette.current
    val progresoAnimado by animateFloatAsState(
        targetValue = item.progress.coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "progreso-real"
    )

    ImaCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
        // Fila de previsualización del archivo: Miniatura + Título completo + Autor + Etiqueta de formato
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(
                url = item.thumbnailUrl,
                esCuadrada = item.isAudio,
                modifier = if (item.isAudio) {
                    Modifier.size(64.dp)
                } else {
                    Modifier.width(96.dp).aspectRatio(16f / 9f)
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = p.texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                    // Sin maxLines = 1 para que se pueda leer todo el título completo
                )
                if (!item.author.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.author,
                        color = p.textoSuave,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                ImaBadgeTag(texto = item.qualityBadge)
            }
        }

        Spacer(Modifier.height(12.dp))

        when (item.phase) {
            ActiveDownloadPhase.PREPARING,
            ActiveDownloadPhase.DOWNLOADING,
            ActiveDownloadPhase.PROCESSING_FFMPEG -> {
                LinearProgressIndicator(
                    progress = { progresoAnimado },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = p.acento,
                    trackColor = p.borde.copy(alpha = 0.35f)
                )
                Spacer(Modifier.height(8.dp))

                // Estadísticas reales del motor (Porcentaje, MB descargados, Velocidad y ETA)
                val lineaMetricas = buildString {
                    append("${(item.progress * 100).roundToInt()}%")
                    item.downloadedText?.let { append(" • $it") }
                    item.speedText?.let { append(" • $it") }
                    item.etaText?.let { append(" • Restan $it") }
                }
                Text(
                    text = lineaMetricas,
                    color = p.texto,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.statusMessage,
                    color = p.textoSuave,
                    fontSize = 12.sp
                )
            }

            ActiveDownloadPhase.COMPLETED -> {
                val resumenFinal = buildString {
                    append("✓ Descarga completada")
                    item.savedExtension?.let { append(" • $it") }
                    formatearBytesReales(item.savedBytes)?.let { append(" • $it") }
                }
                Text(
                    text = resumenFinal,
                    color = p.acento,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.statusMessage,
                    color = p.textoSuave,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.savedUri != null) {
                        ImaButton(
                            texto = "📂 Abrir archivo",
                            onClick = {
                                abrirUriDirecta(context, item.savedUri, item.isAudio, item.savedExtension)
                            },
                            modifier = Modifier.weight(1f),
                            rol = ImaRol.PRINCIPAL
                        )
                    }
                    ImaButton(
                        texto = "Ocultar",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        rol = ImaRol.SUAVE
                    )
                }
            }

            ActiveDownloadPhase.FAILED -> {
                Text(
                    text = "Error en la descarga",
                    color = p.texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.statusMessage,
                    color = p.textoSuave,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                ImaButton(
                    texto = "Cerrar aviso",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    rol = ImaRol.SUAVE
                )
            }
        }
    }
}

private fun abrirUriDirecta(context: Context, uri: Uri, esAudio: Boolean, ext: String?) {
    val extMin = ext?.lowercase(Locale.ROOT) ?: ""
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extMin)
        ?: if (esAudio) "audio/*" else "video/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
fun LinkFlowPanel(
    controller: LinkFlowController,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val state by controller.state.collectAsState()

    AnimatedContent(
        targetState = state,
        label = "link-flow-anim",
        modifier = modifier.fillMaxWidth()
    ) { s ->
        when (s) {
            LinkFlowState.Idle -> Unit

            is LinkFlowState.Analyzing -> {
                ImaCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = p.acento,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.mensaje,
                                color = p.texto,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                s.url,
                                color = p.textoSuave,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ImaSubtlePillButton(texto = "Cancelar", onClick = controller::cancel)
                    }
                }
            }

            is LinkFlowState.Failed -> {
                ImaCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No se pudo procesar el enlace",
                        color = p.texto,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.message,
                        color = p.textoSuave,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ImaButton(
                            texto = "← Cambiar enlace",
                            onClick = controller::cancel,
                            modifier = Modifier.weight(1f),
                            rol = ImaRol.SUAVE
                        )
                        if (s.urlIntentada.isNotBlank()) {
                            ImaButton(
                                texto = "Reintentar",
                                onClick = { controller.analyze(s.urlIntentada) },
                                modifier = Modifier.weight(1f),
                                rol = ImaRol.PRINCIPAL
                            )
                        }
                    }
                }
            }

            is LinkFlowState.Ready -> {
                ReadyFlowPanel(s = s, c = controller)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyFlowPanel(s: LinkFlowState.Ready, c: LinkFlowController) {
    val p = LocalImaPalette.current
    val info = s.info
    val d = s.draft

    val resolutions = remember(info) { info.videoResolutions() }
    val tracks = remember(info) { info.audioTracks() }
    val bestOpusTrack = remember(info) { info.bestOpusOrNativeAudio() }

    val canVideo = resolutions.isNotEmpty()
    val canAudio = tracks.isNotEmpty()
    val chosenRes = resolutions.firstOrNull { it.height == d.height } ?: resolutions.firstOrNull()
    val chosenTrack = tracks.firstOrNull { it.id == d.audioTrackId } ?: bestOpusTrack
    val isList = info.playlistCount != null

    val tamanoActual: ByteSize? = if (isList) null else when (d.kind) {
        Kind.VIDEO -> chosenRes?.size
        Kind.AUDIO -> chosenTrack?.size
    }

    Column(
        Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImaSubtlePillButton(
                texto = "← Cambiar enlace",
                onClick = c::cancel
            )
            if (info.esDeYouTubeMusic) {
                ImaBadgeTag(texto = "🎵 YouTube Music Oficial")
            }
        }

        ImaCard(Modifier.fillMaxWidth()) {
            // 1. Previsualización completa del enlace (Miniatura + Título completo sin recortar)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumbnail(
                    url = info.thumbnailUrl,
                    esCuadrada = info.esDeYouTubeMusic,
                    modifier = if (info.esDeYouTubeMusic) {
                        Modifier.size(80.dp)
                    } else {
                        Modifier.width(116.dp).aspectRatio(16f / 9f)
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        color = p.texto,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                        // Se muestra el título completo sin cortar
                    )
                    val meta = listOfNotNull(info.author, info.platform).joinToString(" • ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = meta,
                            color = p.textoSuave,
                            fontSize = 12.sp
                        )
                    }
                    val extra = listOfNotNull(
                        info.durationSec?.let(::fmtDuration),
                        info.playlistCount?.let { "Lista • $it elementos" }
                    ).joinToString(" • ")
                    if (extra.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = extra,
                            color = p.acento,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 2. PASO 1: Selector principal AUDIO vs VIDEO
            if (canVideo && canAudio) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImaButton(
                        texto = "🎵 Audio",
                        onClick = { c.update { it.copy(kind = Kind.AUDIO) } },
                        modifier = Modifier.weight(1f),
                        rol = if (d.kind == Kind.AUDIO) ImaRol.PRINCIPAL else ImaRol.SUAVE
                    )
                    ImaButton(
                        texto = "🎬 Video",
                        onClick = { c.update { it.copy(kind = Kind.VIDEO) } },
                        modifier = Modifier.weight(1f),
                        rol = if (d.kind == Kind.VIDEO) ImaRol.PRINCIPAL else ImaRol.SUAVE
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 3. Aviso inteligente de YouTube → YouTube Music
            AnimatedVisibility(visible = (d.kind == Kind.AUDIO && info.esVideoNormalDeYouTube)) {
                YouTubeMusicSuggestionBanner(
                    buscando = s.buscandoEnYTMusic,
                    onAceptarCambio = c::switchToYouTubeMusic
                )
            }

            AnimatedVisibility(visible = (d.kind == Kind.AUDIO && s.videoOriginalRespaldo != null)) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Usando portada cuadrada 1:1 y audio oficial de estudio.",
                        color = p.textoSuave,
                        fontSize = 12.sp
                    )
                    ImaSubtlePillButton(
                        texto = "Volver al video original",
                        onClick = c::volverAVideoOriginal
                    )
                }
            }

            // 4. PASO 2: Opciones completas y legibles (0 puntos suspensivos)
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (d.kind == Kind.AUDIO) {
                    val fallbackTrack = bestOpusTrack
                    val opcionesA = s.calidades?.audio
                    
                    if (!opcionesA.isNullOrEmpty()) {
                        val maxAudio = opcionesA.porNivel(NivelAudio.ALTA) ?: opcionesA.first()
                        val selectedAudio = opcionesA.firstOrNull { it.selector == d.selectorAudio } ?: maxAudio
                        
                        ImaActionMultiLineButton(
                            titulo = "⚡ Descarga directa en máxima calidad",
                            subtitulo = "Formato ${maxAudio.titulo} • ${maxAudio.detalle}",
                            onClick = c::startDirectMaxQualityDownload,
                            rol = ImaRol.PRINCIPAL
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "O personaliza la pista real del motor:",
                            color = p.textoSuave,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        ThemedSelectorRow(
                            label = "Pista nativa",
                            current = "${selectedAudio.titulo} • ${selectedAudio.detalle}",
                            options = opcionesA.map { "${it.titulo} • ${it.detalle}" to it.selector }
                        ) { sel ->
                            c.update { it.copy(selectorAudio = sel) }
                        }
                    } else if (fallbackTrack != null) {
                        // Fallback antiguo
                        ImaActionMultiLineButton(
                            titulo = "⚡ Descarga directa en máxima calidad",
                            subtitulo = buildString {
                                append("Formato ${fallbackTrack.codec.uppercase(Locale.ROOT)} nativo • Pura copia bit a bit sin recodificar")
                                fallbackTrack.kbps?.let { append(" • $it kbps reales") }
                                fallbackTrack.size?.let { append(" • ${it.fmt()}") }
                            },
                            onClick = c::startDirectMaxQualityDownload,
                            rol = ImaRol.PRINCIPAL
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "O personaliza la pista real del motor:",
                            color = p.textoSuave,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val actualTrack = chosenTrack ?: fallbackTrack
                        ThemedSelectorRow(
                            label = "Pista nativa",
                            current = actualTrack.labelDetallado(),
                            options = tracks.map { it.labelDetallado() to it.id }
                        ) { idElegido ->
                            c.update { it.copy(audioTrackId = idElegido) }
                        }
                    }

                    // Selector de Contenedor para Audio (Mp3 / Original)
                    val extBase = if (s.calidades?.audio?.isNotEmpty() == true) {
                        val maxAudio = s.calidades.audio.porNivel(NivelAudio.ALTA) ?: s.calidades.audio.first()
                        val selectedAudio = s.calidades.audio.firstOrNull { it.selector == d.selectorAudio } ?: maxAudio
                        selectedAudio.codec
                    } else {
                        (chosenTrack ?: bestOpusTrack)?.extSalidaNativa?.uppercase(Locale.ROOT) ?: "OPUS"
                    }

                    val textoContenedor = if (d.convertirAMp3) {
                        "Convertir a MP3 (CBR 320 kbps)"
                    } else {
                        "Original sin recodificar (.$extBase • Copia bit a bit 0% pérdida)"
                    }
                    ThemedSelectorRow(
                        label = "Contenedor",
                        current = textoContenedor,
                        options = listOf(
                            "Original sin recodificar (.$extBase • 0% Pérdida de calidad)" to false,
                            "Convertir a MP3 (Compatible • CBR 320 kbps)" to true
                        )
                    ) { aMp3 ->
                        c.update { it.copy(convertirAMp3 = aMp3) }
                    }

                    AudioQualityTransparencyCard(p)
                }

                if (d.kind == Kind.VIDEO) {
                    val fallbackRes = chosenRes
                    val opcionesV = s.calidades?.video
                    
                    if (!opcionesV.isNullOrEmpty()) {
                        val maxRes = opcionesV.first()
                        val selectedRes = opcionesV.firstOrNull { it.selector == d.selectorVideo } ?: maxRes
                        
                        ImaActionMultiLineButton(
                            titulo = "⚡ Descarga directa en máxima resolución",
                            subtitulo = "${maxRes.titulo} • ${maxRes.detalle}",
                            onClick = c::startDirectMaxQualityDownload,
                            rol = ImaRol.PRINCIPAL
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "O elige entre las resoluciones reales disponibles:",
                            color = p.textoSuave,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        ThemedSelectorRow(
                            label = "Resolución",
                            current = "${selectedRes.titulo} • ${selectedRes.detalle}",
                            options = opcionesV.map { "${it.titulo} • ${it.detalle}" to it.selector }
                        ) { sel ->
                            c.update { it.copy(selectorVideo = sel, container = null) }
                        }
                    } else if (fallbackRes != null) {
                        // Fallback antiguo
                        val maxRes = resolutions.first()
                        ImaActionMultiLineButton(
                            titulo = "⚡ Descarga directa en máxima resolución",
                            subtitulo = buildString {
                                append(maxRes.labelCompleto())
                                append(" • Audio Opus nativo")
                            },
                            onClick = c::startDirectMaxQualityDownload,
                            rol = ImaRol.PRINCIPAL
                        )

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "O elige entre las resoluciones reales disponibles:",
                            color = p.textoSuave,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        ThemedSelectorRow(
                            label = "Resolución",
                            current = fallbackRes.labelCompleto(),
                            options = resolutions.map { it.labelCompleto() to it.height }
                        ) { h ->
                            c.update { it.copy(height = h, container = null) }
                        }
                    }

                    val chosenExt = if (d.selectorVideo != null && s.calidades?.video != null) {
                        "MP4 (automático)"
                    } else {
                        d.container ?: "Automático (${fallbackRes?.containers?.firstOrNull()?.uppercase(Locale.ROOT) ?: "MP4"})"
                    }
                    
                    ThemedSelectorRow(
                        label = "Contenedor",
                        current = chosenExt,
                        options = listOf<Pair<String, String?>>("Automático" to null) +
                            (fallbackRes?.containers ?: emptyList()).map { it.uppercase(Locale.ROOT) to it }
                    ) { ct ->
                        c.update { it.copy(container = ct) }
                    }

                    if (d.subLang != null) {
                        ThemedSelectorRow(
                            label = "Subtítulos",
                            current = langName(d.subLang) + if (d.subAuto) " (auto)" else "",
                            options = info.manualSubs.map { langName(it) to (it to false) } +
                                info.autoSubs.map { "${langName(it)} (auto)" to (it to true) }
                        ) { (lang, auto) ->
                            c.update { it.copy(subLang = lang, subAuto = auto) }
                        }
                    }
                }

                // FlowRow para que todos los chips se acomoden solos y se lean completos sin cortarse
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (d.kind == Kind.AUDIO) {
                        ImaToggleChip(
                            texto = "Metadatos oficiales",
                            activo = d.metadata,
                            onClick = { c.update { it.copy(metadata = !it.metadata) } }
                        )
                        if (info.thumbnailUrl != null) {
                            ImaToggleChip(
                                texto = "Incrustar portada HD",
                                activo = d.embedCover,
                                onClick = { c.update { it.copy(embedCover = !it.embedCover) } }
                            )
                        }
                    } else if (info.hasSubs) {
                        ImaToggleChip(
                            texto = "Subtítulos",
                            activo = d.subLang != null,
                            onClick = {
                                c.update {
                                    if (it.subLang != null) it.copy(subLang = null, subAuto = false)
                                    else info.defaultSub()?.let { (l, a) -> it.copy(subLang = l, subAuto = a) } ?: it
                                }
                            }
                        )
                    }
                    if (info.thumbnailUrl != null) {
                        ImaToggleChip(
                            texto = "Guardar miniatura aparte",
                            activo = d.saveThumb,
                            onClick = { c.update { it.copy(saveThumb = !it.saveThumb) } }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 5. Botón de descarga personalizada con subtítulo completo legible
            val subtituloPersonalizado = buildString {
                if (d.kind == Kind.AUDIO && chosenTrack != null) {
                    val ext = if (d.convertirAMp3) "MP3" else chosenTrack.extSalidaNativa.uppercase(Locale.ROOT)
                    append("Formato $ext")
                    chosenTrack.kbps?.let { append(" • $it kbps") }
                } else if (d.kind == Kind.VIDEO && chosenRes != null) {
                    append("Resolución ${chosenRes.height}p")
                    val cont = d.container?.uppercase(Locale.ROOT) ?: "Auto"
                    append(" • Contenedor $cont")
                }
                tamanoActual?.let { append(" • Peso ${it.fmt()}") }
            }

            ImaActionMultiLineButton(
                titulo = "Descargar con mi selección",
                subtitulo = subtituloPersonalizado,
                onClick = c::startCustomDownload,
                rol = ImaRol.FUERTE
            )
        }
    }
}

/**
 * Botón temático de 2 líneas (Título + Subtítulo completo) que NUNCA corta el texto con "...".
 * Se adapta automáticamente a Claro, Oscuro, Material You, ImaLoader y Retro.
 */
@Composable
fun ImaActionMultiLineButton(
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rol: ImaRol = ImaRol.PRINCIPAL,
    habilitado: Boolean = true
) {
    val p = LocalImaPalette.current
    val fondo = when (rol) {
        ImaRol.PRINCIPAL -> p.btnPrincipal
        ImaRol.FUERTE -> p.btnFuerte
        ImaRol.SUAVE -> p.btnSuave
    }
    val letra = when (rol) {
        ImaRol.PRINCIPAL -> p.sobreBtnPrincipal
        ImaRol.FUERTE -> p.sobreBtnFuerte
        ImaRol.SUAVE -> p.sobreBtnSuave
    }
    val forma = RoundedCornerShape(24.dp)
    val requiereBordeSuave = (p.modo == ImaThemeMode.CLARO && rol == ImaRol.SUAVE) ||
        (p.modo == ImaThemeMode.OSCURO)

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .imaRelleno(p, fondo, forma, conBorde = requiereBordeSuave)
            .clip(forma)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ImaDownloadTrayIcon(tint = letra, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = titulo,
                color = letra,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitulo.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitulo,
                    color = letra.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AudioQualityTransparencyCard(p: ImaPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .imaRelleno(p, p.campo, RoundedCornerShape(16.dp), conBorde = true)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "💎 Garantía de máxima calidad de audio y graves:",
                color = p.texto,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "YouTube emite el audio de origen en Opus VBR (~160 kbps 48kHz) o AAC (~128 kbps). Al descargar 'Sin recodificar', la app extrae la pista exacta bit a bit del servidor sin pasar por FFmpeg, conservando el 100% de la dinámica original, los graves profundos (sub-bass) y las frecuencias altas hasta 22 kHz.",
                color = p.textoSuave,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun YouTubeMusicSuggestionBanner(
    buscando: Boolean,
    onAceptarCambio: () -> Unit
) {
    val p = LocalImaPalette.current
    val forma = RoundedCornerShape(18.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .imaRelleno(p, p.campo, forma, conBorde = true)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "✨ ¿Quieres portada oficial y audio de estudio?",
                color = p.texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Este enlace es un video de YouTube. Si cambias a YouTube Music, obtendrás la carátula cuadrada original (1:1), metadatos exactos del álbum y el audio limpio sin efectos del videoclip.",
                color = p.textoSuave,
                fontSize = 12.sp
            )
            ImaButton(
                texto = if (buscando) "Buscando canción en YouTube Music…" else "🎵 Cambiar a versión oficial de YouTube Music",
                onClick = onAceptarCambio,
                modifier = Modifier.fillMaxWidth(),
                rol = ImaRol.PRINCIPAL,
                habilitado = !buscando
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  7) ACTUALIZADOR DE YT-DLP PARA LA PANTALLA DE AJUSTES (SIN ERRORES)
// ═══════════════════════════════════════════════════════════════════════════

enum class YtDlpUpdateState {
    IDLE,
    UPDATING,
    ALREADY_UP_TO_DATE,
    UPDATED_SUCCESSFULLY,
    ERROR
}

/**
 * Tarjeta lista para colocar en tu pestaña/pantalla de Ajustes.
 * Se adapta a los 6 temas y actualiza el binario de yt-dlp mostrando claramente
 * si ya está en la mejor versión disponible o si se actualizó a una nueva.
 */
@Composable
fun ImaYtDlpUpdaterCard(
    versionActual: String?,
    onEjecutarActualizacion: suspend () -> Pair<Boolean, String>,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val scope = rememberCoroutineScope()
    var estado by remember { mutableStateOf(YtDlpUpdateState.IDLE) }
    var mensajeResultado by remember { mutableStateOf("") }
    var versionMostrada by remember(versionActual) {
        mutableStateOf(versionActual?.takeIf { it.isNotBlank() } ?: "Instalada")
    }

    ImaCard(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Motor de descargas (yt-dlp)",
                    color = p.texto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Versión actual: $versionMostrada",
                    color = p.textoSuave,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            ImaBadgeTag(
                texto = when (estado) {
                    YtDlpUpdateState.ALREADY_UP_TO_DATE,
                    YtDlpUpdateState.UPDATED_SUCCESSFULLY -> "✓ Al día"
                    YtDlpUpdateState.UPDATING -> "Revisando…"
                    else -> "Oficial"
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mantén el motor actualizado para asegurar que YouTube, TikTok, Instagram y Facebook siempre descarguen sin fallos y a la máxima calidad.",
            color = p.textoSuave,
            fontSize = 12.sp
        )

        if (mensajeResultado.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .imaRelleno(p, p.campo, RoundedCornerShape(14.dp), conBorde = true)
                    .padding(12.dp)
            ) {
                Text(
                    text = mensajeResultado,
                    color = if (estado == YtDlpUpdateState.ERROR) p.texto else p.acento,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ImaButton(
            texto = if (estado == YtDlpUpdateState.UPDATING) {
                "Buscando actualizaciones de yt-dlp…"
            } else {
                "↻ Comprobar y actualizar yt-dlp"
            },
            onClick = {
                if (estado == YtDlpUpdateState.UPDATING) return@ImaButton
                estado = YtDlpUpdateState.UPDATING
                mensajeResultado = "Conectando con el repositorio oficial de yt-dlp…"
                scope.launch {
                    try {
                        val (huboCambio, detalle) = withContext(Dispatchers.IO) {
                            onEjecutarActualizacion()
                        }
                        if (huboCambio) {
                            estado = YtDlpUpdateState.UPDATED_SUCCESSFULLY
                            if (detalle.isNotBlank()) versionMostrada = detalle
                            mensajeResultado = "✓ ¡Motor actualizado con éxito a la última versión ($versionMostrada)!"
                        } else {
                            estado = YtDlpUpdateState.ALREADY_UP_TO_DATE
                            if (detalle.isNotBlank()) versionMostrada = detalle
                            mensajeResultado = "✓ Todo está bien: ya tienes instalada la versión más reciente de yt-dlp."
                        }
                    } catch (e: Exception) {
                        estado = YtDlpUpdateState.ERROR
                        mensajeResultado = "No se pudo actualizar en este momento (verifica tu conexión a internet). El motor actual sigue funcionando con normalidad."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            rol = ImaRol.PRINCIPAL,
            habilitado = (estado != YtDlpUpdateState.UPDATING)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  8) CONTROLES TEMÁTICOS AUXILIARES (SIN TRUNCAR TEXTO)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun <T> ThemedSelectorRow(
    label: String,
    current: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit
) {
    val p = LocalImaPalette.current
    var open by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(16.dp)

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .imaRelleno(p, p.campo, forma, conBorde = true)
                .clip(forma)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.width(88.dp),
                color = p.textoSuave,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = current,
                modifier = Modifier.weight(1f),
                color = p.texto,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
                // Sin maxLines = 1 para que toda la información de kbps y tamaño se lea completa
            )
            Spacer(Modifier.width(8.dp))
            Text("▾", color = p.acento, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(p.superficie)
        ) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            color = p.texto,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

@Composable
fun ImaToggleChip(
    texto: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val forma = RoundedCornerShape(20.dp)
    val fondo = if (activo) p.btnPrincipal else p.btnSuave
    val colorLetra = if (activo) p.sobreBtnPrincipal else p.sobreBtnSuave

    Row(
        modifier
            .heightIn(min = 38.dp)
            .imaRelleno(p, fondo, forma, conBorde = !activo)
            .clip(forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (activo) "✓ $texto" else texto,
            color = colorLetra,
            fontSize = 13.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ImaSubtlePillButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val forma = CircleShape
    Box(
        modifier
            .imaRelleno(p, p.campo, forma, conBorde = true)
            .clip(forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = p.texto,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ImaBadgeTag(texto: String) {
    val p = LocalImaPalette.current
    Box(
        Modifier
            .background(p.btnPrincipal, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = texto,
            color = p.sobreBtnPrincipal,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Thumbnail(
    url: String?,
    esCuadrada: Boolean,
    modifier: Modifier
) {
    val p = LocalImaPalette.current
    val forma = RoundedCornerShape(if (esCuadrada) 14.dp else 12.dp)
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        bmp = if (url == null) null else withContext(Dispatchers.IO) {
            runCatching { loadBitmap(url, 600) }.getOrNull()
        }
    }

    Box(
        modifier
            .clip(forma)
            .background(p.campo)
            .border(1.dp, p.borde, forma),
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun loadBitmap(url: String, targetWidth: Int): Bitmap? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("User-Agent", "Mozilla/5.0")
    }
    try {
        val bytes = conn.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            options
        )
    } finally {
        conn.disconnect()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  9) ACCESO DIRECTO PURO A LA CARPETA DE DESCARGAS DEL TELÉFONO
// ═══════════════════════════════════════════════════════════════════════════

@Immutable
data class SystemDownloadShortcut(
    val id: Long,
    val nombreArchivo: String,
    val extensionReal: String,
    val tamanoBytesReal: Long,
    val fechaModificacionSeg: Long,
    val contentUri: Uri,
    val esAudio: Boolean
)

object ImaDirectDownloadsScanner {
    private val EXT_AUDIO = setOf("opus", "m4a", "mp3", "ogg", "flac", "wav", "aac")
    private val EXT_VIDEO = setOf("mp4", "mkv", "webm", "mov")

    suspend fun obtenerAccesosDirectos(
        context: Context,
        subcarpetaOpcional: String? = null
    ): List<SystemDownloadShortcut> = withContext(Dispatchers.IO) {
        val lista = ArrayList<SystemDownloadShortcut>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.RELATIVE_PATH
            )
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val nombre = cursor.getString(nameCol) ?: continue
                    val relPath = cursor.getString(pathCol) ?: ""
                    if (subcarpetaOpcional != null && !relPath.contains(subcarpetaOpcional, ignoreCase = true)) {
                        continue
                    }
                    val ext = nombre.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext !in EXT_AUDIO && ext !in EXT_VIDEO) continue

                    val id = cursor.getLong(idCol)
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(collection, id)

                    lista += SystemDownloadShortcut(
                        id = id,
                        nombreArchivo = nombre.substringBeforeLast('.'),
                        extensionReal = ext.uppercase(Locale.ROOT),
                        tamanoBytesReal = size,
                        fechaModificacionSeg = date,
                        contentUri = uri,
                        esAudio = ext in EXT_AUDIO
                    )
                }
            }
        } else {
            val dirPublico = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dirObjetivo = subcarpetaOpcional?.let { File(dirPublico, it) } ?: dirPublico
            val archivos = dirObjetivo.listFiles().orEmpty()
                .filter { it.isFile }
                .sortedByDescending { it.lastModified() }

            archivos.forEach { file ->
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext in EXT_AUDIO || ext in EXT_VIDEO) {
                    val uri = runCatching {
                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    }.getOrElse { Uri.fromFile(file) }

                    lista += SystemDownloadShortcut(
                        id = file.hashCode().toLong(),
                        nombreArchivo = file.nameWithoutExtension,
                        extensionReal = ext.uppercase(Locale.ROOT),
                        tamanoBytesReal = file.length(),
                        fechaModificacionSeg = file.lastModified() / 1000L,
                        contentUri = uri,
                        esAudio = ext in EXT_AUDIO
                    )
                }
            }
        }
        lista
    }

    fun abrirArchivoDirecto(context: Context, item: SystemDownloadShortcut) {
        val extMin = item.extensionReal.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extMin)
            ?: if (item.esAudio) "audio/*" else "video/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.contentUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

@Composable
fun ImaDirectDownloadsTab(
    modifier: Modifier = Modifier,
    subcarpetaOpcional: String? = null
) {
    val context = LocalContext.current
    val p = LocalImaPalette.current
    val descargasEnVivo by ImaLiveDownloadsStore.items.collectAsState()
    var accesos by remember { mutableStateOf<List<SystemDownloadShortcut>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }

    fun recargarCarpetaDescargas() {
        cargando = true
    }

    // Recarga automáticamente cuando una descarga en vivo pasa a COMPLETED
    LaunchedEffect(descargasEnVivo, cargando, subcarpetaOpcional) {
        accesos = ImaDirectDownloadsScanner.obtenerAccesosDirectos(context, subcarpetaOpcional)
        cargando = false
    }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val tieneAlturaFija = constraints.hasBoundedHeight

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Descargas",
                    color = p.texto,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                ImaSubtlePillButton(
                    texto = "↻ Actualizar carpeta",
                    onClick = ::recargarCarpetaDescargas
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Acceso directo a tu carpeta Descargas del teléfono (0 MB de caché en la app)",
                color = p.textoSuave,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            // Si hay descargas activas ahora mismo, también se ven arriba en esta pestaña
            if (descargasEnVivo.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    descargasEnVivo.forEach { item ->
                        ImaLiveProgressPreviewCard(
                            item = item,
                            onDismiss = { ImaLiveDownloadsStore.quitarTarjeta(item.id) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (accesos.isEmpty() && !cargando) {
                ImaCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No se encontraron archivos en tu carpeta de Descargas.",
                        color = p.texto,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Todo lo que descargues se guarda directamente en la carpeta pública Descargas de tu teléfono y aparecerá aquí como acceso directo para abrirlo con un toque.",
                        color = p.textoSuave,
                        fontSize = 13.sp
                    )
                }
            } else if (tieneAlturaFija) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(accesos, key = { it.id }) { item ->
                        ShortcutDownloadItemCard(item = item, p = p) {
                            ImaDirectDownloadsScanner.abrirArchivoDirecto(context, item)
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    accesos.forEach { item ->
                        ShortcutDownloadItemCard(item = item, p = p) {
                            ImaDirectDownloadsScanner.abrirArchivoDirecto(context, item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutDownloadItemCard(
    item: SystemDownloadShortcut,
    p: ImaPalette,
    onClick: () -> Unit
) {
    val pesoReal = formatearBytesReales(item.tamanoBytesReal) ?: ""
    ImaCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(p.btnPrincipal, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (item.esAudio) "🎵" else "🎬",
                    fontSize = 20.sp
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.nombreArchivo,
                    color = p.texto,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                    // Sin truncar para que se lea el nombre completo de la canción o video
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(if (item.esAudio) "Audio • " else "Video • ")
                        append(item.extensionReal)
                        if (pesoReal.isNotBlank()) append(" • $pesoReal")
                        append(" • Toca para abrir")
                    },
                    color = p.acento,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  10) FORMATEADORES DE ETIQUETAS REALES
// ═══════════════════════════════════════════════════════════════════════════

private fun VideoRes.labelCompleto(): String {
    val tag = when {
        height >= 4320 -> " • 8K"
        height >= 2160 -> " • 4K"
        height >= 1440 -> " • 2K"
        height >= 1080 -> " • Full HD"
        height >= 720 -> " • HD"
        else -> ""
    }
    val rate = if ((fps ?: 0) > 30) " ${fps}fps" else ""
    val sz = size?.let { " • ${it.fmt()}" } ?: ""
    return "${height}p$rate$tag$sz"
}

private fun AudioTrack.labelDetallado(): String = buildString {
    append(codec)
    append(" (.${extSalidaNativa.uppercase(Locale.ROOT)})")
    kbps?.let { append(" • $it kbps") }
    if (esOpus) append(" • Máxima fidelidad")
    size?.let { append(" • ${it.fmt()}") }
}

private fun ByteSize.fmt(): String {
    val mb = bytes / 1_048_576.0
    val t = when {
        mb >= 1024 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        mb >= 10 -> String.format(Locale.US, "%.1f MB", mb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
    return if (approx) "~$t" else t
}

private fun fmtDuration(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun langName(code: String): String {
    val n = Locale.forLanguageTag(code.removeSuffix("-orig")).getDisplayLanguage(Locale.forLanguageTag("es"))
    return if (n.isBlank()) code else n.replaceFirstChar { it.uppercase() }
}
