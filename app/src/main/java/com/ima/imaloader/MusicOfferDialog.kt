package com.ima.imaloader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MusicOfferDialog(
    vm: MusicViewModel,
    info: MediaInfo,
    url: String,
    outputLabel: String, // "M4A", "MP3", "OPUS"
    onDownloadMusic: (MusicMatch) -> Unit,
    onDownloadOriginal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val direct = "music.youtube.com" in url.lowercase()
    val tip = "Consejo profesional: si buscas la carátula cuadrada 1:1 original y metadatos de estudio al instante, copia los enlaces de tus canciones directamente desde YouTube Music."

    AlertDialog(
        onDismissRequest = {
            vm.reset(deleteJson = true)
            onDismiss()
        },
        title = {
            Text(
                when (state) {
                    MusicState.Ask -> "¿Descargar con portada y datos oficiales?"
                    MusicState.Searching -> "Buscando edición de estudio…"
                    is MusicState.Found -> "¿Es esta la canción oficial?"
                    is MusicState.NotFound -> "No encontramos la ficha de álbum"
                }
            )
        },
        text = {
            Column {
                when (val s = state) {
                    MusicState.Ask -> {
                        Text(
                            "Podemos buscar la pista oficial en YouTube Music e iTunes para incrustar la carátula cuadrada del álbum en alta definición (1200×1200), el artista exacto, álbum, género y año de lanzamiento."
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MusicState.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (direct) "Verificando carátula 1200×1200 y datos del álbum…"
                            else "Buscando pista oficial en YouTube Music e iTunes…"
                        )
                    }
                    is MusicState.Found -> {
                        val m = s.match
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = m.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = m.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Text(m.artist, style = MaterialTheme.typography.bodyMedium)
                                val line = listOfNotNull(m.album, m.year?.toString()).joinToString(" · ")
                                if (line.isNotEmpty()) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Se guardará como $outputLabel con " +
                                if (m.officialCover) "carátula oficial 1200×1200 y metadatos de álbum."
                                else "portada cuadrada de YouTube Music y metadatos limpios.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                vm.reset(deleteJson = true)
                                onDownloadOriginal()
                            }
                        ) {
                            Text("Prefiero descargar el audio directo del video")
                        }
                    }
                    is MusicState.NotFound -> {
                        Text(
                            "No encontramos la ficha oficial de estudio (puede ser un remix, cover o versión en vivo). " +
                                "Puedes descargar el audio directo del video con su propia portada."
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                MusicState.Ask -> Button(onClick = { vm.search(info, url) }) {
                    Text("Buscar canción oficial")
                }
                MusicState.Searching -> {}
                is MusicState.Found -> Button(
                    onClick = {
                        // No borramos el JSON aquí porque DownloadWorker lo usará y luego lo eliminará
                        val match = s.match
                        vm.reset(deleteJson = false)
                        onDownloadMusic(match)
                    }
                ) {
                    Text("Descargar canción")
                }
                is MusicState.NotFound -> Button(
                    onClick = {
                        vm.reset(deleteJson = true)
                        onDownloadOriginal()
                    }
                ) {
                    Text("Descargar audio del video")
                }
            }
        },
        dismissButton = {
            when (state) {
                MusicState.Ask -> TextButton(
                    onClick = {
                        vm.reset(deleteJson = true)
                        onDownloadOriginal()
                    }
                ) { Text("Solo el audio del video") }

                MusicState.Searching -> TextButton(
                    onClick = {
                        vm.reset(deleteJson = true)
                        if (direct) onDismiss()
                    }
                ) { Text("Cancelar") }

                is MusicState.Found -> TextButton(onClick = { vm.reject() }) {
                    Text("No es esta")
                }

                is MusicState.NotFound -> TextButton(
                    onClick = {
                        vm.reset(deleteJson = true)
                        onDismiss()
                    }
                ) { Text("Cerrar") }
            }
        },
    )
}