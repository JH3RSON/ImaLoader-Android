package com.ima.imaloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ima.imaloader.guard.DownloadGuard
import com.ima.imaloader.search.SearchScreen
import com.ima.imaloader.ui.theme.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleManager.init(this)

        
        // Habilita el dibujo Edge-to-Edge para que Compose controle los Insets del Teclado (IME)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val sharedLink = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        } else ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        setContent {
            val baseContext = LocalContext.current
            val currentLang = LocaleManager.currentLanguage
            val localizedContext = remember(currentLang) {
                LocaleManager.wrap(baseContext, currentLang)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration
            ) {
                ImaAppTheme {
                    var showSplash by remember { mutableStateOf(true) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Cargamos la app principal en el fondo para que esté lista instantáneamente
                        ImaLoaderMainApp(initialSharedUrl = sharedLink)

                        // El video se muestra encima y se desvanece suavemente
                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = tween(600))
                        ) {
                            VideoSplashScreen(
                                onVideoFinished = {
                                    showSplash = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImaLoaderMainApp(initialSharedUrl: String) {
    val p = LocalImaPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var urlInput by remember { mutableStateOf(initialSharedUrl) }
    
    var showSettingsSheet by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }
    var loginFor by remember { mutableStateOf<Platform?>(null) }
    
    val descargasActivas by ImaLiveDownloadsStore.items.collectAsState()

    val linkFlowController = remember {
        LinkFlowController(
            scope = scope,
            fetchInfoJson = { url ->
                withContext(Dispatchers.IO) {
                    val req = YoutubeDLRequest(url).apply {
                        addOption("--dump-single-json")
                        addOption("--flat-playlist")
                    }
                    YoutubeDL.getInstance().execute(req, null, null).out
                }
            },
            onStartDownload = { selection ->
                val taskId = ImaLiveDownloadsStore.iniciarTarea(selection)
                scope.launch(Dispatchers.IO) {
                    val workDir = File(context.cacheDir, "ytdlp_work_$taskId").apply { mkdirs() }
                    try {
                        DownloadGuard.protect(
                            context = context,
                            id = taskId,
                            title = selection.title,
                            downloadDir = workDir,
                            payload = null,
                            onStop = { runCatching { YoutubeDL.getInstance().destroyProcessById(taskId) } }
                        ) {
                            val request = YoutubeDLRequest(selection.url).apply {
                                selection.toYtDlpArgs().forEach { addOption(it) }
                                
                                if (selection.kind == Kind.AUDIO) {
                                    val formato = if (selection.convertirAMp3) FormatoAudio.MP3 else FormatoAudio.ORIGINAL
                                    aplicarMotorAudioPro(selection.selectorAudio, formato, RealceAudio.CALIDO_PRO)
                                }
                                
                                addOption("-o", File(workDir, "%(title).180B.%(ext)s").absolutePath)
                            }
                            YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                                DownloadGuard.progress(taskId, progress.toInt(), line)
                                ImaLiveDownloadsStore.actualizarProgresoReal(taskId, progress, etaInSeconds, line)
                            }
                            // Buscar el archivo final generado por yt-dlp/FFmpeg
                            val archivoFinal = workDir.listFiles()
                                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                                ?.maxByOrNull { it.length() }

                            if (archivoFinal != null && archivoFinal.exists()) {
                                val esAudio = selection.kind == Kind.AUDIO
                                
                                if (esAudio) {
                                    val kbpsReal = MedidorKbps.obtenerKbpsReales(archivoFinal)
                                    if (kbpsReal != null) {
                                        // Notificamos el kbpsReal medido con el analizador preciso
                                        println("ImaLoader Audio Pro - Kbps Reales Medidos: $kbpsReal kbps")
                                    }
                                }
                                
                                val (uriPublica, bytesReales) = PublicDownloadsExporter.exportarACarpetaDescargasPublica(context, archivoFinal, esAudio)
                                ImaLiveDownloadsStore.marcarCompletado(taskId, uriPublica, archivoFinal, bytesReales)
                            } else {
                                ImaLiveDownloadsStore.marcarError(taskId, "El motor terminó pero no generó el archivo de salida.")
                            }
                        }
                    } catch (e: Exception) {
                        ImaLiveDownloadsStore.marcarError(taskId, e.message ?: "Error durante la descarga.")
                    } finally {
                        runCatching { workDir.deleteRecursively() }
                    }
                }
                urlInput = ""
            }
        )
    }

    val state by linkFlowController.state.collectAsState()
    LaunchedEffect(state) {
        if (state is LinkFlowState.Failed) {
            val err = (state as LinkFlowState.Failed).message
            if (err.contains("Sign in") || err.contains("login")) {
                loginFor = Platform.detect((state as LinkFlowState.Failed).urlIntentada)
            }
        }
    }
    
    val tabHome = stringResource(R.string.tab_home)
    val tabSearch = stringResource(R.string.tab_search)
    val tabDownloads = stringResource(R.string.tab_downloads)
    val tabDonate = stringResource(R.string.tab_donate)

    val pestanas = remember(descargasActivas.isNotEmpty(), tabHome, tabSearch, tabDownloads, tabDonate) {
        listOf(
            ImaAnimNavItem(tabHome, Icons.Rounded.Home),
            ImaAnimNavItem(tabSearch, Icons.Rounded.Search),
            ImaAnimNavItem(tabDownloads, Icons.Rounded.Download, hasActiveBadge = descargasActivas.isNotEmpty()),
            ImaAnimNavItem(tabDonate, Icons.Rounded.FavoriteBorder)
        )
    }

    // Effect to auto-analyze shared URL
    LaunchedEffect(initialSharedUrl) {
        if (initialSharedUrl.isNotBlank()) linkFlowController.analyze(initialSharedUrl)
    }

    val currentPlatform = loginFor
    if (currentPlatform != null) {
        PlatformLoginScreen(currentPlatform) { ok: Boolean ->
            loginFor = null
            if (ok) linkFlowController.analyze(urlInput)
        }
    }

    ImaBackground(mostrarArte = (currentTab == 0)) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // Top bar with Gear
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    ImaGearButton(
                        icono = Icons.Filled.Settings,
                        onClick = { showSettingsSheet = !showSettingsSheet }
                    )
                }

                // Settings Panel
                AnimatedVisibility(visible = showSettingsSheet) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ImaCard(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.language_title),
                                color = p.texto,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val curLang = LocaleManager.currentLanguage
                                ImaButton(
                                    texto = "🇪🇸 Español",
                                    onClick = { LocaleManager.setLanguage(context, "es") },
                                    modifier = Modifier.weight(1f),
                                    rol = if (curLang == "es") ImaRol.PRINCIPAL else ImaRol.SUAVE
                                )
                                ImaButton(
                                    texto = "🇺🇸 English",
                                    onClick = { LocaleManager.setLanguage(context, "en") },
                                    modifier = Modifier.weight(1f),
                                    rol = if (curLang == "en") ImaRol.PRINCIPAL else ImaRol.SUAVE
                                )
                            }
                        }

                        ImaCard(Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.app_theme),
                                color = p.texto,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            ImaThemeSelector()
                        }
                        ImaYtDlpUpdaterCard(
                            versionActual = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull(),
                            onEjecutarActualizacion = {
                                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                                val nuevaVer = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull().orEmpty()
                                val huboCambio = (status == YoutubeDL.UpdateStatus.DONE)
                                Pair(huboCambio, nuevaVer)
                            }
                        )
                    }
                }
                
                // Main Content Area
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (currentTab) {
                        0 -> {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    ImaTitle(Modifier.padding(horizontal = 24.dp, vertical = 28.dp))
                                }

                                item {
                                    Column(
                                        Modifier.padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ImaCompleteLinkSection(
                                            controller = linkFlowController,
                                            enlaceTexto = urlInput,
                                            onCambioEnlace = { urlInput = it },
                                            onPegarDesdePortapapeles = {
                                                val pasted = clipMgr.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                                if (!pasted.isNullOrBlank()) {
                                                    urlInput = pasted
                                                    linkFlowController.analyze(pasted)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                Text(stringResource(R.string.search_title), color = p.texto, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(14.dp))
                                // Integrate SearchScreen inside here
                                SearchScreen(
                                    onDownload = { url: String ->
                                        urlInput = url
                                        currentTab = 0 
                                        linkFlowController.analyze(url)
                                    },
                                    vm = viewModel()
                                )
                            }
                        }
                        2 -> {
                            ImaDirectDownloadsTab(
                                modifier = Modifier.fillMaxSize(),
                                subcarpetaOpcional = "ImaLoader"
                            )
                        }
                        3 -> {
                            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                Text(stringResource(R.string.support_project_title), color = p.texto, fontSize = 30.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(14.dp))
                                ImaCard(Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(R.string.hello_im_ima),
                                        color = p.texto,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.donate_description),
                                        color = p.textoSuave,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    ImaButton(
                                        texto = stringResource(R.string.donate_button),
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, "https://ko-fi.com/ima28".toUri()).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No se encontró aplicación para abrir el enlace", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        rol = ImaRol.PRINCIPAL
                                    )
                                    
                                    Spacer(Modifier.height(24.dp))
                                    
                                    Text(
                                        stringResource(R.string.suggestions_title),
                                        color = p.texto,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.suggestions_description),
                                        color = p.textoSuave,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    ImaButton(
                                        texto = stringResource(R.string.telegram_button),
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, "https://t.me/IMA_2830".toUri()).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No se encontró aplicación para abrir el enlace", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        rol = ImaRol.SUAVE
                                    )
                                }
                            }
                        }
                    }

                    if (currentTab == 0 && p.modo == ImaThemeMode.MATERIAL) {
                        ImaFab(
                            onClick = { showSettingsSheet = !showSettingsSheet },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 20.dp, bottom = 14.dp)
                        )
                    }
                }

                // Bottom Nav Bar
                ImaGlassBottomBar(
                    items = pestanas,
                    selectedIndex = currentTab,
                    onItemSelected = { currentTab = it },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}
