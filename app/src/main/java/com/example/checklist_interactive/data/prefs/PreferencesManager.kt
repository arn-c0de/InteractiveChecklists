package com.example.checklist_interactive.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.checklist_interactive.data.prefs.SourceEntry
import com.example.checklist_interactive.data.prefs.ContributorEntry

/**
 * Manager for app settings and preferences
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    init {
        // Migrate legacy FAB preference keys (md_*, ifv_*) to unified keys (menu, quick_access)
        migrateFabPrefNamesIfNeeded()
    }
    
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
        private const val KEY_GRID_VIEW = "grid_view_enabled"
        private const val KEY_LAST_IMPORTED_VERSION = "last_imported_version"
        private const val KEY_LAST_MAIN_PAGE = "last_main_page"
        private const val KEY_DOCUMENT_SOURCES_JSON = "document_sources_json"
        private const val KEY_CONTRIBUTORS_JSON = "contributors_json"
        private const val KEY_PDF_FAB_PREFIX = "pdf_fab_"
        private const val KEY_PDF_TOOLBAR_VISIBLE = "pdf_toolbar_visible"

        // Map preferences
        private const val KEY_MAP_CENTER_LAT = "map_center_lat"
        private const val KEY_MAP_CENTER_LON = "map_center_lon"
        private const val KEY_MAP_ZOOM = "map_zoom"
        private const val KEY_MAP_AUTO_CENTER = "map_auto_center"
        // Persisted map tile source id (e.g. "MAPNIK", "OpenTopo", "USGS_SAT", "CartoDB.DarkMatter")
        private const val KEY_MAP_TILE_SOURCE = "map_tile_source"
        private const val KEY_MAP_OVERLAY_COMPASS = "map_overlay_compass"
        private const val KEY_MAP_OVERLAY_RINGS = "map_overlay_rings"
        private const val KEY_MAP_OVERLAY_RINGS_MAX_NM = "map_overlay_rings_max_nm"
        private const val KEY_MAP_OVERLAY_MGRS_GRID = "map_overlay_mgrs_grid"

        // Application language preference (ISO code, e.g., "en", "de")
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val DEFAULT_APP_LANGUAGE = "en"

        // Shared Json instance to avoid redundant creation
        private val json = Json { prettyPrint = true }
    }
    
    /**
     * Stores whether a category is expanded
     */
    fun setCategoryExpanded(category: String, expanded: Boolean) {
        prefs.edit().putBoolean(PREFIX_CATEGORY_EXPANDED + category, expanded).apply()
    }
    
    /**
     * Loads whether a category is expanded (default: true)
     */
    fun isCategoryExpanded(category: String): Boolean {
        return prefs.getBoolean(PREFIX_CATEGORY_EXPANDED + category, true)
    }
    
    /**
     * Loads all stored category states
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
     * Stores an integer value
     */
    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    /**
     * Loads an integer value (with default fallback)
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
     * Grid view preference: true = grid, false = list (default false)
     */
    fun setGridViewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRID_VIEW, enabled).apply()
    }

    fun isGridViewEnabled(): Boolean {
        return prefs.getBoolean(KEY_GRID_VIEW, false)
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
     * Stores which main page was last visible: 0 = file list, 1 = tabs/viewer
     */
    fun setLastMainPage(page: Int) {
        prefs.edit().putInt(KEY_LAST_MAIN_PAGE, page.coerceIn(0, 1)).apply()
    }

    fun getLastMainPage(): Int {
        return prefs.getInt(KEY_LAST_MAIN_PAGE, 0)
    }

    /**
     * Document sources persistence: JSON list of SourceEntry
     */
    fun setDocumentSources(sources: List<SourceEntry>) {
        val jsonString = json.encodeToString(ListSerializer(SourceEntry.serializer()), sources)
        prefs.edit().putString(KEY_DOCUMENT_SOURCES_JSON, jsonString).apply()
    }

    fun getDocumentSources(): List<SourceEntry> {
        val raw = prefs.getString(KEY_DOCUMENT_SOURCES_JSON, null)
        return if (raw.isNullOrBlank()) {
            // Default source list: PLATZHALTER
            emptyList()
        } else {
            try {
                json.decodeFromString(ListSerializer(SourceEntry.serializer()), raw)
            } catch (e: Exception) {
                // If parsing fails, reset to defaults
                emptyList()
            }
        }
    }

    fun resetDocumentSourcesToDefaults() {
        setDocumentSources(emptyList())
    }

    // --- Map view persistence helpers ---

    fun setMapCenter(lat: Double, lon: Double) {
        prefs.edit().putLong(KEY_MAP_CENTER_LAT, java.lang.Double.doubleToRawLongBits(lat)).apply()
        prefs.edit().putLong(KEY_MAP_CENTER_LON, java.lang.Double.doubleToRawLongBits(lon)).apply()
    }

    fun getMapCenter(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_MAP_CENTER_LAT) || !prefs.contains(KEY_MAP_CENTER_LON)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_CENTER_LAT, 0L))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_CENTER_LON, 0L))
        return Pair(lat, lon)
    }

    fun setMapZoom(zoom: Double) {
        prefs.edit().putLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(zoom)).apply()
    }

    fun getMapZoom(): Double? {
        if (!prefs.contains(KEY_MAP_ZOOM)) return null
        return java.lang.Double.longBitsToDouble(prefs.getLong(KEY_MAP_ZOOM, 0L))
    }

    fun setMapAutoCenter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_AUTO_CENTER, enabled).apply()
    }

    fun isMapAutoCenterEnabled(): Boolean {
        return prefs.getBoolean(KEY_MAP_AUTO_CENTER, false)
    }

    /**
     * Persist the selected tile source identifier for the map (nullable).
     * If null, the app will follow the system theme (dark/light) to pick the tile source.
     */
    fun setMapTileSourceId(id: String?) {
        if (id == null) prefs.edit().remove(KEY_MAP_TILE_SOURCE).apply()
        else prefs.edit().putString(KEY_MAP_TILE_SOURCE, id).apply()
    }

    fun getMapTileSourceId(): String? {
        return prefs.getString(KEY_MAP_TILE_SOURCE, null)
    }

    /**
     * Map overlay preferences: compass and range rings
     */
    fun setMapOverlayCompassEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_OVERLAY_COMPASS, enabled).apply()
    }

    fun isMapOverlayCompassEnabled(): Boolean {
        return prefs.getBoolean(KEY_MAP_OVERLAY_COMPASS, false)
    }

    fun setMapOverlayRangeRingsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_OVERLAY_RINGS, enabled).apply()
    }

    fun isMapOverlayRangeRingsEnabled(): Boolean {
        return prefs.getBoolean(KEY_MAP_OVERLAY_RINGS, false)
    }

    fun setMapOverlayRangeRingsMaxNm(nm: Int) {
        prefs.edit().putInt(KEY_MAP_OVERLAY_RINGS_MAX_NM, nm.coerceAtLeast(1)).apply()
    }

    fun getMapOverlayRangeRingsMaxNm(): Int {
        return prefs.getInt(KEY_MAP_OVERLAY_RINGS_MAX_NM, 5)
    }

    fun setMapOverlayMgrsGridEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_OVERLAY_MGRS_GRID, enabled).apply()
    }

    fun isMapOverlayMgrsGridEnabled(): Boolean {
        return prefs.getBoolean(KEY_MAP_OVERLAY_MGRS_GRID, false)
    }

    /**
     * Contributor persistence: JSON list of ContributorEntry
     */
    fun setContributors(contributors: List<ContributorEntry>) {
        val jsonString = json.encodeToString(ListSerializer(ContributorEntry.serializer()), contributors)
        prefs.edit().putString(KEY_CONTRIBUTORS_JSON, jsonString).apply()
    }

    fun getContributors(): List<ContributorEntry> {
        val raw = prefs.getString(KEY_CONTRIBUTORS_JSON, null)
        return if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(ListSerializer(ContributorEntry.serializer()), raw)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun resetContributorsToDefaults() {
        setContributors(emptyList())
    }

    // PDF viewer FAB positions: stored as percentage (0..1) of available area
    fun setPdfViewerFabPosition(name: String, xPercent: Float, yPercent: Float) {
        prefs.edit()
            .putFloat("${KEY_PDF_FAB_PREFIX}${name}_x", xPercent.coerceIn(0f, 1f))
            .putFloat("${KEY_PDF_FAB_PREFIX}${name}_y", yPercent.coerceIn(0f, 1f))
            .apply()
    }

    fun getPdfViewerFabPosition(name: String, defaultX: Float, defaultY: Float): Pair<Float, Float> {
        val x = prefs.getFloat("${KEY_PDF_FAB_PREFIX}${name}_x", defaultX).coerceIn(0f, 1f)
        val y = prefs.getFloat("${KEY_PDF_FAB_PREFIX}${name}_y", defaultY).coerceIn(0f, 1f)
        return Pair(x, y)
    }

    fun resetPdfViewerLayout() {
        prefs.edit().apply {
            // remove all FAB keys (pdf viewer, markdown viewer, internal file viewer)
            remove("${KEY_PDF_FAB_PREFIX}menu_x")
            remove("${KEY_PDF_FAB_PREFIX}menu_y")
            remove("${KEY_PDF_FAB_PREFIX}quick_access_x")
            remove("${KEY_PDF_FAB_PREFIX}quick_access_y")
            remove("${KEY_PDF_FAB_PREFIX}zoom_reset_x")
            remove("${KEY_PDF_FAB_PREFIX}zoom_reset_y")
            // legacy keys - kept for backward compatibility
            remove("${KEY_PDF_FAB_PREFIX}md_menu_x")
            remove("${KEY_PDF_FAB_PREFIX}md_menu_y")
            remove("${KEY_PDF_FAB_PREFIX}md_quick_access_x")
            remove("${KEY_PDF_FAB_PREFIX}md_quick_access_y")
            remove("${KEY_PDF_FAB_PREFIX}ifv_menu_x")
            remove("${KEY_PDF_FAB_PREFIX}ifv_menu_y")
            remove("${KEY_PDF_FAB_PREFIX}ifv_quick_access_x")
            remove("${KEY_PDF_FAB_PREFIX}ifv_quick_access_y")
        }.apply()
    }

    /**
     * PDF toolbar visibility (global for all PDFs)
     */
    fun setPdfToolbarVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_PDF_TOOLBAR_VISIBLE, visible).apply()
    }

    fun isPdfToolbarVisible(): Boolean {
        return prefs.getBoolean(KEY_PDF_TOOLBAR_VISIBLE, false)
    }

    /**
     * Application language preference
     */
    fun setAppLanguage(code: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, code).apply()
    }

    fun getAppLanguage(): String {
        return prefs.getString(KEY_APP_LANGUAGE, DEFAULT_APP_LANGUAGE) ?: DEFAULT_APP_LANGUAGE
    }

    // Migration: move legacy per-view FAB prefs (md_*, ifv_*) to unified names (menu, quick_access)
    private fun migrateFabPrefNamesIfNeeded() {
        val mapping = mapOf(
            "md_menu" to "menu",
            "md_quick_access" to "quick_access",
            "ifv_menu" to "menu",
            "ifv_quick_access" to "quick_access"
        )

        val editor = prefs.edit()
        var changed = false

        for ((legacy, target) in mapping) {
            val legacyXKey = "${KEY_PDF_FAB_PREFIX}${legacy}_x"
            val legacyYKey = "${KEY_PDF_FAB_PREFIX}${legacy}_y"
            val targetXKey = "${KEY_PDF_FAB_PREFIX}${target}_x"
            val targetYKey = "${KEY_PDF_FAB_PREFIX}${target}_y"

            // If target already has values, prefer them (no overwrite). Otherwise, migrate if legacy exists.
            val targetHas = prefs.contains(targetXKey) || prefs.contains(targetYKey)
            val legacyHas = prefs.contains(legacyXKey) || prefs.contains(legacyYKey)

            if (!targetHas && legacyHas) {
                val lx = prefs.getFloat(legacyXKey, -1f)
                val ly = prefs.getFloat(legacyYKey, -1f)
                if (lx >= 0f) {
                    editor.putFloat(targetXKey, lx.coerceIn(0f, 1f))
                }
                if (ly >= 0f) {
                    editor.putFloat(targetYKey, ly.coerceIn(0f, 1f))
                }
                editor.remove(legacyXKey)
                editor.remove(legacyYKey)
                changed = true
            }
        }

        if (changed) editor.apply()
    }
}
