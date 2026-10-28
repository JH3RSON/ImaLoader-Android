package com.ima.imaloader.guard

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ima.imaloader.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadGuardService : Service() {

    companion object {
        const val ACTION_CANCEL_ALL = "ima.guard.CANCEL_ALL"
        private const val CHANNEL_ID = "ima_descargas"
        private const val NOTIF_ID = 4101
        private const val NOTIF_LIMIT_ID = 4102
        private const val LOCK_TIMEOUT_MS = 10 * 60_000L
        private const val HEARTBEAT_MS = 60_000L           // Revisa tráfico y renueva WakeLock cada 1 min
        private const val STALL_MS = 10 * 60_000L          // 10 min sin progreso ni bytes = descarga trabada
        private const val MIN_NOTIF_INTERVAL_MS = 750L     // Evita que Android silencie notificaciones por spam
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nm by lazy { getSystemService(NotificationManager::class.java) }
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    private var collecting = false
    private var stalled = false
    private var wasBlocked = true
    private var lastPercent = -2
    private var lastRx = 0L
    private var lastAliveAt = SystemClock.elapsedRealtime()
    private var lastNotifAt = 0L
    private var lastRenderedKey = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isCancelAction = intent?.action == ACTION_CANCEL_ALL

        // Regla estricta de Android: siempre llamar a startForeground antes de cualquier salida o stopSelf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                buildNotification(DownloadGuard.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                NOTIF_ID,
                buildNotification(DownloadGuard.state.value)
            )
        }

        if (isCancelAction) {
            DownloadGuard.cancelAll(keep = false)
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        if (!collecting) {
            collecting = true
            lastRx = TrafficStats.getUidRxBytes(Process.myUid())
            lastAliveAt = SystemClock.elapsedRealtime()
            scope.launch {
                DownloadGuard.state.collect { state ->
                    onStateChanged(state)
                }
            }
            scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    heartbeat()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Si el usuario cierra la app deslizándola de Recientes: mata procesos, conserva los .part y apaga el servicio. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        DownloadGuard.cancelAll(keep = true)
        stopForegroundAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    /** Android 14 (API 34) timeout callback. */
    override fun onTimeout(startId: Int) {
        handleForegroundTimeout()
    }

    /** Android 15+ (API 35) timeout callback. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundTimeout()
    }

    private fun handleForegroundTimeout() {
        DownloadGuard.onTimeLimit()
        val localizedContext = LocaleManager.wrap(this)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_warning)
            .setContentTitle(localizedContext.getString(com.ima.imaloader.R.string.download_paused))
            .setContentText(localizedContext.getString(com.ima.imaloader.R.string.download_timeout))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_LIMIT_ID, n)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        setWake(false)
        setWifi(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        setWake(false)
        setWifi(false)
        super.onDestroy()
    }

    private fun onStateChanged(s: DownloadGuard.State) {
        val now = SystemClock.elapsedRealtime()
        if (s.percent != lastPercent) {
            lastPercent = s.percent
            lastAliveAt = now
            stalled = false
        }
        val blockedNow = s.blocker != null || s.active == 0
        if (!blockedNow && wasBlocked) {
            lastAliveAt = now
            stalled = false
        }
        wasBlocked = blockedNow

        // Los bloqueos de energía/Wi-Fi se actualizan al instante sin esperar al throttle visual
        applyLocks(s)

        val renderKey = "${s.active}|${s.title}|${s.percent}|${s.statusText}|${s.blocker}|$stalled"
        val isImportantChange = s.active == 0 || s.blocker != null || lastRenderedKey.isEmpty()
        if (renderKey != lastRenderedKey && (isImportantChange || now - lastNotifAt >= MIN_NOTIF_INTERVAL_MS)) {
            lastRenderedKey = renderKey
            lastNotifAt = now
            nm.notify(NOTIF_ID, buildNotification(s))
        }
    }

    private fun heartbeat() {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getUidRxBytes(Process.myUid())
        if (rx == TrafficStats.UNSUPPORTED.toLong() || rx - lastRx > 32_768L) {
            lastAliveAt = now
            stalled = false
        }
        lastRx = rx
        val wasStalled = stalled
        stalled = now - lastAliveAt > STALL_MS
        val s = DownloadGuard.state.value
        applyLocks(s)
        if (stalled != wasStalled) {
            nm.notify(NOTIF_ID, buildNotification(s))
        }
    }

    private fun applyLocks(s: DownloadGuard.State) {
        val working = s.active > 0 && s.blocker == null && !stalled
        setWake(working)
        setWifi(working && s.onWifi)
    }

    private fun setWake(on: Boolean) {
        try {
            val l = wake ?: (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImaLoader:DownloadWakeLock")
                .apply { setReferenceCounted(false) }
                .also { wake = it }
            if (on) {
                l.acquire(LOCK_TIMEOUT_MS) // Renueva el timeout de seguridad en cada heartbeat
            } else if (l.isHeld) {
                l.release()
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun setWifi(on: Boolean) {
        try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            val l = wifi ?: (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(mode, "ImaLoader:DownloadWifiLock")
                .apply { setReferenceCounted(false) }
                .also { wifi = it }
            if (on) {
                if (!l.isHeld) l.acquire()
            } else if (l.isHeld) {
                l.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Descargas en curso",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de tus descargas en segundo plano"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun openAppIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    private fun buildNotification(s: DownloadGuard.State): Notification {
        val localizedContext = LocaleManager.wrap(this)
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadGuardService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = when {
            s.active == 0 -> localizedContext.getString(com.ima.imaloader.R.string.downloads_finished)
            s.active == 1 -> s.title.ifBlank { localizedContext.getString(com.ima.imaloader.R.string.downloading) }
            else -> localizedContext.getString(com.ima.imaloader.R.string.download_active_count, s.active)
        }
        val text = when {
            s.active == 0 -> localizedContext.getString(com.ima.imaloader.R.string.download_ready)
            s.blocker != null -> s.blocker.text
            stalled -> localizedContext.getString(com.ima.imaloader.R.string.download_stalled)
            s.statusText.isNotBlank() && s.percent >= 0 -> "${s.percent}% ·${s.statusText}"
            s.statusText.isNotBlank() -> s.statusText
            s.percent >= 0 -> "${s.percent}%"
            else -> localizedContext.getString(com.ima.imaloader.R.string.download_processing)
        }
        val paused = s.blocker != null
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (paused) R.drawable.ic_media_pause
                else R.drawable.stat_sys_download
            )
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(s.active > 0)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())

        if (s.active > 0) {
            builder.addAction(0, localizedContext.getString(com.ima.imaloader.R.string.download_cancel), cancelIntent)
            if (!paused) {
                builder.setProgress(100, s.percent.coerceAtLeast(0), s.percent < 0)
            }
        }
        return builder.build()
    }
}