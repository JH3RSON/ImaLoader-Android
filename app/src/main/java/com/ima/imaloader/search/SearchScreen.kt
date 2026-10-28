package com.ima.imaloader.search

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun SearchScreen(
    onDownload: (String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val focus = LocalFocusManager.current
    val playing = vm.playing
    val paginador = vm.paginador
    val err = paginador.hayError

    BackHandler(enabled = playing != null || vm.suggestions.isNotEmpty()) {
        if (playing != null) {
            vm.playing = null
        } else {
            vm.dismissSuggestions()
            focus.clearFocus()
        }
    }

    if (playing != null) {
        PlayerPane(
            item = playing,
            mode = vm.mode,
            onDownload = onDownload,
            onBack = { vm.playing = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vm.mode == SearchMode.VIDEO,
                onClick = { vm.onModeChange(SearchMode.VIDEO) },
                label = { Text("YouTube") },
            )
            FilterChip(
                selected = vm.mode == SearchMode.MUSIC,
                onClick = { vm.onModeChange(SearchMode.MUSIC) },
                label = { Text("YouTube Music") },
            )
        }

        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = {
                Text(
                    if (vm.mode == SearchMode.MUSIC) "Buscar canciones oficiales..."
                    else "Buscar videos en YouTube..."
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.search()
                focus.clearFocus()
            }),
        )

        Spacer(Modifier.height(8.dp))

        when {
            vm.suggestions.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(vm.suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.search(s)
                                focus.clearFocus()
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            paginador.cargando && paginador.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            err && paginador.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No se pudo realizar la búsqueda. Verifica tu conexión.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    OutlinedButton(onClick = { vm.search() }) {
                        Text("Reintentar")
                    }
                }
            }
            paginador.items.isEmpty() && !paginador.cargando -> Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyText = when {
                    vm.hasSearched -> "No se encontraron resultados para \"${vm.query}\"."
                    vm.mode == SearchMode.MUSIC -> "Busca en YouTube Music para descargar canciones con portada oficial en HD y metadatos precisos."
                    else -> "Busca cualquier video de YouTube para previsualizarlo o descargarlo directamente."
                }
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                val listaState = rememberLazyListState()
                listaState.AlLlegarAlFinal(
                    cargando = paginador.cargando,
                    terminado = paginador.terminado
                ) {
                    paginador.cargarMas()
                }
                
                LazyColumn(Modifier.fillMaxSize(), state = listaState) {
                    items(paginador.items, key = { it.id }) { item ->
                        ResultRow(item, vm.mode) {
                            vm.dismissSuggestions()
                            focus.clearFocus()
                            vm.playing = item
                        }
                    }
                    if (paginador.cargando) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    if (paginador.hayError && paginador.items.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(onClick = { paginador.cargarMas() }) {
                                    Text("Reintentar cargar más")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: ResultadoVideo, mode: SearchMode, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val music = mode == SearchMode.MUSIC
    val sub = if (music) {
        listOf(item.canal, item.duracionTexto).filter { it.isNotEmpty() }.joinToString(" • ")
    } else {
        listOf(item.canal, item.vistasTexto).filter { it.isNotEmpty() }.joinToString(" • ")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbModifier = if (music) {
            Modifier.size(64.dp)
        } else {
            Modifier.width(148.dp).aspectRatio(16 / 9f)
        }
        Box(thumbModifier.clip(RoundedCornerShape(8.dp)).background(Color.DarkGray)) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(item.miniatura)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (!music && item.duracionTexto.isNotEmpty()) {
                Text(
                    text = item.duracionTexto,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.titulo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerPane(
    item: ResultadoVideo,
    mode: SearchMode,
    onDownload: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var usarVistaWeb by remember(item.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ReproductorIntegrado(
            videoId = item.id,
            esMusica = mode == SearchMode.MUSIC,
            usarVistaWeb = usarVistaWeb,
            onCambiarAVistaWeb = { usarVistaWeb = true }
        )

        Column(Modifier.padding(16.dp)) {
            TextButton(onClick = onBack) { Text("← Volver a resultados") }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.titulo,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.canal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    copyLink(ctx, item.urlPagina)
                    onDownload(item.urlPagina)
                }) {
                    Text(if (mode == SearchMode.MUSIC) "Descargar Canción" else "Descargar Video")
                }
                OutlinedButton(onClick = { copyLink(ctx, item.urlPagina) }) {
                    Text("Copiar link")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { usarVistaWeb = !usarVistaWeb }) {
                Text(
                    if (usarVistaWeb) "Volver al reproductor nativo"
                    else "¿Error al reproducir? Probar vista web"
                )
            }
        }
    }
}

private fun copyLink(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
    Toast.makeText(ctx, "Link copiado al portapapeles", Toast.LENGTH_SHORT).show()
}