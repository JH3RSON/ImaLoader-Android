# ============================================================
# Reglas blindadas para ImaLoader (yt-dlp + FFmpeg + WorkManager)
# ============================================================

# --- 1. Motores nativos y descompresor de binarios Python/FFmpeg (NO TOCAR) ---
-keep class com.yausername.** { *; }
-keep class io.github.junkfood02.** { *; }
-dontwarn com.yausername.**

# Jackson (usado internamente por youtubedl-android)
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache Commons (usado por youtubedl-android para descomprimir libpython.zip.so)
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.**

# --- 2. WorkManager y Coroutines (evita que DownloadWorker falle en modo release) ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn kotlinx.coroutines.**

# --- 3. Atributos necesarios para JSON y reflexión ---
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# --- 4. Tus modelos y motores (Cambia "com.tuapp" por tu paquete real) ---
-keep class com.ima.imaloader.** { *; }