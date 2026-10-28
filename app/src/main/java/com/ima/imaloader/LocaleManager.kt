package com.ima.imaloader

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object LocaleManager {
    private const val PREFS = "imaloader_prefs"
    private const val KEY = "app_language"

    var currentLanguage by mutableStateOf("es")
        private set

    fun init(context: Context) {
        currentLanguage = getSaved(context)
    }

    fun getSaved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "es") ?: "es"

    fun setLanguage(context: Context, lang: String) {
        if (lang == currentLanguage) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, lang)
            .apply() // Changed to apply for async saving
        currentLanguage = lang
    }

    fun wrap(base: Context, lang: String = getSaved(base)): Context {
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}