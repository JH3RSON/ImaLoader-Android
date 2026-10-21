package com.ima.imaloader.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.sin

// Los 6 temas solicitados
enum class AppThemeMode(val title: String, val subtitle: String) {
    LIGHT("Claro", "Estética iOS luminosa"),
    DARK("Oscuro", "Estética iOS profunda"),
    SYSTEM("Automático", "Adaptado al sistema (Claro / Oscuro)"),
    MATERIAL("Material", "Colores dinámicos Material You"),
    IMALOADER("IMALOADER", "Clásico v2.0 con rebote DVD en fondo"),
    FRUTIGER_AERO("Frutiger Aero", "Cristal líquido, agua y burbujas")
}

@Stable
data class ImaPalette(
    val mode: AppThemeMode,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val inputFieldBg: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSubtle: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isGlassTheme: Boolean
)

val LocalImaPalette = staticCompositionLocalOf<ImaPalette> {
    error("ImaPalette no inicializada")
}

@Composable
fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }
}

@Composable
fun ProvideImaTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val reduceMotion = rememberReduceMotionEnabled()

    val targetPalette = remember(themeMode, systemDark) {
        when (themeMode) {
            AppThemeMode.LIGHT -> iosLightPalette(AppThemeMode.LIGHT)
            AppThemeMode.DARK -> iosDarkPalette(AppThemeMode.DARK)
            AppThemeMode.SYSTEM -> if (systemDark) iosDarkPalette(AppThemeMode.SYSTEM) else iosLightPalette(AppThemeMode.SYSTEM)
            AppThemeMode.MATERIAL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val dyn = if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    ImaPalette(
                        mode = AppThemeMode.MATERIAL,
                        background = dyn.background,
                        surface = dyn.surfaceContainer,
                        surfaceElevated = dyn.surfaceContainerHigh,
                        inputFieldBg = dyn.surfaceContainerHighest,
                        primaryAccent = dyn.primary,
                        secondaryAccent = dyn.tertiary,
                        textPrimary = dyn.onBackground,
                        textSecondary = dyn.onSurfaceVariant,
                        borderSubtle = dyn.outlineVariant,
                        success = Color(0xFF22C55E),
                        warning = Color(0xFFF59E0B),
                        error = dyn.error,
                        isGlassTheme = false
                    )
                } else {
                    // Fallback Media Premium Violeta (#7C5CFF)
                    ImaPalette(
                        mode = AppThemeMode.MATERIAL,
                        background = Color(0xFF0B0B0F),
                        surface = Color(0xFF15151C),
                        surfaceElevated = Color(0xFF1D1D26),
                        inputFieldBg = Color(0xFF15151C),
                        primaryAccent = Color(0xFF7C5CFF),
                        secondaryAccent = Color(0xFF00D4A0),
                        textPrimary = Color(0xFFF5F5F7),
                        textSecondary = Color(0xFFA3A3B2),
                        borderSubtle = Color(0xFF2A2A35),
                        success = Color(0xFF22C55E),
                        warning = Color(0xFFF59E0B),
                        error = Color(0xFFEF4444),
                        isGlassTheme = false
                    )
                }
            }
            AppThemeMode.IMALOADER -> {
                // Inspirado directamente en la captura de IMALOADER v2.0 y el ratón neón
                ImaPalette(
                    mode = AppThemeMode.IMALOADER,
                    background = Color(0xFF221933),      // Púrpura oscuro clásico v2.0
                    surface = Color(0xFF2E2246),         // Tarjeta violeta profundo
                    surfaceElevated = Color(0xFF392B58), // Modal elevado
                    inputFieldBg = Color(0xFFEADDFF),    // Cápsula lavanda clara como en v2.0
                    primaryAccent = Color(0xFF7E57C2),   // Botón púrpura v2.0
                    secondaryAccent = Color(0xFF26C6DA), // Cian neón de la cola del ratón
                    textPrimary = Color(0xFFFFFFFF),
                    textSecondary = Color(0xFFD0BCFF),
                    borderSubtle = Color(0xFF533E7D),
                    success = Color(0xFF00E676),
                    warning = Color(0xFFFFD54F),
                    error = Color(0xFFFF5252),
                    isGlassTheme = false
                )
            }
            AppThemeMode.FRUTIGER_AERO -> {
                // Paleta exacta Frutiger Aero (Cielo, agua, cristal y verde fresco)
                ImaPalette(
                    mode = AppThemeMode.FRUTIGER_AERO,
                    background = Color(0xFF4FC3F7),
                    surface = Color(0x59FFFFFF),         // Vidrio blanco translúcido
                    surfaceElevated = Color(0x73FFFFFF),
                    inputFieldBg = Color(0x66FFFFFF),
                    primaryAccent = Color(0xFF039BE5),   // Aero Azul
                    secondaryAccent = Color(0xFF43A047), // Verde fresco para Audio
                    textPrimary = Color(0xFF0B3C5D),     // Azul profundo legible sobre cristal
                    textSecondary = Color(0xFF1F5673),
                    borderSubtle = Color(0xB3FFFFFF),    // Borde luminoso blanco
                    success = Color(0xFF2E7D32),
                    warning = Color(0xFFFFB300),
                    error = Color(0xFFE53935),
                    isGlassTheme = true
                )
            }
        }
    }

    val animDuration = if (reduceMotion) 0 else 320
    val spec = tween<Color>(durationMillis = animDuration, easing = FastOutSlowInEasing)

    val animatedPalette = ImaPalette(
        mode = targetPalette.mode,
        background = animateColorAsState(targetPalette.background, spec, label = "bg").value,
        surface = animateColorAsState(targetPalette.surface, spec, label = "surf").value,
        surfaceElevated = animateColorAsState(targetPalette.surfaceElevated, spec, label = "surfEl").value,
        inputFieldBg = animateColorAsState(targetPalette.inputFieldBg, spec, label = "input").value,
        primaryAccent = animateColorAsState(targetPalette.primaryAccent, spec, label = "prim").value,
        secondaryAccent = animateColorAsState(targetPalette.secondaryAccent, spec, label = "sec").value,
        textPrimary = animateColorAsState(targetPalette.textPrimary, spec, label = "txtP").value,
        textSecondary = animateColorAsState(targetPalette.textSecondary, spec, label = "txtS").value,
        borderSubtle = animateColorAsState(targetPalette.borderSubtle, spec, label = "border").value,
        success = animateColorAsState(targetPalette.success, spec, label = "succ").value,
        warning = animateColorAsState(targetPalette.warning, spec, label = "warn").value,
        error = animateColorAsState(targetPalette.error, spec, label = "err").value,
        isGlassTheme = targetPalette.isGlassTheme
    )

    CompositionLocalProvider(LocalImaPalette provides animatedPalette) {
        content()
    }
}

private fun iosDarkPalette(mode: AppThemeMode) = ImaPalette(
    mode = mode,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF2C2C2E),
    inputFieldBg = Color(0xFF1C1C1E),
    primaryAccent = Color(0xFF0A84FF),
    secondaryAccent = Color(0xFF5E5CE6),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    borderSubtle = Color(0xA6545458),
    success = Color(0xFF30D158),
    warning = Color(0xFFFFD60A),
    error = Color(0xFFFF453A),
    isGlassTheme = false
)

private fun iosLightPalette(mode: AppThemeMode) = ImaPalette(
    mode = mode,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    inputFieldBg = Color(0xFFFFFFFF),
    primaryAccent = Color(0xFF007AFF),
    secondaryAccent = Color(0xFF5856D6),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    borderSubtle = Color(0x4A3C3C43),
    success = Color(0xFF34C759),
    warning = Color(0xFFFFCC00),
    error = Color(0xFFFF3B30),
    isGlassTheme = false
)

/**
 * ESPECIFICACIÓN DE ANIMACIÓN iOS PREMIUM PARA BOTONES Y TARJETAS:
 * - Al presionar: escala a targetScale (0.96 para botones, 0.98 para tarjetas) en 80 ms.
 * - Al soltar: regreso a 1.0 con spring controlado sin rebotes exagerados.
 */
fun Modifier.iosPressable(
    targetScale: Float = 0.96f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotionEnabled()
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) targetScale else 1f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 80, easing = LinearOutSlowInEasing)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 600f)
        },
        label = "iosPressScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "iosPressAlpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = if (enabled) alpha else 0.45f
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onClick()
                    }
                }
            }
        }
}

/**
 * FONDO ANIMADO 1: TEMA IMALOADER (v2.0)
 * Nombres de las plataformas compatibles (YouTube, Facebook, Instagram, TikTok, X)
 * rebotando por las paredes estilo protector de pantalla DVD y cambiando de color al chocar.
 */
private class BouncingAppNode(
    val name: String,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var colorIndex: Int
)

@Composable
fun ImaloaderBouncingBackground(modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotionEnabled()
    val dvdColors = remember {
        listOf(
            Color(0x4426C6DA), // Cian neón tenue
            Color(0x44AB47BC), // Púrpura neón tenue
            Color(0x44FF4081), // Rosa tenue (oreja del ratón)
            Color(0x4442A5F5), // Azul tenue
            Color(0x4466BB6A), // Verde tenue
            Color(0x44FFCA28)  // Ámbar tenue
        )
    }

    val nodes = remember {
        listOf(
            BouncingAppNode("YouTube", 90f, 220f, 2.3f, 1.9f, 0),
            BouncingAppNode("Facebook", 420f, 520f, -2.1f, 2.2f, 1),
            BouncingAppNode("Instagram", 180f, 880f, 2.0f, -2.4f, 2),
            BouncingAppNode("TikTok", 520f, 1180f, -2.4f, -1.8f, 3),
            BouncingAppNode("X", 280f, 1450f, 2.6f, 2.1f, 4)
        )
    }

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos ->
                frameTick = nanos
            }
        }
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 48f
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.BOLD
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val _tick = frameTick // Suscribe el redibujado al frame
        val w = size.width
        val h = size.height

        for (node in nodes) {
            val textWidth = paint.measureText(node.name)
            val textHeight = 48f

            if (!reduceMotion && w > textWidth && h > textHeight) {
                node.x += node.vx
                node.y += node.vy

                var bounced = false
                if (node.x <= 16f) {
                    node.x = 16f
                    node.vx = -node.vx
                    bounced = true
                } else if (node.x + textWidth >= w - 16f) {
                    node.x = w - textWidth - 16f
                    node.vx = -node.vx
                    bounced = true
                }

                if (node.y - textHeight <= 40f) {
                    node.y = textHeight + 40f
                    node.vy = -node.vy
                    bounced = true
                } else if (node.y >= h - 40f) {
                    node.y = h - 40f
                    node.vy = -node.vy
                    bounced = true
                }

                if (bounced) {
                    node.colorIndex = (node.colorIndex + 1) % dvdColors.size
                }
            }

            val c = dvdColors[node.colorIndex]
            paint.color = android.graphics.Color.argb(
                (c.alpha * 255).toInt(),
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt()
            )
            drawContext.canvas.nativeCanvas.drawText(node.name, node.x, node.y, paint)
        }
    }
}

/**
 * FONDO ANIMADO 2: TEMA FRUTIGER AERO
 * Degradado vertical #B3E5FC -> #4FC3F7 -> #039BE5 -> #0277BD + 8 burbujas translúcidas con punto de luz.
 */
@Composable
fun FrutigerAeroBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aeroBubbles")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbleRise"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Cielo y agua Frutiger Aero
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFB3E5FC),
                    Color(0xFF4FC3F7),
                    Color(0xFF039BE5),
                    Color(0xFF0277BD)
                )
            )
        )

        // 7 burbujas translúcidas con balanceo horizontal y reflejo superior izquierdo
        val bubbleSeeds = listOf(0.12f, 0.28f, 0.44f, 0.60f, 0.74f, 0.86f, 0.93f)
        bubbleSeeds.forEachIndexed { idx, xFraction ->
            val phase = (progress + idx * 0.17f) % 1f
            val y = size.height * (1.1f - phase * 1.25f)
            val sway = sin((phase * 6.28f) + idx) * 24f
            val x = (size.width * xFraction) + sway
            val radius = 22f + (idx % 3) * 16f

            // Cuerpo de la burbuja
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = radius,
                center = Offset(x, y)
            )
            // Borde luminoso
            drawCircle(
                color = Color.White.copy(alpha = 0.48f),
                radius = radius,
                center = Offset(x, y),
                style = Stroke(width = 2f)
            )
            // Punto de luz especular arriba a la izquierda
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = radius * 0.22f,
                center = Offset(x - radius * 0.35f, y - radius * 0.35f)
            )
        }
    }
}