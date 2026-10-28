package com.ima.imaloader

import org.json.JSONObject

data class MediaFormat(
    val id: String,
    val ext: String?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val vcodec: String?,   // null = sin video
    val acodec: String?,   // null = sin audio
    val tbr: Double?,
    val abr: Double?,
    val sizeBytes: Long?,  // null = desconocido (nunca 0)
    val sizeApprox: Boolean = false,
) {
    val hasVideo get() = vcodec != null
    val hasAudio get() = acodec != null
    val isAudioOnly get() = hasAudio && !hasVideo
    val isVideoOnly get() = hasVideo && !hasAudio
    val isMuxed get() = hasVideo && hasAudio
}

data class MediaInfo(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val releaseYear: String?,
    val thumbnail: String?,
    val durationSec: Long?,
    val webpageUrl: String?,
    val isYoutubeMusic: Boolean = false,
    val hasSubtitles: Boolean = false,
    val formats: List<MediaFormat>,
    val rawJson: String? = null,
    val directUrl: String? = null,
    val originalUrl: String = "",
)


enum class FileKind { VIDEO, AUDIO }

enum class VCodec(val title: String, val rank: Int) {
    VP9("VP9", 1),
    H264("H.264", 2),
    HEVC("HEVC", 3),
    AV1("AV1", 4),
    OTHER("Video", 5);

    companion object {
        fun of(raw: String?): VCodec {
            val c = raw?.lowercase().orEmpty()
            return when {
                c.startsWith("vp9") || c.startsWith("vp09") -> VP9
                c.startsWith("avc") || c.startsWith("h264") -> H264
                c.startsWith("hev") || c.startsWith("hvc") || c.startsWith("hevc") -> HEVC
                c.startsWith("av01") || c.startsWith("av1") -> AV1
                else -> OTHER
            }
        }
    }
}

enum class VideoCodec(val sort: String) {
    COMPAT("res,vcodec:h264,acodec:m4a,ext:mp4:m4a"),
    QUALITY("res,fps,vcodec:vp9.2:vp9:av01,br")
}

/** Contenedor de salida de video estilo Seal */
enum class VideoContainer(val id: String, val title: String, val desc: String) {
    MP4("mp4", "MP4", "Recomendado por compatibilidad con galerías, TV y editores."),
    MKV("mkv", "MKV", "Contenedor flexible sin recodificar, ideal para 4K."),
    ORIGINAL("orig", "Conservar origen", "Guarda el contenedor nativo del servidor sin remuxear.")
}

data class Size(val bytes: Long?, val approx: Boolean = false) {
    fun label(): String {
        val b = bytes?.takeIf { it > 0L } ?: return "Tamaño no disponible"
        val prefix = if (approx) "~" else ""
        val mb = b / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "$prefix%.2f GB".format(mb / 1024.0)
        } else {
            "$prefix%.1f MB".format(mb)
        }
    }
}

data class VideoQuality(val height: Int, val fps: Int, val size: Size) {
    val name: String
        get() = when {
            height >= 2160 -> "4K (2160p)"
            height >= 1440 -> "2K QHD (1440p)"
            height >= 1080 -> "Full HD (1080p)"
            height >= 720 -> "HD (720p)"
            height >= 480 -> "SD (480p)"
            else -> "Bajo consumo (${height}p)"
        }
}

data class AudioOutputFormat(
    val id: String,
    val title: String,
    val subtitle: String,
    val requiresConversion: Boolean,
)

data class AudioBitrateOption(
    val id: String,      // "0", "320K", "192K", "128K"
    val title: String,
    val subtitle: String,
)

object FormatPlanner {
    val AUDIO_FORMATS = listOf(
        AudioOutputFormat(
            id = "best",
            title = "Mejor audio disponible (Sin convertir)",
            subtitle = "Descarga el flujo original puro sin pérdida (Opus de alta fidelidad si está disponible)",
            requiresConversion = false
        ),
        AudioOutputFormat(
            id = "m4a",
            title = "M4A",
            subtitle = "Buena compatibilidad, eficiencia y soporte de carátula oficial",
            requiresConversion = true
        ),
        AudioOutputFormat(
            id = "mp3",
            title = "MP3",
            subtitle = "Máxima compatibilidad con cualquier equipo o reproductor",
            requiresConversion = true
        ),
        AudioOutputFormat(
            id = "opus",
            title = "Opus",
            subtitle = "Alta eficiencia moderna, si el reproductor lo admite",
            requiresConversion = true
        )
    )

    val AUDIO_BITRATES = listOf(
        AudioBitrateOption("0", "Mejor calidad disponible", "Ajuste óptimo según la fuente original"),
        AudioBitrateOption("320K", "320 kbps", "Máximo bitrate constante (si la conversión lo permite)"),
        AudioBitrateOption("192K", "192 kbps", "Equilibrio entre buena calidad y peso moderado"),
        AudioBitrateOption("128K", "128 kbps", "Menor tamaño de archivo para ahorrar espacio")
    )
}

sealed interface Target {
    data class Video(
        val maxHeight: Int? = null,
        val mp4: Boolean = true,
        val keepOriginalContainer: Boolean = false,
        val codec: VideoCodec = VideoCodec.COMPAT,
        val formatId: String? = null,
        val formatHasAudio: Boolean = false,
        val withAudio: Boolean = true,
        val audioFormatId: String? = null,
        val embedSubs: Boolean = false,
        val saveThumbnail: Boolean = false,
    ) : Target

    data class Audio(
        val format: String = "best",         // "best", "mp3", "m4a", "opus"
        val quality: String = "0",           // "0", "320K", "192K", "128K"
        val formatId: String? = null,
        val embedCover: Boolean = true,
        val embedMetadata: Boolean = true,
        val music: Boolean = false,          // Modo canción (portada cuadrada + metadatos iTunes/YTM)
        val infoJsonPath: String? = null,    // Ruta al análisis enriquecido con datos oficiales
    ) : Target
}

object TargetCodec {
    fun encode(t: Target): String = JSONObject().apply {
        when (t) {
            is Target.Video -> {
                put("type", "video")
                put("h", t.maxHeight ?: 0)
                put("mp4", t.mp4)
                put("orig", t.keepOriginalContainer)
                put("codec", t.codec.name)
                put("fid", t.formatId ?: "")
                put("fha", t.formatHasAudio)
                put("wa", t.withAudio)
                put("aid", t.audioFormatId ?: "")
                put("subs", t.embedSubs)
                put("thumb", t.saveThumbnail)
            }
            is Target.Audio -> {
                put("type", "audio")
                put("f", t.format)
                put("q", t.quality)
                put("fid", t.formatId ?: "")
                put("cov", t.embedCover)
                put("meta", t.embedMetadata)
                put("music", t.music)
                put("ij", t.infoJsonPath ?: "")
            }
        }
    }.toString()

    fun decode(raw: String): Target {
        val o = JSONObject(raw)
        return if (o.optString("type") == "audio") {
            Target.Audio(
                format = o.optString("f", o.optString("fmt", "best")),
                quality = o.optString("q", "0"),
                formatId = o.optString("fid").ifEmpty { null },
                embedCover = o.optBoolean("cov", true),
                embedMetadata = o.optBoolean("meta", true),
                music = o.optBoolean("music", false),
                infoJsonPath = o.optString("ij").ifEmpty { null },
            )
        } else {
            val codecName = o.optString("codec", VideoCodec.COMPAT.name)
            val codec = runCatching { VideoCodec.valueOf(codecName) }.getOrDefault(VideoCodec.COMPAT)
            Target.Video(
                maxHeight = o.optInt("h", 0).takeIf { it > 0 },
                mp4 = o.optBoolean("mp4", true),
                keepOriginalContainer = o.optBoolean("orig", false),
                codec = codec,
                formatId = o.optString("fid").ifEmpty { null },
                formatHasAudio = o.optBoolean("fha", false),
                withAudio = o.optBoolean("wa", true),
                audioFormatId = o.optString("aid").ifEmpty { null },
                embedSubs = o.optBoolean("subs", false),
                saveThumbnail = o.optBoolean("thumb", false),
            )
        }
    }
}

enum class DownloadPhase(val label: String) {
    PREPARING("Preparando flujos y formatos..."),
    DOWNLOADING("Descargando contenido..."),
    MERGING("Procesando formato, carátula y metadatos..."),
    EXPORTING("Guardando en la biblioteca del dispositivo..."),
    COMPLETED("Descarga completada"),
    PAUSED("Descarga en pausa"),
    FAILED("Error en la descarga")
}

data class DownloadTaskUi(
    val id: String,
    val url: String,
    val target: Target,
    val title: String,
    val thumbnail: String?,
    val progressPct: Int,
    val downloadedText: String?,
    val speedText: String?,
    val etaText: String?,
    val phase: DownloadPhase,
    val exportedUri: String? = null,
    val exportedMime: String? = null,
    val finalFileName: String? = null,
    val finalSizeBytes: Long? = null,
    val errorText: String? = null,
)

enum class ErrorKind {
    NETWORK, FORBIDDEN, LOGIN, FORMAT, STORAGE, PROCESSING, CANCELED,
    EXTRACTOR, UNSUPPORTED, RESTRICTED, UNKNOWN
}

class DownloadException(val kind: ErrorKind, message: String, cause: Throwable? = null) :
    Exception(message, cause)