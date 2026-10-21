package com.ima.imaloader

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ImaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { ensureInitialized(this@ImaApp) }
            runCatching { updateEngine(this@ImaApp) }
        }
    }

    companion object {
        private val initMutex = Mutex()
        @Volatile
        private var initialized = false

        suspend fun ensureInitialized(context: Context) {
            if (initialized) return
            initMutex.withLock {
                if (initialized) return
                withContext(Dispatchers.IO) {
                    val appCtx = context.applicationContext
                    YoutubeDL.getInstance().init(appCtx)
                    FFmpeg.getInstance().init(appCtx)
                    initialized = true
                }
            }
        }

        suspend fun updateEngine(context: Context) {
            withContext(Dispatchers.IO) {
                try {
                    YtDlpUpdater.update(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
