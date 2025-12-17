package com.example.checklist_interactive.data.tabs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.checklist_interactive.R
import com.example.checklist_interactive.data.files.FileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TabManager - Manages open tabs for MD and PDF files
 * 
 * Features:
 * - Multiple open tabs with persistence
 * - Active tab tracking
 * - Tab history for quick navigation
 * - Support for both MD and PDF files
 */
class TabManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tab_manager", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "TabManager"
        private const val KEY_TAB_PATHS = "tab_paths"
        private const val KEY_TAB_PAGES = "tab_pages"
        private const val KEY_ACTIVE_TAB = "active_tab"
        private const val KEY_TAB_HISTORY = "tab_history"
        private const val MAX_TABS = 10
        private const val MAX_HISTORY = 20
        
        // Special identifier for map tab
        const val MAP_TAB_PATH = "special://aviation_map"
    }
    
    sealed class TabContent {
        data class DocumentTab(val fileInfo: FileInfo, val pageNumber: Int = -1) : TabContent()
        object MapTab : TabContent()
    }
    
    data class TabInfo(
        val content: TabContent,
        val fileInfo: FileInfo // For compatibility, map tabs use a synthetic FileInfo
    ) {
        val pageNumber: Int
            get() = when (content) {
                is TabContent.DocumentTab -> content.pageNumber
                is TabContent.MapTab -> -1
            }
    }
    
    private val _openTabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val openTabs: StateFlow<List<TabInfo>> = _openTabs.asStateFlow()
    
    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()
    
    private val _navigationHistory = MutableStateFlow<List<String>>(emptyList())
    val navigationHistory: StateFlow<List<String>> = _navigationHistory.asStateFlow()
    
    init {
        loadTabsFromPreferences()
    }
    
    /**
     * Open a new tab or switch to existing tab
     */
    fun openTab(fileInfo: FileInfo, pageNumber: Int = -1): Int {
        val currentTabs = _openTabs.value.toMutableList()

        // Check if tab already exists
        val existingIndex = currentTabs.indexOfFirst { it.fileInfo.path == fileInfo.path }

        if (existingIndex >= 0) {
            // Switch to existing tab
            _activeTabIndex.value = existingIndex
            // Update page number if provided
            if (pageNumber >= 0 && currentTabs[existingIndex].content is TabContent.DocumentTab) {
                val oldContent = currentTabs[existingIndex].content as TabContent.DocumentTab
                currentTabs[existingIndex] = currentTabs[existingIndex].copy(
                    content = oldContent.copy(pageNumber = pageNumber)
                )
                _openTabs.value = currentTabs
            }
            // Add to history & persist
            addToHistory(fileInfo.path)
            saveTabsToPreferences(blocking = true)
            try {
                val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                appPrefs.edit().putString("last_opened_file", fileInfo.path).commit()
            } catch (e: Exception) {
                // ignore
            }
            return existingIndex
        } else {
            // Add new tab
            if (currentTabs.size >= MAX_TABS) {
                // Remove oldest tab (first one that's not active)
                val indexToRemove = if (_activeTabIndex.value == 0) 1 else 0
                currentTabs.removeAt(indexToRemove)
                if (_activeTabIndex.value > indexToRemove) {
                    _activeTabIndex.value -= 1
                }
            }

            currentTabs.add(TabInfo(TabContent.DocumentTab(fileInfo, pageNumber), fileInfo))
            _openTabs.value = currentTabs
            _activeTabIndex.value = currentTabs.size - 1

            // Add to navigation history & persist
            addToHistory(fileInfo.path)
            saveTabsToPreferences(blocking = true)
            try {
                val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                appPrefs.edit().putString("last_opened_file", fileInfo.path).commit()
            } catch (e: Exception) {
                // ignore
            }

            return _activeTabIndex.value
        }
    }
    
    /**
     * Open the aviation map tab or switch to it if already open
     */
    fun openMapTab(): Int {
        val currentTabs = _openTabs.value.toMutableList()
        
        // Check if map tab already exists
        val existingIndex = currentTabs.indexOfFirst { it.content is TabContent.MapTab }
        
        if (existingIndex >= 0) {
            // Switch to existing map tab
            _activeTabIndex.value = existingIndex
            return existingIndex
        } else {
            // Add new map tab
            if (currentTabs.size >= MAX_TABS) {
                // Remove oldest tab (first one that's not active)
                val indexToRemove = if (_activeTabIndex.value == 0) 1 else 0
                currentTabs.removeAt(indexToRemove)
                if (_activeTabIndex.value > indexToRemove) {
                    _activeTabIndex.value -= 1
                }
            }
            
            // Create synthetic FileInfo for map tab
            val mapFileInfo = FileInfo(
                name = "aviation_map",
                displayName = context.getString(R.string.aviation_map_tab_title),
                path = MAP_TAB_PATH,
                category = "maps",
                size = 0,
                lastModified = System.currentTimeMillis(),
                extension = "map"
            )
            
            currentTabs.add(TabInfo(TabContent.MapTab, mapFileInfo))
            _openTabs.value = currentTabs
            _activeTabIndex.value = currentTabs.size - 1
            
            addToHistory(MAP_TAB_PATH)
            saveTabsToPreferences(blocking = true)
            
            return _activeTabIndex.value
        }
    }
    
    /**
     * Close a tab by index. Returns the removed TabInfo or null if index invalid.
     * This allows callers to update external state (eg. last-opened file) when a tab is closed.
     */
    fun closeTab(index: Int): TabInfo? {
        if (index < 0 || index >= _openTabs.value.size) return null

        val currentTabs = _openTabs.value.toMutableList()
        val removed = currentTabs.removeAt(index)
        Log.d(TAG, "closeTab: removed=${removed.fileInfo.path} index=$index")
        _openTabs.value = currentTabs

        // Adjust active tab index
        if (currentTabs.isEmpty()) {
            _activeTabIndex.value = 0
        } else if (_activeTabIndex.value >= currentTabs.size) {
            _activeTabIndex.value = currentTabs.size - 1
        } else if (_activeTabIndex.value > index) {
            _activeTabIndex.value -= 1
        }

        // Persist synchronously so all tabs are removed immediately.
        saveTabsToPreferences(blocking = true)
        // Update last opened file
        try {
            val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val currentActive = getActiveTab()?.fileInfo?.path ?: ""
            appPrefs.edit().putString("last_opened_file", currentActive).commit()
        } catch (e: Exception) {
            // ignore
        }
        Log.d(TAG, "closeTab: remaining=${_openTabs.value.map { it.fileInfo.path }} activeIndex=${_activeTabIndex.value}")
        return removed
    }
    
    /**
     * Switch to a tab by index
     */
    fun switchToTab(index: Int) {
        if (index < 0 || index >= _openTabs.value.size) return
        
        _activeTabIndex.value = index
        val tab = _openTabs.value[index]
        addToHistory(tab.fileInfo.path)
        
        saveTabsToPreferences()
        // Persist last opened file immediately
        try {
            val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            appPrefs.edit().putString("last_opened_file", tab.fileInfo.path).commit()
        } catch (e: Exception) {
            // ignore
        }
    }
    
    /**
     * Get currently active tab
     */
    fun getActiveTab(): TabInfo? {
        val tabs = _openTabs.value
        val index = _activeTabIndex.value
        return if (index >= 0 && index < tabs.size) tabs[index] else null
    }
    
    /**
     * Navigate to previous tab in history
     */
    fun navigateToPreviousInHistory(): TabInfo? {
        val history = _navigationHistory.value
        if (history.size < 2) return null
        
        // Get previous item (skip current which is at index 0)
        val previousPath = history.getOrNull(1) ?: return null
        
        // Find tab with this path
        val tabs = _openTabs.value
        val index = tabs.indexOfFirst { it.fileInfo.path == previousPath }
        
        return if (index >= 0) {
            switchToTab(index)
            tabs[index]
        } else {
            null
        }
    }
    
    /**
     * Update page number for current tab
     */
    fun updateCurrentTabPage(pageNumber: Int) {
        val index = _activeTabIndex.value
        if (index < 0 || index >= _openTabs.value.size) return

        val currentTabs = _openTabs.value.toMutableList()
        val tab = currentTabs[index]
        if (tab.content is TabContent.DocumentTab) {
            val doc = tab.content as TabContent.DocumentTab
            currentTabs[index] = tab.copy(content = doc.copy(pageNumber = pageNumber))
            _openTabs.value = currentTabs
            saveTabsToPreferences()
        }
    }
    
    /**
     * Close all tabs
     */
    fun closeAllTabs() {
        _openTabs.value = emptyList()
        _activeTabIndex.value = 0
        saveTabsToPreferences()
    }

    /**
     * Move a tab from one index to another (reordering)
     */
    fun moveTab(fromIndex: Int, toIndex: Int) {
        val current = _openTabs.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= current.size) return
        if (toIndex < 0 || toIndex >= current.size) return

        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _openTabs.value = current

        // Update active tab index if necessary
        val active = _activeTabIndex.value
        _activeTabIndex.value = when {
            active == fromIndex -> toIndex
            fromIndex < active && toIndex >= active -> active - 1
            fromIndex > active && toIndex <= active -> active + 1
            else -> active
        }

        saveTabsToPreferences()
    }
    
    /**
     * Add path to navigation history
     */
    private fun addToHistory(path: String) {
        val history = _navigationHistory.value.toMutableList()
        
        // Remove if already exists
        history.remove(path)
        
        // Add to front
        history.add(0, path)
        
        // Limit size
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
        
        _navigationHistory.value = history
        saveHistoryToPreferences()
    }
    
    /**
     * Save tabs to SharedPreferences
     */
    /**
     * Save tabs to SharedPreferences.
     * If [blocking] is true this will use a synchronous commit() so the
     * changes are immediately persisted (useful when closing tabs before
     * the process may be killed).
     */
    private fun saveTabsToPreferences(blocking: Boolean = false) {
        val tabs = _openTabs.value
        val paths = tabs.joinToString("|") { it.fileInfo.path }
        val pages = tabs.joinToString("|") { it.pageNumber.toString() }

        val editor = prefs.edit()
        editor.putString(KEY_TAB_PATHS, paths)
        editor.putString(KEY_TAB_PAGES, pages)
        editor.putInt(KEY_ACTIVE_TAB, _activeTabIndex.value)

        if (blocking) {
            editor.commit()
            Log.d(TAG, "saveTabsToPreferences(commit): paths='$paths' pages='$pages' active=${_activeTabIndex.value}")
        } else {
            editor.apply()
            Log.d(TAG, "saveTabsToPreferences(apply): paths='$paths' pages='$pages' active=${_activeTabIndex.value}")
        }
    }
    
    /**
     * Load tabs from SharedPreferences
     * Note: Actual FileInfo objects need to be resolved by caller
     */
    private fun loadTabsFromPreferences() {
        val pathsString = prefs.getString(KEY_TAB_PATHS, "") ?: ""
        val pagesString = prefs.getString(KEY_TAB_PAGES, "") ?: ""
        val activeTab = prefs.getInt(KEY_ACTIVE_TAB, 0)
        
        _activeTabIndex.value = activeTab
        
        // Note: Tab restoration will be handled by MainActivity
        // as it needs access to FileManager to resolve FileInfo objects
    }
    
    /**
     * Restore tabs from saved paths
     * Called by MainActivity after FileManager is available
     */
    fun restoreTabsFromPaths(pathToFileInfoResolver: (String) -> FileInfo?) {
        val pathsString = prefs.getString(KEY_TAB_PATHS, "") ?: ""
        val pagesString = prefs.getString(KEY_TAB_PAGES, "") ?: ""
        
        if (pathsString.isEmpty()) return
        
        val paths = pathsString.split("|").filter { it.isNotEmpty() }
        val pages = pagesString.split("|").filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: -1 }
        
        val restoredTabs = mutableListOf<TabInfo>()
        
        paths.forEachIndexed { index, path ->
            if (path == MAP_TAB_PATH) {
                // Recreate map tab
                val mapFileInfo = FileInfo(
                    name = "aviation_map",
                    displayName = context.getString(R.string.aviation_map_tab_title),
                    path = MAP_TAB_PATH,
                    category = "maps",
                    size = 0,
                    lastModified = System.currentTimeMillis(),
                    extension = "map"
                )
                restoredTabs.add(TabInfo(TabContent.MapTab, mapFileInfo))
            } else {
                val fileInfo = pathToFileInfoResolver(path)
                if (fileInfo != null) {
                    val pageNumber = pages.getOrNull(index) ?: -1
                    val content = TabContent.DocumentTab(fileInfo, pageNumber)
                    restoredTabs.add(TabInfo(content, fileInfo))
                }
            }
        }
        
        if (restoredTabs.isNotEmpty()) {
            _openTabs.value = restoredTabs
            
            // Ensure active tab index is valid
            val activeTab = prefs.getInt(KEY_ACTIVE_TAB, 0)
            _activeTabIndex.value = activeTab.coerceIn(0, restoredTabs.size - 1)
        }
    }
    
    /**
     * Save navigation history to SharedPreferences
     */
    private fun saveHistoryToPreferences() {
        val history = _navigationHistory.value
        val historyString = history.joinToString("|")
        
        prefs.edit().apply {
            putString(KEY_TAB_HISTORY, historyString)
            apply()
        }
    }
    
    /**
     * Load navigation history from SharedPreferences
     */
    fun loadHistoryFromPreferences() {
        val historyString = prefs.getString(KEY_TAB_HISTORY, "") ?: ""
        if (historyString.isNotEmpty()) {
            _navigationHistory.value = historyString.split("|").filter { it.isNotEmpty() }
        }
    }

    /**
     * Persist tabs and history synchronously. Useful to call on activity pause/stop
     * to ensure state is saved immediately before the process may be killed.
     */
    fun persistAll() {
        saveTabsToPreferences(blocking = true)
        saveHistoryToPreferences()
    }
}
