package com.ima.imaloader.ui.theme

import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class OpcionVideo(
    val alto: Int,
    val fps: Int,
    val hdr: String?,
    val codec: String,
    val bytes: Long?,
    val aprox: Boolean,
    val selector: String,
    val etiquetaFallback: String? = null
) {
    val titulo: String
        get() {
            if (alto == 0) {
                return etiquetaFallback?.takeIf { it.isNotBlank() } ?: "Calidad estándar"
            }
            val prefijo = when (alto) {
                4320 -> "8K · "
                2160 -> "4K · "
                1440 -> "2K · "
                1080 -> "FHD · "
                720 -> "HD · "
                else -> ""
            }
            val textoFps = if (fps > 30) "$fps" else ""
            val textoHdr = hdr?.let { " $it" } ?: ""
            return "$prefijo${alto}p$textoFps$textoHdr"
        }

    val detalle: String
        get() = "$codec · ${pesoTexto(bytes, aprox)}"
}

data class OpcionAudio(
    val kbps: Int?,
    val codec: String,
    val bytes: Long?,
    val aprox: Boolean,
    val selector: String,
    val delVideo: Boolean = false
) {
    val titulo: String
        get() = if (kbps != null && kbps > 0) "$kbps kbps" else "Audio original"

    val detalle: String
        get() = "$codec · ${pesoTexto(bytes, aprox)}"
}

data class Calidades(
    val video: List<OpcionVideo>,
    val audio: List<OpcionAudio>
)

enum class NivelAudio { AHORRO, MEDIA, ALTA }

fun List<OpcionAudio>.porNivel(nivel: NivelAudio): OpcionAudio? {
    if (isEmpty()) return null
    val ordenados = sortedBy { it.kbps ?: 128 }
    return when (nivel) {
        NivelAudio.AHORRO -> ordenados.first()
        NivelAudio.MEDIA -> ordenados[(ordenados.size - 1) / 2]
        NivelAudio.ALTA -> ordenados.last()
    }
}

fun pesoTexto(bytes: Long?, aprox: Boolean): String {
    if (bytes == null || bytes <= 0L) return "peso no informado"
    val mb = bytes / 1048576.0
    val texto = if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
    return if (aprox) "≈ $texto" else texto
}

object CalidadesReales {

    private val ESCALERA_ALTO = intArrayOf(144, 240, 360, 480, 540, 720, 1080, 1440, 2160, 4320)
    private val ESCALERA_ANCHO = listOf(
        256 to 144,
        426 to 240,
        640 to 360,
        854 to 480,
        960 to 540,
        1280 to 720,
        1920 to 1080,
        2560 to 1440,
        3840 to 2160,
        7680 to 4320
    )
    private val EXT_SOLO_AUDIO = setOf("m4a", "mp3", "aac", "opus", "ogg", "wav", "flac")
    private val REGEX_RES = Regex("(?<!\\d)(144|240|360|480|540|720|1080|1440|2160|4320)p", RegexOption.IGNORE_CASE)

    private class FormatoCrudo(
        val id: String,
        val ext: String,
        val protocolo: String,
        val vcodec: String,
        val acodec: String,
        val w: Int,
        val h: Int,
        val fps: Int,
        val hdr: String?,
        val tbr: Double,
        val vbr: Double,
        val abr: Double,
        val bytes: Long?,
        val aprox: Boolean,
        val nota: String,
        val prefIdioma: Int,
        val prefCalidad: Int
    ) {
        val esDirecto: Boolean
            get() = protocolo == "https" || protocolo == "http"

        val tieneVideo: Boolean
            get() = vcodec != "none" && vcodec != "images" && ext !in EXT_SOLO_AUDIO &&
                (vcodec.isNotEmpty() || w > 0 || h > 0 || acodec == "none")

        val tieneAudio: Boolean
            get() = acodec != "none" && (acodec.isNotEmpty() || ext in EXT_SOLO_AUDIO || !soloVideoExplicito)

        private val soloVideoExplicito: Boolean
            get() = acodec == "none"

        val soloVideo: Boolean
            get() = tieneVideo && acodec == "none"

        val soloAudio: Boolean
            get() = !tieneVideo && acodec != "none" && (acodec.isNotEmpty() || ext in EXT_SOLO_AUDIO)
    }

    /**
     * Analiza el JSON completo devuelto por yt-dlp (--dump-single-json o -J).
     */
    fun analizar(infoJson: String): Calidades {
        val inicio = infoJson.indexOf('{')
        if (inicio < 0) return Calidades(emptyList(), emptyList())

        val raiz = runCatching { JSONObject(infoJson.substring(inicio)) }.getOrNull()
            ?: return Calidades(emptyList(), emptyList())

        val info = raiz.optJSONArray("entries")?.optJSONObject(0) ?: raiz
        val duracion = info.leerDouble("duration")
        val arr = info.optJSONArray("formats")

        val todos = if (arr != null && arr.length() > 0) {
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { leerFormato(it, duracion) }
            }
        } else {
            listOfNotNull(leerFormato(info, duracion))
        }

        if (todos.isEmpty()) return Calidades(emptyList(), emptyList())

        val audiosPuros = todos.filter { it.soloAudio }
        val videos = todos.filter { it.tieneVideo }

        return Calidades(
            video = armarVideo(videos, audiosPuros),
            audio = armarAudio(audiosPuros, todos, duracion)
        )
    }

    // ---------- Lectura segura de JSON ----------

    private fun JSONObject.leerString(k: String): String =
        if (isNull(k)) "" else optString(k, "").trim()

    private fun JSONObject.leerDouble(k: String): Double =
        if (isNull(k)) 0.0 else optDouble(k, 0.0).takeIf { !it.isNaN() && it > 0.0 } ?: 0.0

    private fun JSONObject.leerIntConDefault(k: String, def: Int): Int =
        if (isNull(k)) def else optInt(k, def)

    private fun leerFormato(o: JSONObject, duracion: Double): FormatoCrudo? {
        val id = o.leerString("format_id").ifEmpty { "default" }
        val ext = o.leerString("ext").lowercase(Locale.ROOT)
        val nota = o.leerString("format_note")
        val protocolo = o.leerString("protocol").lowercase(Locale.ROOT)

        // Descartar storyboards, DRM y pistas duplicadas DRC
        if (id.startsWith("sb") || "storyboard" in nota.lowercase(Locale.ROOT)) return null
        if ("drc" in id.lowercase(Locale.ROOT)) return null
        if (ext == "mhtml" || ext == "jpg" || ext == "webp" || protocolo == "mhtml") return null
        if (o.optBoolean("has_drm", false) || !o.isNull("has_drm") && o.leerString("has_drm").isNotEmpty() && o.leerString("has_drm") != "false") return null

        val vbr = o.leerDouble("vbr")
        val abr = o.leerDouble("abr")
        val tbrRaw = o.leerDouble("tbr")
        val tbr = when {
            tbrRaw > 0.0 -> tbrRaw
            vbr > 0.0 || abr > 0.0 -> vbr + abr
            else -> 0.0
        }

        val exacto = o.leerDouble("filesize").toLong()
        val approx = o.leerDouble("filesize_approx").toLong()

        val bytes: Long? = when {
            exacto > 0L -> exacto
            approx > 0L -> approx
            tbr > 0.0 && duracion > 0.0 -> ((tbr * 1000.0 / 8.0) * duracion).toLong()
            else -> null
        }

        val dr = o.leerString("dynamic_range")
        val hdr = if (dr.isEmpty() || dr.equals("SDR", ignoreCase = true)) null else dr

        return FormatoCrudo(
            id = id,
            ext = ext,
            protocolo = protocolo,
            vcodec = o.leerString("vcodec").lowercase(Locale.ROOT),
            acodec = o.leerString("acodec").lowercase(Locale.ROOT),
            w = o.leerDouble("width").roundToInt(),
            h = o.leerDouble("height").roundToInt(),
            fps = o.leerDouble("fps").roundToInt(),
            hdr = hdr,
            tbr = tbr,
            vbr = vbr,
            abr = abr,
            bytes = bytes,
            aprox = exacto <= 0L,
            nota = nota,
            prefIdioma = o.leerIntConDefault("language_preference", -1),
            prefCalidad = o.leerIntConDefault("quality", 0)
        )
    }

    // ---------- Construcción de Video ----------

    private data class ClaveGrupoVideo(
        val alto: Int,
        val altoFps: Boolean,
        val hdr: String?,
        val subClaveSinDimensiones: String
    )

    private fun armarVideo(videos: List<FormatoCrudo>, audios: List<FormatoCrudo>): List<OpcionVideo> {
        if (videos.isEmpty()) return emptyList()

        return videos
            .groupBy { f ->
                val alto = calcularAltoEstandar(f)
                val subClave = if (alto == 0) etiquetaFallbackDe(f) else ""
                ClaveGrupoVideo(
                    alto = alto,
                    altoFps = f.fps > 35,
                    hdr = f.hdr,
                    subClaveSinDimensiones = subClave
                )
            }
            .map { (clave, grupo) ->
                val alto = clave.alto
                // Priorizamos: 1) Que tenga tamaño conocido/directo, 2) Familia de códec ideal, 3) Mayor bitrate
                val elegido = grupo.sortedWith(
                    compareBy<FormatoCrudo> { if (it.bytes != null) 0 else 1 }
                        .thenBy { if (it.esDirecto) 0 else 1 }
                        .thenBy { prioridadCodecVideo(familiaVideo(it.vcodec), alto) }
                        .thenByDescending { if (it.vbr > 0) it.vbr else it.tbr }
                ).first()

                val audioCompanero = if (elegido.soloVideo) mejorAudioPara(audios, elegido.ext) else null
                val (pesoTotal, esAprox) = sumarPesos(elegido, audioCompanero)

                OpcionVideo(
                    alto = alto,
                    fps = elegido.fps,
                    hdr = elegido.hdr,
                    codec = familiaVideo(elegido.vcodec),
                    bytes = pesoTotal,
                    aprox = esAprox,
                    selector = construirSelectorVideo(elegido, audioCompanero),
                    etiquetaFallback = clave.subClaveSinDimensiones.ifEmpty { null }
                )
            }
            .sortedWith(
                compareByDescending<OpcionVideo> { it.alto }
                    .thenByDescending { it.hdr != null }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.bytes ?: 0L }
            )
    }

    private fun calcularAltoEstandar(f: FormatoCrudo): Int {
        // 1. Buscar resolución explícita en format_note o format_id (ej. "1080p", "2160p60", "bytevc1_720p")
        REGEX_RES.find(f.nota)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        REGEX_RES.find(f.id)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        if (f.w <= 0 && f.h <= 0) return 0

        val menor = if (f.w > 0 && f.h > 0) minOf(f.w, f.h) else maxOf(f.w, f.h)
        val mayor = maxOf(f.w, f.h)

        // 2. Comprobar si el lado menor encaja en la escalera estándar (funciona para 16:9 y 9:16 vertical)
        ESCALERA_ALTO.firstOrNull { abs(menor - it) <= it * 0.06 }?.let { return it }

        // 3. Soporte para videos Ultrawide (21:9, ej. 3840x1606 -> 2160p, 1920x800 -> 1080p)
        ESCALERA_ANCHO.firstOrNull { (anchoRef, _) -> abs(mayor - anchoRef) <= anchoRef * 0.06 }
            ?.let { (_, altoEquivalente) -> return altoEquivalente }

        return menor
    }

    private fun etiquetaFallbackDe(f: FormatoCrudo): String {
        val base = f.nota.ifEmpty { f.id }.uppercase(Locale.ROOT)
        return when {
            "HD" in base || "HIGH" in base -> "HD"
            "SD" in base || "LOW" in base -> "SD"
            else -> base.take(16)
        }
    }

    private fun familiaVideo(v: String): String = when {
        v.startsWith("avc") || v.startsWith("h264") -> "H.264"
        v.startsWith("vp9") || v.startsWith("vp09") -> "VP9"
        v.startsWith("av01") || v.startsWith("av1") -> "AV1"
        v.startsWith("hev") || v.startsWith("hvc") || v.startsWith("h265") || v.startsWith("bytevc1") -> "H.265"
        v.isEmpty() || v == "none" -> "MP4"
        else -> v.substringBefore('.').uppercase(Locale.ROOT)
    }

    private fun prioridadCodecVideo(familia: String, alto: Int): Int {
        // Hasta 1080p priorizamos H.264 por compatibilidad universal; en 2K/4K/8K priorizamos VP9/AV1/H.265
        val orden = if (alto in 1..1080) {
            listOf("H.264", "VP9", "H.265", "AV1")
        } else {
            listOf("VP9", "AV1", "H.265", "H.264")
        }
        val idx = orden.indexOf(familia)
        return if (idx < 0) 99 else idx
    }

    private fun mejorAudioPara(audios: List<FormatoCrudo>, extVideo: String): FormatoCrudo? {
        if (audios.isEmpty()) return null
        val maxIdioma = audios.maxOf { it.prefIdioma }
        val candidatosIdioma = audios.filter { it.prefIdioma == maxIdioma }
        val extPreferida = if (extVideo == "mp4") "m4a" else "webm"
        val afines = candidatosIdioma.filter { it.ext == extPreferida }

        return afines.ifEmpty { candidatosIdioma }.maxByOrNull {
            if (it.abr > 0.0) it.abr else it.tbr
        }
    }

    private fun sumarPesos(v: FormatoCrudo, a: FormatoCrudo?): Pair<Long?, Boolean> {
        if (a == null) return v.bytes to v.aprox
        val vb = v.bytes
        val ab = a.bytes
        return when {
            vb != null && ab != null -> (vb + ab) to (v.aprox || a.aprox)
            vb != null -> vb to true // Si solo conocemos el peso del video, lo mostramos como aproximado
            else -> null to false
        }
    }

    private fun construirSelectorVideo(v: FormatoCrudo, a: FormatoCrudo?): String {
        val exacto = when {
            !v.soloVideo -> v.id
            a != null -> "${v.id}+${a.id}"
            else -> "${v.id}+ba/${v.id}"
        }
        return if (v.h > 0) {
            "$exacto/bv*[height<=${v.h}]+ba/b[height<=${v.h}]"
        } else {
            "$exacto/bv*+ba/b"
        }
    }

    // ---------- Construcción de Audio ----------

    private fun armarAudio(
        audiosPuros: List<FormatoCrudo>,
        todos: List<FormatoCrudo>,
        duracion: Double
    ): List<OpcionAudio> {
        // Si la plataforma (ej. TikTok, IG, FB, X) no entrega pistas de solo-audio separadas:
        if (audiosPuros.isEmpty()) {
            val mejorCombinado = todos
                .filter { it.tieneAudio }
                .maxByOrNull { if (it.abr > 0.0) it.abr else it.tbr }
                ?: return emptyList()

            val kbpsReal = mejorCombinado.abr.takeIf { it > 0.0 }?.roundToInt()
            val bytesEstimados = when {
                kbpsReal != null && duracion > 0.0 -> ((kbpsReal * 1000.0 / 8.0) * duracion).toLong()
                duracion > 0.0 -> ((128.0 * 1000.0 / 8.0) * duracion).toLong()
                else -> mejorCombinado.bytes
            }

            return listOf(
                OpcionAudio(
                    kbps = kbpsReal ?: 128,
                    codec = familiaAudio(mejorCombinado.acodec),
                    bytes = bytesEstimados,
                    aprox = true,
                    selector = "ba/b",
                    delVideo = true
                )
            )
        }

        val maxIdioma = audiosPuros.maxOf { it.prefIdioma }
        return audiosPuros
            .filter { it.prefIdioma == maxIdioma }
            .groupBy { f ->
                val bitrate = (if (f.abr > 0.0) f.abr else f.tbr).roundToInt()
                // Agrupamos por familia de códec y escalón de bitrate cercano para evitar duplicados idénticos
                familiaAudio(f.acodec) to bitrate
            }
            .map { (clave, grupo) ->
                val elegido = grupo.sortedWith(
                    compareBy<FormatoCrudo> { if (it.bytes != null) 0 else 1 }
                        .thenBy { if (it.esDirecto) 0 else 1 }
                        .thenByDescending { if (it.abr > 0.0) it.abr else it.tbr }
                ).first()

                OpcionAudio(
                    kbps = clave.second.takeIf { it > 0 },
                    codec = clave.first,
                    bytes = elegido.bytes,
                    aprox = elegido.aprox,
                    selector = "${elegido.id}/ba/b",
                    delVideo = false
                )
            }
            .sortedByDescending { it.kbps ?: 0 }
    }

    private fun familiaAudio(a: String): String = when {
        a.startsWith("opus") -> "Opus"
        a.startsWith("mp4a") || a.startsWith("aac") -> "AAC"
        a.startsWith("mp3") -> "MP3"
        a.startsWith("vorbis") -> "Vorbis"
        a.startsWith("flac") -> "FLAC"
        a.startsWith("ec-3") || a.startsWith("ac-3") -> "Dolby"
        a.isEmpty() || a == "none" -> "AAC"
        else -> a.substringBefore('.').uppercase(Locale.ROOT)
    }
}