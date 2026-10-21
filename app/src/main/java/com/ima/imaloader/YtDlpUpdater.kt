package com.ima.imaloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YtDlpUpdater {
    /** Devuelve un texto para mostrar en Ajustes, ej: "yt-dlp 2026.10.xx (DONE)" */
    suspend fun update(ctx: Context): String = withContext(Dispatchers.IO) {
        ImaApp.ensureInitialized(ctx)
        val appCtx = ctx.applicationContext
        val result = runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(appCtx, YoutubeDL.UpdateChannel.NIGHTLY)
        }
        val version = runCatching { YoutubeDL.getInstance().version(appCtx) }
            .getOrNull() ?: "versión desconocida"

        if (result.isFailure) {
            "yt-dlp $version (no se pudo actualizar: ${result.exceptionOrNull()?.message})"
        } else {
            val status = result.getOrNull()?.name ?: "listo"
            "yt-dlp $version ($status)"
        }
    }
}
