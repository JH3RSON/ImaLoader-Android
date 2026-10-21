package com.ima.imaloader

import kotlin.math.roundToInt

data class Suggestion(
    val title: String,
    val badge: String,
    val note: String,
    val video: MediaFormat?,
    val audio: MediaFormat?,
    val mp4: Boolean = false,
)

object FormatCatalog {

    /** Todos los formatos de video ordenados por resolución, FPS, prioridad VP9 y bitrate */
    fun videoFormats(info: MediaInfo): List<MediaFormat> =
        info.formats.filter { it.hasVideo }.sortedWith(
            compareByDescending<MediaFormat> { it.height ?: 0 }
                .thenByDescending { it.fps ?: 0.0 }
                .thenBy { VCodec.of(it.vcodec).rank }
                .thenByDescending { it.tbr ?: 0.0 }
        )

    /** Pistas de solo audio ordenadas por bitrate real (Opus 251 suele ir primero en YouTube) */
    fun audioFormats(info: MediaInfo): List<MediaFormat> {
        val audioOnly = info.formats.filter { it.isAudioOnly }
        val source = audioOnly.ifEmpty { info.formats.filter { it.hasAudio } }
        return source.sortedWith(
            compareByDescending<MediaFormat> { it.abr ?: it.tbr ?: 0.0 }
                .thenByDescending { it.sizeBytes ?: 0L }
        )
    }

    private fun isAac(f: MediaFormat) = f.ext == "m4a" || f.acodec?.startsWith("mp4a") == true
    private fun isOpus(f: MediaFormat) = f.acodec?.lowercase()?.startsWith("opus") == true

    /** Mejor audio separado para acompañar un stream de video DASH */
    fun autoAudio(info: MediaInfo, video: MediaFormat, preferM4a: Boolean): MediaFormat? {
        if (video.hasAudio) return null
        val all = info.formats.filter { it.isAudioOnly }
        val aac = all.filter { isAac(it) }
        val pool = if (preferM4a && aac.isNotEmpty()) aac else all
        return pool.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
    }

    fun videoSuggestions(info: MediaInfo): List<Suggestion> {
        val vids = videoFormats(info)
        if (vids.isEmpty()) return emptyList()

        val best = vids.first()
        val bestIsMp4 = best.ext == "mp4" || VCodec.of(best.vcodec) == VCodec.H264
        val h264 = vids.firstOrNull { VCodec.of(it.vcodec) == VCodec.H264 }
        val light = vids.filter { (it.height ?: 0) in 1..720 }.let { l ->
            val top = l.firstOrNull()?.height
            l.filter { it.height == top }.minWithOrNull(
                compareBy<MediaFormat>(
                    { it.sizeBytes ?: Long.MAX_VALUE },
                    { it.tbr ?: Double.MAX_VALUE }
                )
            )
        }

        return listOfNotNull(
            Suggestion(
                title = "Máxima calidad real",
                badge = "Recomendado",
                note = listOfNotNull(
                    best.height?.let { "${it}p" },
                    best.fps?.takeIf { it > 30 }?.let { "${it.roundToInt()}fps" },
                    VCodec.of(best.vcodec).title
                ).joinToString(" · "),
                video = best,
                audio = autoAudio(info, best, bestIsMp4),
                mp4 = bestIsMp4,
            ),
            h264?.let {
                Suggestion(
                    title = "Compatible universal",
                    badge = "H.264 MP4",
                    note = "Abre en cualquier TV, editor o galería · ${it.height}p",
                    video = it,
                    audio = autoAudio(info, it, true),
                    mp4 = true
                )
            },
            light?.let {
                val lightMp4 = it.ext == "mp4" || VCodec.of(it.vcodec) == VCodec.H264
                Suggestion(
                    title = "Ahorro de datos",
                    badge = "Liviano",
                    note = "${it.height}p · Descarga rápida",
                    video = it,
                    audio = autoAudio(info, it, lightMp4),
                    mp4 = lightMp4
                )
            },
        ).distinctBy { Triple(it.video?.id, it.audio?.id, it.mp4) }
    }

    fun audioSuggestions(info: MediaInfo): List<Suggestion> {
        val a = audioFormats(info)
        if (a.isEmpty()) return emptyList()
        val best = a.first()
        val opus = a.firstOrNull { isOpus(it) }
        val aac = a.firstOrNull { isAac(it) }
        val low = a.last()

        return listOfNotNull(
            Suggestion(
                title = "Máxima fidelidad nativa",
                badge = "Hi-Fi Puro",
                note = "${audioName(best)} · ${bitrate(best, false)} (Stream directo)",
                video = null,
                audio = best
            ),
            opus?.takeIf { it.id != best.id }?.let {
                Suggestion(
                    title = "Opus Master (251)",
                    badge = "20 kHz",
                    note = "Opus · ${bitrate(it, false)}",
                    video = null,
                    audio = it
                )
            },
            aac?.let {
                Suggestion(
                    title = "Compatible universal (M4A)",
                    badge = "AAC Nativo",
                    note = "AAC · ${bitrate(it, false)}",
                    video = null,
                    audio = it
                )
            },
            low.takeIf { a.size > 1 }?.let {
                Suggestion(
                    title = "Voz / Podcast (Ligero)",
                    badge = "Ahorro",
                    note = "${audioName(it)} · ${bitrate(it, false)}",
                    video = null,
                    audio = it
                )
            },
        ).distinctBy { it.audio?.id }
    }

    fun audioName(f: MediaFormat): String {
        val c = f.acodec?.lowercase().orEmpty()
        return when {
            c.startsWith("mp4a") || c == "aac" -> "AAC"
            c.startsWith("opus") -> "Opus"
            c.startsWith("vorbis") -> "Vorbis"
            c.startsWith("mp3") -> "MP3"
            c.startsWith("flac") -> "FLAC"
            c.startsWith("ec-3") || c.startsWith("ac-3") -> "Dolby"
            c.isEmpty() -> "Audio"
            else -> c.substringBefore('.').uppercase()
        }
    }

    fun codecs(f: MediaFormat): String = when {
        f.hasVideo && f.hasAudio -> "${VCodec.of(f.vcodec).title} + ${audioName(f)}"
        f.hasVideo -> VCodec.of(f.vcodec).title
        else -> audioName(f)
    }

    fun bitrate(f: MediaFormat, video: Boolean): String {
        val k = (if (video) f.tbr else (f.abr ?: f.tbr)) ?: return "bitrate variable"
        return if (k >= 1000) "%.1f Mbps".format(k / 1000) else "${k.roundToInt()} kbps"
    }

    fun total(vararg parts: MediaFormat?): Size {
        val p = parts.filterNotNull()
        if (p.isEmpty() || p.any { it.sizeBytes == null }) return Size(null, false)
        return Size(p.sumOf { it.sizeBytes!! }, p.any { it.sizeApprox })
    }
}