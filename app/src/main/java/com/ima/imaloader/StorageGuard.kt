package com.ima.imaloader

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mantiene limpia la caché sin romper yt-dlp/FFmpeg ni borrar descargas en curso.
 *
 * REGLA DE ORO: NUNCA tocar context.filesDir ni context.noBackupFilesDir,
 * porque ahí se descomprimen Python, yt-dlp y FFmpeg.
 */
object StorageGuard {

    private const val MAX_CACHE_BYTES = 150L * 1024 * 1024          // Tope de caché: 150 MB
    private val ACTIVE_GRACE_MS = TimeUnit.MINUTES.toMillis(60)     // Intocable si se modificó hace < 60 min
    private val MAX_AGE_MS = TimeUnit.HOURS.toMillis(12)            // Basura general: 12 horas
    private val PARTIAL_AGE_MS = TimeUnit.DAYS.toMillis(3)          // Descargas pausadas (.part): 3 días
    private val PARTIAL_EXT = setOf("part", "ytdl", "temp", "tmp", "frag")
    private val SKIP_DIRS = setOf("image_cache")

    /**
     * @param isInUse devuelve true para archivos o carpetas de descargas activas/pausadas
     */
    fun clean(context: Context, isInUse: (File) -> Boolean = { false }) {
        runCatching {
            val appCtx = context.applicationContext
            val roots = listOfNotNull(appCtx.cacheDir, appCtx.externalCacheDir)
            val now = System.currentTimeMillis()

            // Limpia caché interna residual de yt-dlp si existiera en cacheDir
            File(appCtx.cacheDir, "yt-dlp").deleteRecursively()

            roots.forEach { root ->
                fun removableCandidates(): List<File> = root.walkTopDown()
                    .onEnter { dir -> dir.name !in SKIP_DIRS && !isInUse(dir) }
                    .filter { file ->
                        file.isFile &&
                            !isInUse(file) &&
                            // NUNCA borrar un archivo que se está descargando/modificando ahora mismo
                            (now - file.lastModified() > ACTIVE_GRACE_MS)
                    }
                    .toList()

                // 1) Borra archivos temporales viejos
                removableCandidates().forEach { f ->
                    val isPartial = f.extension.lowercase() in PARTIAL_EXT
                    val limit = if (isPartial) PARTIAL_AGE_MS else MAX_AGE_MS
                    if (now - f.lastModified() > limit) {
                        f.delete()
                    }
                }

                // 2) Si aún supera el tope, borra primero lo que NO sea un .part en pausa;
                //    y solo borra .part antiguos si sigue superando el límite.
                val remaining = removableCandidates().sortedWith(
                    compareBy<File> { it.extension.lowercase() in PARTIAL_EXT }
                        .thenBy { it.lastModified() }
                )
                var total = remaining.sumOf { it.length() }
                for (f in remaining) {
                    if (total <= MAX_CACHE_BYTES) break
                    total -= f.length()
                    f.delete()
                }

                // 3) Elimina carpetas temporales que hayan quedado vacías
                root.walkBottomUp()
                    .filter { dir ->
                        dir.isDirectory &&
                            dir != root &&
                            dir.name !in SKIP_DIRS &&
                            !isInUse(dir) &&
                            dir.list().isNullOrEmpty()
                    }
                    .forEach { it.delete() }
            }
        }
    }
}