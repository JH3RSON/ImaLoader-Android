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

data class ExportedMediaResult(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

object StorageExporter {

    private const val ROOT_WORK_DIR = "dl_work"
    private const val SUBFOLDER = "ImaLoader"

    fun workDirFor(context: Context, processId: String): File =
        File(File(context.applicationContext.cacheDir, ROOT_WORK_DIR), processId)

    fun cleanWorkDir(context: Context, processId: String) {
        runCatching { workDirFor(context, processId).deleteRecursively() }
    }

    fun purgeStaleCache(context: Context) {
        StorageGuard.clean(context)
    }

    suspend fun exportAndClean(
        context: Context,
        workDir: File,
        isAudio: Boolean,
        exportSeparateThumbnail: Boolean = false,
    ): ExportedMediaResult? = withContext(Dispatchers.IO) {
        val mediaFile = findCompletedMediaFile(workDir) ?: return@withContext null
        val sizeBytes = mediaFile.length()
        val ext = mediaFile.extension.lowercase()
        val mime = resolveMimeType(ext, isAudio)
        val fileName = mediaFile.name

        try {
            if (exportSeparateThumbnail) {
                findSeparateImageFile(workDir)?.let { img ->
                    runCatching { moveImageToPictures(context.applicationContext, img) }
                }
            }
            val uri = moveFileToMediaStore(context.applicationContext, mediaFile, mime, isAudio)
                ?: return@withContext null
            ExportedMediaResult(
                uri = uri,
                fileName = fileName,
                mimeType = mime,
                sizeBytes = sizeBytes,
            )
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private fun findCompletedMediaFile(workDir: File): File? {
        val ignoredExts = setOf("part", "ytdl", "temp", "json", "jpg", "jpeg", "webp", "png", "vtt", "srt")
        return workDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase() !in ignoredExts }
            ?.maxByOrNull { it.length() }
    }

    private fun findSeparateImageFile(workDir: File): File? {
        val imageExts = setOf("jpg", "jpeg", "png", "webp")
        return workDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase() in imageExts }
            ?.maxByOrNull { it.length() }
    }

    private fun moveFileToMediaStore(
        context: Context,
        source: File,
        mime: String,
        isAudio: Boolean,
    ): Uri? {
        val ext = source.extension.lowercase()
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

    private fun moveImageToPictures(context: Context, source: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SUBFOLDER")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            source.delete()
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