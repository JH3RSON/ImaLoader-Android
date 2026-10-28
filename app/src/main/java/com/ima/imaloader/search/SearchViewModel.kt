package com.ima.imaloader.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    var mode by mutableStateOf(SearchMode.VIDEO)
        private set
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
        
    val paginador = PaginadorBusqueda<ResultadoVideo>(
        scope = viewModelScope,
        idDe = { it.id },
        pedir = ::buscarEnYoutube
    )

    var hasSearched by mutableStateOf(false)
        private set

    var playing by mutableStateOf<ResultadoVideo?>(null)

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = try {
                SearchApi.suggestions(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun dismissSuggestions() {
        suggestJob?.cancel()
        suggestions = emptyList()
    }

    fun search(text: String = query) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        query = clean
        dismissSuggestions()
        searchJob?.cancel()
        
        hasSearched = true
        paginador.buscar(clean, mode == SearchMode.MUSIC)
    }

    fun onModeChange(newMode: SearchMode) {
        if (newMode == mode) return
        mode = newMode
        playing = null
        dismissSuggestions()
        paginador.buscar("", mode == SearchMode.MUSIC) // clean results
        if (query.isNotBlank()) {
            search(query)
        } else {
            hasSearched = false
        }
    }
}