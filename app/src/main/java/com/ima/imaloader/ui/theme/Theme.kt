package com.ima.imaloader.ui.theme

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTheme(val label: String, val isFixed: Boolean) {
    LIGHT("Claro", false),
    DARK("Oscuro", false),
    AUTO("Automático", false),
    MATERIAL("Material", false),
    IMALOADER("ImaLoader", true),
    FRUTIGER_AERO("Frutiger Aero", true);

    companion object {
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name.equals(name, true) } ?: AUTO
    }
}

@Composable
fun ImaTheme(
    appTheme: AppTheme = AppTheme.AUTO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.LIGHT -> lightColorScheme(
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E8F0),
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF8B5CF6),
            onPrimary = Color.White,
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF64748B),
            outline = Color(0xFFE2E8F0)
        )
        AppTheme.DARK -> darkColorScheme(
            background = Color(0xFF0A0E17),
            surface = Color(0xFF151B2B),
            surfaceVariant = Color(0xFF1E293B),
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF8B5CF6),
            onPrimary = Color.White,
            onSurface = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF1E293B)
        )
        AppTheme.MATERIAL -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) darkColorScheme(
                    background = Color(0xFF0A0E17),
                    surface = Color(0xFF151B2B),
                    surfaceVariant = Color(0xFF1E293B),
                    primary = Color(0xFF3B82F6),
                    onSurface = Color(0xFFF8FAFC),
                    onSurfaceVariant = Color(0xFF94A3B8)
                ) else lightColorScheme(
                    background = Color(0xFFF8FAFC),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE2E8F0),
                    primary = Color(0xFF3B82F6),
                    onSurface = Color(0xFF0F172A),
                    onSurfaceVariant = Color(0xFF64748B)
                )
            }
        }
        AppTheme.AUTO -> {
            if (darkTheme) darkColorScheme(
                background = Color(0xFF0A0E17),
                surface = Color(0xFF151B2B),
                surfaceVariant = Color(0xFF1E293B),
                primary = Color(0xFF3B82F6),
                secondary = Color(0xFF8B5CF6),
                onPrimary = Color.White,
                onSurface = Color(0xFFF8FAFC),
                onSurfaceVariant = Color(0xFF94A3B8),
                outline = Color(0xFF1E293B)
            ) else lightColorScheme(
                background = Color(0xFFF8FAFC),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFE2E8F0),
                primary = Color(0xFF3B82F6),
                secondary = Color(0xFF8B5CF6),
                onPrimary = Color.White,
                onSurface = Color(0xFF0F172A),
                onSurfaceVariant = Color(0xFF64748B),
                outline = Color(0xFFE2E8F0)
            )
        }
        AppTheme.IMALOADER -> darkColorScheme(
            background = Color(0xFF262433),
            surface = Color(0xFF333045),
            surfaceVariant = Color(0xFF45415C),
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF4FC3F7),
            onPrimary = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFA4AAC0),
            outline = Color(0xFF5A557A)
        )
        AppTheme.FRUTIGER_AERO -> lightColorScheme(
            background = Color(0xFFB3E5FC),
            surface = Color.White.copy(alpha = 0.65f),
            surfaceVariant = Color.White.copy(alpha = 0.45f),
            primary = Color(0xFF0277BD),
            secondary = Color(0xFF039BE5),
            onPrimary = Color.White,
            onSurface = Color(0xFF0B3C5D),
            onSurfaceVariant = Color(0xFF33647A),
            outline = Color.White.copy(alpha = 0.8f)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            if (appTheme == AppTheme.IMALOADER) {
                AnimatedBouncingDvdBackground()
            } else if (appTheme == AppTheme.FRUTIGER_AERO) {
                FrutigerAeroGlassBackground()
            }
            content()
        }
    }
}

@Composable
fun AnimatedBouncingDvdBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "dvd_bounce")
    
    // Lista de entidades que rebotarán simulando el DVD (en blanco por ahora)
    val platforms = listOf("YouTube", "TikTok", "Instagram", "Facebook", "X")
    
    Box(Modifier.fillMaxSize()) {
        platforms.forEachIndexed { index, name ->
            val offsetX by infiniteTransition.animateFloat(
                initialValue = (index * 50f),
                targetValue = 300f + (index * 20f),
                animationSpec = infiniteRepeatable(
                    animation = tween(7000 + (index * 1500), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dvd_x_$index"
            )
            val offsetY by infiniteTransition.animateFloat(
                initialValue = (index * 80f),
                targetValue = 600f + (index * 40f),
                animationSpec = infiniteRepeatable(
                    animation = tween(5000 + (index * 1200), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dvd_y_$index"
            )

            // Colores vibrantes de la versión clásica
            val color = when(index) {
                0 -> Color(0xFFFF0000)
                1 -> Color(0xFFE1306C)
                2 -> Color(0xFF1DA1F2)
                3 -> Color(0xFF1877F2)
                else -> Color(0xFF8B5CF6)
            }

            Box(
                Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder para el logo (texto tenue por ahora)
                Text(
                    text = name.take(1),
                    color = color.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
fun FrutigerAeroGlassBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFB3E5FC), Color(0xFF4FC3F7), Color(0xFF0277BD))
                )
            )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "bubble_anim")
        val bubbles = remember { List(8) { Triple(Math.random().toFloat(), (30 + Math.random() * 50).toFloat(), (10000L + Math.random() * 8000).toLong()) } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            bubbles.forEachIndexed { index, bubble ->
                val (startX, sizeDp, dur) = bubble
                val prog by infiniteTransition.animateFloat(
                    initialValue = 1.1f,
                    targetValue = -0.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(dur.toInt(), easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * 1200)
                    ),
                    label = "bubble_$index"
                )
                Box(
                    Modifier
                        .offset(x = (startX * (w.value - sizeDp)).dp, y = (prog * h.value).dp)
                        .size(sizeDp.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                )
            }
        }
    }
}
