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
    val formats: List<MediaFormat>,
    val directUrl: String? = null,
    val originalUrl: String = "" 
)

enum class FileKind { VIDEO, AUDIO }

/** Orden de preferencia en 4K/2K: VP9 (rank 1) gana a AV1 (rank 4) para máxima fluidez */
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
            height >= 2160 -> "4K Ultra HD"
            height >= 1440 -> "2K QHD"
            height >= 1080 -> "Full HD"
            height >= 720 -> "HD"
            height >= 480 -> "SD"
            else -> "Baja"
        }
}

data class AudioOutputFormat(
    val id: String,
    val title: String,
    val qualityArg: String,
    val isLosslessOrPassthrough: Boolean,
    val hint: String,
)

object FormatPlanner {
    val AUDIO_FORMATS = listOf(
        AudioOutputFormat(
            id = "best",
            title = "Original (Puro)",
            qualityArg = "0",
            isLosslessOrPassthrough = true,
            hint = "Sin recodificar: extrae el stream original (Opus 160k / AAC) con 0% pérdida y portada HD."
        ),
        AudioOutputFormat(
            id = "mp3",
            title = "MP3 320k",
            qualityArg = "320K",
            isLosslessOrPassthrough = false,
            hint = "MP3 a 320 kbps constantes (CBR) con carátula ID3v2 HD integrada."
        ),
        AudioOutputFormat(
            id = "m4a",
            title = "M4A (AAC)",
            qualityArg = "0",
            isLosslessOrPassthrough = false,
            hint = "Contenedor Apple/Android universal con metadatos completos y portada."
        ),
        AudioOutputFormat(
            id = "opus",
            title = "Opus Hi-Fi",
            qualityArg = "0",
            isLosslessOrPassthrough = true,
            hint = "El códec más eficiente de YouTube Music con carátula incrustada."
        ),
        AudioOutputFormat(
            id = "flac",
            title = "FLAC (Estudio)",
            qualityArg = "0",
            isLosslessOrPassthrough = true,
            hint = "Compresión sin pérdidas con soporte completo de etiquetas y portada HD."
        ),
    )
}

sealed interface Target {
    data class Video(
        val maxHeight: Int? = null,
        val mp4: Boolean = false,
        val codec: VideoCodec = VideoCodec.COMPAT,
        val formatId: String? = null,
        val formatHasAudio: Boolean = false,
        val withAudio: Boolean = true,
        val audioFormatId: String? = null,
    ) : Target

    data class Audio(
        val format: String = "best",
        val quality: String = "0",
        val formatId: String? = null,
        val squareCover: Boolean = true, // Recorta la portada a 1:1 estilo álbum musical
    ) : Target
}

object TargetCodec {
    fun encode(t: Target): String = JSONObject().apply {
        when (t) {
            is Target.Video -> {
                put("type", "video")
                put("h", t.maxHeight ?: 0)
                put("mp4", t.mp4)
                put("codec", t.codec.name)
                put("fid", t.formatId ?: "")
                put("fha", t.formatHasAudio)
                put("wa", t.withAudio)
                put("aid", t.audioFormatId ?: "")
            }
            is Target.Audio -> {
                put("type", "audio")
                put("fmt", t.format)
                put("q", t.quality)
                put("fid", t.formatId ?: "")
                put("sq", t.squareCover)
            }
        }
    }.toString()

    fun decode(raw: String): Target {
        val o = JSONObject(raw)
        return if (o.optString("type") == "audio") {
            Target.Audio(
                format = o.optString("fmt", "best"),
                quality = o.optString("q", "0"),
                formatId = o.optString("fid").ifEmpty { null },
                squareCover = o.optBoolean("sq", true),
            )
        } else {
            val codecName = o.optString("codec", VideoCodec.COMPAT.name)
            val codec = runCatching { VideoCodec.valueOf(codecName) }.getOrDefault(VideoCodec.COMPAT)
            Target.Video(
                maxHeight = o.optInt("h", 0).takeIf { it > 0 },
                mp4 = o.optBoolean("mp4", false),
                codec = codec,
                formatId = o.optString("fid").ifEmpty { null },
                formatHasAudio = o.optBoolean("fha", false),
                withAudio = o.optBoolean("wa", true),
                audioFormatId = o.optString("aid").ifEmpty { null },
            )
        }
    }
}

enum class DownloadPhase(val label: String) {
    PREPARING("Preparando motor..."),
    DOWNLOADING("Descargando stream..."),
    MERGING("Uniendo pistas e incrustando portada HD..."),
    EXPORTING("Guardando en tu biblioteca..."),
    COMPLETED("¡Descarga completada!"),
    PAUSED("En pausa (esperando red)..."),
    FAILED("Error en la descarga")
}

data class DownloadTaskUi(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val progressPct: Int,
    val speedText: String?,
    val etaText: String?,
    val phase: DownloadPhase,
    val exportedUri: String? = null,
    val errorText: String? = null,
)

enum class ErrorKind {
    NETWORK, FORBIDDEN, LOGIN, FORMAT, STORAGE, PROCESSING, CANCELED,
    EXTRACTOR, UNSUPPORTED,
    RESTRICTED,   // NUEVO: audiencia restringida (edad, región, cuenta privada)
    UNKNOWN
}

class DownloadException(val kind: ErrorKind, message: String, cause: Throwable? = null) :
    Exception(message, cause)