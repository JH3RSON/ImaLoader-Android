package com.ima.imaloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadEngine(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_YT_CLIENT = "yt_client"

        private val ANON_CLIENTS = listOf(
            "youtube:player_client=default,-android_vr",
            "youtube:player_client=tv",
            "youtube:player_client=tv_simply",
            "youtube:player_client=mweb",
            "",
            "youtube:player_client=android_vr",
        )

        private val AUTH_CLIENTS = listOf(
            "",
            "youtube:player_client=tv,web_creator",
        )

        private val RETRYABLE_ERRORS = setOf(
            ErrorKind.LOGIN,
            ErrorKind.FORBIDDEN,
            ErrorKind.FORMAT,
            ErrorKind.EXTRACTOR,
            ErrorKind.RESTRICTED
        )

        private val SPEED_REGEX = Regex("""at\s+([0-9.]+[KMG]i?B/s)""")
    }

    private fun cookieFor(url: String): File? {
        val p = Platform.detect(url)
        if (p == Platform.OTHER) return null
        return PlatformCookies.file(context, p).takeIf { it.exists() && it.length() > 0L }
    }

    private fun hasGoodQuality(info: MediaInfo): Boolean =
        info.formats.any { (it.height ?: 0) > 360 || it.isAudioOnly }

    suspend fun fetchInfo(rawUrl: String, cookies: File? = null): MediaInfo {
        val url = UrlNormalizer.normalize(rawUrl)
        return if (Platform.detect(url) == Platform.YOUTUBE) {
            fetchInfoYoutube(url, cookies)
        } else {
            fetchInfoOther(url, cookies)
        }
    }

    private suspend fun fetchInfoYoutube(url: String, cookies: File? = null): MediaInfo =
        withContext(Dispatchers.IO) {
            ImaApp.ensureInitialized(context)
            val ck = cookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)

            var lastError: DownloadException? = null
            var lowResFallback: MediaInfo? = null

            if (ck != null) {
                for (clientArgs in AUTH_CLIENTS) {
                    try {
                        val info = InfoParser.parse(runInfo(url, ck, clientArgs), url)
                        if (info.formats.isNotEmpty()) return@withContext info
                    } catch (e: DownloadException) {
                        lastError = e
                        if (e.kind !in RETRYABLE_ERRORS) throw e
                    }
                }
            }

            val saved = prefs.getString(KEY_YT_CLIENT, null)
            val anonOrder = (listOfNotNull(saved) + ANON_CLIENTS).distinct()

            for (clientArgs in anonOrder) {
                try {
                    val info = InfoParser.parse(runInfo(url, null, clientArgs), url)
                    if (info.formats.isNotEmpty()) {
                        if (hasGoodQuality(info)) {
                            prefs.edit().putString(KEY_YT_CLIENT, clientArgs).apply()
                            return@withContext info
                        } else if (lowResFallback == null) {
                            lowResFallback = info
                        }
                    }
                } catch (e: DownloadException) {
                    lastError = e
                    if (clientArgs == saved) {
                        prefs.edit().remove(KEY_YT_CLIENT).apply()
                    }
                    if (e.kind !in RETRYABLE_ERRORS) throw e
                }
            }

            lowResFallback?.let { return@withContext it }
            throw lastError ?: DownloadException(ErrorKind.LOGIN, "YouTube pide verificación de sesión")
        }

    private suspend fun fetchInfoOther(url: String, explicitCookies: File? = null): MediaInfo =
        withContext(Dispatchers.IO) {
            ImaApp.ensureInitialized(context)
            val p = Platform.detect(url)
            val session = explicitCookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)

            // En Instagram probamos sin sesión primero para no depender de cookies salvo que sea privado
            val attempts: List<File?> = when {
                session == null -> listOf(null)
                p in listOf(Platform.FACEBOOK, Platform.X) -> listOf(session, null)
                else -> listOf(null, session)
            }

            var lastError: DownloadException? = null
            for (ck in attempts) {
                try {
                    val info = InfoParser.parse(runInfo(url, ck, ""), url)
                    if (info.formats.isNotEmpty()) return@withContext info
                    lastError = DownloadException(ErrorKind.EXTRACTOR, "Sin formatos disponibles")
                } catch (e: DownloadException) {
                    lastError = e
                    if (p != Platform.INSTAGRAM && e.kind !in RETRYABLE_ERRORS) throw e
                }
            }

            // Rescate automático sin login para Instagram si yt-dlp fue bloqueado por el detector de bots
            if (p == Platform.INSTAGRAM) {
                InstagramResolver.resolveAnonymous(url)?.let { return@withContext it }
            }

            throw lastError ?: DownloadException(ErrorKind.UNKNOWN, "No se pudo analizar el enlace")
        }

    private fun runInfo(url: String, ck: File?, clientArgs: String): String {
        val req = YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--playlist-items", "1")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            ck?.let { addOption("--cookies", it.absolutePath) }
            if (clientArgs.isNotEmpty()) addOption("--extractor-args", clientArgs)
        }
        return try {
            YoutubeDL.getInstance().execute(req).out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw classify(e)
        }
    }

    suspend fun download(
        rawUrl: String,
        target: Target,
        outDir: File,
        processId: String,
        cookies: File? = null,
        onProgress: (Float, Long, DownloadPhase, String?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        ImaApp.ensureInitialized(context)
        outDir.mkdirs()

        val url = UrlNormalizer.normalize(rawUrl)
        val platform = Platform.detect(url)
        val ck = cookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)

        if (platform == Platform.YOUTUBE) {
            downloadYoutube(url, target, outDir, processId, ck, onProgress)
        } else {
            downloadOther(url, platform, target, outDir, processId, ck, onProgress)
        }
    }

    private fun downloadYoutube(
        url: String,
        target: Target,
        outDir: File,
        processId: String,
        ck: File?,
        onProgress: (Float, Long, DownloadPhase, String?) -> Unit,
    ) {
        val savedClient = prefs.getString(KEY_YT_CLIENT, null)
        val attempts: List<Pair<File?, String>> = if (ck != null) {
            listOf(
                ck to "",
                null to (savedClient ?: "youtube:player_client=default,-android_vr")
            )
        } else {
            (listOfNotNull(savedClient) + listOf(
                "youtube:player_client=default,-android_vr",
                "youtube:player_client=tv"
            )).distinct().map { null to it }
        }

        var lastError: DownloadException? = null
        for ((cookieFile, clientArgs) in attempts) {
            val req = buildRequest(url, target, outDir, cookieFile, clientArgs)
            try {
                executeWithPhase(req, processId, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val classified = classify(e)
                lastError = classified
                if (cookieFile == null && clientArgs == savedClient && classified.kind in RETRYABLE_ERRORS) {
                    prefs.edit().remove(KEY_YT_CLIENT).apply()
                }
                if (classified.kind !in RETRYABLE_ERRORS) throw classified
            }
        }
        throw lastError ?: DownloadException(ErrorKind.UNKNOWN, "Error al descargar de YouTube")
    }

    private suspend fun downloadOther(
        url: String,
        platform: Platform,
        target: Target,
        outDir: File,
        processId: String,
        session: File?,
        onProgress: (Float, Long, DownloadPhase, String?) -> Unit,
    ) {
        // Si ya viene del rescate directo de Instagram (id == "ig_direct_mp4"), bajamos el enlace directo
        val directTargetFix = if (target is Target.Video && target.formatId == "ig_direct_mp4") {
            target.copy(formatId = null, formatHasAudio = true)
        } else target

        val attempts: List<File?> = when {
            session == null -> listOf(null)
            platform in listOf(Platform.FACEBOOK, Platform.X) -> listOf(session, null)
            else -> listOf(null, session)
        }

        var lastError: DownloadException? = null
        for (ck in attempts) {
            val req = buildRequest(url, directTargetFix, outDir, ck, "")
            try {
                executeWithPhase(req, processId, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val classified = classify(e)
                lastError = classified
                if (platform != Platform.INSTAGRAM && classified.kind !in RETRYABLE_ERRORS) throw classified
            }
        }

        // Si yt-dlp falló al descargar de Instagram por bloqueo de bots, descargamos desde la URL directa resuelta
        if (platform == Platform.INSTAGRAM) {
            val resolved = InstagramResolver.resolveAnonymous(url)?.directUrl
            if (resolved != null) {
                val fallbackTarget = when (target) {
                    is Target.Video -> target.copy(formatId = null, formatHasAudio = true, mp4 = true)
                    is Target.Audio -> target.copy(formatId = null)
                }
                val req = buildRequest(resolved, fallbackTarget, outDir, null, "")
                try {
                    executeWithPhase(req, processId, onProgress)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = classify(e)
                }
            }
        }

        throw lastError ?: DownloadException(ErrorKind.UNKNOWN, "Error al descargar")
    }

    private fun executeWithPhase(
        req: YoutubeDLRequest,
        processId: String,
        onProgress: (Float, Long, DownloadPhase, String?) -> Unit,
    ) {
        YoutubeDL.getInstance().execute(req, processId) { progress, eta, line ->
            val l = line.orEmpty()
            val phase = when {
                "[Merger]" in l || "[ExtractAudio]" in l || "[EmbedThumbnail]" in l ||
                    "[Metadata]" in l || "[ThumbnailsConvertor]" in l -> DownloadPhase.MERGING
                progress > 0f -> DownloadPhase.DOWNLOADING
                else -> DownloadPhase.PREPARING
            }
            val speed = SPEED_REGEX.find(l)?.groupValues?.getOrNull(1)
            onProgress(progress, eta, phase, speed)
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        StorageExporter.cleanWorkDir(context, processId)
    }

    private fun buildRequest(
        url: String,
        target: Target,
        outDir: File,
        ck: File?,
        clientArgs: String,
    ) = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--playlist-items", "1")
        addOption("--no-cache-dir")
        addOption("--restrict-filenames") // Evita errores de FFmpeg con emojis o barras en títulos
        addOption("-o", "${outDir.absolutePath}/%(title).100B [%(id)s].%(ext)s")
        addOption("--retries", "10")
        addOption("--fragment-retries", "10")
        addOption("--concurrent-fragments", "3") // Acelera descargas DASH de YouTube sin saturar RAM
        addOption("--socket-timeout", "20")
        addOption("--no-mtime")
        addOption("--embed-metadata")
        addOption("--parse-metadata", ":(?P<meta_synopsis>)")
        addOption("--parse-metadata", ":(?P<meta_description>)")

        ck?.let { addOption("--cookies", it.absolutePath) }
        if (clientArgs.isNotEmpty()) {
            addOption("--extractor-args", clientArgs)
        }

        when (target) {
            is Target.Video -> {
                val h = target.maxHeight?.let { "[height<=$it]" }.orEmpty()
                val generic = "bv*$h+ba/b$h"
                val id = target.formatId
                val aid = target.audioFormatId
                val audioPart = if (target.mp4) "ba[ext=m4a]/ba" else "ba"

                val selector = when {
                    id == null -> generic
                    target.formatHasAudio -> "$id/$generic"
                    !target.withAudio -> "$id/bv*$h"
                    aid != null -> "$id+$aid/$id+$audioPart/$id/$generic"
                    else -> "$id+$audioPart/$id+ba/$id/$generic"
                }
                addOption("-f", selector)
                if (id == null) addOption("-S", target.codec.sort)

                val merges = !target.formatHasAudio && target.withAudio
                when {
                    merges -> addOption("--merge-output-format", if (target.mp4) "mp4" else "mkv")
                    target.mp4 -> addOption("--remux-video", "mp4")
                }
            }
            is Target.Audio -> {
                val fid = target.formatId
                val selector = if (fid != null && fid != "ig_direct_mp4") {
                    "$fid/ba[acodec^=opus]/ba/b"
                } else {
                    "ba[acodec^=opus]/ba/b" // Prioriza Opus 251 (160 kbps, 20 kHz) por defecto
                }
                addOption("-f", selector)
                addOption("-x")
                addOption("--audio-format", target.format)
                addOption("--audio-quality", target.quality)

                // Incrustar siempre portada en alta calidad (JPG q:v 2)
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                if (target.squareCover) {
                    // Recorta la portada a un cuadrado 1:1 perfecto centrado estilo álbum oficial
                    addOption(
                        "--ppa",
                        "ThumbnailsConvertor+ffmpeg_o:-c:v mjpeg -vf crop='min(iw\\,ih):min(iw\\,ih)' -q:v 2"
                    )
                }
            }
        }
    }

    private fun classify(e: Exception): DownloadException {
        if (e is YoutubeDL.CanceledException) {
            return DownloadException(ErrorKind.CANCELED, "Descarga cancelada o en pausa", e)
        }
        val m = e.message.orEmpty()
        val low = m.lowercase()
        val kind = when {
            "no space left" in low -> ErrorKind.STORAGE
            "isn't available to everyone" in low || "certain audiences" in low ||
                "private account" in low || "this video is private" in low -> ErrorKind.RESTRICTED
            "sign in" in low || "not a bot" in low || "login_required" in low ||
                "login" in low || "inappropriate for some users" in low ||
                "cookies are no longer valid" in low || "authentication" in low ||
                "empty media response" in low -> ErrorKind.LOGIN
            "403" in m || "http error 403" in low -> ErrorKind.FORBIDDEN
            "requested format is not available" in low -> ErrorKind.FORMAT
            "ffmpeg" in low || "postprocessing" in low -> ErrorKind.PROCESSING
            "no video formats" in low || "unable to extract" in low ||
                "cannot parse data" in low -> ErrorKind.EXTRACTOR
            "unsupported url" in low -> ErrorKind.UNSUPPORTED
            listOf(
                "timed out", "unable to download", "connection", "network is unreachable",
                "temporary failure", "resolve", "reset by peer", "eof"
            ).any { it in low } -> ErrorKind.NETWORK
            else -> ErrorKind.UNKNOWN
        }
        return DownloadException(kind, m.take(300), e)
    }
}