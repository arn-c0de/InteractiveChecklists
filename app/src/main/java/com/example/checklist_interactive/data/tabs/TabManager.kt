package com.example.checklist_interactive.data.tabs

import android.content.Context
import android.content.SharedPreferences
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
class TabManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tab_manager", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_TAB_PATHS = "tab_paths"
        private const val KEY_TAB_PAGES = "tab_pages"
        private const val KEY_ACTIVE_TAB = "active_tab"
        private const val KEY_TAB_HISTORY = "tab_history"
        private const val MAX_TABS = 10
        private const val MAX_HISTORY = 20
    }
    
    data class TabInfo(
        val fileInfo: FileInfo,
        val pageNumber: Int = -1
    )
    
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
    fun openTab(fileInfo: FileInfo, pageNumber: Int = -1) {
        val currentTabs = _openTabs.value.toMutableList()
        
        // Check if tab already exists
        val existingIndex = currentTabs.indexOfFirst { it.fileInfo.path == fileInfo.path }
        
        if (existingIndex >= 0) {
            // Switch to existing tab
            _activeTabIndex.value = existingIndex
            // Update page number if provided
            if (pageNumber >= 0) {
                currentTabs[existingIndex] = currentTabs[existingIndex].copy(pageNumber = pageNumber)
                _openTabs.value = currentTabs
            }
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
            
            currentTabs.add(TabInfo(fileInfo, pageNumber))
            _openTabs.value = currentTabs
            _activeTabIndex.value = currentTabs.size - 1
        }
        
        // Add to navigation history
        addToHistory(fileInfo.path)
        
        // Persist
        // Use blocking save to ensure the closed tab state is persisted immediately
        // (prevents the closed tab from being restored after an app restart).
        saveTabsToPreferences(blocking = true)
    }
    
    /**
     * Close a tab by index
     */
    fun closeTab(index: Int) {
        if (index < 0 || index >= _openTabs.value.size) return
        
        val currentTabs = _openTabs.value.toMutableList()
        currentTabs.removeAt(index)
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
        currentTabs[index] = currentTabs[index].copy(pageNumber = pageNumber)
        _openTabs.value = currentTabs
        
        saveTabsToPreferences()
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
        } else {
            editor.apply()
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
            val fileInfo = pathToFileInfoResolver(path)
            if (fileInfo != null) {
                val pageNumber = pages.getOrNull(index) ?: -1
                restoredTabs.add(TabInfo(fileInfo, pageNumber))
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
}
