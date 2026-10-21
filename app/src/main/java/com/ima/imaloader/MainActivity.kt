package com.ima.imaloader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ima.imaloader.ui.*
import com.ima.imaloader.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.ima.imaloader.youtube.YouTubeScreen
import java.util.Locale

import kotlin.math.roundToInt

import androidx.core.net.toUri
import java.util.UUID
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Captura enlaces compartidos desde YouTube, Instagram, Facebook, TikTok o X ("Compartir -> IMALOADER")
        val sharedLink = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else ""

        setContent {
            var currentTheme by remember { mutableStateOf(AppThemeMode.IMALOADER) }

            ProvideImaTheme(themeMode = currentTheme) {
                ImaLoaderMainApp(
                    initialSharedUrl = sharedLink,
                    currentTheme = currentTheme,
                    onThemeChange = { currentTheme = it }
                )
            }
        }
    }
}

enum class DownloadButtonState { IDLE, ANALYZING, DOWNLOADING, COMPLETED }

// Eliminada data class ActiveDownloadTask


object SizeFormatter {
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "Peso variable"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImaLoaderMainApp(
    initialSharedUrl: String,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    val palette = LocalImaPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { DownloadEngine(context) }

    var urlInput by remember { mutableStateOf(initialSharedUrl) }
    var isInputFocused by remember { mutableStateOf(false) }
    var analyzeError by remember { mutableStateOf<String?>(null) }
    var buttonState by remember { mutableStateOf(DownloadButtonState.IDLE) }

    var analyzedMedia by remember { mutableStateOf<MediaInfo?>(null) }

    // Estados de navegación, ajustes, progreso y toast
    var showSettingsSheet by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }
    var loginFor by remember { mutableStateOf<Platform?>(null) }
    var activeDownload by remember { mutableStateOf<DownloadTaskUi?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    // Eliminado loginFor duplicado
    
    var isSwitchingYtMusic by remember { mutableStateOf(false) }
    var quickPreview by remember { mutableStateOf<QuickMediaPreview?>(null) }

    // Detección instantánea al pegar o escribir un link en la barra
    LaunchedEffect(urlInput) {
        val text = urlInput.trim()
        if ("http://" !in text && "https://" !in text) {
            quickPreview = null
            return@LaunchedEffect
        }
        // 1) En menos de 150ms muestra portada HD y título en pantalla
        quickPreview = QuickPreview.fetchInstant(text)

        // 2) En paralelo va precargando los formatos completos en RAM
        buttonState = DownloadButtonState.ANALYZING
        runCatching { engine.fetchInfo(text) }
            .onSuccess { info ->
                quickPreview = QuickMediaPreview(
                    url = Platform.extractUrl(text),
                    title = info.title,
                    author = info.artist,
                    thumbnailUrl = info.thumbnail,
                    platform = Platform.detect(text),
                    isYoutubeMusic = info.isYoutubeMusic,
                )
                buttonState = DownloadButtonState.IDLE
            }.onFailure {
                buttonState = DownloadButtonState.IDLE
            }
    }

    fun triggerToast(msg: String) {
        scope.launch {
            toastMessage = msg
            delay(2400)
            if (toastMessage == msg) toastMessage = null
        }
    }

    // Progreso real gestionado por WorkManager
    var currentWorkId by remember { mutableStateOf<UUID?>(null) }
    val workInfo by currentWorkId?.let { id ->
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collectAsState(initial = null)
    } ?: remember { mutableStateOf(null) }

    // Escuchar el estado de WorkManager para actualizar la UI en tiempo real
    LaunchedEffect(workInfo) {
        val info = workInfo ?: return@LaunchedEffect

        when (info.state) {
            WorkInfo.State.RUNNING -> {
                buttonState = DownloadButtonState.DOWNLOADING
                val progressValue = info.progress.getInt(DownloadWorker.K_PROGRESS, 0)
                val phaseName = info.progress.getString(DownloadWorker.K_PHASE) ?: DownloadPhase.DOWNLOADING.name
                val phase = runCatching { DownloadPhase.valueOf(phaseName) }.getOrDefault(DownloadPhase.DOWNLOADING)
                
                activeDownload = DownloadTaskUi(
                    id = currentWorkId.toString(),
                    title = info.progress.getString(DownloadWorker.K_TITLE) ?: "Descargando...",
                    thumbnail = info.progress.getString(DownloadWorker.K_THUMB),
                    progressPct = progressValue,
                    speedText = info.progress.getString(DownloadWorker.K_SPEED),
                    etaText = info.progress.getString(DownloadWorker.K_ETA),
                    phase = phase
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                activeDownload = activeDownload?.copy(
                    progressPct = 100,
                    phase = DownloadPhase.COMPLETED,
                    speedText = "Descarga completa",
                    etaText = null
                )
                buttonState = DownloadButtonState.COMPLETED
                triggerToast("Descarga exitosa")
                delay(2000)
                activeDownload = null
                buttonState = DownloadButtonState.IDLE
                currentWorkId = null
            }
            WorkInfo.State.FAILED -> {
                val err = info.outputData.getString(DownloadWorker.K_ERROR) ?: "Error desconocido"
                analyzeError = err
                buttonState = DownloadButtonState.IDLE
                activeDownload = null
                currentWorkId = null
                triggerToast("Falló la descarga")
            }
            WorkInfo.State.CANCELLED -> {
                buttonState = DownloadButtonState.IDLE
                activeDownload = null
                currentWorkId = null
            }
            else -> {}
        }
    }

    fun executeAnalyze(linkToAnalyze: String) {
        scope.launch {
            analyzeError = null
            buttonState = DownloadButtonState.ANALYZING
            val startTime = System.currentTimeMillis()

            try {
                val info = engine.fetchInfo(linkToAnalyze)
                
                // Mantiene el spinner mínimo 500ms para fluidez visual tipo iOS
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 500) delay(500 - elapsed)

                analyzedMedia = info
                buttonState = DownloadButtonState.IDLE
                triggerToast("Enlace analizado · Calidades reales listas")
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 500) delay(500 - elapsed)
                
                buttonState = DownloadButtonState.IDLE
                analyzeError = e.message ?: "Error desconocido"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // 1. CAPA TRASERA: FONDOS ESPECIALES SEGÚN EL TEMA
        when (palette.mode) {
            AppThemeMode.IMALOADER -> ImaloaderBouncingBackground()
            AppThemeMode.FRUTIGER_AERO -> FrutigerAeroBackground()
            else -> {}
        }

        // 2. CAPA PRINCIPAL DE INTERFAZ
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    // Mini tarjeta de descarga flotante sobre la barra inferior (Sección 7)
                    AnimatedVisibility(
                        visible = activeDownload != null,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                        exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 2 }
                    ) {
                        activeDownload?.let { task ->
                            SatisfyingDownloadProgressCard(
                                task = task,
                                onCancel = {
                                    currentWorkId?.let { id ->
                                        Downloads.cancelAndClean(context, id)
                                    }
                                    activeDownload = null
                                }
                            )
                        }
                    }

                    IosBottomNavigationBar(
                        selectedTab = currentTab,
                        onSelectTab = { tab -> currentTab = tab }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    1 -> {
                        // Explorador YouTube
                        YouTubeScreen(
                            onSendToDownloads = { url ->
                                urlInput = url
                                currentTab = 2 // Cambiar a pestaña Descargas
                                executeAnalyze(url)
                            }
                        )
                    }
                    3 -> {
                        // Pestaña Donar (Limpia e informativa)
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Donar",
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                            )
                            Text(
                                text = "Apoya a ImaLoader",
                                color = palette.textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "El desarrollo de esta aplicación es independiente y gratuito.\nPuedes apoyar el proyecto para mantenerlo sin anuncios y recibir nuevas actualizaciones.",
                                color = palette.textSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        // Inicio / Descargas (0 y 2)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            // ENCABEZADO: Logo del ratón + IMALOADER + "hecho con ❤️ por Ima" + Ajustes arriba a la derecha
                            item {
                                HeaderWithLogoAndSettings(
                                    onOpenSettings = { showSettingsSheet = true }
                                )
                            }

                            // CAMPO PARA PEGAR ENLACE CON TRANSICIÓN DE FOCO iOS (180ms)
                            item {
                                LinkInputCard(
                                    value = urlInput,
                                    isFocused = isInputFocused,
                                    errorText = analyzeError,
                                    onValueChange = {
                                        urlInput = it
                                        analyzeError = null
                                    },
                                    onFocusChange = { isInputFocused = it },
                                    onPasteClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                        if (!pasted.isNullOrBlank()) {
                                            urlInput = pasted
                                            analyzeError = null
                                            executeAnalyze(pasted)
                                        } else {
                                            analyzeError = "El portapapeles está vacío"
                                        }
                                    },
                                    onAnalyzeClick = {
                                        if (urlInput.isBlank()) {
                                            analyzeError = "Primero pega un enlace de YouTube, Facebook, Instagram, TikTok o X"
                                        } else {
                                            executeAnalyze(urlInput)
                                        }
                                    },
                                    onLoginClick = { p, has ->
                                        if (has) {
                                            PlatformCookies.clear(context, p)
                                            CookieManager.getInstance().removeAllCookies(null)
                                        }
                                        loginFor = p
                                    },
                                    buttonState = buttonState
                                )
                            }

                            // TARJETA DE RESULTADO Y SELECTOR DE CALIDAD / FORMATO REAL
                            // (La UI anterior ConfigureDownloadCard fue reemplazada por DownloadSelectorSheet)
                        }
                    }
                }
            }
        }

        // 3. TOAST DISCRETO ESTILO iOS (Sección 8.1)
        AnimatedVisibility(
            visible = toastMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp),
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { 24 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { 16 }
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = palette.surfaceElevated.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, palette.borderSubtle),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = palette.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = toastMessage.orEmpty(),
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 4. MODAL DE AJUSTES (Esquina superior derecha) CON SELECTOR DE LOS 6 TEMAS Y PAYPAL DISCRETO
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = if (palette.isGlassTheme) Color(0xE6E3F2FD) else palette.surfaceElevated,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                SettingsSheetContent(
                    currentTheme = currentTheme,
                    onSelectTheme = { onThemeChange(it) },
                    onOpenPayPal = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://www.paypal.me/".toUri())
                        context.startActivity(intent)
                    }
                )
            }
        }
        
        // 4.5. MENSAJES DE ERROR CON LOGIN (AUDIENCIA RESTRINGIDA)
        // (Ocultado intencionalmente para manejar en LinkInputCard)

        // pantalla de login
        loginFor?.let { p ->
            PlatformLoginScreen(p) { ok ->
                loginFor = null
                if (ok) executeAnalyze(urlInput) // reintenta si se logueo con exito
            }
        }

        // 5. SELECTOR DE DESCARGA
        analyzedMedia?.let { info ->
            FormatSelectorSheet(
                info = info,
                currentUrl = urlInput,
                isSwitchingToYtMusic = isSwitchingYtMusic,
                onSwitchToYoutubeMusic = { ytMusicUrl ->
                    scope.launch {
                        isSwitchingYtMusic = true
                        urlInput = ytMusicUrl
                        runCatching { engine.fetchInfo(ytMusicUrl) }
                            .onSuccess { newInfo -> analyzedMedia = newInfo }
                        isSwitchingYtMusic = false
                    }
                },
                onDismiss = { analyzedMedia = null },
                onConfirm = { target ->
                    val workId = Downloads.enqueue(context, info.originalUrl, target, info)
                    currentWorkId = workId
                    triggerToast("Descarga iniciada en segundo plano")
                    analyzedMedia = null
                }
            )
        }

        // 5. PANTALLA DE SELECCIÓN PERSONALIZADA (ELIMINADA PUES AHORA ESTÁ INTEGRADA EN LA TARJETA PRINCIPAL)
    }
}

// Icono de ratón con engranaje (Mouse Gear)
@Composable
fun MouseGearIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Silueta simplificada de un ratón
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.3f, h * 0.4f),
            size = Size(w * 0.4f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.2f, h * 0.2f),
            style = Stroke(width = w * 0.08f)
        )
        // Rueda de scroll (engranaje)
        drawCircle(
            color = tint,
            radius = w * 0.08f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.6f)
        )
        // Oreja izquierda (engranaje simplificado)
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.35f),
            style = Stroke(width = w * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.04f, w * 0.04f)))
        )
        // Oreja derecha
        drawCircle(
            color = tint,
            radius = w * 0.12f,
            center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.35f),
            style = Stroke(width = w * 0.06f)
        )
    }
}

/**
 * ENCABEZADO PRINCIPAL:
 * - Icono del ratón (logo2.png recortado y adaptado)
 * - Título IMALOADER 3.0
 * - Subtítulo "hecho con ❤️ por Ima" con pulso suave
 * - Botón de Ajustes en la esquina superior derecha
 */
@Composable
fun HeaderWithLogoAndSettings(onOpenSettings: () -> Unit) {
    val palette = LocalImaPalette.current
    val infiniteTransition = rememberInfiniteTransition(label = "heartBeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tu icono del ratón neón adaptado y recortado con bordes redondeados tipo iOS
            ImaMouseLogoBadge(size = 54.dp)

            Column {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "IMALOADER",
                        color = palette.textPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "3.0",
                        color = palette.secondaryAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // "hecho con ❤️ por Ima" debajo del título como pediste
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "hecho con",
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Corazón",
                        tint = Color(0xFFFF453A),
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                    Text(
                        text = "por Ima",
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Botón de Ajustes en la esquina superior derecha
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
                .iosPressable(targetScale = 0.94f, onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            MouseGearIcon(
                tint = palette.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * ICONO DEL RATÓN NEÓN RECORTADO Y ADAPTADO:
 * Carga tu archivo logo2.png si lo guardas como `res/drawable/logo_imaloader.png`.
 * Si aún no lo pegas en drawable, renderiza automáticamente el emblema con los colores exactos del ratón.
 */
@Composable
fun ImaMouseLogoBadge(size: Dp) {
    val context = LocalContext.current
    val drawableId = remember {
        context.resources.getIdentifier("logo_imaloader", "drawable", context.packageName)
    }

    Box(
        modifier = Modifier
            .size(size)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF141836), Color(0xFF241B35))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF26C6DA), Color(0xFFAB47BC))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (drawableId != 0) {
            Image(
                painter = painterResource(id = drawableId),
                contentDescription = "Logo IMALOADER",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Respaldo vectorial con la estética de tu ratón + flecha de descarga cian
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Logo IMALOADER",
                tint = Color(0xFF26C6DA),
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

/**
 * CAMPO DE PEGADO DE ENLACES CON ANIMACIÓN DE FOCO Y RESPLANDOR TENUE
 */
@Composable
fun LinkInputCard(
    value: String,
    isFocused: Boolean,
    errorText: String?,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onPasteClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onLoginClick: (Platform, Boolean) -> Unit,
    buttonState: DownloadButtonState
) {
    val palette = LocalImaPalette.current
    val isImaloaderClassic = palette.mode == AppThemeMode.IMALOADER

    val borderColor by animateColorAsState(
        targetValue = when {
            errorText != null -> palette.error
            isFocused -> palette.primaryAccent
            else -> palette.borderSubtle
        },
        animationSpec = tween(180),
        label = "inputBorder"
    )

    val fieldShape = if (palette.mode == AppThemeMode.FRUTIGER_AERO || isImaloaderClassic) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(16.dp)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(fieldShape)
                .background(palette.inputFieldBg)
                .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = fieldShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = if (isImaloaderClassic) Color(0xFF4A3570) else palette.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Pega un enlace de YouTube, FB, IG, TikTok…",
                            color = if (isImaloaderClassic) Color(0xFF5E4B80) else palette.textSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            color = if (isImaloaderClassic) Color(0xFF1F1630) else palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { onFocusChange(it.isFocused) }
                    )
                }

                // Botón Limpiar
                AnimatedVisibility(
                    visible = value.isNotEmpty(),
                    enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.textSecondary.copy(alpha = 0.15f))
                            .iosPressable(targetScale = 0.9f) { onValueChange("") }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Borrar",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                // Botón Pegar
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.16f))
                        .iosPressable(targetScale = 0.94f, onClick = onPasteClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Pegar",
                        color = if (isImaloaderClassic) Color(0xFF4A2574) else palette.primaryAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = errorText != null) {
            val e = try {
                val kindStr = errorText!!.substringBefore(":").trim()
                val kind = runCatching { ErrorKind.valueOf(kindStr) }.getOrDefault(ErrorKind.UNKNOWN)
                DownloadException(kind, errorText.substringAfter(":").trim())
            } catch (ex: Exception) {
                DownloadException(ErrorKind.UNKNOWN, errorText!!)
            }
            val p = Platform.detect(value)
            val ctx = LocalContext.current
            val has = PlatformCookies.has(ctx, p)
            
            Column {
                Text(
                    text = friendlyError(value, e, has),
                    color = palette.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp)
                )
                
                if (shouldOfferLogin(value, e)) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onLoginClick(p, has) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(if (has) "Usar otra cuenta" else "Iniciar sesión en ${p.label}", fontSize = 12.sp)
                    }
                }
            }
        }

        // Botón principal "Analizar enlace" con transformación a spinner (Especificación 2)
        IosPrimaryActionButton(
            text = "Analizar enlace",
            state = if (buttonState == DownloadButtonState.ANALYZING) DownloadButtonState.ANALYZING else DownloadButtonState.IDLE,
            onClick = onAnalyzeClick
        )
    }
}





/**
 * BOTÓN PRINCIPAL CON TRANSICIÓN DE ESTADOS iOS (Sección 2 de Animaciones):
 * - Escala 0.96 en 80ms, retorno spring 200ms.
 * - Al iniciar: sustituye el texto por un indicador circular animado sin cambiar el tamaño del botón.
 * - Al terminar: reemplaza el indicador por un check mediante escala y fade de 200ms.
 */
@Composable
fun IosPrimaryActionButton(
    text: String,
    state: DownloadButtonState,
    onClick: () -> Unit
) {
    val palette = LocalImaPalette.current
    val buttonBg by animateColorAsState(
        targetValue = if (state == DownloadButtonState.COMPLETED) palette.success else palette.primaryAccent,
        animationSpec = tween(220),
        label = "btnColor"
    )

    val shape = if (palette.mode == AppThemeMode.FRUTIGER_AERO || palette.mode == AppThemeMode.IMALOADER) {
        RoundedCornerShape(26.dp)
    } else {
        RoundedCornerShape(15.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(buttonBg)
            .iosPressable(
                targetScale = 0.96f,
                enabled = state == DownloadButtonState.IDLE,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Reflejo superior sutil para el tema Frutiger Aero
        if (palette.isGlassTheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )
        }

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f))
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "buttonMorph"
        ) { currentState ->
            when (currentState) {
                DownloadButtonState.IDLE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                DownloadButtonState.ANALYZING, DownloadButtonState.DOWNLOADING -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DownloadButtonState.COMPLETED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completado",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Descargado",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * MINI TARJETA DE PROGRESO CONTINUO SOBRE LA BARRA DE NAVEGACIÓN (Sección 7)
 */
@Composable
fun SatisfyingDownloadProgressCard(
    task: DownloadTaskUi,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progressPct.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "download_progress"
    )
    val palette = LocalImaPalette.current

    OutlinedCard(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.primaryAccent.copy(alpha = 0.35f)),
        colors = CardDefaults.outlinedCardColors(containerColor = palette.surfaceElevated),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!task.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = task.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.phase.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = palette.primaryAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${task.progressPct}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Barra de progreso con degradado fluido
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.textSecondary.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceAtLeast(0.03f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    palette.primaryAccent,
                                    palette.secondaryAccent
                                )
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stats = listOfNotNull(
                    task.speedText?.takeIf { it.isNotBlank() },
                    task.etaText?.takeIf { it.isNotBlank() }?.let { "Restan $it" }
                ).joinToString(" · ")

                Text(
                    text = stats.ifEmpty { "Procesando con máxima fidelidad..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )

                if (task.phase != DownloadPhase.COMPLETED && task.phase != DownloadPhase.EXPORTING) {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Cancelar", style = MaterialTheme.typography.labelMedium, color = palette.error)
                    }
                }
            }
        }
    }
}

/**
 * PANEL DE AJUSTES (Abierto desde la esquina superior derecha):
 * - Selector de los 6 temas (Claro, Oscuro, Automático, Material, IMALOADER, Frutiger Aero)
 * - Botón discreto y minimalista de PayPal (sin Yape, como pediste en el documento)
 */
@Composable
fun SettingsSheetContent(
    currentTheme: AppThemeMode,
    onSelectTheme: (AppThemeMode) -> Unit,
    onOpenPayPal: () -> Unit
) {
    val palette = LocalImaPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Ajustes y Apariencia",
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Selector de Tema",
            color = palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { mode ->
                val isSelected = currentTheme == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) palette.primaryAccent.copy(alpha = 0.16f)
                            else palette.surface.copy(alpha = 0.6f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) palette.primaryAccent else palette.borderSubtle,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .iosPressable(targetScale = 0.98f) { onSelectTheme(mode) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.title,
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = mode.subtitle,
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Botón discreto de PayPal en Ajustes (Sección 2.4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface.copy(alpha = 0.7f))
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
                .iosPressable(targetScale = 0.97f, onClick = onOpenPayPal)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0070BA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
                Column {
                    Text(
                        text = "Apóyanos en PayPal",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Opcional · Seguro · Hecho por Ima",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
fun GlassOrSolidCard(content: @Composable () -> Unit) {
    val palette = LocalImaPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(22.dp))
    ) {
        if (palette.isGlassTheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
        }
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun IosBottomNavigationBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val palette = LocalImaPalette.current
    val isGlass = palette.isGlassTheme
    val items = listOf(
        "Inicio" to Icons.Outlined.Home,
        "Buscar" to Icons.Outlined.Search,
        "Descargas" to Icons.Outlined.Download,
        "Donar" to Icons.Outlined.FavoriteBorder
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = if (isGlass) Color.White.copy(alpha = 0.5f) else palette.surfaceElevated.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, if (isGlass) Color.White.copy(alpha = 0.7f) else palette.borderSubtle),
            shadowElevation = if (isGlass) 0.dp else 12.dp
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, (label, icon) ->
                    val selected = selectedTab == index
                    val tint by animateColorAsState(
                        targetValue = if (selected) {
                            if (palette.mode == AppThemeMode.IMALOADER) Color(0xFF9333EA) else palette.primaryAccent
                        } else palette.textSecondary,
                        animationSpec = tween(180),
                        label = "navTint"
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (selected) tint.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .iosPressable(targetScale = 0.9f) { onSelectTab(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = label,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}