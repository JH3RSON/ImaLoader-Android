@file:Suppress("AndroidRImport", "unused")

package com.ima.imaloader

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import coil.compose.AsyncImage
import com.ima.imaloader.guard.DownloadGuard
import com.ima.imaloader.ui.theme.DownloadSelection
import com.ima.imaloader.ui.theme.Kind
import com.ima.imaloader.ui.theme.toYtDlpArgs
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("unused")
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val processId = inputData.getString(K_RESUME_KEY) ?: id.toString()
    private val notifId = processId.hashCode()

    override suspend fun doWork(): Result {
        val json = inputData.getString("selection_json") ?: return Result.failure()
        val selection = DownloadSelection.fromJson(json)
        val title = selection.title

        ensureChannel()
        runCatching { setForeground(foreground(title, DownloadPhase.PREPARING.label)) }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()

        // Output template directly to Downloads folder
        val extTemplate = when (selection.kind) {
            Kind.AUDIO -> if (selection.convertirAMp3) "mp3" else selection.audioExtReal
            else -> "%(ext)s"
        }

        val req = YoutubeDLRequest(selection.url).apply {
            addOption("-o", "${downloadsDir.absolutePath}/%(title).100B.$extTemplate")
            selection.toYtDlpArgs().forEach { addOption(it) }
        }

        var lastPct = -1
        var lastNotifTime = 0L

        return try {
            DownloadGuard.protect(
                context = applicationContext,
                id = processId,
                title = title,
                downloadDir = downloadsDir,
                payload = null,
                onStop = { runCatching { YoutubeDL.getInstance().destroyProcessById(processId) } }
            ) {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(req, processId) { p, eta, speed ->
                        val pct = p.toInt().coerceIn(0, 100)
                        val now = SystemClock.elapsedRealtime()
                        
                        DownloadGuard.progress(processId, pct, DownloadPhase.DOWNLOADING.label)
                        
                        if (pct != lastPct && (now - lastNotifTime >= 400L || pct == 100)) {
                            lastPct = pct
                            lastNotifTime = now
                            val etaStr = if (eta > 0) "${eta}s" else null
                            setProgressAsync(
                                workDataOf(
                                    K_PROGRESS to pct,
                                    K_PHASE to DownloadPhase.DOWNLOADING.name,
                                    K_SPEED to speed,
                                    K_ETA to (etaStr ?: ""),
                                    K_TITLE to title,
                                    K_THUMB to (selection.thumbnailUrl ?: ""),
                                )
                            )
                            val subtitle = listOfNotNull(DownloadPhase.DOWNLOADING.label, speed, etaStr?.let { "Restan $it" }).joinToString(" · ")
                            notifier().notify(notifId, notification(title, pct, subtitle))
                        }
                    }
                }
            }

            setProgressAsync(
                workDataOf(
                    K_PROGRESS to 100,
                    K_PHASE to DownloadPhase.COMPLETED.name,
                    K_EXPORTED_MIME to if (selection.kind == Kind.AUDIO) "audio/*" else "video/*",
                    K_FINAL_BYTES to (selection.estimatedSizeBytes ?: 0L),
                    K_TITLE to title,
                    K_THUMB to (selection.thumbnailUrl ?: ""),
                )
            )

            Result.success(
                workDataOf(
                    K_PROGRESS to 100,
                    K_PHASE to DownloadPhase.COMPLETED.name,
                    K_EXPORTED_MIME to if (selection.kind == Kind.AUDIO) "audio/*" else "video/*",
                    K_FINAL_BYTES to (selection.estimatedSizeBytes ?: 0L),
                    K_TITLE to title,
                    K_THUMB to (selection.thumbnailUrl ?: ""),
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf(K_ERROR to e.message))
        }
    }

    private fun notifier() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        val channel = NotificationChannel(CH, "Descargas", NotificationManager.IMPORTANCE_LOW)
        notifier().createNotificationChannel(channel)
    }

    private fun notification(title: String, pct: Int, subtitle: String) =
        NotificationCompat.Builder(applicationContext, CH)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("$pct%")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun foreground(title: String, subtitle: String) =
        if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                notifId,
                notification(title, 0, subtitle),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notifId, notification(title, 0, subtitle))
        }

    companion object {
        const val K_URL = "url"
        const val K_TITLE = "title"
        const val K_THUMB = "thumb"
        const val K_TARGET_JSON = "target_json"
        const val K_RESUME_KEY = "resume_key"
        const val K_PROGRESS = "progress"
        const val K_PHASE = "phase"
        const val K_SPEED = "speed"
        const val K_SIZE_TEXT = "size_text"
        const val K_ETA = "eta"
        const val K_EXPORTED_URI = "exported_uri"
        const val K_EXPORTED_MIME = "exported_mime"
        const val K_FINAL_NAME = "final_name"
        const val K_FINAL_BYTES = "final_bytes"
        const val K_ERROR = "error"
        private const val CH = "downloads"
    }
}

@Suppress("unused")
object Downloads {
    fun enqueue(
        ctx: Context,
        selection: DownloadSelection,
        resumeKey: String = UUID.randomUUID().toString(),
    ): UUID {
        val data = workDataOf(
            DownloadWorker.K_URL to selection.url,
            DownloadWorker.K_TITLE to selection.title,
            DownloadWorker.K_THUMB to (selection.thumbnailUrl ?: ""),
            "selection_json" to selection.toJson(),
            DownloadWorker.K_RESUME_KEY to resumeKey,
        )
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag("imaloader_download")
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "dl_$resumeKey",
            ExistingWorkPolicy.REPLACE,
            req
        )
        return req.id
    }

    /** Pausa deteniendo el Worker sin borrar la carpeta de trabajo (.part) */
    fun pause(ctx: Context, resumeKey: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork("dl_$resumeKey")
    }

    /** Cancela definitivamente y elimina los archivos temporales .part */
    fun cancelAndClean(ctx: Context, resumeKey: String, target: Target? = null) {
        WorkManager.getInstance(ctx).cancelUniqueWork("dl_$resumeKey")
        StorageExporter.cleanWorkDir(ctx, resumeKey)
        MusicResolver.deleteJsonIfExists((target as? Target.Audio)?.infoJsonPath)
    }
}

// ================= Tarjeta de Progreso y Resultado Completado (Estilo Seal) =================

@Composable
@Suppress("unused")
fun DownloadStatusCard(
    task: DownloadTaskUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenDownloadsTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progressPct.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "dl_progress"
    )

    OutlinedCard(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!task.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = task.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.finalFileName ?: task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.phase.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${task.progressPct}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (task.phase != DownloadPhase.COMPLETED) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceAtLeast(0.03f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val details = listOfNotNull(
                        task.downloadedText?.takeIf { it.isNotBlank() },
                        task.speedText?.takeIf { it.isNotBlank() },
                        task.etaText?.takeIf { it.isNotBlank() }?.let { "Restan $it" }
                    ).joinToString(" · ")

                    Text(
                        text = details.ifEmpty { "Procesando flujo..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (task.phase == DownloadPhase.PAUSED) {
                            TextButton(onClick = onResume) { Text("Reanudar") }
                        } else if (task.phase == DownloadPhase.DOWNLOADING) {
                            TextButton(onClick = onPause) { Text("Pausar") }
                        }
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                    }
                }
            } else {
                // ---------- Estado Final: Abrir archivo, Compartir y Ver en descargas ----------
                Spacer(Modifier.height(10.dp))
                val finalSizeStr = Size(task.finalSizeBytes, false).label()
                Text(
                    text = "Tamaño final: $finalSizeStr · Listo en tu biblioteca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val uriStr = task.exportedUri ?: return@FilledTonalButton
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uriStr.toUri(), task.exportedMime ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { ctx.startActivity(Intent.createChooser(intent, "Abrir archivo")) }
                        }
                    ) { Text("Abrir archivo") }

                    OutlinedButton(
                        onClick = {
                            val uriStr = task.exportedUri ?: return@OutlinedButton
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = task.exportedMime ?: "*/*"
                                putExtra(Intent.EXTRA_STREAM, uriStr.toUri())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { ctx.startActivity(Intent.createChooser(share, "Compartir")) }
                        }
                    ) { Text("Compartir") }

                    TextButton(onClick = onOpenDownloadsTab) {
                        Text("Ver en descargas")
                    }
                }
            }
        }
    }
}
