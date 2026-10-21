package com.ima.imaloader

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.yausername.youtubedl_android.YoutubeDL
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val engine = DownloadEngine(ctx)
    private val processId = id.toString()
    private val notifId = id.hashCode()

    override suspend fun doWork(): Result {
        val url = inputData.getString(K_URL) ?: return Result.failure()
        val title = inputData.getString(K_TITLE) ?: "Descargando..."
        val thumb = inputData.getString(K_THUMB)
        val targetJson = inputData.getString(K_TARGET_JSON)
        val target = if (targetJson != null) TargetCodec.decode(targetJson) else Target.Video()
        val isAudio = target is Target.Audio

        // Carpeta temporal aislada para este trabajo
        val workDir = StorageExporter.workDirFor(applicationContext, processId)

        ensureChannel()
        runCatching { setForeground(foreground(title, 0, DownloadPhase.PREPARING.label)) }

        var lastPct = -1
        var lastPhase = DownloadPhase.PREPARING
        var lastNotifTime = 0L

        return try {
            engine.download(url, target, workDir, processId) { p, eta, phase, speed ->
                val pct = p.toInt().coerceIn(0, 100)
                val now = SystemClock.elapsedRealtime()
                if ((pct != lastPct || phase != lastPhase) && (now - lastNotifTime >= 400L || pct == 100)) {
                    lastPct = pct
                    lastPhase = phase
                    lastNotifTime = now
                    val etaStr = if (eta > 0) "${eta}s" else null
                    setProgressAsync(
                        workDataOf(
                            K_PROGRESS to pct,
                            K_PHASE to phase.name,
                            K_SPEED to (speed ?: ""),
                            K_ETA to (etaStr ?: ""),
                            K_TITLE to title,
                            K_THUMB to (thumb ?: ""),
                        )
                    )
                    val subtitle = listOfNotNull(phase.label, speed, etaStr?.let { "Restan $it" }).joinToString(" · ")
                    notifier().notify(notifId, notification(title, pct, subtitle))
                }
            }

            // Fase de exportación real a Galería / Música y borrado de caché
            setProgressAsync(
                workDataOf(
                    K_PROGRESS to 100,
                    K_PHASE to DownloadPhase.EXPORTING.name,
                    K_TITLE to title,
                )
            )
            val exportedUri = StorageExporter.exportAndClean(applicationContext, workDir, isAudio)
                ?: return Result.failure(workDataOf(K_ERROR to "No se encontró el archivo final para exportar"))

            Result.success(
                workDataOf(
                    K_PROGRESS to 100,
                    K_PHASE to DownloadPhase.COMPLETED.name,
                    K_EXPORTED_URI to exportedUri.toString(),
                    K_TITLE to title,
                    K_THUMB to (thumb ?: ""),
                )
            )
        } catch (e: DownloadException) {
            when (e.kind) {
                ErrorKind.CANCELED -> {
                    if (isStopped) {
                        Result.retry()
                    } else {
                        StorageExporter.cleanWorkDir(applicationContext, processId)
                        Result.failure()
                    }
                }
                ErrorKind.NETWORK, ErrorKind.FORBIDDEN -> Result.retry()
                else -> {
                    StorageExporter.cleanWorkDir(applicationContext, processId)
                    Result.failure(workDataOf(K_ERROR to "${e.kind}: ${e.message}"))
                }
            }
        }
    }

    /*
    override fun onStopped() {
        // Solo matamos el proceso en ejecución; si fue pausa por red, WorkManager reintentará sobre el .part
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }
    */

    private fun notifier() =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CH, "Descargas", NotificationManager.IMPORTANCE_LOW)
            notifier().createNotificationChannel(channel)
        }
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

    private fun foreground(title: String, pct: Int, subtitle: String) =
        if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                notifId,
                notification(title, pct, subtitle),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notifId, notification(title, pct, subtitle))
        }

    companion object {
        const val K_URL = "url"
        const val K_TITLE = "title"
        const val K_THUMB = "thumb"
        const val K_TARGET_JSON = "target_json"
        const val K_PROGRESS = "progress"
        const val K_PHASE = "phase"
        const val K_SPEED = "speed"
        const val K_ETA = "eta"
        const val K_EXPORTED_URI = "exported_uri"
        const val K_ERROR = "error"
        private const val CH = "downloads"
    }
}

object Downloads {
    fun enqueue(
        ctx: Context,
        url: String,
        target: Target,
        info: MediaInfo? = null,
    ): UUID {
        val data = workDataOf(
            DownloadWorker.K_URL to url,
            DownloadWorker.K_TITLE to (info?.title ?: "Descarga de ImaLoader"),
            DownloadWorker.K_THUMB to (info?.thumbnail ?: ""),
            DownloadWorker.K_TARGET_JSON to TargetCodec.encode(target),
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
        WorkManager.getInstance(ctx).enqueue(req)
        return req.id
    }

    fun cancelAndClean(ctx: Context, workId: UUID) {
        WorkManager.getInstance(ctx).cancelWorkById(workId)
        StorageExporter.cleanWorkDir(ctx, workId.toString())
    }
}