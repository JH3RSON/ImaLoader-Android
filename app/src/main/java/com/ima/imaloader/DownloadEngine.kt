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
            ErrorKind.EXTRACTOR
        )

        private val SPEED_REGEX = Regex("""at\s+([0-9.]+[KMG]i?B/s)""")
        private val DOWNLOADED_REGEX = Regex("""of\s+~?([0-9.]+[KMG]i?B)""")
    }

    private fun cookieFor(url: String): File? {
        val p = Platform.detect(url)
        if (p == Platform.OTHER) return null
        return PlatformCookies.file(context, p).takeIf { it.exists() && it.length() > 0L }
    }

    private fun hasGoodQuality(info: MediaInfo): Boolean =
        info.formats.any { (it.height ?: 0) > 360 || it.isAudioOnly }

    suspend fun fetchInfo(rawUrl: String, cookies: File? = null, forceRefresh: Boolean = false): MediaInfo {
        val url = UrlNormalizer.normalize(rawUrl)
        if (!forceRefresh) {
            QuickPreview.getCachedInfo(url)?.let { return it }
        }
        val info = if (Platform.detect(url) == Platform.YOUTUBE) {
            fetchInfoYoutube(url, cookies)
        } else {
            fetchInfoOther(url, cookies)
        }
        QuickPreview.putCachedInfo(url, info)
        return info
    }

    private suspend fun fetchInfoYoutube(url: String, cookies: File? = null): MediaInfo =
        withContext(Dispatchers.IO) {
            val ck = cookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)
            val isYtMusic = "music.youtube.com" in url.lowercase()

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
            val baseOrder = if (isYtMusic) {
                listOf("youtube:player_client=default,-android_vr", "") + ANON_CLIENTS
            } else {
                listOfNotNull(saved) + ANON_CLIENTS
            }

            for (clientArgs in baseOrder.distinct()) {
                try {
                    val info = InfoParser.parse(runInfo(url, null, clientArgs), url)
                    if (info.formats.isNotEmpty()) {
                        if (hasGoodQuality(info)) {
                            if (!isYtMusic) prefs.edit().putString(KEY_YT_CLIENT, clientArgs).apply()
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
            val p = Platform.detect(url)
            val session = explicitCookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)

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
                    if (e.kind !in RETRYABLE_ERRORS) throw e
                }
            }

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
            addOption("--no-cache-dir")
            addOption("--socket-timeout", "15")
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
        onProgress: (Float, Long, DownloadPhase, String?, String?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        outDir.mkdirs()

        val url = UrlNormalizer.normalize(rawUrl)
        val platform = Platform.detect(url)
        val ck = cookies?.takeIf { it.exists() && it.length() > 0L } ?: cookieFor(url)
        val musicJson = (target as? Target.Audio)?.infoJsonPath
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.length() > 0L }

        try {
            // 1) Si tenemos el JSON enriquecido con iTunes + YouTube Music, intentamos primero con --load-info-json
            if (musicJson != null) {
                try {
                    val req = buildRequest(url, target, outDir, ck, "", musicJson)
                    executeWithPhase(req, processId, onProgress)
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Si el stream pre-analizado caducó, continúa abajo con el método normal
                }
            }

            // 2) Ejecución normal o respaldo si el JSON caducó
            if (platform == Platform.YOUTUBE) {
                downloadYoutube(url, target, outDir, processId, ck, onProgress)
            } else {
                downloadOther(url, platform, target, outDir, processId, ck, onProgress)
            }
        } finally {
            musicJson?.delete()
        }
    }

    private fun downloadYoutube(
        url: String,
        target: Target,
        outDir: File,
        processId: String,
        ck: File?,
        onProgress: (Float, Long, DownloadPhase, String?, String?) -> Unit,
    ) {
        val savedClient = prefs.getString(KEY_YT_CLIENT, null)
        val isYtMusic = "music.youtube.com" in url.lowercase()
        val attempts: List<Pair<File?, String>> = if (ck != null) {
            listOf(
                ck to "",
                null to (savedClient ?: "youtube:player_client=default,-android_vr")
            )
        } else if (isYtMusic) {
            listOf(
                null to "youtube:player_client=default,-android_vr",
                null to "",
                null to "youtube:player_client=tv"
            )
        } else {
            (listOfNotNull(savedClient) + listOf(
                "youtube:player_client=default,-android_vr",
                "youtube:player_client=tv"
            )).distinct().map { null to it }
        }

        var lastError: DownloadException? = null
        for ((cookieFile, clientArgs) in attempts) {
            val req = buildRequest(url, target, outDir, cookieFile, clientArgs, null)
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
        onProgress: (Float, Long, DownloadPhase, String?, String?) -> Unit,
    ) {
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
            val req = buildRequest(url, directTargetFix, outDir, ck, "", null)
            try {
                executeWithPhase(req, processId, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val classified = classify(e)
                lastError = classified
                if (classified.kind !in RETRYABLE_ERRORS) throw classified
            }
        }

        if (platform == Platform.INSTAGRAM) {
            val resolved = InstagramResolver.resolveAnonymous(url)?.directUrl
            if (resolved != null) {
                val fallbackTarget = when (target) {
                    is Target.Video -> target.copy(formatId = null, formatHasAudio = true, mp4 = true)
                    is Target.Audio -> target.copy(formatId = null)
                }
                val req = buildRequest(resolved, fallbackTarget, outDir, null, "", null)
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
        onProgress: (Float, Long, DownloadPhase, String?, String?) -> Unit,
    ) {
        YoutubeDL.getInstance().execute(req, processId) { progress, eta, line ->
            val l = line.orEmpty()
            val phase = when {
                "[Merger]" in l || "[ExtractAudio]" in l || "[EmbedThumbnail]" in l ||
                    "[Metadata]" in l || "[ThumbnailsConvertor]" in l || "[EmbedSubtitle]" in l -> DownloadPhase.MERGING
                progress > 0f -> DownloadPhase.DOWNLOADING
                else -> DownloadPhase.PREPARING
            }
            val speed = SPEED_REGEX.find(l)?.groupValues?.getOrNull(1)
            val totalSize = DOWNLOADED_REGEX.find(l)?.groupValues?.getOrNull(1)
            onProgress(progress, eta, phase, speed, totalSize)
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
        infoJsonFile: File?,
    ): YoutubeDLRequest {
        val req = if (infoJsonFile != null) {
            YoutubeDLRequest(emptyList()).apply {
                addOption("--load-info-json", infoJsonFile.absolutePath)
            }
        } else {
            YoutubeDLRequest(url)
        }

        return req.apply {
            addOption("--no-playlist")
            addOption("--playlist-items", "1")
            addOption("--no-cache-dir")

            val template = if (target is Target.Audio && target.music) {
                "%(artist,uploader)s - %(track,title).100B.%(ext)s"
            } else {
                "%(title).120B [%(id)s].%(ext)s"
            }
            addOption("-o", "${outDir.absolutePath}/$template")

            addOption("--retries", "10")
            addOption("--fragment-retries", "10")
            addOption("--concurrent-fragments", "3")
            addOption("--socket-timeout", "20")
            addOption("--no-mtime")

            ck?.let { addOption("--cookies", it.absolutePath) }
            if (clientArgs.isNotEmpty() && infoJsonFile == null) {
                addOption("--extractor-args", clientArgs)
            }

            when (target) {
                is Target.Video -> {
                    val h = target.maxHeight?.let { "[height<=$it]" }.orEmpty()
                    val generic = "bv*$h+ba/b$h"
                    val id = target.formatId
                    val aid = target.audioFormatId
                    val audioPart = if (target.mp4 && !target.keepOriginalContainer) "ba[ext=m4a]/ba" else "ba"

                    val selector = when {
                        id == null -> generic
                        target.formatHasAudio -> "$id/$generic"
                        !target.withAudio -> "$id/bv*$h"
                        aid != null -> "$id+$aid/$id+$audioPart/$id/$generic"
                        else -> "$id+$audioPart/$id+ba/$id/$generic"
                    }
                    addOption("-f", selector)
                    if (id == null) addOption("-S", target.codec.sort)

                    if (!target.keepOriginalContainer) {
                        val merges = !target.formatHasAudio && target.withAudio
                        when {
                            merges -> addOption("--merge-output-format", if (target.mp4) "mp4" else "mkv")
                            target.mp4 -> addOption("--remux-video", "mp4")
                        }
                    }

                    addOption("--embed-metadata")
                    if (target.embedSubs) {
                        addOption("--embed-subs")
                        addOption("--sub-langs", "all,-live_chat")
                    }
                    if (target.saveThumbnail) {
                        addOption("--write-thumbnail")
                        addOption("--convert-thumbnails", "jpg")
                    }
                }

                is Target.Audio -> {
                    val sel = when {
                        target.formatId != null && target.formatId != "ig_direct_mp4" -> "${target.formatId}/ba/b"
                        else -> "ba/b"
                    }
                    addOption("-f", sel)
                    addOption("-x")
                    addOption("--audio-format", target.format)
                    addOption("--audio-quality", target.quality)

                    if (target.embedMetadata || target.music) {
                        addOption("--embed-metadata")
                        addOption("--parse-metadata", "%(artist,uploader)s:%(meta_artist)s - Topic$")
                        addOption("--parse-metadata", ":(?P<meta_synopsis>)")
                        addOption("--parse-metadata", ":(?P<meta_description>)")
                    }

                    if (target.music) {
                        addOption("--embed-thumbnail")
                        addOption("--convert-thumbnails", "png")
                        addOption(
                            "--ppa",
                            "ThumbnailsConvertor+ffmpeg_o:-c:v png -vf crop=ih:ih"
                        )
                    } else if (target.embedCover && target.format in listOf("mp3", "m4a", "opus", "flac", "best")) {
                        addOption("--embed-thumbnail")
                        addOption("--convert-thumbnails", "jpg")
                    }
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