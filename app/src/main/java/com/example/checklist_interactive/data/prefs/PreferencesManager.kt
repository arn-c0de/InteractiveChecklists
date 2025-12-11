package com.example.checklist_interactive.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Manager für App-Einstellungen und Präferenzen
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFIX_CATEGORY_EXPANDED = "category_expanded_"
        private const val KEY_DARK_MODE = "dark_mode"
    }
    
    /**
     * Speichert ob eine Kategorie ausgeklappt ist
     */
    fun setCategoryExpanded(category: String, expanded: Boolean) {
        prefs.edit().putBoolean(PREFIX_CATEGORY_EXPANDED + category, expanded).apply()
    }
    
    /**
     * Lädt ob eine Kategorie ausgeklappt ist (Standard: true)
     */
    fun isCategoryExpanded(category: String): Boolean {
        return prefs.getBoolean(PREFIX_CATEGORY_EXPANDED + category, true)
    }
    
    /**
     * Lädt alle gespeicherten Kategorien-Stati
     */
    fun getAllCategoryStates(): Map<String, Boolean> {
        return prefs.all
            .filterKeys { it.startsWith(PREFIX_CATEGORY_EXPANDED) }
            .mapKeys { it.key.removePrefix(PREFIX_CATEGORY_EXPANDED) }
            .mapValues { it.value as? Boolean ?: true }
    }

    /**
     * Dark theme preference
     */
    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isDarkModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }
}
