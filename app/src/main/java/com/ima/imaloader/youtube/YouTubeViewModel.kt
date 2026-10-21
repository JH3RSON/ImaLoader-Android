package com.ima.imaloader.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class YouTubeViewModel : ViewModel() {

    var searchMode by mutableStateOf(YouTubeSearchMode.YOUTUBE)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var isSearching by mutableStateOf(false)
        private set

    var searchError by mutableStateOf<String?>(null)
        private set

    var results by mutableStateOf<List<YouTubeSearchResult>>(emptyList())
        private set

    var activeVideoId by mutableStateOf<String?>(null)
        private set

    var apiKey by mutableStateOf<String?>(null)
        private set

    fun onQueryChange(value: String) {
        searchQuery = value
        if (searchError != null) searchError = null
    }

    fun setMode(mode: YouTubeSearchMode) {
        if (searchMode != mode) {
            searchMode = mode
            if (searchQuery.isNotBlank()) {
                performSearch()
            }
        }
    }

    fun setCustomApiKey(key: String?) {
        apiKey = key
    }

    fun performSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return

        // Comprobar si el texto ingresado es una URL o ID directo de YouTube
        val directId = YouTubeUrlParser.extractVideoId(query)
        if (directId != null) {
            activeVideoId = directId
            searchError = null
            return
        }

        isSearching = true
        searchError = null

        viewModelScope.launch {
            val list = YouTubeSearchService.search(query, searchMode, apiKey)
            isSearching = false
            results = list
            if (list.isEmpty()) {
                searchError = "No se encontraron resultados para \"$query\" en ${searchMode.label}."
            }
        }
    }

    fun playVideo(id: String) {
        activeVideoId = id
    }

    fun closePlayer() {
        activeVideoId = null
    }

    fun clearSearch() {
        searchQuery = ""
        results = emptyList()
        searchError = null
    }
}
