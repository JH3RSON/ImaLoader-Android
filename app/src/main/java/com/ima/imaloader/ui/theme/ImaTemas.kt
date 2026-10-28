// ═══════════════════════════════════════════════════════════════════════════
//  ImaTemas.kt — Sistema de Temas Completo (Versión Final 1:1)
//  Temas: Automático (Claro/Oscuro según sistema) · Claro · Oscuro ·
//         Material You (Dinámico) · ImaLoader (Rebote DVD) · Retro (3D)
// ═══════════════════════════════════════════════════════════════════════════
package com.ima.imaloader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ima.imaloader.R
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════
//  1) MODOS DE TEMA Y PALETA DE COLORES
// ═══════════════════════════════════════════════════════════════════════════

enum class ImaThemeMode(val etiquetaRes: Int) {
    AUTO(R.string.theme_auto),
    CLARO(R.string.theme_light),
    OSCURO(R.string.theme_dark),
    MATERIAL(R.string.theme_material),
    IMALOADER(R.string.theme_imaloader),
    RETRO(R.string.theme_retro)
}

enum class ImaLook { PLANO, CRISTAL, RELIEVE }

@Immutable
data class ImaPalette(
    val modo: ImaThemeMode,
    val oscuro: Boolean,
    val iconosBarraOscuros: Boolean,
    val look: ImaLook,
    val fondo: List<Color>,
    val texto: Color,
    val textoSuave: Color,
    val superficie: Color,
    val campo: Color,
    val campoTexto: Color,
    val borde: Color,
    val acento: Color,
    val sobreAcento: Color,
    val btnPrincipal: Color,
    val sobreBtnPrincipal: Color,
    val btnFuerte: Color,
    val sobreBtnFuerte: Color,
    val btnSuave: Color,
    val sobreBtnSuave: Color,
    val navFondo: Color,
    val navIndicador: Color,
    val navActivo: Color,
    val navInactivo: Color,
    val ajustesFondo: Color,
    val ajustesIcono: Color
)

val PaletaClaro = ImaPalette(
    modo = ImaThemeMode.CLARO,
    oscuro = false,
    iconosBarraOscuros = true,
    look = ImaLook.PLANO,
    fondo = listOf(Color(0xFFF7F7F9), Color(0xFFEEEEF2), Color(0xFFD9D9DF), Color(0xFFF2F2F6)),
    texto = Color(0xFF000000),
    textoSuave = Color(0xFF2C2C2E),
    superficie = Color(0xFFFFFFFF),
    campo = Color(0xFFFFFFFF),
    campoTexto = Color(0xFF1C1C1E),
    borde = Color(0xFFD8D8DC),
    acento = Color(0xFF000000),
    sobreAcento = Color(0xFFFFFFFF),
    btnPrincipal = Color(0xFF0A0A0C),
    sobreBtnPrincipal = Color(0xFFFFFFFF),
    btnFuerte = Color(0xFF0A0A0C),
    sobreBtnFuerte = Color(0xFFFFFFFF),
    btnSuave = Color(0xFFE9E9EE),
    sobreBtnSuave = Color(0xFF000000),
    navFondo = Color(0xFFF5F5F8),
    navIndicador = Color(0xFFE4E4E9),
    navActivo = Color(0xFF000000),
    navInactivo = Color(0xFF2C2C2E),
    ajustesFondo = Color(0xFFFFFFFF),
    ajustesIcono = Color(0xFF1C1C1E)
)

val PaletaOscuro = ImaPalette(
    modo = ImaThemeMode.OSCURO,
    oscuro = true,
    iconosBarraOscuros = false,
    look = ImaLook.PLANO,
    fondo = listOf(Color(0xFF000000)),
    texto = Color(0xFFFFFFFF),
    textoSuave = Color(0xFFD1D1D6),
    superficie = Color(0xFF1C1C1E),
    campo = Color(0xFF262629),
    campoTexto = Color(0xFFF2F2F7),
    borde = Color(0xFF3A3A3C),
    acento = Color(0xFFFFFFFF),
    sobreAcento = Color(0xFF000000),
    btnPrincipal = Color(0xFF2C2C2E),
    sobreBtnPrincipal = Color(0xFFFFFFFF),
    btnFuerte = Color(0xFF2C2C2E),
    sobreBtnFuerte = Color(0xFFFFFFFF),
    btnSuave = Color(0xFF2C2C2E),
    sobreBtnSuave = Color(0xFFFFFFFF),
    navFondo = Color(0xFF242426),
    navIndicador = Color(0xFF3A3A3C),
    navActivo = Color(0xFFFFFFFF),
    navInactivo = Color(0xFFC7C7CC),
    ajustesFondo = Color(0xFF262629),
    ajustesIcono = Color(0xFFFFFFFF)
)

val PaletaMaterialFallback = ImaPalette(
    modo = ImaThemeMode.MATERIAL,
    oscuro = false,
    iconosBarraOscuros = true,
    look = ImaLook.PLANO,
    fondo = listOf(Color(0xFFEEF3FC)),
    texto = Color(0xFF234469),
    textoSuave = Color(0xFF2F4358),
    superficie = Color(0xFFE1E9F8),
    campo = Color(0xFFD3E5F8),
    campoTexto = Color(0xFF1B3149),
    borde = Color.Transparent,
    acento = Color(0xFF2E6DA4),
    sobreAcento = Color(0xFFFFFFFF),
    btnPrincipal = Color(0xFF2E6DA4),
    sobreBtnPrincipal = Color(0xFFFFFFFF),
    btnFuerte = Color(0xFF2E6DA4),
    sobreBtnFuerte = Color(0xFFFFFFFF),
    btnSuave = Color(0xFFC5ECD0),
    sobreBtnSuave = Color(0xFF1A2E22),
    navFondo = Color(0xFFC5ECD0),
    navIndicador = Color(0xFF9AD0F3),
    navActivo = Color(0xFF103758),
    navInactivo = Color(0xFF283D32),
    ajustesFondo = Color(0xFF2E6DA4),
    ajustesIcono = Color(0xFFFFFFFF)
)

fun crearPaletaMaterialYou(context: Context, sistemaOscuro: Boolean): ImaPalette {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return PaletaMaterialFallback
    }
    val dyn = if (sistemaOscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (sistemaOscuro) {
        ImaPalette(
            modo = ImaThemeMode.MATERIAL,
            oscuro = true,
            iconosBarraOscuros = false,
            look = ImaLook.PLANO,
            fondo = listOf(dyn.surface, dyn.surfaceContainerLow),
            texto = dyn.primary,
            textoSuave = dyn.onSurfaceVariant,
            superficie = dyn.surfaceContainer,
            campo = dyn.primaryContainer.copy(alpha = 0.65f),
            campoTexto = dyn.onPrimaryContainer,
            borde = Color.Transparent,
            acento = dyn.primary,
            sobreAcento = dyn.onPrimary,
            btnPrincipal = dyn.primary,
            sobreBtnPrincipal = dyn.onPrimary,
            btnFuerte = dyn.primary,
            sobreBtnFuerte = dyn.onPrimary,
            btnSuave = dyn.tertiaryContainer,
            sobreBtnSuave = dyn.onTertiaryContainer,
            navFondo = dyn.tertiaryContainer,
            navIndicador = dyn.primaryContainer,
            navActivo = dyn.onPrimaryContainer,
            navInactivo = dyn.onTertiaryContainer.copy(alpha = 0.8f),
            ajustesFondo = dyn.primary,
            ajustesIcono = dyn.onPrimary
        )
    } else {
        ImaPalette(
            modo = ImaThemeMode.MATERIAL,
            oscuro = false,
            iconosBarraOscuros = true,
            look = ImaLook.PLANO,
            fondo = listOf(lerp(dyn.surface, dyn.primaryContainer, 0.22f)),
            texto = lerp(dyn.primary, Color.Black, 0.25f),
            textoSuave = dyn.onSurfaceVariant,
            superficie = dyn.surfaceContainerLow,
            campo = dyn.primaryContainer.copy(alpha = 0.85f),
            campoTexto = dyn.onPrimaryContainer,
            borde = Color.Transparent,
            acento = dyn.primary,
            sobreAcento = dyn.onPrimary,
            btnPrincipal = dyn.primary,
            sobreBtnPrincipal = dyn.onPrimary,
            btnFuerte = dyn.primary,
            sobreBtnFuerte = dyn.onPrimary,
            btnSuave = dyn.tertiaryContainer,
            sobreBtnSuave = dyn.onTertiaryContainer,
            navFondo = dyn.tertiaryContainer,
            navIndicador = dyn.primaryContainer,
            navActivo = dyn.onPrimaryContainer,
            navInactivo = dyn.onTertiaryContainer,
            ajustesFondo = dyn.primary,
            ajustesIcono = dyn.onPrimary
        )
    }
}

val PaletaImaLoader = ImaPalette(
    modo = ImaThemeMode.IMALOADER,
    oscuro = true,
    iconosBarraOscuros = false,
    look = ImaLook.CRISTAL,
    fondo = listOf(Color(0xFF190E2C), Color(0xFF21123A), Color(0xFF291647)),
    texto = Color(0xFFFFFFFF),
    textoSuave = Color(0xFFE5DFF5),
    superficie = Color(0xFF281945),
    campo = Color(0x33FFFFFF),
    campoTexto = Color(0xFFFFFFFF),
    borde = Color(0x4DFFFFFF),
    acento = Color(0xFF7B52D3),
    sobreAcento = Color(0xFFFFFFFF),
    btnPrincipal = Color(0xFF7B52D3),
    sobreBtnPrincipal = Color(0xFFFFFFFF),
    btnFuerte = Color(0x33FFFFFF),
    sobreBtnFuerte = Color(0xFFFFFFFF),
    btnSuave = Color(0x33FFFFFF),
    sobreBtnSuave = Color(0xFFFFFFFF),
    navFondo = Color(0xCC261742),
    navIndicador = Color(0xFF8B5CF6),
    navActivo = Color(0xFF9F75FF),
    navInactivo = Color(0xFFE0D7F5),
    ajustesFondo = Color(0x33FFFFFF),
    ajustesIcono = Color(0xFFFFFFFF)
)

val PaletaRetro = ImaPalette(
    modo = ImaThemeMode.RETRO,
    oscuro = true,
    iconosBarraOscuros = true,
    look = ImaLook.RELIEVE,
    fondo = listOf(Color(0xFF7E57C2)),
    texto = Color(0xFFFFFFFF),
    textoSuave = Color(0xFFFFFFFF),
    superficie = Color(0xFF5A5A60),
    campo = Color(0xFF2D2D30),
    campoTexto = Color(0xFFEAEAEA),
    borde = Color(0xFF1A1A1C),
    acento = Color(0xFFFFC107),
    sobreAcento = Color(0xFF1C1C1E),
    btnPrincipal = Color(0xFF7A7A80),
    sobreBtnPrincipal = Color(0xFFFFFFFF),
    btnFuerte = Color(0xFF7A7A80),
    sobreBtnFuerte = Color(0xFFFFFFFF),
    btnSuave = Color(0xFF7A7A80),
    sobreBtnSuave = Color(0xFFFFFFFF),
    navFondo = Color(0xFF7A7A80),
    navIndicador = Color(0xFF38383C),
    navActivo = Color(0xFFFFFFFF),
    navInactivo = Color(0xFFF0F0F5),
    ajustesFondo = Color(0xFF7A7A80),
    ajustesIcono = Color(0xFFF2F2F7)
)

fun paletaPara(
    modo: ImaThemeMode,
    sistemaOscuro: Boolean,
    context: Context? = null
): ImaPalette = when (modo) {
    ImaThemeMode.AUTO -> if (sistemaOscuro) PaletaOscuro else PaletaClaro
    ImaThemeMode.CLARO -> PaletaClaro
    ImaThemeMode.OSCURO -> PaletaOscuro
    ImaThemeMode.MATERIAL -> if (context != null) {
        crearPaletaMaterialYou(context, sistemaOscuro)
    } else {
        PaletaMaterialFallback
    }
    ImaThemeMode.IMALOADER -> PaletaImaLoader
    ImaThemeMode.RETRO -> PaletaRetro
}

// ═══════════════════════════════════════════════════════════════════════════
//  2) PERSISTENCIA DEL TEMA (SharedPreferences)
// ═══════════════════════════════════════════════════════════════════════════

object ImaThemeStore {
    private const val ARCHIVO = "imaloader_tema_prefs"
    private const val CLAVE = "modo_tema"
    private var iniciado = false

    var modo by mutableStateOf(ImaThemeMode.AUTO)
        private set

    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        val guardado = context.applicationContext
            .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .getString(CLAVE, null)
        modo = ImaThemeMode.entries.firstOrNull { it.name == guardado } ?: ImaThemeMode.AUTO
    }

    fun guardar(context: Context, nuevo: ImaThemeMode) {
        modo = nuevo
        context.applicationContext
            .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE, nuevo.name)
            .apply()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  3) PROVEEDOR GLOBAL DEL TEMA (ImaAppTheme)
// ═══════════════════════════════════════════════════════════════════════════

val LocalImaPalette = staticCompositionLocalOf { PaletaClaro }

private val ImaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ImaAppTheme(content: @Composable () -> Unit) {
    val contexto = LocalContext.current
    val sistemaOscuro = isSystemInDarkTheme()
    LaunchedEffect(Unit) { ImaThemeStore.iniciar(contexto) }

    val p = paletaPara(ImaThemeStore.modo, sistemaOscuro, contexto)

    val vista = LocalView.current
    if (!vista.isInEditMode) {
        SideEffect {
            val ventana = vista.context.buscarActividad()?.window ?: return@SideEffect
            val control = WindowCompat.getInsetsController(ventana, vista)
            control.isAppearanceLightStatusBars = p.iconosBarraOscuros
            control.isAppearanceLightNavigationBars = p.iconosBarraOscuros
        }
    }

    CompositionLocalProvider(LocalImaPalette provides p) {
        MaterialTheme(
            colorScheme = p.aEsquemaMaterial(),
            shapes = ImaShapes,
            typography = AppTypography // Usar la tipografía AppTypography de Type.kt
        ) {
            CompositionLocalProvider(LocalContentColor provides p.texto) {
                Box(Modifier.fillMaxSize().background(p.fondo.first())) {
                    content()
                }
            }
        }
    }
}

private tailrec fun Context.buscarActividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActividad()
    else -> null
}

private fun ImaPalette.aEsquemaMaterial(): ColorScheme {
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = acento,
        onPrimary = sobreAcento,
        primaryContainer = btnPrincipal,
        onPrimaryContainer = sobreBtnPrincipal,
        secondary = acento,
        onSecondary = sobreAcento,
        secondaryContainer = btnSuave,
        onSecondaryContainer = sobreBtnSuave,
        tertiary = acento,
        onTertiary = sobreAcento,
        background = Color.Transparent,
        onBackground = texto,
        surface = superficie,
        onSurface = texto,
        surfaceVariant = superficie,
        onSurfaceVariant = textoSuave,
        surfaceTint = acento,
        outline = textoSuave.copy(alpha = 0.5f),
        outlineVariant = textoSuave.copy(alpha = 0.2f),
        surfaceContainerLowest = superficie,
        surfaceContainerLow = superficie,
        surfaceContainer = lerp(superficie, texto, 0.04f),
        surfaceContainerHigh = lerp(superficie, texto, 0.07f),
        surfaceContainerHighest = lerp(superficie, texto, 0.10f)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  4) FONDOS DINÁMICOS Y REBOTE DVD (ImaBackground)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ImaBackground(
    modifier: Modifier = Modifier,
    mostrarArte: Boolean = true,
    content: @Composable () -> Unit
) {
    val p = LocalImaPalette.current
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = p, animationSpec = tween(350), label = "fondo-tema") { pal ->
            Box(Modifier.fillMaxSize()) {
                when (pal.modo) {
                    ImaThemeMode.RETRO -> RetroMosaico(atenuar = !mostrarArte)
                    ImaThemeMode.CLARO -> FondoClaroSuave(pal)
                    else -> Box(Modifier.fillMaxSize().background(pal.pincelDeFondo()))
                }

                // Tema ImaLoader: Iconos de neón con rebote estilo DVD por toda la pantalla
                AnimatedVisibility(
                    visible = mostrarArte && pal.modo == ImaThemeMode.IMALOADER,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LogosReboteDVD(lista = LogosNeon)
                }

                // Tema Retro: Iconos 3D clásicos como en la imagen de referencia
                AnimatedVisibility(
                    visible = mostrarArte && pal.modo == ImaThemeMode.RETRO,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LogosEstaticosRetro(lista = LogosRetro)
                }
            }
        }
        content()
    }
}

@Composable
private fun FondoClaroSuave(pal: ImaPalette) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(pal.fondo))
        // Sombra difuminada central-inferior idéntica a la captura "CLARO"
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x22000000), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.65f),
                radius = size.width * 0.65f
            ),
            center = Offset(size.width * 0.5f, size.height * 0.65f),
            radius = size.width * 0.65f
        )
    }
}

private fun ImaPalette.pincelDeFondo(): Brush =
    if (fondo.size == 1) SolidColor(fondo[0]) else Brush.verticalGradient(fondo)

// ── Mosaico pixelado del tema Retro ─────────────────────────────────────────

private val ColoresRetro = listOf(
    Color(0xFFFFCA28), Color(0xFF9CCC65), Color(0xFF29B6F6), Color(0xFFFF7043), Color(0xFFEC407A),
    Color(0xFFAB47BC), Color(0xFF26C6DA), Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF66BB6A)
)

private class ImaBloque(val x: Float, val y: Float, val w: Float, val h: Float, val color: Color)

@Composable
private fun RetroMosaico(atenuar: Boolean) {
    val oscurecer by animateFloatAsState(if (atenuar) 0.55f else 0f, label = "retro-atenuar")
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val columnas = 8
            val lado = size.width / columnas
            val filas = (size.height / lado).toInt() + 2
            val azar = Random(14L)
            val bloques = ArrayList<ImaBloque>()
            for (f in 0 until filas) {
                for (c in 0 until columnas) {
                    val color = ColoresRetro[azar.nextInt(ColoresRetro.size)]
                    bloques += ImaBloque(c * lado, f * lado, lado + 1.5f, lado + 1.5f, color)
                }
            }
            repeat(columnas * filas / 3) {
                val c = azar.nextInt(columnas)
                val f = azar.nextInt(filas)
                val ancho = (1 + azar.nextInt(2)) * lado + 1.5f
                val alto = (1 + azar.nextInt(3)) * lado + 1.5f
                bloques += ImaBloque(
                    c * lado,
                    f * lado,
                    ancho,
                    alto,
                    ColoresRetro[azar.nextInt(ColoresRetro.size)]
                )
            }
            onDrawBehind {
                bloques.forEach { drawRect(it.color, Offset(it.x, it.y), Size(it.w, it.h)) }
                if (oscurecer > 0f) {
                    drawRect(Color.Black.copy(alpha = oscurecer))
                }
            }
        }
    )
}

// ── Logos de redes y motor de Rebote DVD ────────────────────────────────────

enum class ImaLogo {
    INSTAGRAM,
    RETRO_CAMERA,
    TIKTOK,
    FACEBOOK,
    FACEBOOK_3D_TILE,
    X,
    TWITTER_BIRD,
    YOUTUBE
}

private class ImaLogoConfig(
    val tipo: ImaLogo,
    val xInicial: Float,
    val yInicial: Float,
    val tam: Dp,
    val giro: Float,
    val alfa: Float,
    val desenfoque: Dp = 0.dp,
    val velX: Float = 45f, // velocidad horizontal en dp/s para rebote DVD
    val velY: Float = 38f  // velocidad vertical en dp/s para rebote DVD
)

private val LogosNeon = listOf(
    ImaLogoConfig(ImaLogo.INSTAGRAM, 0.48f, 0.04f, 88.dp, 10f, 0.95f, velX = 52f, velY = 44f),
    ImaLogoConfig(ImaLogo.INSTAGRAM, 0.12f, 0.28f, 70.dp, -12f, 0.90f, velX = -46f, velY = 50f),
    ImaLogoConfig(ImaLogo.TIKTOK, 0.48f, 0.31f, 64.dp, 0f, 0.95f, velX = 58f, velY = -42f),
    ImaLogoConfig(ImaLogo.FACEBOOK, 0.88f, 0.26f, 92.dp, 15f, 0.40f, 4.dp, velX = -38f, velY = 36f),
    ImaLogoConfig(ImaLogo.FACEBOOK, 0.18f, 0.60f, 90.dp, -8f, 0.95f, velX = 44f, velY = -48f),
    ImaLogoConfig(ImaLogo.X, 0.78f, 0.58f, 64.dp, 0f, 0.92f, velX = -54f, velY = -40f),
    ImaLogoConfig(ImaLogo.YOUTUBE, 0.56f, 0.71f, 94.dp, -15f, 0.92f, velX = -48f, velY = 46f),
    ImaLogoConfig(ImaLogo.TIKTOK, 0.20f, 0.80f, 74.dp, 12f, 0.92f, velX = 50f, velY = -52f),
    ImaLogoConfig(ImaLogo.YOUTUBE, 0.78f, 0.82f, 72.dp, 15f, 0.65f, velX = -42f, velY = -38f)
)

// En Retro incluimos la cámara retro detrás del título y el pájaro clásico de Twitter abajo a la derecha
private val LogosRetro = listOf(
    ImaLogoConfig(ImaLogo.INSTAGRAM, 0.11f, 0.21f, 105.dp, -10f, 1f),
    ImaLogoConfig(ImaLogo.RETRO_CAMERA, 0.50f, 0.25f, 138.dp, 0f, 1f),
    ImaLogoConfig(ImaLogo.FACEBOOK_3D_TILE, 0.22f, 0.61f, 118.dp, -10f, 1f),
    ImaLogoConfig(ImaLogo.TIKTOK, 0.79f, 0.62f, 112.dp, 10f, 1f),
    ImaLogoConfig(ImaLogo.YOUTUBE, 0.24f, 0.77f, 122.dp, -8f, 1f),
    ImaLogoConfig(ImaLogo.TWITTER_BIRD, 0.77f, 0.77f, 115.dp, -6f, 1f)
)

private class EstadoDVD(
    xIni: Float,
    yIni: Float,
    var vx: Float,
    var vy: Float
) {
    var x by mutableFloatStateOf(xIni)
    var y by mutableFloatStateOf(yIni)
}

/** Animación continua de rebote estilo DVD contra los 4 bordes de la pantalla. */
@Composable
private fun LogosReboteDVD(lista: List<ImaLogoConfig>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val anchoMaxPx = with(density) { maxWidth.toPx() }
        val altoMaxPx = with(density) { maxHeight.toPx() }

        val estados = remember(anchoMaxPx, altoMaxPx) {
            lista.map { cfg ->
                val tamPx = with(density) { cfg.tam.toPx() }
                val xPx = (anchoMaxPx * cfg.xInicial - tamPx / 2f).coerceIn(0f, (anchoMaxPx - tamPx).coerceAtLeast(1f))
                val yPx = (altoMaxPx * cfg.yInicial - tamPx / 2f).coerceIn(0f, (altoMaxPx - tamPx).coerceAtLeast(1f))
                val vxPx = with(density) { cfg.velX.dp.toPx() }
                val vyPx = with(density) { cfg.velY.dp.toPx() }
                EstadoDVD(xPx, yPx, vxPx, vyPx)
            }
        }

        LaunchedEffect(anchoMaxPx, altoMaxPx) {
            var ultimoTiempo = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val ahora = withInfiniteAnimationFrameMillis { it }
                val dt = ((ahora - ultimoTiempo) / 1000f).coerceIn(0f, 0.05f)
                ultimoTiempo = ahora

                estados.forEachIndexed { idx, est ->
                    val tamPx = with(density) { lista[idx].tam.toPx() }
                    val limiteX = (anchoMaxPx - tamPx).coerceAtLeast(1f)
                    val limiteY = (altoMaxPx - tamPx).coerceAtLeast(1f)

                    var nuevoX = est.x + est.vx * dt
                    var nuevoY = est.y + est.vy * dt

                    if (nuevoX <= 0f) {
                        nuevoX = 0f
                        est.vx = abs(est.vx)
                    } else if (nuevoX >= limiteX) {
                        nuevoX = limiteX
                        est.vx = -abs(est.vx)
                    }

                    if (nuevoY <= 0f) {
                        nuevoY = 0f
                        est.vy = abs(est.vy)
                    } else if (nuevoY >= limiteY) {
                        nuevoY = limiteY
                        est.vy = -abs(est.vy)
                    }

                    est.x = nuevoX
                    est.y = nuevoY
                }
            }
        }

        lista.forEachIndexed { idx, cfg ->
            val est = estados[idx]
            ImaLogoView(
                tipo = cfg.tipo,
                neon = true,
                relieve = false,
                modifier = Modifier
                    .offset { IntOffset(est.x.roundToInt(), est.y.roundToInt()) }
                    .size(cfg.tam)
                    .rotate(cfg.giro)
                    .alpha(cfg.alfa)
                    .then(if (cfg.desenfoque > 0.dp) Modifier.blur(cfg.desenfoque) else Modifier)
            )
        }
    }
}

@Composable
private fun LogosEstaticosRetro(lista: List<ImaLogoConfig>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        lista.forEach { cfg ->
            ImaLogoView(
                tipo = cfg.tipo,
                neon = false,
                relieve = true,
                modifier = Modifier
                    .offset(x = maxWidth * cfg.xInicial - cfg.tam / 2, y = maxHeight * cfg.yInicial - cfg.tam / 2)
                    .size(cfg.tam)
                    .rotate(cfg.giro)
                    .alpha(cfg.alfa)
            )
        }
    }
}

@Composable
fun ImaLogoView(
    tipo: ImaLogo,
    modifier: Modifier = Modifier,
    neon: Boolean = false,
    relieve: Boolean = false
) {
    Canvas(modifier) {
        val s = size.minDimension
        if (neon) {
            val halo = colorHalo(tipo)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(halo.copy(alpha = 0.45f), Color.Transparent),
                    center = center,
                    radius = s * 0.85f
                ),
                radius = s * 0.85f,
                center = center
            )
        }
        if (relieve) {
            translate(s * 0.04f, s * 0.06f) {
                dibujarLogo(tipo, s, Color.Black.copy(alpha = 0.45f), esRetro = true)
            }
        }
        dibujarLogo(tipo, s, null, esRetro = relieve)
    }
}

private fun colorHalo(t: ImaLogo): Color = when (t) {
    ImaLogo.INSTAGRAM, ImaLogo.RETRO_CAMERA -> Color(0xFFE1306C)
    ImaLogo.TIKTOK -> Color(0xFF25F4EE)
    ImaLogo.FACEBOOK, ImaLogo.FACEBOOK_3D_TILE -> Color(0xFF1877F2)
    ImaLogo.X -> Color.White
    ImaLogo.TWITTER_BIRD -> Color(0xFF1DA1F2)
    ImaLogo.YOUTUBE -> Color(0xFFFF3B30)
}

private fun DrawScope.dibujarLogo(tipo: ImaLogo, s: Float, sombraPlana: Color?, esRetro: Boolean) {
    fun pintura(c: Color): Brush = SolidColor(sombraPlana ?: c)

    when (tipo) {
        ImaLogo.INSTAGRAM -> {
            val gradiente = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
                    Color(0xFF962FBF), Color(0xFF4F5BD5)
                ),
                start = Offset(0f, s),
                end = Offset(s, 0f)
            )
            val pincel = if (sombraPlana != null) SolidColor(sombraPlana) else gradiente
            if (esRetro) {
                // En Retro, el logo de arriba a la izquierda es un bloque 3D relleno con ícono blanco
                drawRoundRect(pincel, Offset.Zero, Size(s, s), CornerRadius(s * 0.26f))
                if (sombraPlana == null) {
                    val g = s * 0.075f
                    val m = s * 0.16f
                    drawRoundRect(
                        SolidColor(Color.White),
                        Offset(m, m),
                        Size(s - m * 2, s - m * 2),
                        CornerRadius(s * 0.18f),
                        style = Stroke(g)
                    )
                    drawCircle(SolidColor(Color.White), s * 0.17f, Offset(s / 2, s / 2), style = Stroke(g))
                    drawCircle(SolidColor(Color.White), s * 0.045f, Offset(s * 0.68f, s * 0.32f))
                }
            } else {
                val g = s * 0.085f
                drawRoundRect(
                    pincel,
                    Offset(g / 2, g / 2),
                    Size(s - g, s - g),
                    CornerRadius(s * 0.28f),
                    style = Stroke(g)
                )
                drawCircle(pincel, s * 0.22f, Offset(s / 2, s / 2), style = Stroke(g))
                drawCircle(pincel, s * 0.06f, Offset(s * 0.74f, s * 0.26f))
            }
        }

        ImaLogo.RETRO_CAMERA -> {
            val radio = CornerRadius(s * 0.24f)
            if (sombraPlana != null) {
                drawRoundRect(SolidColor(sombraPlana), Offset.Zero, Size(s, s), radio)
                return
            }
            // Cuerpo crema inferior
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFF5EFE6), Color(0xFFD7C8B6))),
                Offset.Zero,
                Size(s, s),
                radio
            )
            // Franja superior de cuero marrón clásico
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF6D4C41), Color(0xFF4E342E))),
                Offset(0f, 0f),
                Size(s, s * 0.38f),
                radio
            )
            // Franjas arcoíris arriba a la izquierda
            val anchoF = s * 0.035f
            val xBase = s * 0.16f
            listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5)).forEachIndexed { i, col ->
                drawRect(col, Offset(xBase + i * anchoF, s * 0.04f), Size(anchoF, s * 0.34f))
            }
            // Visor oscuro arriba a la derecha
            drawRoundRect(
                Color(0xFF1E1E1E),
                Offset(s * 0.70f, s * 0.10f),
                Size(s * 0.16f, s * 0.16f),
                CornerRadius(s * 0.04f)
            )
            // Lente central 3D con reflejos
            val centro = Offset(s * 0.5f, s * 0.54f)
            drawCircle(Color(0xFF2B2B2B), s * 0.30f, centro)
            drawCircle(Color(0xFFE0E0E0), s * 0.26f, centro)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF1A237E), Color(0xFF0D1117)), centro, s * 0.22f),
                s * 0.22f,
                centro
            )
            drawCircle(Color.White.copy(alpha = 0.45f), s * 0.06f, Offset(s * 0.43f, s * 0.47f))
        }

        ImaLogo.YOUTUBE -> {
            if (esRetro) {
                val pincel = if (sombraPlana != null) {
                    SolidColor(sombraPlana)
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFFF4B4B), Color(0xFFD50000)))
                }
                drawRoundRect(pincel, Offset(0f, s * 0.14f), Size(s, s * 0.72f), CornerRadius(s * 0.22f))
                val play = Path().apply {
                    moveTo(s * 0.40f, s * 0.34f)
                    lineTo(s * 0.40f, s * 0.66f)
                    lineTo(s * 0.68f, s * 0.50f)
                    close()
                }
                drawPath(play, pintura(Color.White))
            } else {
                val pincel = if (sombraPlana != null) {
                    SolidColor(sombraPlana)
                } else {
                    Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFF7A00), Color(0xFFE040FB)))
                }
                val g = s * 0.075f
                drawRoundRect(
                    pincel,
                    Offset(g, s * 0.18f),
                    Size(s - g * 2, s * 0.64f),
                    CornerRadius(s * 0.20f),
                    style = Stroke(g)
                )
                val play = Path().apply {
                    moveTo(s * 0.41f, s * 0.37f)
                    lineTo(s * 0.41f, s * 0.63f)
                    lineTo(s * 0.65f, s * 0.50f)
                    close()
                }
                drawPath(play, pincel, style = Stroke(g * 0.85f, join = StrokeJoin.Round))
            }
        }

        ImaLogo.FACEBOOK -> {
            drawCircle(pintura(Color(0xFF1877F2)), s / 2, Offset(s / 2, s / 2))
            val f = Path().apply {
                moveTo(s * 0.56f, s * 0.98f)
                lineTo(s * 0.56f, s * 0.43f)
                cubicTo(s * 0.56f, s * 0.31f, s * 0.62f, s * 0.25f, s * 0.74f, s * 0.25f)
            }
            drawPath(f, pintura(Color.White), style = Stroke(width = s * 0.13f, join = StrokeJoin.Round))
            drawLine(pintura(Color.White), Offset(s * 0.40f, s * 0.52f), Offset(s * 0.73f, s * 0.52f), strokeWidth = s * 0.11f)
        }

        ImaLogo.FACEBOOK_3D_TILE -> {
            val radio = CornerRadius(s * 0.22f)
            val pincel = if (sombraPlana != null) {
                SolidColor(sombraPlana)
            } else {
                Brush.verticalGradient(listOf(Color(0xFF4E8DF5), Color(0xFF1B56B3)))
            }
            drawRoundRect(pincel, Offset.Zero, Size(s, s), radio)
            if (sombraPlana == null) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.Transparent)),
                    Offset(s * 0.03f, s * 0.03f),
                    Size(s * 0.94f, s * 0.94f),
                    radio,
                    style = Stroke(s * 0.04f)
                )
                val f = Path().apply {
                    moveTo(s * 0.58f, s * 0.95f)
                    lineTo(s * 0.58f, s * 0.42f)
                    cubicTo(s * 0.58f, s * 0.28f, s * 0.65f, s * 0.22f, s * 0.78f, s * 0.22f)
                }
                drawPath(f, SolidColor(Color.White), style = Stroke(width = s * 0.15f, join = StrokeJoin.Round))
                drawLine(SolidColor(Color.White), Offset(s * 0.42f, s * 0.52f), Offset(s * 0.76f, s * 0.52f), strokeWidth = s * 0.12f)
            }
        }

        ImaLogo.X -> {
            drawLine(
                pintura(Color.White),
                Offset(s * 0.16f, s * 0.14f),
                Offset(s * 0.84f, s * 0.86f),
                strokeWidth = s * 0.12f,
                cap = StrokeCap.Round
            )
            drawLine(
                pintura(Color.White),
                Offset(s * 0.84f, s * 0.14f),
                Offset(s * 0.16f, s * 0.86f),
                strokeWidth = s * 0.06f,
                cap = StrokeCap.Round
            )
        }

        ImaLogo.TWITTER_BIRD -> {
            val pincel = if (sombraPlana != null) {
                SolidColor(sombraPlana)
            } else {
                Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1)))
            }
            val ave = Path().apply {
                moveTo(s * 0.12f, s * 0.74f)
                cubicTo(s * 0.35f, s * 0.86f, s * 0.72f, s * 0.76f, s * 0.82f, s * 0.38f)
                lineTo(s * 0.94f, s * 0.26f)
                lineTo(s * 0.82f, s * 0.28f)
                cubicTo(s * 0.88f, s * 0.18f, s * 0.76f, s * 0.14f, s * 0.64f, s * 0.22f)
                cubicTo(s * 0.52f, s * 0.28f, s * 0.48f, s * 0.38f, s * 0.50f, s * 0.46f)
                cubicTo(s * 0.32f, s * 0.44f, s * 0.20f, s * 0.32f, s * 0.14f, s * 0.20f)
                cubicTo(s * 0.08f, s * 0.36f, s * 0.16f, s * 0.52f, s * 0.28f, s * 0.58f)
                lineTo(s * 0.14f, s * 0.56f)
                cubicTo(s * 0.18f, s * 0.66f, s * 0.28f, s * 0.70f, s * 0.34f, s * 0.72f)
                close()
            }
            drawPath(ave, pincel)
        }

        ImaLogo.TIKTOK -> {
            fun nota(dx: Float, dy: Float, color: Color) = translate(dx, dy) {
                val c = pintura(color)
                val g = s * 0.13f
                val asta = Path().apply {
                    moveTo(s * 0.56f, s * 0.14f)
                    lineTo(s * 0.56f, s * 0.68f)
                }
                val gancho = Path().apply {
                    moveTo(s * 0.56f, s * 0.14f)
                    cubicTo(s * 0.60f, s * 0.30f, s * 0.72f, s * 0.36f, s * 0.86f, s * 0.37f)
                }
                drawPath(asta, c, style = Stroke(g, cap = StrokeCap.Round))
                drawPath(gancho, c, style = Stroke(g, cap = StrokeCap.Round))
                drawCircle(c, s * 0.16f, Offset(s * 0.39f, s * 0.70f), style = Stroke(g))
            }
            if (sombraPlana == null) {
                nota(-s * 0.035f, -s * 0.025f, Color(0xFF25F4EE))
                nota(s * 0.035f, s * 0.025f, Color(0xFFFE2C55))
            }
            nota(0f, 0f, if (esRetro && sombraPlana == null) Color(0xFF161618) else Color.White)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  5) ICONOS VECTORIALES PROPIOS (IDÉNTICOS A TU IMAGEN DE REFERENCIA)
// ═══════════════════════════════════════════════════════════════════════════

/** Icono de bandeja de descarga real (flecha hacia abajo + bandeja inferior). */
@Composable
fun ImaDownloadTrayIcon(tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val g = w * 0.12f
        // Flecha vertical
        drawLine(tint, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.65f), strokeWidth = g, cap = StrokeCap.Round)
        val punta = Path().apply {
            moveTo(w * 0.28f, h * 0.45f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.72f, h * 0.45f)
        }
        drawPath(punta, tint, style = Stroke(g, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Base de bandeja
        drawLine(tint, Offset(w * 0.20f, h * 0.84f), Offset(w * 0.80f, h * 0.84f), strokeWidth = g, cap = StrokeCap.Round)
    }
}

/** Icono de eslabón de enlace ("🔗") limpio para el campo de texto. */
@Composable
fun ImaLinkChainIcon(tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val g = w * 0.10f
        drawRoundRect(
            tint,
            Offset(w * 0.08f, h * 0.32f),
            Size(w * 0.46f, h * 0.36f),
            CornerRadius(h * 0.18f),
            style = Stroke(g)
        )
        drawRoundRect(
            tint,
            Offset(w * 0.46f, h * 0.32f),
            Size(w * 0.46f, h * 0.36f),
            CornerRadius(h * 0.18f),
            style = Stroke(g)
        )
        drawLine(tint, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.64f, h * 0.5f), strokeWidth = g, cap = StrokeCap.Round)
    }
}

/** Icono de llama/gota estilizada que aparece en el FAB del tema Material. */
@Composable
fun ImaSparkLogoIcon(tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.50f, s * 0.12f)
            cubicTo(s * 0.68f, s * 0.38f, s * 0.82f, s * 0.54f, s * 0.74f, s * 0.74f)
            cubicTo(s * 0.68f, s * 0.86f, s * 0.32f, s * 0.86f, s * 0.26f, s * 0.74f)
            cubicTo(s * 0.18f, s * 0.54f, s * 0.32f, s * 0.38f, s * 0.50f, s * 0.12f)
            close()
        }
        drawPath(path, tint, style = Stroke(s * 0.09f, join = StrokeJoin.Round))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  6) COMPONENTES VISUALES PRINCIPALES (1:1 CON LA REFERENCIA)
// ═══════════════════════════════════════════════════════════════════════════

fun Modifier.imaRelleno(
    p: ImaPalette,
    color: Color,
    forma: Shape,
    conBorde: Boolean = false,
    sombraSuaveClaro: Boolean = false
): Modifier = when (p.look) {
    ImaLook.PLANO -> this
        .then(
            if (sombraSuaveClaro && !p.oscuro) {
                Modifier.shadow(4.dp, forma, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            } else {
                Modifier
            }
        )
        .background(color, forma)
        .then(
            if (conBorde && p.borde != Color.Transparent) Modifier.border(1.dp, p.borde, forma)
            else Modifier
        )

    ImaLook.CRISTAL -> this
        .background(color, forma)
        .border(1.dp, p.borde, forma)

    ImaLook.RELIEVE -> this
        .shadow(6.dp, forma)
        .background(
            Brush.verticalGradient(
                listOf(
                    lerp(color, Color.White, 0.28f),
                    color,
                    lerp(color, Color.Black, 0.32f)
                )
            ),
            forma
        )
        .border(
            1.5.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.70f), Color.Black.copy(alpha = 0.65f))
            ),
            forma
        )
}

@Composable
fun ImaTitle(modifier: Modifier = Modifier, subtitulo: String = "hecho con ❤️ por Ima") {
    val p = LocalImaPalette.current
    val base = TextStyle(fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
    Column(modifier) {
        if (p.look == ImaLook.RELIEVE) {
            Box {
                // Sombra 3D gruesa detrás del texto cromado en Retro
                Text(
                    "IMALOADER",
                    style = base.copy(
                        color = Color(0xFF1A1A1E),
                        drawStyle = Stroke(width = 14f, join = StrokeJoin.Round)
                    ),
                    modifier = Modifier.offset(x = 1.dp, y = 3.dp)
                )
                Text(
                    "IMALOADER",
                    style = base.copy(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFD0D4DC),
                                Color(0xFF6E727A),
                                Color(0xFFE8ECF2)
                            )
                        )
                    )
                )
            }
        } else {
            Text("IMALOADER", style = base.copy(color = p.texto))
            Spacer(Modifier.height(2.dp))
            Text(subtitulo, color = p.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

enum class ImaRol { PRINCIPAL, FUERTE, SUAVE }

// Reemplaza SOLO la función ImaButton dentro de tu ImaTemas.kt por esta:
@Composable
fun ImaButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rol: ImaRol = ImaRol.PRINCIPAL,
    mostrarIconoDescarga: Boolean = false,
    icono: ImageVector? = null,
    habilitado: Boolean = true
) {
    val p = LocalImaPalette.current
    val fondo = when (rol) {
        ImaRol.PRINCIPAL -> p.btnPrincipal
        ImaRol.FUERTE -> p.btnFuerte
        ImaRol.SUAVE -> p.btnSuave
    }
    val letra = when (rol) {
        ImaRol.PRINCIPAL -> p.sobreBtnPrincipal
        ImaRol.FUERTE -> p.sobreBtnFuerte
        ImaRol.SUAVE -> p.sobreBtnSuave
    }
    val estiloTexto = if (p.look == ImaLook.RELIEVE) {
        LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(1f, 2f), 3f))
    } else {
        LocalTextStyle.current
    }
    val forma = RoundedCornerShape(26.dp)
    val requiereBordeSuave = (p.modo == ImaThemeMode.CLARO && rol == ImaRol.SUAVE) ||
        (p.modo == ImaThemeMode.OSCURO)

    Row(
        modifier
            .heightIn(min = 50.dp)
            .alpha(if (habilitado) 1f else 0.5f)
            .imaRelleno(p, fondo, forma, conBorde = requiereBordeSuave)
            .clip(forma)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mostrarIconoDescarga) {
            ImaDownloadTrayIcon(tint = letra, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, tint = letra, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = texto,
            color = letra,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = estiloTexto
        )
    }
}

@Composable
fun ImaLinkField(
    valor: String,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier,
    pista: String = "Pega un enlace de YouTube, FB, IG,...",
    mostrarIconoEnlace: Boolean = true,
    iconoPersonalizado: ImageVector? = null
) {
    val p = LocalImaPalette.current
    val forma = CircleShape
    val estiloInput = if (p.modo == ImaThemeMode.RETRO) {
        // En Retro el campo de enlace es oscuro hundido, no plateado
        Modifier
            .shadow(4.dp, forma)
            .background(Color(0xFF28282C), forma)
            .border(1.5.dp, Color(0xFF505056), forma)
    } else {
        Modifier.imaRelleno(
            p = p,
            color = p.campo,
            forma = forma,
            conBorde = true,
            sombraSuaveClaro = (p.modo == ImaThemeMode.CLARO)
        )
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(estiloInput)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconoPersonalizado != null) {
            Icon(iconoPersonalizado, contentDescription = null, tint = p.campoTexto, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        } else if (mostrarIconoEnlace) {
            ImaLinkChainIcon(tint = p.campoTexto.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        BasicTextField(
            value = valor,
            onValueChange = onCambio,
            singleLine = true,
            textStyle = TextStyle(color = p.campoTexto, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(p.campoTexto),
            modifier = Modifier.weight(1f),
            decorationBox = { interior ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (valor.isEmpty()) {
                        Text(
                            pista,
                            color = p.campoTexto.copy(alpha = 0.80f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    interior()
                }
            }
        )
    }
}

@Composable
fun ImaGearButton(icono: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalImaPalette.current
    val forma = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(48.dp)
            .imaRelleno(
                p = p,
                color = p.ajustesFondo,
                forma = forma,
                conBorde = true,
                sombraSuaveClaro = (p.modo == ImaThemeMode.CLARO)
            )
            .clip(forma)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = "Ajustes", tint = p.ajustesIcono, modifier = Modifier.size(24.dp))
    }
}

@Immutable
data class ImaNavItem(val etiqueta: String, val icono: ImageVector)

@Composable
fun ImaNavBar(
    items: List<ImaNavItem>,
    seleccionado: Int,
    onSeleccion: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val formaBarra = RoundedCornerShape(34.dp)
    val formaItem = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .imaRelleno(
                    p = p,
                    color = p.navFondo,
                    forma = formaBarra,
                    conBorde = true,
                    sombraSuaveClaro = (p.modo == ImaThemeMode.CLARO)
                )
                .padding(6.dp)
        ) {
            items.forEachIndexed { i, item ->
                val activo = i == seleccionado
                val color = if (activo) p.navActivo else p.navInactivo
                val esTemaImaLoader = p.modo == ImaThemeMode.IMALOADER

                Box(
                    Modifier
                        .weight(1f)
                        .height(62.dp)
                        .clip(formaItem)
                        .then(
                            if (activo && !esTemaImaLoader) {
                                Modifier.background(p.navIndicador, formaItem)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (activo && p.look == ImaLook.RELIEVE) {
                                Modifier.border(1.dp, Color.Black.copy(alpha = 0.6f), formaItem)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSeleccion(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            item.icono,
                            contentDescription = item.etiqueta,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.etiqueta,
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    // En ImaLoader, el indicador activo es una barra de neón morada en el borde inferior
                    if (activo && esTemaImaLoader) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                                .width(36.dp)
                                .height(3.5.dp)
                                .background(p.navIndicador, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImaFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null
) {
    val p = LocalImaPalette.current
    val forma = RoundedCornerShape(20.dp)
    Box(
        modifier
            .size(58.dp)
            .shadow(6.dp, forma)
            .imaRelleno(p, p.btnPrincipal, forma)
            .clip(forma)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, tint = p.sobreBtnPrincipal)
        } else {
            ImaSparkLogoIcon(tint = p.sobreBtnPrincipal, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun ImaCard(
    modifier: Modifier = Modifier,
    forma: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalImaPalette.current
    val color = if (p.look == ImaLook.CRISTAL) p.campo else p.superficie
    Column(
        modifier
            .imaRelleno(p, color, forma, conBorde = true, sombraSuaveClaro = (p.modo == ImaThemeMode.CLARO))
            .clip(forma)
            .padding(16.dp),
        content = content
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  7) COMPONENTES CONECTADOS AL MOTOR REAL (CERO CALIDADES O MB FALSOS)
//     Úsalos en Inicio (selector de formatos), Buscar y Descargas.
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Representa un formato real devuelto por el motor (yt-dlp / FFmpeg).
 * NUNCA inventa extensiones ("m4a") ni tamaños: usa exactamente lo que entrega el motor.
 */
@Immutable
data class ImaRealFormatInfo(
    val formatId: String,
    val extensionReal: String,        // Ej: format.ext real del motor ("opus", "m4a", "mp4", "webm")
    val etiquetaCalidad: String,      // Ej: "1080p60", "160 kbps", etc. directo del motor
    val tamanoBytesReal: Long? = null,// Ej: format.filesize ?: format.filesizeApprox (null si el motor no lo da)
    val codecReal: String? = null     // Ej: "opus", "avc1", etc.
) {
    /** Texto real para mostrar en el botón sin inventar megabytes ni formatos. */
    fun textoBotonReal(): String {
        val ext = extensionReal.uppercase(Locale.ROOT)
        val tamanoStr = formatearBytesReales(tamanoBytesReal)
        return buildString {
            append(etiquetaCalidad)
            append(" • ")
            append(ext)
            if (tamanoStr != null) {
                append(" (")
                append(tamanoStr)
                append(")")
            }
        }
    }
}

/** Convierte los bytes reales del motor a KB/MB/GB. Si es null o <= 0, devuelve null (no inventa). */
fun formatearBytesReales(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes.toDouble() / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}

/**
 * Estado real de una descarga en la pestaña "Descargas".
 * El mensaje completado usa la extensión y peso reales del archivo final generado por el motor.
 */
@Immutable
data class ImaRealDownloadTask(
    val id: String,
    val titulo: String,
    val autorOCanal: String,
    val extensionFinalReal: String,     // Extensión real del archivo en disco o flujo del motor
    val resolucionOBitrateReal: String, // Calidad real reportada por el motor
    val bytesDescargados: Long,
    val bytesTotales: Long?,            // null si el motor aún no conoce el tamaño total
    val progreso: Float,                // 0f..1f calculado por el motor
    val completado: Boolean,
    val velocidadReal: String? = null   // Ej: "4.8 MB/s" directo de la salida de yt-dlp
)

/** Botón de selección de formato que muestra únicamente los datos reales del motor. */
@Composable
fun ImaRealFormatButton(
    formato: ImaRealFormatInfo,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImaButton(
        texto = formato.textoBotonReal(),
        onClick = onClick,
        modifier = modifier,
        rol = if (seleccionado) ImaRol.PRINCIPAL else ImaRol.SUAVE,
        mostrarIconoDescarga = seleccionado
    )
}

/** Tarjeta de descarga para la pestaña "Descargas" adaptada a todos los temas y con datos 100% reales. */
@Composable
fun ImaRealDownloadCard(
    tarea: ImaRealDownloadTask,
    onClickAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalImaPalette.current
    val extReal = tarea.extensionFinalReal.uppercase(Locale.ROOT)
    val pesoTotalReal = formatearBytesReales(tarea.bytesTotales)
    val pesoActualReal = formatearBytesReales(tarea.bytesDescargados)

    ImaCard(modifier = modifier.fillMaxWidth().clickable(enabled = tarea.completado, onClick = onClickAbrir)) {
        Text(
            text = tarea.titulo,
            color = p.texto,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tarea.autorOCanal,
            color = p.textoSuave,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))

        if (tarea.completado) {
            // Mensaje 100% real basado en el archivo entregado por el motor
            val detalleReal = buildString {
                append("Descargado • ")
                append(extReal)
                if (tarea.resolucionOBitrateReal.isNotBlank()) {
                    append(" • ")
                    append(tarea.resolucionOBitrateReal)
                }
                val pesoFinal = pesoTotalReal ?: pesoActualReal
                if (pesoFinal != null) {
                    append(" • ")
                    append(pesoFinal)
                }
            }
            Text(
                text = detalleReal,
                color = p.acento,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            LinearProgressIndicator(
                progress = { tarea.progreso.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = p.acento,
                trackColor = p.borde.copy(alpha = 0.35f)
            )
            Spacer(Modifier.height(6.dp))
            val estadoProgresoReal = buildString {
                append("${(tarea.progreso * 100).roundToInt()}% • ")
                append(extReal)
                if (pesoActualReal != null && pesoTotalReal != null) {
                    append(" • $pesoActualReal / $pesoTotalReal")
                } else if (pesoActualReal != null) {
                    append(" • $pesoActualReal")
                }
                if (!tarea.velocidadReal.isNullOrBlank()) {
                    append(" • ${tarea.velocidadReal}")
                }
            }
            Text(
                text = estadoProgresoReal,
                color = p.textoSuave,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  8) SELECTOR DE TEMA (CON MODO AUTOMÁTICO CLARO/OSCURO SEGÚN SISTEMA)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun ImaThemeSelector(modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val p = LocalImaPalette.current
    val actual = ImaThemeStore.modo
    val sistemaOscuro = isSystemInDarkTheme()

    Column(modifier) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ImaThemeMode.entries.forEach { modo ->
                MiniTema(
                    modo = modo,
                    seleccionado = (modo == actual),
                    sistemaOscuro = sistemaOscuro,
                    context = contexto
                ) {
                    ImaThemeStore.guardar(contexto, modo)
                }
            }
        }
        val textoAyuda = when (actual) {
            ImaThemeMode.AUTO -> contexto.getString(R.string.theme_auto_desc)
            ImaThemeMode.MATERIAL -> contexto.getString(R.string.theme_material_desc)
            else -> null
        }
        if (textoAyuda != null) {
            Text(
                textoAyuda,
                color = p.textoSuave,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MiniTema(
    modo: ImaThemeMode,
    seleccionado: Boolean,
    sistemaOscuro: Boolean,
    context: Context,
    onClick: () -> Unit
) {
    val p = LocalImaPalette.current
    val vista = paletaPara(modo, sistemaOscuro, context)
    val forma = RoundedCornerShape(18.dp)
    val fondo: Brush = when (modo) {
        ImaThemeMode.AUTO -> Brush.horizontalGradient(
            0f to PaletaClaro.fondo[0],
            0.5f to PaletaClaro.fondo[0],
            0.5f to PaletaOscuro.fondo[0],
            1f to PaletaOscuro.fondo[0]
        )
        ImaThemeMode.RETRO -> Brush.linearGradient(
            listOf(Color(0xFFFFCA28), Color(0xFF9CCC65), Color(0xFF29B6F6), Color(0xFFEC407A))
        )
        else -> vista.pincelDeFondo()
    }
    val barraCampo = if (modo == ImaThemeMode.AUTO) Color(0xFF8E8E93) else vista.campo
    val barraBoton = if (modo == ImaThemeMode.AUTO) Color(0xFF3A3A3C) else vista.btnPrincipal
    val bordeBarra = vista.texto.copy(alpha = 0.25f)

    Column(
        Modifier
            .width(86.dp)
            .clip(forma)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(86.dp, 118.dp)
                .clip(forma)
                .background(fondo)
                .border(
                    if (seleccionado) 3.dp else 1.dp,
                    if (seleccionado) p.acento else p.texto.copy(alpha = 0.25f),
                    forma
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(58.dp, 12.dp)
                        .background(barraCampo, CircleShape)
                        .border(1.dp, bordeBarra, CircleShape)
                )
                Box(
                    Modifier
                        .size(58.dp, 12.dp)
                        .background(barraBoton, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(modo.etiquetaRes),
            color = p.texto,
            fontSize = 12.sp,
            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
