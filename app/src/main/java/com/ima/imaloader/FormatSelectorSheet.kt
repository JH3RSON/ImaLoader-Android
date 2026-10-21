package com.ima.imaloader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectorSheet(
    info: MediaInfo,
    currentUrl: String,
    isSwitchingToYtMusic: Boolean = false,
    onSwitchToYoutubeMusic: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (Target) -> Unit,
) {
    val videos = remember(info) { FormatCatalog.videoFormats(info) }
    val dashVideos = remember(videos) { videos.filter { it.isVideoOnly } }
    val muxedVideos = remember(videos) { videos.filter { it.isMuxed } }
    val audios = remember(info) { FormatCatalog.audioFormats(info) }
    val sugV = remember(info) { FormatCatalog.videoSuggestions(info) }
    val sugA = remember(info) { FormatCatalog.audioSuggestions(info) }

    var kind by remember(info.isYoutubeMusic) {
        mutableStateOf(
            if (info.isYoutubeMusic || videos.isEmpty()) FileKind.AUDIO else FileKind.VIDEO
        )
    }

    // ---- Estado de video
    var video by remember(info) { mutableStateOf(sugV.firstOrNull()?.video ?: videos.firstOrNull()) }
    var audio by remember(info) { mutableStateOf(sugV.firstOrNull()?.audio) }
    var audioManual by remember(info) { mutableStateOf(false) }
    var noAudio by remember(info) { mutableStateOf(false) }
    var mp4 by remember(info) { mutableStateOf(sugV.firstOrNull()?.mp4 ?: false) }
    var audioOpen by remember { mutableStateOf(false) }

    // ---- Estado de audio
    var audioPick by remember(info) { mutableStateOf(sugA.firstOrNull()?.audio ?: audios.firstOrNull()) }
    var outFormat by remember { mutableStateOf(FormatPlanner.AUDIO_FORMATS.first()) }
    var squareCover by remember(info) { mutableStateOf(true) }

    val showYtMusicBanner = remember(currentUrl, info, kind) {
        kind == FileKind.AUDIO && !info.isYoutubeMusic && QuickPreview.isStandardYoutubeVideo(currentUrl)
    }

    fun selectVideo(v: MediaFormat) {
        video = v
        noAudio = false
        audio = if (v.hasAudio) null
        else if (!audioManual) FormatCatalog.autoAudio(info, v, mp4) else audio
    }

    fun applySuggestion(s: Suggestion) {
        video = s.video
        audio = s.audio
        mp4 = s.mp4
        audioManual = false
        noAudio = false
    }

    val v = video
    val a = if (noAudio) null else audio
    val summary: String
    val size: Size
    val canDownload: Boolean

    if (kind == FileKind.VIDEO) {
        canDownload = v != null
        summary = if (v == null) "Elige un formato" else listOfNotNull(
            v.height?.let { "${it}p" },
            VCodec.of(v.vcodec).title +
                when {
                    v.hasAudio -> " + ${FormatCatalog.audioName(v)}"
                    a != null -> " + ${FormatCatalog.audioName(a)}"
                    else -> " · sin audio"
                },
            if (mp4) "MP4" else "MKV",
        ).joinToString(" · ")
        size = FormatCatalog.total(v, if (v?.hasAudio == true) null else a)
    } else {
        val p = audioPick
        canDownload = p != null
        val coverTag = if (squareCover) "Portada 1:1 HD" else "Portada 16:9 HD"
        summary = if (p == null) "Elige un audio"
        else "${FormatCatalog.audioName(p)} · ${FormatCatalog.bitrate(p, false)} → ${outFormat.title} · $coverTag"
        size = FormatCatalog.total(p)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // ---------- Cabecera con Previsualización HD ----------
                item {
                    Column {
                        MediaPreviewHeader(
                            info = info,
                            showSquarePreview = kind == FileKind.AUDIO && squareCover
                        )

                        if (videos.isNotEmpty() && audios.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = kind == FileKind.VIDEO,
                                    onClick = { kind = FileKind.VIDEO },
                                    label = { Text("Video") }
                                )
                                FilterChip(
                                    selected = kind == FileKind.AUDIO,
                                    onClick = { kind = FileKind.AUDIO },
                                    label = { Text("Audio / Música") }
                                )
                            }
                        }
                    }
                }

                // ---------- Banner de recomendación de YouTube Music ----------
                item {
                    AnimatedVisibility(
                        visible = showYtMusicBanner,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        YoutubeMusicTipBanner(
                            isLoading = isSwitchingToYtMusic,
                            onSwitchClick = {
                                QuickPreview.toYoutubeMusicUrl(currentUrl)?.let(onSwitchToYoutubeMusic)
                            }
                        )
                    }
                }

                if (kind == FileKind.VIDEO) {
                    // ---------- Sugeridos ----------
                    item { SectionTitle("Ajustes rápidos sugeridos") }
                    items(sugV, key = { "sv_${it.title}" }) { s ->
                        val sel = !noAudio && video?.id == s.video?.id && audio?.id == s.audio?.id && mp4 == s.mp4
                        SuggestionCard(s, selected = sel) { applySuggestion(s) }
                    }

                    // ---------- Streams de Video Puro (Alta Definición DASH) ----------
                    if (dashVideos.isNotEmpty()) {
                        item {
                            SectionTitle("Streams de Alta Definición (Se unen con el mejor audio)")
                        }
                        dashVideos.groupBy { it.height }.forEach { (h, list) ->
                            item(key = "hdr_dash_$h") {
                                ResolutionHeader(h)
                            }
                            items(list, key = { "vd_${it.id}" }) { f ->
                                FormatRow(f, selected = video?.id == f.id) { selectVideo(f) }
                            }
                        }
                    }

                    // ---------- Videos con Audio Integrado ----------
                    if (muxedVideos.isNotEmpty()) {
                        item { SectionTitle("Formatos Directos (Video + Audio integrados)") }
                        items(muxedVideos, key = { "vm_${it.id}" }) { f ->
                            FormatRow(f, selected = video?.id == f.id) { selectVideo(f) }
                        }
                    }

                    // ---------- Pista de Audio para el Video ----------
                    item {
                        Column {
                            SectionTitle("Pista de audio acompañante")
                            val cur = video
                            when {
                                cur == null -> {}
                                cur.hasAudio -> Text(
                                    "Este formato ya trae su pista de audio integrada.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                audios.isEmpty() -> Text(
                                    "Este enlace no ofrece pistas de audio separadas.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                else -> {
                                    val current = when {
                                        noAudio -> "Sin audio (video mudo)"
                                        audio != null -> "${FormatCatalog.audioName(audio!!)} · " +
                                            "${FormatCatalog.bitrate(audio!!, false)} · " +
                                            Size(audio!!.sizeBytes, audio!!.sizeApprox).label()
                                        else -> "Sin audio disponible"
                                    }
                                    FormatCard(
                                        title = current,
                                        subtitle = if (audioOpen) "Toca para ocultar pistas" else "Toca para elegir otra pista de audio",
                                        badge = "Auto"
                                    ) { audioOpen = !audioOpen }

                                    if (audioOpen) {
                                        FormatCard(title = "Descargar sin audio (mudo)", selected = noAudio) {
                                            noAudio = true
                                            audioManual = true
                                        }
                                        audios.forEach { f ->
                                            FormatRow(f, selected = !noAudio && audio?.id == f.id) {
                                                audio = f
                                                noAudio = false
                                                audioManual = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---------- Contenedor ----------
                    item {
                        Column {
                            SectionTitle("Contenedor final")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !mp4,
                                    onClick = {
                                        mp4 = false
                                        video?.let { if (!audioManual) audio = FormatCatalog.autoAudio(info, it, false) }
                                    },
                                    label = { Text("MKV (Puro)") }
                                )
                                FilterChip(
                                    selected = mp4,
                                    onClick = {
                                        mp4 = true
                                        video?.let { if (!audioManual) audio = FormatCatalog.autoAudio(info, it, true) }
                                    },
                                    label = { Text("MP4 (Compatible)") }
                                )
                            }
                        }
                    }
                } else {
                    // ---------- AUDIO ----------
                    item { SectionTitle("Sugeridos para música y voz") }
                    items(sugA, key = { "sa_${it.title}" }) { s ->
                        SuggestionCard(s, selected = audioPick?.id == s.audio?.id) { audioPick = s.audio }
                    }

                    // ---------- Portada y Metadatos ----------
                    item {
                        Column {
                            SectionTitle("Portada y Metadatos")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = squareCover,
                                    onClick = { squareCover = true },
                                    label = { Text("Carátula Álbum 1:1 HD") }
                                )
                                FilterChip(
                                    selected = !squareCover,
                                    onClick = { squareCover = false },
                                    label = { Text("Miniatura 16:9 Original") }
                                )
                            }
                            Text(
                                text = if (info.isYoutubeMusic) {
                                    "Metadatos de YouTube Music activos: se incrustará Artista, Álbum, Año y Portada oficial en alta definición."
                                } else {
                                    "Se incrustarán Título, Artista limpio y Portada HD recortada sin bordes negros."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // ---------- Formato de salida ----------
                    item {
                        Column {
                            SectionTitle("Contenedor / Conversión de salida")
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FormatPlanner.AUDIO_FORMATS.forEach { f ->
                                    FilterChip(
                                        selected = outFormat == f,
                                        onClick = { outFormat = f },
                                        label = { Text(f.title) }
                                    )
                                }
                            }
                            Text(
                                text = outFormat.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    item { SectionTitle("Todos los streams de audio disponibles en el servidor") }
                    items(audios, key = { "a_${it.id}" }) { f ->
                        FormatRow(f, selected = audioPick?.id == f.id) { audioPick = f }
                    }
                }
            }

            // ---------- Barra inferior fija ----------
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = size.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = canDownload && !isSwitchingToYtMusic,
                        onClick = {
                            if (kind == FileKind.VIDEO) {
                                val sv = video ?: return@Button
                                onConfirm(
                                    Target.Video(
                                        maxHeight = sv.height,
                                        mp4 = mp4,
                                        formatId = sv.id,
                                        formatHasAudio = sv.hasAudio,
                                        withAudio = sv.hasAudio || !noAudio,
                                        audioFormatId = if (sv.hasAudio || noAudio) null else audio?.id,
                                    )
                                )
                            } else {
                                val p = audioPick ?: return@Button
                                onConfirm(
                                    Target.Audio(
                                        format = outFormat.id,
                                        quality = outFormat.qualityArg,
                                        formatId = p.id,
                                        squareCover = squareCover,
                                    )
                                )
                            }
                        },
                    ) { Text("Descargar") }
                }
            }
        }
    }
}

// ================= Cabecera de Previsualización HD y Banner de YT Music =================

@Composable
fun MediaPreviewHeader(
    info: MediaInfo,
    showSquarePreview: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!info.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = "Portada de ${info.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(
                        width = if (showSquarePreview) 84.dp else 120.dp,
                        height = 84.dp
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            if (info.isYoutubeMusic) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "YouTube Music · Metadatos Oficiales",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val metaLine = listOfNotNull(
                info.artist,
                info.album,
                info.releaseYear,
                info.durationSec?.let { "%d:%02d".format(it / 60, it % 60) }
            )
            if (metaLine.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metaLine.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun YoutubeMusicTipBanner(
    isLoading: Boolean,
    onSwitchClick: () -> Unit,
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "¿Buscas la carátula oficial y metadatos de estudio?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Para obtener la portada cuadrada 1:1 original del álbum, el nombre exacto del artista y el año de lanzamiento sin bordes de video, copia tus enlaces desde YouTube Music o convierte este enlace con un toque:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onSwitchClick,
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    if (isLoading) "Cargando datos de YouTube Music..."
                    else "Cambiar enlace a YouTube Music (Portada Oficial)"
                )
            }
        }
    }
}

// ================= Tarjetas auxiliares =================

@Composable
private fun ResolutionHeader(h: Int?) =
    Text(
        text = h?.let { "${it}p · ${VideoQuality(it, 30, Size(null, false)).name}" } ?: "Otros",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )

@Composable
private fun SectionTitle(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )

@Composable
private fun SuggestionCard(s: Suggestion, selected: Boolean, onClick: () -> Unit) {
    val size = FormatCatalog.total(s.video, s.audio)
    val containerLabel = if (s.video != null) {
        if (s.mp4) "MP4" else "MKV"
    } else {
        s.audio?.ext?.uppercase() ?: ""
    }
    FormatCard(
        title = s.title,
        subtitle = "${s.note} · $containerLabel\n${size.label()}",
        badge = if (selected) "Elegido" else s.badge,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun FormatRow(f: MediaFormat, selected: Boolean, onClick: () -> Unit) {
    val isVideo = f.hasVideo
    val title = if (isVideo) {
        f.height?.let { h ->
            "${h}p" + (f.fps?.takeIf { it > 30 }?.let { " · ${it.roundToInt()} fps" } ?: "")
        } ?: "Video"
    } else {
        FormatCatalog.bitrate(f, false)
    }
    val line2 = listOfNotNull(
        FormatCatalog.codecs(f),
        f.ext?.uppercase(),
        if (isVideo) FormatCatalog.bitrate(f, true) else null,
    ).joinToString(" · ")
    val tag = when {
        f.hasVideo && f.hasAudio -> "Video + audio"
        f.hasVideo -> "Stream HD"
        else -> "Audio puro"
    }
    FormatCard(
        title = title,
        subtitle = "$line2\n${Size(f.sizeBytes, f.sizeApprox).label()}",
        badge = tag,
        trailing = f.id,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
fun FormatCard(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, false)
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}