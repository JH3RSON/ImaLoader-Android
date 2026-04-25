package com.ima.imaloader

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("ImaLoader", "Error crítico", e)
        }
        setContent { MaterialTheme { MainScreen() } }
    }
}

val BackgroundColor = Color(0xFF2D2440)
val InputColor = Color(0xFFEADDFF)
val ButtonColor = Color(0xFF7E57C2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val contexto = LocalContext.current
    var link by remember { mutableStateOf("") }
    var estadoTexto by remember { mutableStateOf("🐭 ImaLoader listo para la acción") }
    var mostrarYape by remember { mutableStateOf(false) }
    var progresoDescarga by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        FondoDvdAnimado(maxWidth.value, maxHeight.value)

        Text("By Ima 🐭", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.logo), contentDescription = null, modifier = Modifier.size(90.dp))
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("IMALOADER", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("v2.0", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }

            Text(estadoTexto, color = Color(0xFFEADDFF), fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))

            if (progresoDescarga > 0f && progresoDescarga < 100f) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { progresoDescarga / 100f },
                    modifier = Modifier.fillMaxWidth(0.75f).height(12.dp),
                    color = Color(0xFFE040FB),
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
                Text("${progresoDescarga.toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("Pega un link cool aquí... 🐀", color = Color.DarkGray) },
                singleLine = true,
                shape = RoundedCornerShape(32.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = InputColor, unfocusedContainerColor = InputColor,
                    focusedTextColor = Color.Black, unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth(0.9f).height(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(0.9f), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { scope.launch { ejecutarDescarga(contexto, link, "m4a", { estadoTexto = it }, { progresoDescarga = it }) } },
                    shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = ButtonColor), modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("M4A 🎵\n(Audio Puro)", textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { scope.launch { ejecutarDescarga(contexto, link, "mp4", { estadoTexto = it }, { progresoDescarga = it }) } },
                    shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = ButtonColor), modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("MP4 🎬\n(Video HD)", textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTÓN INDEPENDIENTE PARA WINDOWS
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/JH3RSON/ImaLoader/releases/tag/v1.0.1".toUri())
                    contexto.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(0.9f).height(50.dp)
            ) {
                Icon(Icons.Default.Computer, contentDescription = null, tint = Color(0xFF00E5FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Descargar ImaLoader para Windows 💻", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { mostrarYape = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E24AA)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yapeame un cafecito ☕", color = Color.White)
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://www.paypal.com/paypalme/Jherson2830".toUri())
                        contexto.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PayPal 💙", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("⭐ Si te gusta, recomiéndanos y déjame una estrellita", color = Color(0xFFFFD54F), fontSize = 11.sp)
            Text("hecho con ❤️ por Ima", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }

        if (mostrarYape) {
            AlertDialog(
                onDismissRequest = { mostrarYape = false },
                confirmButton = { TextButton(onClick = { mostrarYape = false }) { Text("¡Gracias Ima! ✨", color = Color(0xFF8E24AA), fontWeight = FontWeight.Bold) } },
                containerColor = Color(0xFFF3E5F5),
                title = { Text("¡Invítame un cafecito! ☕", fontWeight = FontWeight.Black, color = Color(0xFF4A148C), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Image(painter = painterResource(id = R.drawable.yape_qr), contentDescription = null, modifier = Modifier.size(220.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Yapea al: 981067701", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF8E24AA))
                        Text("Jherson Imanol Flores Escobar", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            )
        }
    }
}

class EntidadDvd(xi: Float, yi: Float, var velX: Float, var velY: Float, val texto: String, val color: Color) {
    var x by mutableFloatStateOf(xi)
    var y by mutableFloatStateOf(yi)
}

@Composable
fun FondoDvdAnimado(anchoMax: Float, altoMax: Float) {
    val limiteX = anchoMax - 120f
    val limiteY = altoMax - 40f
    val entidades = remember {
        listOf(
            EntidadDvd(10f, 10f, 2f, 2.5f, "YouTube 🐭", Color(0xFFFF4B4B)),
            EntidadDvd(100f, 300f, -2.5f, 2f, "TikTok 🎵", Color(0xFF4BFFFF)),
            EntidadDvd(200f, 100f, 2.5f, -2.5f, "Facebook 💙", Color(0xFF4B8BFF)),
            EntidadDvd(50f, 500f, -2f, -3f, "Instagram 📸", Color(0xFFFF4BE1)),
            EntidadDvd(150f, 400f, 3f, 2f, "X 🐦", Color.White)
        )
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            entidades.forEach { e ->
                e.x += e.velX
                e.y += e.velY
                if (e.x <= 0f || e.x >= limiteX) e.velX *= -1
                if (e.y <= 0f || e.y >= limiteY) e.velY *= -1
            }
            delay(16)
        }
    }
    entidades.forEach { e ->
        Text(text = e.texto, color = e.color.copy(alpha = 0.12f), fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = e.x.dp, y = e.y.dp))
    }
}

suspend fun ejecutarDescarga(contexto: Context, url: String, formato: String, onStatusUpdate: (String) -> Unit, onProgressUpdate: (Float) -> Unit) {
    if (url.isEmpty()) { onStatusUpdate("🤔 Pega un link..."); return }
    withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("🐭 Afilando dientes...")
            try { YoutubeDL.getInstance().updateYoutubeDL(contexto) } catch (e: Exception) { }
            onProgressUpdate(1f)

            val directorio = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ImaLoader")
            if (!directorio.exists()) directorio.mkdirs()

            val peticion = YoutubeDLRequest(url)
            peticion.addOption("--retries", "10")
            peticion.addOption("--continue")
            peticion.addOption("--no-mtime")
            if (formato == "m4a") {
                peticion.addOption("-f", "bestaudio[ext=m4a]")
                peticion.addOption("--add-metadata")
                peticion.addOption("--embed-thumbnail")
            } else {
                peticion.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]")
            }
            peticion.addOption("-o", "${directorio.absolutePath}/%(title)s.%(ext)s")

            YoutubeDL.getInstance().execute(peticion, "ImaTask") { progress, _, _ ->
                if (progress > 0) { onProgressUpdate(progress); onStatusUpdate("🔥 Descargando...") }
            }

            // EL FIX DE ORO: Escanea TODA la carpeta forzadamente
            val archivos = directorio.listFiles()?.map { it.absolutePath }?.toTypedArray()
            if (archivos != null) {
                MediaScannerConnection.scanFile(contexto, archivos, null) { _, _ -> }
            }

            onProgressUpdate(100f)
            onStatusUpdate("💖 ¡Éxito! Revisa tu carpeta de Descargas")
        } catch (e: Exception) {
            onProgressUpdate(0f)
            onStatusUpdate("😭 Ops, link no válido o sin red.")
        }
    }
}