package com.ima.imaloader

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun VideoSplashScreen(
    onVideoFinished: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity

    // 1. Ocultar la barra de estado y de navegación (Modo inmersivo)
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 2. Instanciar y configurar ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Cargar el video desde la carpeta res/raw
            val videoUri = Uri.parse("android.resource://${context.packageName}/raw/imaloadervid")
            setMediaItem(MediaItem.fromUri(videoUri))
            
            playWhenReady = true // Autoplay
            prepare()
            
            // Si el video termina de forma natural
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onVideoFinished()
                    }
                }
            })
        }
    }

    // 3. Monitorear el progreso para cortar antes de la pantalla blanca final
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(100)
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            
            // AJUSTE: Corta el video 1500 milisegundos (1.5 segundos) antes de que termine.
            // Sube o baja este número para cortar exactamente en la parte donde sale el nombre.
            val tiempoParaCortarAlFinal = 1500L 
            
            if (dur > 0 && pos > 0 && dur - pos <= tiempoParaCortarAlFinal) {
                onVideoFinished()
                break
            }
        }
    }

    // 3. Gestionar el ciclo de vida del reproductor y restaurar el sistema UI
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release() // Liberar recursos
            
            // Restaurar las barras del sistema al salir del Splash
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 4. UI: Mostrar el video a pantalla completa
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // No mostrar controles de reproducción
                    // ZOOM expandirá el video para cubrir la pantalla (puede recortar los bordes). 
                    // Si prefieres ver todo el video sin recortes y con bandas negras, usa RESIZE_MODE_FIT
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM 
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
