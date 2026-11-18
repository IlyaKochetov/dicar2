package com.dicar.vehicle

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "language"
    const val SYSTEM = ""
    const val ENGLISH = "en"
    const val RUSSIAN = "ru"

    fun selected(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun wrap(context: Context): Context {
        val language = selected(context)
        if (language == SYSTEM) return context
        // Override only the locale so system configuration changes (including night mode)
        // continue to propagate to the activity.
        val config = Configuration()
        config.setLocales(LocaleList(Locale.forLanguageTag(language)))
        return context.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    fun apply(context: Context) {
        val language = selected(context)
        val config = Configuration(context.resources.configuration)
        val locales = if (language == SYSTEM) Resources.getSystem().configuration.locales
            else LocaleList(Locale.forLanguageTag(language))
        config.setLocales(locales)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun select(activity: Activity, language: String) {
        require(language in setOf(SYSTEM, ENGLISH, RUSSIAN))
        if (selected(activity) == language) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
        apply(activity.applicationContext)
        activity.recreate()
    }
}
