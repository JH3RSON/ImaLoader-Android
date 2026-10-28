package com.ima.imaloader.search

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ima.imaloader.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ResultadoVideo(
    val id: String,
    val titulo: String,
    val canal: String,
    val miniatura: String,
    val duracionSegundos: Int,
    val duracionTexto: String,
    val vistasTexto: String,
    val urlPagina: String,
    val esMusica: Boolean = false
)

data class StreamDirecto(
    val url: String,
    val userAgent: String,
    val headers: Map<String, String>,
    val esSoloAudio: Boolean
)

private val cacheStreamDirecto = ConcurrentHashMap<String, StreamDirecto>()

class PaginadorBusqueda<T>(
    private val scope: CoroutineScope,
    private val tamanoPagina: Int = 15,
    private val limiteTotal: Int = 150,
    private val idDe: (T) -> String,
    private val pedir: suspend (consulta: String, esMusica: Boolean, desde: Int, cantidad: Int) -> List<T>
) {
    val items = mutableStateListOf<T>()
    var cargando by mutableStateOf(false)
        private set
    var terminado by mutableStateOf(false)
        private set
    var hayError by mutableStateOf(false)
        private set

    private var consulta = ""
    private var modoMusica = false
    private var siguienteDesde = 0
    private var trabajo: Job? = null
    private val vistos = HashSet<String>()

    /** Llamar cuando el usuario hace una búsqueda NUEVA o cambia entre YouTube y YouTube Music */
    fun buscar(nuevaConsulta: String, esMusica: Boolean = modoMusica) {
        val limpia = nuevaConsulta.trim()
        trabajo?.cancel()
        consulta = limpia
        modoMusica = esMusica
        items.clear()
        vistos.clear()
        siguienteDesde = 0
        terminado = false
        hayError = false
        cargando = false
        if (consulta.isNotEmpty()) {
            cargarMas()
        }
    }

    /** Pide el siguiente bloque de resultados para el scroll infinito */
    fun cargarMas() {
        if (cargando || terminado || consulta.isEmpty()) return
        cargando = true
        hayError = false
        val consultaActual = consulta
        val musicaActual = modoMusica

        trabajo = scope.launch {
            try {
                val pagina = withContext(Dispatchers.IO) {
                    pedir(consultaActual, musicaActual, siguienteDesde, tamanoPagina)
                }
                if (consultaActual != consulta || musicaActual != modoMusica) return@launch

                if (pagina.isEmpty()) {
                    terminado = true
                } else {
                    siguienteDesde += pagina.size
                    val nuevos = pagina.filter { vistos.add(idDe(it)) }
                    items.addAll(nuevos)
                    if (siguienteDesde >= limiteTotal) {
                        terminado = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hayError = true
            } finally {
                if (consultaActual == consulta && musicaActual == modoMusica) {
                    cargando = false
                }
            }
        }
    }
}

/** Detector de final de lista corregido: no se bloquea si falla un intento previo */
@Composable
fun LazyListState.AlLlegarAlFinal(
    margen: Int = 4,
    cargando: Boolean,
    terminado: Boolean,
    accion: () -> Unit
) {
    val accionActual by rememberUpdatedState(accion)
    LaunchedEffect(this, cargando, terminado) {
        snapshotFlow {
            val info = layoutInfo
            val total = info.totalItemsCount
            val ultimoVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && !cargando && !terminado && ultimoVisible >= (total - 1 - margen)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { accionActual() }
    }
}

/** Función que busca en YouTube o YouTube Music por bloques usando yt-dlp */
suspend fun buscarEnYoutube(
    consulta: String,
    esMusica: Boolean,
    desde: Int,
    cantidad: Int
): List<ResultadoVideo> {
    val hasta = desde + cantidad
    val queryFinal = if (esMusica) "$consulta topic OR official audio" else consulta
    val pedido = YoutubeDLRequest("ytsearch$hasta:$queryFinal").apply {
        addOption("--flat-playlist")
        addOption("--dump-json")
        addOption("--no-warnings")
        addOption("--playlist-items", "${desde + 1}-$hasta")
    }

    val salida = YoutubeDL.getInstance().execute(pedido).out
    return salida.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("{") && it.endsWith("}") }
        .mapNotNull { linea ->
            try {
                val j = JSONObject(linea)
                val id = j.optString("id")
                if (id.isEmpty()) return@mapNotNull null

                val duracionSeg = j.optDouble("duration", 0.0).toInt()
                val vistas = j.optLong("view_count", -1L)

                ResultadoVideo(
                    id = id,
                    titulo = j.optString("title", "Sin título"),
                    canal = j.optString("channel").ifEmpty { j.optString("uploader", "YouTube") },
                    miniatura = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    duracionSegundos = duracionSeg,
                    duracionTexto = formatearDuracion(duracionSeg),
                    vistasTexto = formatearVistas(vistas),
                    urlPagina = if (esMusica) "https://music.youtube.com/watch?v=$id" else "https://www.youtube.com/watch?v=$id",
                    esMusica = esMusica
                )
            } catch (e: Exception) {
                null
            }
        }.toList()
}

/** Extrae el enlace directo MP4/M4A ultra-rápido usando banderas de optimización de yt-dlp y caché en memoria */
suspend fun obtenerStreamDirecto(videoId: String, esMusica: Boolean): StreamDirecto? = withContext(Dispatchers.IO) {
    val claveCache = "$videoId|$esMusica"
    cacheStreamDirecto[claveCache]?.let { return@withContext it }

    try {
        val urlVideo = "https://www.youtube.com/watch?v=$videoId"
        val request = YoutubeDLRequest(urlVideo).apply {
            if (esMusica) {
                addOption("-f", "140/ba[ext=m4a]/18/b[ext=mp4]")
            } else {
                addOption("-f", "18/22/b[ext=mp4][vcodec!*=av01]/b")
            }
            addOption("--extractor-args", "youtube:player_client=android,web")
            addOption("--youtube-skip-dash-manifest")
            addOption("--youtube-skip-hls-manifest")
            addOption("--no-check-certificates")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--dump-json")
        }

        val salida = YoutubeDL.getInstance().execute(request).out
        val jsonLinea = salida.lineSequence().firstOrNull { it.trim().startsWith("{") } ?: return@withContext null
        val json = JSONObject(jsonLinea)

        val streamUrl = json.optString("url").ifEmpty {
            json.optJSONArray("requested_formats")?.optJSONObject(0)?.optString("url") ?: ""
        }
        if (streamUrl.isEmpty()) return@withContext null

        val headersMap = mutableMapOf<String, String>()
        val headersJson = json.optJSONObject("http_headers")
        if (headersJson != null) {
            val keys = headersJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                headersMap[k] = headersJson.optString(k)
            }
        }

        val userAgent = headersMap["User-Agent"]
            ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        if (!headersMap.containsKey("User-Agent")) {
            headersMap["User-Agent"] = userAgent
        }

        val vcodec = json.optString("vcodec", "")
        val soloAudio = vcodec == "none"

        val resultado = StreamDirecto(
            url = streamUrl,
            userAgent = userAgent,
            headers = headersMap,
            esSoloAudio = soloAudio
        )
        cacheStreamDirecto[claveCache] = resultado
        resultado
    } catch (e: Exception) {
        null
    }
}

/** Componente nativo de ExoPlayer (Media3) optimizado para inicio ultra-rápido de reproducción */
@OptIn(UnstableApi::class)
@Composable
private fun ReproductorNativoExoPlayer(
    datosStream: StreamDirecto,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(datosStream.url) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(datosStream.userAgent)
            setDefaultRequestProperties(datosStream.headers)
            setAllowCrossProtocolRedirects(true)
            setConnectTimeoutMs(5000)
            setReadTimeoutMs(5000)
        }
        val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(datosStream.url.toUri()))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 10000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("ExoPlayer", "Error reproduciendo video nativo: ${error.message}", error)
                        onError()
                    }
                })
            }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val frame = FrameLayout(ctx)
            val playerView = LayoutInflater.from(ctx).inflate(
                R.layout.item_player_view,
                frame,
                false
            ) as PlayerView
            playerView.apply {
                player = exoPlayer
                keepScreenOn = true
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        }
    )
}

/** Reproductor integrado al tema: intenta reproducción nativa directa en ExoPlayer y tiene vista web embebida limpia */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReproductorIntegrado(
    videoId: String,
    esMusica: Boolean,
    usarVistaWeb: Boolean,
    onCambiarAVistaWeb: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stream by remember(videoId, esMusica) { mutableStateOf<StreamDirecto?>(null) }
    var cargandoStream by remember(videoId, esMusica) { mutableStateOf(true) }
    var errorNativo by remember(videoId, esMusica) { mutableStateOf(false) }

    LaunchedEffect(videoId, esMusica, usarVistaWeb) {
        if (!usarVistaWeb) {
            cargandoStream = true
            errorNativo = false
            val resultado = obtenerStreamDirecto(videoId, esMusica)
            stream = resultado
            cargandoStream = false
            if (resultado == null) {
                errorNativo = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            usarVistaWeb || errorNativo -> {
                // Vista Web embebida (m.youtube.com o music.youtube.com)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }
                        }
                    },
                    update = { webView ->
                        val url = if (esMusica) "https://music.youtube.com/watch?v=$videoId" else "https://m.youtube.com/watch?v=$videoId"
                        if (webView.tag != url) {
                            webView.tag = url
                            webView.loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
            }

            cargandoStream -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            stream != null -> {
                ReproductorNativoExoPlayer(
                    datosStream = stream!!,
                    onError = {
                        errorNativo = true
                        onCambiarAVistaWeb()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun formatearDuracion(segundos: Int): String {
    if (segundos <= 0) return ""
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatearVistas(vistas: Long): String {
    if (vistas < 0) return ""
    return when {
        vistas >= 1_000_000 -> String.format(Locale.US, "%.1f M visualizaciones", vistas / 1_000_000.0)
        vistas >= 1_000 -> String.format(Locale.US, "%d K visualizaciones", vistas / 1_000)
        else -> "$vistas visualizaciones"
    }
}