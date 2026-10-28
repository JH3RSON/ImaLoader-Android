package com.ima.imaloader.ui.theme

import android.graphics.BlurMaskFilter
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// 1. CONSTANTES DE TIEMPO Y ACCESIBILIDAD
// ============================================================================
object ImaAnimSpecs {
    const val PRESS_DURATION = 110          // 90–130 ms
    const val RECOVERY_DURATION = 180       // 150–220 ms
    const val GLOW_DURATION = 200           // 150–250 ms
    const val SELECTION_DURATION = 240      // 180–280 ms
    const val SCREEN_TRANSITION = 260       // 220–320 ms
    const val CARD_ENTER_DURATION = 250     // 200–300 ms
    const val THEME_TRANSITION = 320        // 250–400 ms
    const val DOWNLOAD_CONFIRM = 300        // 250–400 ms
}

/**
 * Respeta la preferencia del sistema si el usuario desactivó o redujo las animaciones.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale > 0f
    }
}

// ============================================================================
// 2. MODIFICADOR MAESTRO PARA BOTONES: PRESIÓN 96%, VIDRIO, REFLEJO Y SOMBRA
// ============================================================================
/**
 * Aplica a cualquier botón existente sin romper tu tema:
 * - Escala al 96% al presionar y rebote elástico al soltar.
 * - Borde de vidrio satinado y barrido de luz diagonal al tocar.
 * - Sombra/halo exterior dinámico adaptado al color del tema activo.
 * - Vibración visual horizontal suave si pasas un incremento en [errorShakeTrigger].
 */
fun Modifier.imaGlassButton(
    interactionSource: MutableInteractionSource,
    shape: Shape = RoundedCornerShape(50),
    glowColor: Color? = null,
    pressScale: Float = 0.96f, // Puedes pasar 0.92f si detectas tu tema Retro
    enableHaptics: Boolean = true,
    errorShakeTrigger: Int = 0
): Modifier = composed {
    val animEnabled = rememberAnimationsEnabled()
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val activeGlowColor = glowColor ?: MaterialTheme.colorScheme.primary

    // Retroalimentación háptica ligera al tocar
    LaunchedEffect(isPressed) {
        if (isPressed && enableHaptics) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // 1. Escala física (96% al presionar, resorte elástico al soltar)
    val scale by animateFloatAsState(
        targetValue = if (isPressed && animEnabled) pressScale else 1f,
        animationSpec = if (isPressed) {
            tween(durationMillis = ImaAnimSpecs.PRESS_DURATION, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 0.55f, stiffness = 500f)
        },
        label = "ButtonPressScale"
    )

    // 2. Intensidad del halo / brillo en el borde (150-250ms)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed && animEnabled) 0.55f else 0.15f,
        animationSpec = tween(durationMillis = ImaAnimSpecs.GLOW_DURATION),
        label = "ButtonGlowAlpha"
    )

    // 3. Barrido de reflejo de luz sobre vidrio al tocar
    val shimmerSweep = remember { Animatable(-0.4f) }
    LaunchedEffect(isPressed) {
        if (isPressed && animEnabled) {
            shimmerSweep.snapTo(-0.4f)
            shimmerSweep.animateTo(
                targetValue = 1.4f,
                animationSpec = tween(durationMillis = 230, easing = LinearOutSlowInEasing)
            )
        }
    }

    // 4. Sombra y profundidad dinámica (se hunde al presionar)
    val shadowRadiusDp by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 12.dp,
        animationSpec = tween(durationMillis = ImaAnimSpecs.PRESS_DURATION),
        label = "DynamicShadow"
    )

    // 5. Micro-sacudida discreta en caso de error (ej. portapapeles vacío)
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(errorShakeTrigger) {
        if (errorShakeTrigger > 0 && animEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing)
            )
            shakeAnim.snapTo(0f)
        }
    }
    val shakeOffsetPx = with(LocalDensity.current) {
        (sin(shakeAnim.value * Math.PI * 4) * (1f - shakeAnim.value) * 6.dp.toPx()).roundToInt()
    }

    this
        .offset { IntOffset(x = shakeOffsetPx, y = 0) }
        .scale(scale)
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }

            // A) Halo y sombra exterior dinámica
            val blurRadiusPx = shadowRadiusDp.toPx().coerceAtLeast(1f)
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = activeGlowColor.copy(alpha = glowAlpha).toArgb()
                        maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                canvas.drawPath(path, paint)
            }

            // B) Contenido original de tu botón
            drawContent()

            // C) Reflejo de luz diagonal sobre vidrio al tocar
            if (shimmerSweep.value in -0.3f..1.3f) {
                clipPath(path) {
                    val startX = size.width * shimmerSweep.value
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f),
                                activeGlowColor.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            start = Offset(startX - size.width * 0.35f, 0f),
                            end = Offset(startX + size.width * 0.15f, size.height)
                        )
                    )
                }
            }

            // D) Borde especular tipo vidrio (bisel de luz superior/inferior)
            val borderStroke = if (isPressed) 1.8.dp.toPx() else 1.dp.toPx()
            val glassBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isPressed) 0.65f else 0.30f),
                    activeGlowColor.copy(alpha = if (isPressed) 0.85f else 0.35f),
                    Color.White.copy(alpha = 0.10f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
            drawPath(
                path = path,
                brush = glassBrush,
                style = Stroke(width = borderStroke)
            )
        }
}

// ============================================================================
// 3. MODIFICADOR PARA EL CAMPO DE ENLACE (INPUT CON FOCO Y VALIDACIÓN)
// ============================================================================
/**
 * Aplica al contenedor o TextField donde se pega el enlace:
 * - Se ilumina suavemente al recibir foco o al escribir.
 * - Destella brevemente en color de confirmación cuando detecta un enlace válido.
 */
fun Modifier.imaFocusableInput(
    interactionSource: MutableInteractionSource,
    isValidLink: Boolean = false,
    cornerRadius: Dp = 50.dp
): Modifier = composed {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val validColor = Color(0xFF34C759) // Verde confirmación suave tipo iOS

    // Destello temporal cuando el enlace pasa a ser válido
    var showValidFlash by remember { mutableStateOf(false) }
    LaunchedEffect(isValidLink) {
        if (isValidLink) {
            showValidFlash = true
            delay(900)
            showValidFlash = false
        }
    }

    val targetBorderColor = when {
        showValidFlash -> validColor
        isFocused -> primaryColor
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = ImaAnimSpecs.GLOW_DURATION),
        label = "InputBorderColor"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused || showValidFlash) 0.42f else 0f,
        animationSpec = tween(durationMillis = ImaAnimSpecs.GLOW_DURATION),
        label = "InputGlowAlpha"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused || showValidFlash) 1.8.dp else 1.dp,
        animationSpec = tween(durationMillis = ImaAnimSpecs.GLOW_DURATION),
        label = "InputBorderWidth"
    )

    this.drawBehind {
        val radiusPx = cornerRadius.toPx()
        // Resplandor exterior cuando está enfocado
        if (glowAlpha > 0.01f) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = borderColor.copy(alpha = glowAlpha).toArgb()
                        maskFilter = BlurMaskFilter(14.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                canvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    radiusPx, radiusPx, paint
                )
            }
        }
        // Borde definido
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = borderWidth.toPx())
        )
    }
}

// ============================================================================
// 4. BOTÓN "ANALIZAR ENLACE" CON ESTADOS SIN SALTOS DE TAMAÑO
// ============================================================================
enum class AnalyzeButtonState { Idle, Analyzing, Success, Error }

@Composable
fun ImaAnalyzeButton(
    state: AnalyzeButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleText: String = "Analizar enlace",
    analyzingText: String = "Analizando enlace…"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val errorTrigger = remember(state) { if (state == AnalyzeButtonState.Error) 1 else 0 }

    Button(
        onClick = { if (state == AnalyzeButtonState.Idle || state == AnalyzeButtonState.Error) onClick() },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp) // Altura fija para evitar cualquier salto en la interfaz
            .imaGlassButton(
                interactionSource = interactionSource,
                errorShakeTrigger = errorTrigger
            )
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.88f, animationSpec = tween(180)))
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "AnalyzeButtonContent"
        ) { currentState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (currentState) {
                    AnalyzeButtonState.Idle -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(idleText, fontWeight = FontWeight.SemiBold)
                    }
                    AnalyzeButtonState.Analyzing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.2.dp,
                            color = LocalContentColor.current
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(analyzingText, fontWeight = FontWeight.Medium)
                    }
                    AnalyzeButtonState.Success -> {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Enlace listo", fontWeight = FontWeight.SemiBold)
                    }
                    AnalyzeButtonState.Error -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reintentar análisis", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. BARRA DE NAVEGACIÓN INFERIOR CON CÁPSULA DESLIZANTE Y RESPLANDOR
// ============================================================================
data class ImaAnimNavItem(
    val label: String,
    val icon: ImageVector,
    val hasActiveBadge: Boolean = false // Útil para indicar descarga activa en "Descargas"
)

@Composable
fun ImaGlassBottomBar(
    items: List<ImaAnimNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(surfaceColor)
            .drawBehind {
                // Borde de vidrio sutil alrededor de toda la barra
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    cornerRadius = CornerRadius(34.dp.toPx(), 34.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        val tabWidth = maxWidth / items.size.coerceAtLeast(1)

        // 1. Indicador de cápsula deslizante (se desplaza suavemente en lugar de saltar)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = tween(
                durationMillis = ImaAnimSpecs.SELECTION_DURATION,
                easing = FastOutSlowInEasing
            ),
            label = "BottomBarCapsuleSlide"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(primaryColor.copy(alpha = 0.18f))
                .drawBehind {
                    // Resplandor suave detrás de la pestaña activa
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            asFrameworkPaint().apply {
                                isAntiAlias = true
                                color = primaryColor.copy(alpha = 0.38f).toArgb()
                                maskFilter = BlurMaskFilter(16.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                            }
                        }
                        canvas.drawRoundRect(
                            8f, 8f, size.width - 8f, size.height - 8f,
                            28.dp.toPx(), 28.dp.toPx(), paint
                        )
                    }
                }
        )

        // 2. Iconos y etiquetas con micro-rebote y cambio gradual de color
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex

                // El icono crece un poco al seleccionarse y vuelve a su escala
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.14f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                    label = "NavIconScale"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    animationSpec = tween(durationMillis = ImaAnimSpecs.SELECTION_DURATION),
                    label = "NavContentColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (selectedIndex != index) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(iconScale)
                            )
                            // Pequeño punto indicador si hay una descarga activa
                            if (item.hasActiveBadge) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-1).dp)
                                        .clip(CircleShape)
                                        .background(primaryColor)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 6. SELECTOR DESLIZANTE (PARA AUDIO / VIDEO / COMANDOS O CALIDADES)
// ============================================================================
@Composable
fun ImaSegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(ImaAnimSpecs.SELECTION_DURATION, easing = FastOutSlowInEasing),
            label = "SegmentSlide"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(17.dp))
                .background(primaryColor.copy(alpha = 0.25f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, text ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(ImaAnimSpecs.SELECTION_DURATION),
                    label = "SegmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOptionSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. TRANSICIONES DE PANTALLA, TARJETAS DE RESULTADO Y BOTÓN DE AJUSTES
// ============================================================================
object ImaTransitions {
    /**
     * Transición suave entre secciones (Inicio, Buscar, Descargas, Donar) o Ajustes:
     * Deslizamiento horizontal corto (12% del ancho) + desvanecimiento (260ms).
     */
    fun <S : Comparable<S>> AnimatedContentTransitionScope<S>.sectionTransition(): ContentTransform {
        val isForward = targetState > initialState
        val enterSlide = slideInHorizontally(
            animationSpec = tween(ImaAnimSpecs.SCREEN_TRANSITION, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> if (isForward) (fullWidth * 0.12f).toInt() else -(fullWidth * 0.12f).toInt() }
        ) + fadeIn(tween(ImaAnimSpecs.SCREEN_TRANSITION))

        val exitSlide = slideOutHorizontally(
            animationSpec = tween(ImaAnimSpecs.SCREEN_TRANSITION, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> if (isForward) -(fullWidth * 0.12f).toInt() else (fullWidth * 0.12f).toInt() }
        ) + fadeOut(tween(ImaAnimSpecs.SCREEN_TRANSITION / 2))

        return enterSlide.togetherWith(exitSlide)
    }

    /**
     * Aparición de la tarjeta de resultado tras analizar un enlace (250ms).
     */
    val resultCardEnter: EnterTransition = fadeIn(
        animationSpec = tween(ImaAnimSpecs.CARD_ENTER_DURATION)
    ) + slideInVertically(
        animationSpec = tween(ImaAnimSpecs.CARD_ENTER_DURATION, easing = FastOutSlowInEasing),
        initialOffsetY = { it / 6 }
    )

    val resultCardExit: ExitTransition = fadeOut(
        animationSpec = tween(180)
    ) + scaleOut(targetScale = 0.96f, animationSpec = tween(180))
}

/**
 * Envuelve el icono de Ajustes (tuerca) para que gire suavemente al abrir/cerrar.
 */
@Composable
fun Modifier.imaSettingsGearRotation(isSettingsOpen: Boolean): Modifier {
    val rotation by animateFloatAsState(
        targetValue = if (isSettingsOpen) 60f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "SettingsGearRotation"
    )
    return this.graphicsLayer { rotationZ = rotation }
}