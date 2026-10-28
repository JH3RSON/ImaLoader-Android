package com.ima.imaloader.guard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object GuardSetup {

    /** Android 13+ (API 33): verifica si falta el permiso para mostrar la notificación de descarga. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** true = la app está en "Sin restricciones" de batería y Doze no cortará sus procesos. */
    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Solicita directamente quitar la restricción de batería con un diálogo de 1 toque.
     * Si el sistema bloquea el intent directo, abre la lista general como respaldo.
     */
    @SuppressLint("BatteryLife")
    fun requestUnrestrictedBattery(context: Context) {
        if (isBatteryUnrestricted(context)) return
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (!open(context, directIntent)) {
            openBatterySettings(context)
        }
    }

    /** Abre la lista de optimización de batería del sistema. */
    fun openBatterySettings(context: Context) {
        open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Abre los ajustes de la app (útil en Xiaomi/Huawei/Oppo/Samsung para activar "Inicio automático"). */
    fun openAppSettings(context: Context) {
        open(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    /** Detecta fabricantes con gestión agresiva de procesos en segundo plano. */
    fun isAggressiveOem(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "samsung")
            .any { it in m }
    }

    private fun open(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
