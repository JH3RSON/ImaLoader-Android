package com.ima.imaloader.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface YtDlpUpdateState {
    data object Idle : YtDlpUpdateState
    data object Updating : YtDlpUpdateState
    data class Updated(val from: String, val to: String) : YtDlpUpdateState
    data class UpToDate(val version: String) : YtDlpUpdateState
    data object Busy : YtDlpUpdateState
    data class Failed(val reason: String) : YtDlpUpdateState
}

/**
 * Actualizador seguro de yt-dlp.
 * Corre en un SupervisorJob independiente de la UI para evitar que salir de la pantalla
 * cancele la escritura del binario a mitad de camino y corrompa el motor.
 */
object YtDlpUpdater {

    private val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _state = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    val state: StateFlow<YtDlpUpdateState> = _state.asStateFlow()

    private val _version = MutableStateFlow("Cargando...")
    val version: StateFlow<String> = _version.asStateFlow()

    /** Lee la versión en segundo plano sin bloquear el hilo principal (UI). */
    fun loadVersionAsync(ctx: Context) {
        val app = ctx.applicationContext
        updaterScope.launch {
            val ver = readVersionSafe(app)
            _version.value = ver
        }
    }

    private fun readVersionSafe(app: Context): String = runCatching {
        // Primero intenta lectura rápida de preferencias; si no existe, inicializa y consulta
        YoutubeDL.getInstance().versionName(app)?.takeIf { it.isNotBlank() }
            ?: run {
                YoutubeDL.getInstance().init(app)
                YoutubeDL.getInstance().version(app)
            }
    }.getOrNull().orEmpty().ifBlank { "Incluida de fábrica" }

    /**
     * Inicia la actualización de forma atómica en segundo plano.
     */
    fun triggerUpdate(
        ctx: Context,
        isBusy: () -> Boolean,
        channel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.STABLE,
    ) {
        if (isBusy()) {
            _state.value = YtDlpUpdateState.Busy
            return
        }
        val app = ctx.applicationContext
        updaterScope.launch {
            if (!lock.tryLock()) return@launch
            try {
                if (isBusy()) {
                    _state.value = YtDlpUpdateState.Busy
                    return@launch
                }
                _state.value = YtDlpUpdateState.Updating
                val before = readVersionSafe(app)

                // Garantiza que el motor base esté inicializado antes de pedir actualización
                YoutubeDL.getInstance().init(app)

                val status = YoutubeDL.getInstance().updateYoutubeDL(app, channel)
                val after = readVersionSafe(app)
                _version.value = after

                _state.value = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> YtDlpUpdateState.Updated(before, after)
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> YtDlpUpdateState.UpToDate(after)
                    else -> YtDlpUpdateState.Failed("No se pudo comprobar la versión.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = YtDlpUpdateState.Failed(friendlyError(e))
            } finally {
                lock.unlock()
            }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val noNet = generateSequence(e) { it.cause }.any { it is IOException }
        return if (noNet) {
            "Sin conexión o GitHub no responde. Inténtalo de nuevo."
        } else {
            "No se pudo actualizar (${e.localizedMessage ?: "error interno"})."
        }
    }
}

@Composable
fun YtDlpUpdateItem(
    isBusy: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val updateState by YtDlpUpdater.state.collectAsState()
    val currentVersion by YtDlpUpdater.version.collectAsState()
    var useNightly by remember { mutableStateOf(false) }

    val isUpdating = updateState is YtDlpUpdateState.Updating

    LaunchedEffect(Unit) {
        YtDlpUpdater.loadVersionAsync(ctx)
    }

    val statusMessage = when (val s = updateState) {
        YtDlpUpdateState.Idle -> "Toca para buscar mejoras de extracción"
        YtDlpUpdateState.Updating -> "Descargando e instalando actualización (no cierres la app)..."
        is YtDlpUpdateState.Updated -> "✔ Actualizado correctamente a ${s.to}"
        is YtDlpUpdateState.UpToDate -> "✔ Ya tienes la versión más reciente"
        YtDlpUpdateState.Busy -> "⚠ Espera a que terminen las descargas en curso"
        is YtDlpUpdateState.Failed -> "✖ ${s.reason}"
    }

    Column(modifier = modifier) {
        ListItem(
            modifier = Modifier.clickable(enabled = !isUpdating) {
                val channel = if (useNightly) {
                    YoutubeDL.UpdateChannel.NIGHTLY
                } else {
                    YoutubeDL.UpdateChannel.STABLE
                }
                YtDlpUpdater.triggerUpdate(ctx, isBusy, channel)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text("Actualizar motor yt-dlp") },
            supportingContent = {
                Column {
                    Text("Versión actual: $currentVersion")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (updateState) {
                            is YtDlpUpdateState.Failed, YtDlpUpdateState.Busy -> MaterialTheme.colorScheme.error
                            is YtDlpUpdateState.Updated, is YtDlpUpdateState.UpToDate -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            trailingContent = {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            },
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !useNightly,
                enabled = !isUpdating,
                onClick = { useNightly = false },
                label = { Text("Canal Estable (Seguro)") }
            )
            FilterChip(
                selected = useNightly,
                enabled = !isUpdating,
                onClick = { useNightly = true },
                label = { Text("Canal Nightly (Parches rápidos)") }
            )
        }
    }
}