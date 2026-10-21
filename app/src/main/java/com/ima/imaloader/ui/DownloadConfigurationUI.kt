package com.ima.imaloader.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ima.imaloader.DownloadButtonState
import com.ima.imaloader.GlassOrSolidCard
import com.ima.imaloader.IosPrimaryActionButton
import com.ima.imaloader.MediaInfo
import java.util.Locale

// Enum simple para separar video y audio en la UI
enum class MediaType { AUDIO, VIDEO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureDownloadCard(
    media: MediaInfo,
    selectedType: MediaType,
    onTypeChange: (MediaType) -> Unit,
    selectedMaxResolution: Int?,
    onMaxResolutionChange: (Int?) -> Unit,
    selectedAudioFormat: String,
    onAudioFormatChange: (String) -> Unit,
    buttonState: DownloadButtonState,
    onCancel: () -> Unit,
    onStartDownload: () -> Unit
) {
    val palette = LocalImaPalette.current
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioFormatDialog by remember { mutableStateOf(false) }

    GlassOrSolidCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 1. Tipo de descarga (Segmented)
            val modes = listOf(MediaType.AUDIO to "Audio", MediaType.VIDEO to "Vídeo")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.background.copy(alpha = 0.45f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modes.forEach { (mode, label) ->
                    val active = selectedType == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) palette.primaryAccent else Color.Transparent)
                            .clickable { onTypeChange(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else palette.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Opciones de descarga
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ajustes de descarga",
                    color = palette.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedType == MediaType.VIDEO) {
                        item {
                            val resLabel = if (selectedMaxResolution == null) "Mejor calidad" else if (selectedMaxResolution == 0) "Más baja" else "${selectedMaxResolution}p"
                            PreferenceChip(
                                label = "Resolución Máxima: $resLabel",
                                onClick = { showVideoQualityDialog = true }
                            )
                        }
                    } else {
                        item {
                            PreferenceChip(
                                label = "Formato de Audio: ${selectedAudioFormat.uppercase()}",
                                onClick = { showAudioFormatDialog = true }
                            )
                        }
                    }
                }
            }

            // 3. Botones
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar", color = palette.textSecondary)
                }
                Box(modifier = Modifier.weight(2f)) {
                    IosPrimaryActionButton(
                        text = "Iniciar descarga",
                        state = buttonState,
                        onClick = onStartDownload
                    )
                }
            }
        }
    }

    if (showVideoQualityDialog) {
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            title = { Text("Calidad máxima del vídeo") },
            text = {
                val opts = listOf(
                    null to "Mejor calidad disponible",
                    2160 to "2160p (4K)",
                    1440 to "1440p (2K)",
                    1080 to "1080p (FHD)",
                    720 to "720p (HD)",
                    480 to "480p",
                    360 to "360p"
                )
                LazyColumn {
                    items(opts) { (res, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMaxResolutionChange(res); showVideoQualityDialog = false }
                                .padding(8.dp)
                        ) {
                            RadioButton(selected = selectedMaxResolution == res, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVideoQualityDialog = false }) { Text("Cerrar") } }
        )
    }

    if (showAudioFormatDialog) {
        AlertDialog(
            onDismissRequest = { showAudioFormatDialog = false },
            title = { Text("Formato del audio") },
            text = {
                val opts = listOf("best", "mp3", "m4a", "opus", "flac")
                Column {
                    opts.forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAudioFormatChange(format)
                                    showAudioFormatDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedAudioFormat == format,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (format == "best") "Mejor (Sin conversión)" else format.uppercase())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAudioFormatDialog = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
fun PreferenceChip(label: String, onClick: () -> Unit) {
    val palette = LocalImaPalette.current
    Surface(
        color = palette.background.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = palette.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
