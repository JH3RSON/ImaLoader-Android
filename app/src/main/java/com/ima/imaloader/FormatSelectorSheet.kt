package com.ima.imaloader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ima.imaloader.ui.theme.ImaCard
import com.ima.imaloader.ui.theme.ImaRealFormatButton
import com.ima.imaloader.ui.theme.ImaRealFormatInfo
import com.ima.imaloader.ui.theme.LocalImaPalette
import kotlin.math.roundToInt

fun MediaFormat.toImaRealFormatInfo(): ImaRealFormatInfo {
    val isAudio = this.isAudioOnly || this.vcodec == null
    val qualityLabel = if (isAudio) {
        this.abr?.let { "${it.roundToInt()} kbps" } ?: "Audio"
    } else {
        val h = this.height ?: 0
        if (h > 0) "${h}p${this.fps?.roundToInt()?.let { if (it > 30) it else "" } ?: ""}" else "Video"
    }
    return ImaRealFormatInfo(
        formatId = this.id,
        extensionReal = this.ext ?: "unknown",
        etiquetaCalidad = qualityLabel,
        tamanoBytesReal = this.sizeBytes,
        codecReal = if (isAudio) this.acodec else this.vcodec
    )
}

fun TargetForFormat(format: MediaFormat, info: MediaInfo): Target {
    val isAudio = format.isAudioOnly || format.vcodec == null
    return if (isAudio) {
        Target.Audio(
            formatId = format.id,
            format = format.ext ?: "m4a",
            embedCover = true,
            embedMetadata = true
        )
    } else {
        val companionAudio = if (!format.hasAudio) {
            FormatCatalog.autoAudio(info, format, preferM4a = format.ext == "mp4")
        } else null
        Target.Video(
            maxHeight = format.height,
            mp4 = format.ext == "mp4",
            keepOriginalContainer = format.ext != "mp4",
            formatId = format.id,
            formatHasAudio = format.hasAudio,
            withAudio = true,
            audioFormatId = companionAudio?.id,
            embedSubs = false,
            saveThumbnail = false
        )
    }
}

// Para retrocompatibilidad y evitar romper MainActivity si todavía espera este nombre.
// Ahora simplemente renderiza la lista usando los componentes del nuevo tema.
@Composable
fun FormatSelectorSheet(
    info: MediaInfo,
    onDismiss: () -> Unit,
    onConfirm: (Target) -> Unit,
) {
    val p = LocalImaPalette.current
    val allFormats = info.formats.map { it.toImaRealFormatInfo() }

    if (allFormats.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ImaCard(Modifier.fillMaxWidth()) {
            Text(
                "Formatos reales disponibles del motor:",
                color = p.texto,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(4.dp))
            allFormats.forEach { fmt ->
                ImaRealFormatButton(
                    formato = fmt,
                    seleccionado = false,
                    onClick = {
                        val realFormat = info.formats.find { it.id == fmt.formatId }
                        if (realFormat != null) {
                            onConfirm(TargetForFormat(realFormat, info))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
