package com.ima.imaloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MusicState {
    data object Ask : MusicState
    data object Searching : MusicState
    data class Found(val match: MusicMatch, val hasMore: Boolean) : MusicState
    data class NotFound(val reason: String? = null) : MusicState
}

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val resolver = MusicResolver(app, DownloadEngine(app))
    private val _state = MutableStateFlow<MusicState>(MusicState.Ask)
    val state = _state.asStateFlow()

    private var job: Job? = null
    private var cands: List<Candidate> = emptyList()
    private var idx = 0

    fun search(info: MediaInfo, url: String) {
        job?.cancel()
        cleanupCurrentMatch()
        job = viewModelScope.launch {
            _state.value = MusicState.Searching
            try {
                val seed = resolver.seedFrom(info)
                cands = if ("music.youtube.com" in url.lowercase()) {
                    listOfNotNull(resolver.candidateFromUrl(url, info))
                } else {
                    resolver.candidates(seed)
                }
                idx = 0
                showCurrent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = MusicState.NotFound(e.message)
            }
        }
    }

    private suspend fun showCurrent() {
        while (idx < cands.size) {
            val m = resolver.build(cands[idx])
            if (m != null) {
                _state.value = MusicState.Found(m, idx + 1 < cands.size)
                return
            }
            idx++
        }
        _state.value = MusicState.NotFound()
    }

    /** "No es esta": borra el JSON descartado y prueba la siguiente candidata */
    fun reject() {
        cleanupCurrentMatch()
        idx++
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = MusicState.Searching
            showCurrent()
        }
    }

    fun reset(deleteJson: Boolean = true) {
        job?.cancel()
        if (deleteJson) cleanupCurrentMatch()
        cands = emptyList()
        idx = 0
        _state.value = MusicState.Ask
    }

    private fun cleanupCurrentMatch() {
        val current = _state.value
        if (current is MusicState.Found) {
            MusicResolver.deleteJsonIfExists(current.match.infoJsonPath)
        }
    }
}