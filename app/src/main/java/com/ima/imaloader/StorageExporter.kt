package com.ima.imaloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageExporter {

    private const val ROOT_WORK_DIR = "dl_work"
    private const val SUBFOLDER = "ImaLoader"

    /** Carpeta temporal aislada para cada proceso de descarga */
    fun workDirFor(context: Context, processId: String): File =
        File(File(context.applicationContext.cacheDir, ROOT_WORK_DIR), processId)

    /** Elimina por completo la carpeta temporal de una descarga terminada o cancelada */
    fun cleanWorkDir(context: Context, processId: String) {
        runCatching { workDirFor(context, processId).deleteRecursively() }
    }

    /** Se ejecuta al iniciar la app: borra restos de descargas viejas (>12h) y caché de yt-dlp */
    fun purgeStaleCache(context: Context) {
        runCatching {
            val appCtx = context.applicationContext
            val root = File(appCtx.cacheDir, ROOT_WORK_DIR)
            val cutoff = System.currentTimeMillis() - (12L * 3600L * 1000L)
            root.listFiles()?.forEach { dir ->
                if (dir.lastModified() < cutoff) dir.deleteRecursively()
            }
            // También limpiamos la carpeta interna de caché que yt-dlp haya podido crear
            File(appCtx.cacheDir, "yt-dlp").deleteRecursively()
            File(appCtx.filesDir, ".cache").deleteRecursively()
        }
    }

    /**
     * Mueve el archivo final desde la carpeta temporal hacia Música o Películas/Descargas
     * del teléfono y borra la carpeta temporal para dejar 0 bytes de basura.
     */
    suspend fun exportAndClean(
        context: Context,
        workDir: File,
        isAudio: Boolean,
    ): Uri? = withContext(Dispatchers.IO) {
        val mediaFile = findCompletedMediaFile(workDir) ?: return@withContext null
        val exportedUri = try {
            moveFileToMediaStore(context.applicationContext, mediaFile, isAudio)
        } finally {
            // Pase lo que pase tras exportar, borramos los archivos temporales
            runCatching { workDir.deleteRecursively() }
        }
        exportedUri
    }

    private fun findCompletedMediaFile(workDir: File): File? {
        val ignoredExts = setOf("part", "ytdl", "temp", "json", "jpg", "webp", "png")
        return workDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase() !in ignoredExts }
            ?.maxByOrNull { it.length() }
    }

    private fun moveFileToMediaStore(context: Context, source: File, isAudio: Boolean): Uri? {
        val ext = source.extension.lowercase()
        val mime = resolveMimeType(ext, isAudio)
        val displayName = source.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val (collection, relPath) = when {
                isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER"
                ext in setOf("mp4", "mkv", "webm", "mov") ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                        "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER"
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                    "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(collection, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out, bufferSize = 64 * 1024) }
                }
                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                source.delete()
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            )
            val targetDir = File(baseDir, SUBFOLDER).apply { mkdirs() }
            val destFile = File(targetDir, displayName)
            source.copyTo(destFile, overwrite = true)
            source.delete()
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mime), null)
            return Uri.fromFile(destFile)
        }
    }

    private fun resolveMimeType(ext: String, isAudio: Boolean): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "mp4" -> if (isAudio) "audio/mp4" else "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> if (isAudio) "audio/webm" else "video/webm"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (isAudio) "audio/*" else "video/*"
    }
}