package com.ima.imaloader.youtube

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val PLAYER_BASE_URL = "https://imaloader.local/"

@SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
@Composable
fun YouTubePlayerWebView(
    videoId: String,
    modifier: Modifier = Modifier
) {
    // key() fuerza recrear la WebView cuando cambia el video
    key(videoId) {
        PlayerWebViewInner(videoId, modifier)
    }
}

@Composable
private fun PlayerWebViewInner(videoId: String, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.apply {
                loadUrl("about:blank")
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val wv = WebView(context).apply {
                    setBackgroundColor(AndroidColor.BLACK)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        javaScriptCanOpenWindowsAutomatically = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val host = request?.url?.host ?: return true
                            val allowed = host == "imaloader.local" ||
                                    host.endsWith("youtube.com") ||
                                    host.endsWith("youtube-nocookie.com") ||
                                    host.endsWith("ytimg.com") ||
                                    host.endsWith("googlevideo.com") ||
                                    host.endsWith("google.com")
                            return !allowed
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                errorMessage = "No se pudo cargar el reproductor. Revisa tu conexión."
                            }
                        }
                    }
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(PlayerBridge { code ->
                        errorMessage = playerErrorText(code)
                    }, "AndroidPlayer")

                    loadDataWithBaseURL(
                        PLAYER_BASE_URL,
                        buildPlayerHtml(videoId),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
                webView = wv
                wv
            }
        )

        errorMessage?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = msg,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

private class PlayerBridge(private val onError: (Int) -> Unit) {
    @JavascriptInterface
    fun onPlayerError(code: Int) = onError(code)
}

private fun playerErrorText(code: Int) = when (code) {
    2   -> "Enlace de video inválido."
    5   -> "El reproductor HTML5 no está disponible en este dispositivo."
    100 -> "El video no existe o es privado."
    101, 150 -> "El propietario no permite reproducirlo embebido."
    else -> "No se pudo reproducir el video (código $code)."
}

private fun buildPlayerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
    #player { width:100%; height:100%; }
  </style>
</head>
<body>
  <div id="player"></div>
  <script src="https://www.youtube.com/iframe_api"></script>
  <script>
    var player;
    function onYouTubeIframeAPIReady() {
      player = new YT.Player('player', {
        videoId: '$videoId',
        host: 'https://www.youtube-nocookie.com',
        playerVars: {
          'playsinline': 1,
          'controls': 1,
          'rel': 0,
          'modestbranding': 1,
          'origin': '$PLAYER_BASE_URL',
          'enablejsapi': 1
        },
        events: {
          'onReady': function (e) { e.target.playVideo(); },
          'onError': function (e) {
            if (window.AndroidPlayer) window.AndroidPlayer.onPlayerError(e.data);
          }
        }
      });
    }
  </script>
</body>
</html>
""".trimIndent()
