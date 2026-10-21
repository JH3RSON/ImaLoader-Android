package com.ima.imaloader

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlatformLoginScreen(platform: Platform, onDone: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val loginUrl = platform.loginUrl ?: run {
        onDone(false)
        return
    }

    val webView = remember {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Quita la marca "; wv" para que Google/X no bloqueen el login por seguridad
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
            loadUrl(loginUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { webView }
        )
        Button(
            onClick = {
                // En YouTube congelamos la página en robots.txt antes de extraer para que no rote la cookie
                if (platform == Platform.YOUTUBE) {
                    webView.loadUrl("https://www.youtube.com/robots.txt")
                }
                val saved = PlatformCookies.saveFromWebView(ctx, platform)
                onDone(saved)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Ya inicié sesión en ${platform.label}")
        }
    }
}
