package com.ima.imaloader.guard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object DownloadGuard {

    private const val TAG = "DownloadGuard"
    private const val MB = 1024L * 1024L
    private const val STOP_GRACE_MS = 30_000L          // 30 s de margen entre elementos de una cola/playlist
    private const val OFFLINE_HARD_STOP_MS = 15_000L   // 15 s sin red antes de matar el socket colgado

    object Config {
        @Volatile var wifiOnly = false
        @Volatile var minFreeBytes = 300 * MB
        @Volatile var minBatteryPercent = 5
        @Volatile var pauseWhenHot = true
        @Volatile var maxRetries = 3
        @Volatile var requireValidatedNetwork = true
    }

    enum class Blocker(val text: String, val hardStop: Boolean) {
        NO_NETWORK("En pausa: reconectando a internet…", false),
        NO_NETWORK_LONG("En pausa: sin internet. Seguirá sola al volver", true),
        WIFI_ONLY("En pausa: esperando conexión Wi-Fi", true),
        LOW_STORAGE("En pausa: poco almacenamiento disponible", true),
        LOW_BATTERY("En pausa: batería muy baja. Conecta el cargador", true),
        HOT("En pausa: enfriando el dispositivo", true)
    }

    data class State(
        val active: Int = 0,
        val title: String = "",
        val percent: Int = -1,          // -1 = indeterminado (ej. procesando con FFmpeg)
        val statusText: String = "",    // Texto opcional de fase (ej. "Convirtiendo audio…")
        val blocker: Blocker? = null,
        val onWifi: Boolean = false,
        val timeLimit: Boolean = false
    )

    data class Pending(val id: String, val title: String, val payload: String)

    private data class NetInfo(val online: Boolean, val unmetered: Boolean, val wifi: Boolean)
    private val OFFLINE = NetInfo(online = false, unmetered = false, wifi = false)
    private class Entry(val title: String, val job: Job?, val onStop: (() -> Unit)?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val percents = ConcurrentHashMap<String, Int>()
    private val statuses = ConcurrentHashMap<String, String>()
    private val keepOnCancel: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val net = MutableStateFlow(OFFLINE)

    private lateinit var app: Context
    @Volatile private var inited = false
    @Volatile private var storageDir: File? = null
    @Volatile private var offlineSince: Long = 0L
    private var monitor: Job? = null
    private var stopJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (inited) return
        app = context.applicationContext
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            updateNetState(cm)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetState(cm, network)
                    refresh()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    net.value = readNet(caps)
                    if (net.value.online) offlineSince = 0L
                    refresh()
                }
                override fun onLost(network: Network) {
                    updateNetState(cm)
                    refresh()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo vigilar la red: ${e.message}")
            net.value = NetInfo(online = true, unmetered = true, wifi = true)
        }
        inited = true
    }

    private fun updateNetState(cm: ConnectivityManager, fallbackNet: Network? = null) {
        val active = cm.activeNetwork ?: fallbackNet
        val caps = active?.let { runCatching { cm.getNetworkCapabilities(it) }.getOrNull() }
        val info = readNet(caps)
        net.value = info
        if (info.online) {
            offlineSince = 0L
        } else if (offlineSince == 0L) {
            offlineSince = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Lanza una descarga protegida en el scope propio de DownloadGuard (sobrevive aunque se cierre la Activity).
     */
    fun launchProtected(
        context: Context,
        id: String,
        title: String,
        downloadDir: File? = null,
        payload: String? = null,
        onStop: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ): Job = ioScope.launch {
        try {
            protect(context, id, title, downloadDir, payload, onStop, block)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onError?.invoke(t)
        }
    }

    /**
     * Envuelve tu función de descarga existente sin cambiar tu arquitectura.
     */
    suspend fun <T> protect(
        context: Context,
        id: String,
        title: String,
        downloadDir: File? = null,
        payload: String? = null,
        onStop: (() -> Unit)? = null,
        block: suspend () -> T
    ): T {
        init(context)
        downloadDir?.let { storageDir = it }
        val job = currentCoroutineContext()[Job]
        check(register(id, title, job, onStop, payload)) { "La descarga '$id' ya está en curso" }
        try {
            var failures = 0
            while (true) {
                refresh()
                state.first { it.blocker == null }
                var pausedByUs = false
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val result = coroutineScope {
                        val work = async { block() }
                        var done = false
                        val killer = if (onStop != null) launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                state.first { it.blocker?.hardStop == true }
                                pausedByUs = true
                                work.cancel()
                            } finally {
                                if (!done) runCatching { onStop() }
                            }
                        } else null
                        try {
                            work.await().also { done = true }
                        } finally {
                            killer?.cancel()
                        }
                    }
                    if (pausedByUs) continue
                    return result
                } catch (e: CancellationException) {
                    if (pausedByUs && currentCoroutineContext().isActive) continue
                    throw e
                } catch (e: Exception) {
                    if (pausedByUs) continue
                    if (SystemClock.elapsedRealtime() - startedAt > 60_000L) failures = 0
                    if (!net.value.online) {
                        delay(1_500L)
                        continue
                    }
                    if (isTransient(e) && failures < Config.maxRetries) {
                        failures++
                        delay(1_000L shl failures) // Backoff exponencial: 2 s, 4 s, 8 s
                        continue
                    }
                    throw e
                }
            }
        } finally {
            unregister(id)
        }
    }

    /**
     * Reporta progreso (-1 para indeterminado, 0..100 para porcentaje) y texto opcional (ej. "Extrayendo audio…").
     */
    fun progress(id: String, percent: Int, statusText: String? = null) {
        if (!entries.containsKey(id)) return
        percents[id] = if (percent < 0) -1 else percent.coerceIn(0, 100)
        if (statusText != null) statuses[id] = statusText
        val p = avgPercent()
        val st = statuses[id].orEmpty()
        _state.update { cur ->
            if (cur.percent == p && cur.statusText == st) cur
            else cur.copy(percent = p, statusText = st)
        }
    }

    fun isActive(id: String): Boolean = entries.containsKey(id)
    fun hasActive(): Boolean = entries.isNotEmpty()

    /** Cancela una descarga específica de inmediato. */
    fun cancel(id: String, keepPending: Boolean = false) {
        val e = entries.remove(id) ?: return
        if (keepPending) keepOnCancel.add(id)
        runCatching { e.onStop?.invoke() }
        e.job?.cancel(CancellationException("Cancelada por el usuario"))
        cleanupAfterRemoval(id)
    }

    /**
     * Cancela todas las descargas de inmediato.
     * keep = true conserva el registro pendiente y los archivos parciales para reanudar luego.
     */
    fun cancelAll(keep: Boolean = false) {
        val snapshot = entries.toMap()
        entries.clear()
        percents.clear()
        statuses.clear()
        snapshot.forEach { (id, e) ->
            if (keep) {
                keepOnCancel.add(id)
            } else {
                runCatching { removePending(id) }
            }
            runCatching { e.onStop?.invoke() }
            e.job?.cancel(CancellationException("Detenida por DownloadGuard"))
        }
        monitor?.cancel()
        monitor = null
        stopJob?.cancel()
        stopJob = null
        _state.update { State(timeLimit = it.timeLimit) }
    }

    fun pending(context: Context): List<Pending> =
        prefs(context).all.mapNotNull { (k, v) ->
            if (!k.startsWith("p_") || v !is String) return@mapNotNull null
            runCatching {
                val o = JSONObject(v)
                Pending(k.removePrefix("p_"), o.getString("t"), o.getString("p"))
            }.getOrNull()
        }

    fun clearPending(context: Context, id: String) {
        prefs(context).edit().remove("p_$id").apply()
    }

    internal fun onTimeLimit() {
        cancelAll(keep = true)
        _state.update { it.copy(timeLimit = true) }
    }

    private fun register(
        id: String,
        title: String,
        job: Job?,
        onStop: (() -> Unit)?,
        payload: String?
    ): Boolean = synchronized(this) {
        if (entries.containsKey(id)) return@synchronized false
        entries[id] = Entry(title, job, onStop)
        payload?.let { p -> runCatching { savePending(id, title, p) } }
        stopJob?.cancel()
        stopJob = null
        val first = entries.size == 1
        _state.update {
            it.copy(
                active = entries.size,
                title = title,
                percent = avgPercent(),
                timeLimit = false
            )
        }
        if (first) {
            startMonitor()
            startService()
        }
        refresh()
        true
    }

    private fun unregister(id: String) = synchronized(this) {
        entries.remove(id)
        cleanupAfterRemoval(id)
    }

    private fun cleanupAfterRemoval(id: String) = synchronized(this) {
        percents.remove(id)
        statuses.remove(id)
        if (!keepOnCancel.remove(id)) runCatching { removePending(id) }
        if (entries.isEmpty()) {
            monitor?.cancel()
            monitor = null
            _state.value = State(timeLimit = _state.value.timeLimit)
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(STOP_GRACE_MS)
                stopServiceIfIdle()
            }
        } else {
            val firstEntry = entries.entries.firstOrNull()
            _state.update {
                it.copy(
                    active = entries.size,
                    title = firstEntry?.value?.title.orEmpty(),
                    percent = avgPercent(),
                    statusText = firstEntry?.key?.let { k -> statuses[k] }.orEmpty()
                )
            }
        }
    }

    private fun avgPercent(): Int {
        val valid = percents.values.filter { it >= 0 }
        return if (valid.isEmpty()) -1 else valid.average().toInt()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadGuardService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar DownloadGuardService en primer plano: ${e.message}")
        }
    }

    private fun stopServiceIfIdle() = synchronized(this) {
        if (entries.isEmpty() && inited) {
            runCatching { app.stopService(Intent(app, DownloadGuardService::class.java)) }
        }
    }

    private fun startMonitor() {
        if (monitor?.isActive == true) return
        monitor = scope.launch {
            while (isActive) {
                refresh()
                delay(4_000L)
            }
        }
    }

    private fun refresh() {
        if (!inited) return
        val b = computeBlocker(_state.value.blocker)
        val wifi = net.value.wifi
        _state.update {
            if (it.blocker == b && it.onWifi == wifi) it
            else it.copy(blocker = b, onWifi = wifi)
        }
    }

    private fun computeBlocker(previous: Blocker?): Blocker? {
        val n = net.value
        if (!n.online) {
            val elapsed = if (offlineSince > 0L) SystemClock.elapsedRealtime() - offlineSince else 0L
            return if (elapsed >= OFFLINE_HARD_STOP_MS) Blocker.NO_NETWORK_LONG else Blocker.NO_NETWORK
        }
        if (Config.wifiOnly && !n.unmetered) return Blocker.WIFI_ONLY
        val margin = if (previous == Blocker.LOW_STORAGE) 100 * MB else 0L
        if (freeBytes() < Config.minFreeBytes + margin) return Blocker.LOW_STORAGE
        if (batteryLow(wasLow = previous == Blocker.LOW_BATTERY)) return Blocker.LOW_BATTERY
        if (Config.pauseWhenHot && isHot()) return Blocker.HOT
        return null
    }

    private fun readNet(c: NetworkCapabilities?): NetInfo {
        if (c == null) return OFFLINE
        val internet = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!Config.requireValidatedNetwork || c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        return NetInfo(
            online = internet,
            unmetered = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
    }

    @Suppress("DEPRECATION")
    private fun freeBytes(): Long {
        val targetFree = dirFreeBytes(storageDir ?: Environment.getExternalStorageDirectory())
        val cacheFree = if (inited) dirFreeBytes(app.cacheDir) else Long.MAX_VALUE
        return minOf(targetFree, cacheFree)
    }

    private fun dirFreeBytes(dir: File?): Long {
        var f = dir
        while (f != null && !f.exists()) f = f.parentFile
        return try {
            if (f != null) StatFs(f.path).availableBytes else Long.MAX_VALUE
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun batteryLow(wasLow: Boolean): Boolean {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        if (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) return false
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return false
        return level * 100 / scale <= Config.minBatteryPercent + if (wasLow) 3 else 0
    }

    private fun isHot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val pm = app.getSystemService(PowerManager::class.java) ?: return false
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    private val FATAL = listOf(
        "private video", "sign in", "log in", "login", "unsupported url", "video unavailable",
        "has been removed", "copyright", "not available in your country", "geo", "age-restricted",
        "members-only", "requires authentication", "cookies", "no space left", "permission denied",
        "http error 401", "http error 403", "http error 404"
    )
    private val TRANSIENT = listOf(
        "timed out", "timeout", "connection reset", "connection refused", "connection aborted",
        "network is unreachable", "temporary failure", "name resolution", "unable to download",
        "incomplete read", "ssl", "http error 5", "429", "too many requests", "remote end closed",
        "broken pipe", "eof occurred", "unexpected end"
    )

    private fun isTransient(e: Throwable): Boolean {
        val msg = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
        if (FATAL.any { it in msg }) return false
        return e is IOException || TRANSIENT.any { it in msg }
    }

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("download_guard", Context.MODE_PRIVATE)

    private fun savePending(id: String, title: String, payload: String) {
        prefs(app).edit()
            .putString("p_$id", JSONObject().put("t", title).put("p", payload).toString())
            .commit() // Síncrono para asegurar persistencia incluso si el sistema mata el proceso
    }

    private fun removePending(id: String) {
        prefs(app).edit().remove("p_$id").commit()
    }
}