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
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_IMPORT_FOLDER_URI = "import_folder_uri"
        private const val KEY_IMPORT_DIALOG_SHOWN = "import_dialog_shown"
        private const val KEY_MARKDOWN_FONT_SIZE = "markdown_font_size"
        private const val DEFAULT_MARKDOWN_FONT_SIZE = 18
        private const val KEY_VISIBLE_AIRCRAFTS = "visible_aircrafts"
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

    /**
     * Checks if this is the first launch of the app
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Marks that the app has been launched before
     */
    fun setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    /**
     * Saves the external import folder URI
     */
    fun setImportFolderUri(uri: String?) {
        if (uri == null) {
            prefs.edit().remove(KEY_IMPORT_FOLDER_URI).apply()
        } else {
            prefs.edit().putString(KEY_IMPORT_FOLDER_URI, uri).apply()
        }
    }

    /**
     * Gets the external import folder URI
     */
    fun getImportFolderUri(): String? {
        return prefs.getString(KEY_IMPORT_FOLDER_URI, null)
    }

    /**
     * Tracks whether the "Import files from device" dialog has been shown (so we don't show it repeatedly)
     */
    fun hasShownImportDialog(): Boolean {
        return prefs.getBoolean(KEY_IMPORT_DIALOG_SHOWN, false)
    }

    fun setImportDialogShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_IMPORT_DIALOG_SHOWN, shown).apply()
    }

    /**
     * Speichert einen Integer-Wert
     */
    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    /**
     * Lädt einen Integer-Wert (mit Standard-Fallback)
     */
    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    /**
     * Markdown font size preference (in sp)
     */
    fun setMarkdownFontSize(sp: Int) {
        setInt(KEY_MARKDOWN_FONT_SIZE, sp)
    }

    fun getMarkdownFontSize(): Int {
        return getInt(KEY_MARKDOWN_FONT_SIZE, DEFAULT_MARKDOWN_FONT_SIZE)
    }

    // Aircraft visibility settings: stored as a StringSet in SharedPreferences
    fun setVisibleAircrafts(aircrafts: Set<String>) {
        prefs.edit().putStringSet(KEY_VISIBLE_AIRCRAFTS, aircrafts).apply()
    }

    fun getVisibleAircrafts(): Set<String> {
        // Return a copy to avoid accidental mutation
        return prefs.getStringSet(KEY_VISIBLE_AIRCRAFTS, null)?.toSet() ?: emptySet()
    }

    fun isAircraftVisible(name: String): Boolean {
        val set = getVisibleAircrafts()
        if (set.isEmpty()) return true // default: visible
        return set.any { it.equals(name, ignoreCase = true) }
    }

    fun setAircraftVisible(name: String, visible: Boolean) {
        val current = getVisibleAircrafts().toMutableSet()
        if (visible) current.add(name) else current.removeIf { it.equals(name, ignoreCase = true) }
        setVisibleAircrafts(current)
    }

    fun resetVisibleAircrafts() {
        // Clearing the stored set returns to default behavior (all visible)
        setVisibleAircrafts(emptySet())
    }

    /**
     * Allows registering a SharedPreferences change listener to react on updates.
     */
    fun registerOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
