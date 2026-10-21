package com.ima.imaloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

object PlatformCookies {
    fun file(ctx: Context, p: Platform): File {
        val primary = File(ctx.applicationContext.filesDir, "cookies_${p.name.lowercase()}.txt")
        // Compatibilidad por si ya tenías cookies.txt guardado de antes para YouTube
        if (p == Platform.YOUTUBE && !primary.exists()) {
            val legacy = File(ctx.applicationContext.filesDir, "cookies.txt")
            if (legacy.exists() && legacy.length() > 0L) return legacy
        }
        return primary
    }

    fun has(ctx: Context, p: Platform): Boolean =
        file(ctx, p).let { it.exists() && it.length() > 0L }

    fun clear(ctx: Context, p: Platform) {
        runCatching { file(ctx, p).delete() }
        if (p == Platform.YOUTUBE) {
            runCatching { File(ctx.applicationContext.filesDir, "cookies.txt").delete() }
        }
    }

    fun importFromUri(ctx: Context, p: Platform, uri: Uri) {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            file(ctx, p).outputStream().use { input.copyTo(it) }
        }
    }

    fun saveFromWebView(ctx: Context, p: Platform): Boolean {
        val url = p.cookieUrl ?: return false
        val domain = p.cookieDomain ?: return false
        val cm = CookieManager.getInstance().also { it.flush() }
        val raw = cm.getCookie(url) ?: return false

        if (p.sessionCookie != null && p.sessionCookie !in raw) {
            return false // Aún no ha iniciado sesión realmente
        }

        val expiry = System.currentTimeMillis() / 1000L + (365L * 24L * 3600L)
        val lines = mutableListOf("# Netscape HTTP Cookie File")

        raw.split(";")
            .map { it.trim() }
            .filter { "=" in it }
            .forEach { cookie ->
                val (name, value) = cookie.split("=", limit = 2)
                if (name.isNotBlank()) {
                    lines += "$domain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
                }
            }

        file(ctx, p).writeText(lines.joinToString("\n") + "\n")
        return true
    }
}
