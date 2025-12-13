package com.example.checklist_interactive.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.checklist_interactive.data.prefs.SourceEntry
import com.example.checklist_interactive.data.prefs.ContributorEntry

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
        private const val KEY_MARKDOWN_SECTIONS_EXPANDED = "markdown_sections_expanded"
        private const val KEY_ACTIVE_TAG_FILTERS = "active_tag_filters"
        private const val KEY_TAG_FILTER_MODE = "tag_filter_mode" // "any" or "all"
        private const val KEY_LAST_IMPORTED_VERSION = "last_imported_version"
        private const val KEY_DOCUMENT_SOURCES_JSON = "document_sources_json"
        private const val KEY_CONTRIBUTORS_JSON = "contributors_json"
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

    /**
     * Markdown sections expanded state preference
     * true = all sections expanded by default, false = all sections collapsed by default
     */
    fun setMarkdownSectionsExpandedByDefault(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_MARKDOWN_SECTIONS_EXPANDED, expanded).apply()
    }

    fun areMarkdownSectionsExpandedByDefault(): Boolean {
        return prefs.getBoolean(KEY_MARKDOWN_SECTIONS_EXPANDED, false) // Default: collapsed
    }

    // Aircraft visibility settings: stored as a StringSet in SharedPreferences
    fun setVisibleAircrafts(aircrafts: Set<String>) {
        prefs.edit().putStringSet(KEY_VISIBLE_AIRCRAFTS, aircrafts).apply()
    }

    /**
     * Returns the stored visible aircrafts, or null if no preference has been set yet.
     * A null value means "use default behavior" (show all bundled aircrafts).
     * An empty set means the user explicitly selected "show none".
     */
    fun getVisibleAircrafts(): Set<String>? {
        return prefs.getStringSet(KEY_VISIBLE_AIRCRAFTS, null)?.toSet()
    }

    fun isAircraftVisible(name: String): Boolean {
        val set = getVisibleAircrafts()
        if (set == null) return true // default: visible
        if (set.isEmpty()) return false // explicit: none visible
        return set.any { it.equals(name, ignoreCase = true) }
    }

    fun setAircraftVisible(name: String, visible: Boolean) {
        val current = getVisibleAircrafts()?.toMutableSet() ?: mutableSetOf()
        if (visible) current.add(name) else current.removeIf { it.equals(name, ignoreCase = true) }
        setVisibleAircrafts(current)
    }

    fun resetVisibleAircrafts() {
        // Remove the stored preference to return to default behavior (all visible)
        prefs.edit().remove(KEY_VISIBLE_AIRCRAFTS).apply()
    }

    // Tag filtering preferences
    
    /**
     * Sets the currently active tag filters
     */
    fun setActiveTagFilters(tags: Set<String>) {
        prefs.edit().putStringSet(KEY_ACTIVE_TAG_FILTERS, tags).apply()
    }
    
    /**
     * Gets the currently active tag filters
     */
    fun getActiveTagFilters(): Set<String> {
        return prefs.getStringSet(KEY_ACTIVE_TAG_FILTERS, emptySet())?.toSet() ?: emptySet()
    }
    
    /**
     * Clears all tag filters
     */
    fun clearTagFilters() {
        prefs.edit().remove(KEY_ACTIVE_TAG_FILTERS).apply()
    }
    
    /**
     * Sets the tag filter mode: "any" (files with any of the selected tags) or "all" (files with all selected tags)
     */
    fun setTagFilterMode(mode: String) {
        prefs.edit().putString(KEY_TAG_FILTER_MODE, mode).apply()
    }
    
    /**
     * Gets the tag filter mode (default: "any")
     */
    fun getTagFilterMode(): String {
        return prefs.getString(KEY_TAG_FILTER_MODE, "any") ?: "any"
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

    /**
     * Gets the last imported app version
     */
    fun getLastImportedVersion(): String? {
        return prefs.getString(KEY_LAST_IMPORTED_VERSION, null)
    }

    /**
     * Sets the last imported app version
     */
    fun setLastImportedVersion(version: String) {
        prefs.edit().putString(KEY_LAST_IMPORTED_VERSION, version).apply()
    }

    /**
     * Document sources persistence: JSON list of SourceEntry
     */
    fun setDocumentSources(sources: List<SourceEntry>) {
        val json = Json { prettyPrint = true }.encodeToString(ListSerializer(SourceEntry.serializer()), sources)
        prefs.edit().putString(KEY_DOCUMENT_SOURCES_JSON, json).apply()
    }

    fun getDocumentSources(): List<SourceEntry> {
        val raw = prefs.getString(KEY_DOCUMENT_SOURCES_JSON, null)
        return if (raw.isNullOrBlank()) {
            // Default source list: PLATZHALTER
            listOf(SourceEntry("PLATZHALTER)", "PLATZHALTER", "CC BY-NC-SA 3.0 DE"))
        } else {
            try {
                Json.decodeFromString(ListSerializer(SourceEntry.serializer()), raw)
            } catch (e: Exception) {
                // If parsing fails, reset to defaults
                listOf(SourceEntry("PLATZHALTER", "PLATZHALTER", "CC BY-NC-SA 3.0 DE"))
            }
        }
    }

    fun resetDocumentSourcesToDefaults() {
        setDocumentSources(listOf(SourceEntry("PLATZHALTER)", "PLATZHALTER", "CC BY-NC-SA 3.0 DE")))
    }

    /**
     * Contributor persistence: JSON list of ContributorEntry
     */
    fun setContributors(contributors: List<ContributorEntry>) {
        val json = Json { prettyPrint = true }.encodeToString(ListSerializer(ContributorEntry.serializer()), contributors)
        prefs.edit().putString(KEY_CONTRIBUTORS_JSON, json).apply()
    }

    fun getContributors(): List<ContributorEntry> {
        val raw = prefs.getString(KEY_CONTRIBUTORS_JSON, null)
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                Json.decodeFromString(ListSerializer(ContributorEntry.serializer()), raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun resetContributorsToDefaults() {
        setContributors(emptyList())
    }
}
