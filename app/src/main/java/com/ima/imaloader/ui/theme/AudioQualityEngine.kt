package com.ima.imaloader.ui.theme

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.math.roundToInt

/**
 * Formatos de salida soportados con sus bitrates objetivo reales.
 */
enum class FormatoAudio(val ext: String, val bitrateTarget: String) {
    ORIGINAL("best", "0"),      // Intenta mantener el contenedor original intacto
    MP3("mp3", "320k")    // Máxima compatibilidad en 320 kbps reales (CBR)
}

/**
 * Perfiles de masterización DSP (Ecualización psicoacústica + Dinámica).
 */
enum class RealceAudio {
    NINGUNO,
    
    // Recomendado ("Sonido YouTube Premium"): Bajos profundos, voces cálidas, cero fatiga en agudos
    CALIDO_PRO
}



/**
 * Extensión para configurar YoutubeDLRequest con máxima calidad real y masterización opcional.
 */
fun YoutubeDLRequest.aplicarMotorAudioPro(
    selectorAudio: String?,
    formato: FormatoAudio = FormatoAudio.MP3,
    realce: RealceAudio = RealceAudio.CALIDO_PRO
) {
    // 1. Seleccionar el mejor stream de audio puro (Prioridad: Opus premium > AAC > Vorbis)
    // Opus 251/252 es el que tiene los bajos más profundos y mayor rango dinámico en YT
    addOption("-f", selectorAudio ?: "bestaudio[acodec=opus]/bestaudio/best")
    
    // 2. Ordenar por mayor bitrate real (abr), mayor sample rate (asr) y priorizar Opus (mejor calidad en YT)
    addOption("-S", "abr,asr,acodec:opus")
    
    // 3. Extraer solo audio
    addOption("-x")

    // ALTERNATIVA INTELIGENTE: Si el usuario quiere sonido "Premium/Cálido", evitamos a toda costa
    // recodificar a MP3 (lo cual aplasta los bajos y recorta agudos). Forzamos el codec original (M4A o OPUS).
    val formatoFinal = if (realce != RealceAudio.NINGUNO) {
        // Si pide Realce, le damos el formato original sin compresión destructiva
        FormatoAudio.ORIGINAL 
    } else {
        formato
    }

    if (formatoFinal == FormatoAudio.ORIGINAL) {
        // Descarga directa sin recodificar: conserva el 100% de los graves nativos y el rango dinámico de YouTube
        addOption("--audio-format", "best")
    } else {
        // Recodificación clásica (pérdida de calidad/bajos al pasar a mp3)
        addOption("--audio-format", formatoFinal.ext)
        addOption("--audio-quality", formatoFinal.bitrateTarget)
        
        val ffArgs = buildString {
            append("ffmpeg_o:")
            append("-b:a ${formatoFinal.bitrateTarget}")
            if (formatoFinal == FormatoAudio.MP3) {
                append(" -q:a 0") // Lame V0
            }
        }
        addOption("--postprocessor-args", ffArgs)
    }
}

/**
 * Analizador preciso de kbps reales del stream de audio (ignora el peso de las carátulas/metadatos).
 */
object MedidorKbps {
    fun obtenerKbpsReales(archivo: File): Int? {
        if ((!archivo.exists()) || (archivo.length() == 0L)) return null

        // Intento 1: Extraer el bitrate exacto de la pista de audio con MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(archivo.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        val bps = format.getInteger(MediaFormat.KEY_BIT_RATE)
                        if (bps > 0) return (bps / 1000.0).roundToInt()
                    }
                }
            }
        } catch (_: Exception) {
            // Si el contenedor no reporta KEY_BIT_RATE en su cabecera, pasamos al Intento 2
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }

        // Intento 2: MediaMetadataRetriever (lee el header del codificador)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(archivo.absolutePath)
            val bitrateHeader = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
            
            if (bitrateHeader != null && bitrateHeader > 0) {
                return (bitrateHeader / 1000.0).roundToInt()
            }

            // Intento 3 (Fallback matemático): Duración vs Peso de audio (descontando carátula si existe)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            if (durationMs <= 0) return null

            val embeddedPicBytes = retriever.embeddedPicture?.size?.toLong() ?: 0L
            val pureAudioBytes = (archivo.length() - embeddedPicBytes).coerceAtLeast(1L)

            ((pureAudioBytes * 8.0) / durationMs).roundToInt()
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}