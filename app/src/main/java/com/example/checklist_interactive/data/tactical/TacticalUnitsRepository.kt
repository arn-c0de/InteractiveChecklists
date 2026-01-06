package com.example.checklist_interactive.data.tactical

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Repository for Tactical Units - provides high-level API for unit management
 */
class TacticalUnitsRepository(private val context: Context) {
    private val db = TacticalDatabase.getInstance(context)
    private val unitsDao = db.tacticalUnitsDao()
    private val historyDao = db.tacticalUnitHistoryDao()
    
    // --- Query functions ---
    
    fun getAllUnits(): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getAllUnits()
    }
    
    fun getAllUnits(showHidden: Boolean): Flow<List<TacticalUnitEntity>> {
        return if (showHidden) {
            unitsDao.getAllUnits()
        } else {
            unitsDao.getAllVisibleUnits()
        }
    }
    
    fun getAllActiveUnits(): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getAllActiveUnits()
    }
    
    fun getAllActiveUnitsNoTimeFilter(): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getAllActiveUnitsNoTimeFilter()
    }
    
    fun getAllActiveUnits(showHidden: Boolean): Flow<List<TacticalUnitEntity>> {
        return if (showHidden) {
            unitsDao.getAllActiveUnits()
        } else {
            unitsDao.getAllActiveVisibleUnits()
        }
    }
    
    fun getLiveUnits(): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getLiveUnits()
    }
    
    fun getAllInactiveUnits(): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getAllInactiveUnits()
    }
    
    fun getUnitsByCategory(category: String): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getUnitsByCategory(category)
    }
    
    fun getUnitsByCoalition(coalition: Int): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getUnitsByCoalition(coalition)
    }
    
    fun getUnitsByCategoryAndCoalition(category: String, coalition: Int): Flow<List<TacticalUnitEntity>> {
        return unitsDao.getUnitsByCategoryAndCoalition(category, coalition)
    }
    
    fun searchUnits(query: String): Flow<List<TacticalUnitEntity>> {
        return unitsDao.searchUnits(query)
    }
    
    suspend fun getUnitById(id: Int): TacticalUnitEntity? {
        return unitsDao.getUnitById(id)
    }
    
    fun getUnitByIdFlow(id: Int): Flow<TacticalUnitEntity?> {
        return unitsDao.getUnitByIdFlow(id)
    }
    
    suspend fun getUnitByDcsId(dcsId: String): TacticalUnitEntity? {
        return unitsDao.getUnitByDcsId(dcsId)
    }
    
    // --- Filter functions ---
    
    /**
     * Get units with multiple filters applied
     */
    fun getUnitsFiltered(
        categories: Set<String> = emptySet(),
        coalitions: Set<Int> = emptySet(),
        activeOnly: Boolean = true,
        searchQuery: String = ""
    ): Flow<List<TacticalUnitEntity>> {
        return getAllUnits().map { units ->
            units.filter { unit ->
                // Filter by active status
                if (activeOnly && unit.isActive != 1) return@filter false
                
                // Filter by categories (if specified)
                if (categories.isNotEmpty() && !categories.contains(unit.category)) return@filter false
                
                // Filter by coalitions (if specified)
                if (coalitions.isNotEmpty() && !coalitions.contains(unit.coalition)) return@filter false
                
                // Filter by search query
                if (searchQuery.isNotEmpty()) {
                    val query = searchQuery.lowercase()
                    val matchesName = unit.name.lowercase().contains(query)
                    val matchesGroup = unit.groupName?.lowercase()?.contains(query) ?: false
                    val matchesPilot = unit.pilotName?.lowercase()?.contains(query) ?: false
                    if (!matchesName && !matchesGroup && !matchesPilot) return@filter false
                }
                
                true
            }
        }
    }
    
    // --- Statistics functions ---
    
    suspend fun getActiveUnitCount(): Int {
        return unitsDao.getActiveUnitCount()
    }
    
    suspend fun getActiveUnitCountByCategory(category: String): Int {
        return unitsDao.getActiveUnitCountByCategory(category)
    }
    
    suspend fun getActiveUnitCountByCoalition(coalition: Int): Int {
        return unitsDao.getActiveUnitCountByCoalition(coalition)
    }
    
    /**
     * Get category distribution (map of category -> count)
     */
    suspend fun getCategoryDistribution(): Map<String, Int> {
        val categories = listOf("aircraft", "helicopter", "ground", "ship", "structure", "weapon")
        return categories.associateWith { category ->
            unitsDao.getActiveUnitCountByCategory(category)
        }
    }
    
    /**
     * Get coalition distribution (map of coalition -> count)
     */
    suspend fun getCoalitionDistribution(): Map<Int, Int> {
        val coalitions = listOf(0, 1, 2) // Neutral, Red, Blue
        return coalitions.associateWith { coalition ->
            unitsDao.getActiveUnitCountByCoalition(coalition)
        }
    }
    
    // --- History functions ---
    
    fun getHistoryForUnit(unitId: Int): Flow<List<TacticalUnitHistoryEntity>> {
        return historyDao.getHistoryForUnit(unitId)
    }
    
    suspend fun getRecentHistoryForUnit(unitId: Int, limit: Int = 100): List<TacticalUnitHistoryEntity> {
        return historyDao.getRecentHistoryForUnit(unitId, limit)
    }
    
    // --- Cleanup functions ---

    /**
     * Mark all inactive units as hidden (instead of deleting)
     */
    suspend fun hideInactiveUnits() {
        val cutoffTime = Instant.now().minusSeconds(1).toString() // Hide all inactive units
        unitsDao.markInactiveUnitsHiddenOlderThan(cutoffTime)
    }

    /**
     * Mark inactive units older than specified days as hidden
     */
    suspend fun hideOldInactiveUnits(daysOld: Int = 7) {
        val cutoffTime = Instant.now().minus(daysOld.toLong(), ChronoUnit.DAYS).toString()
        unitsDao.markInactiveUnitsHiddenOlderThan(cutoffTime)
    }

    /**
     * Mark units (both active and inactive) older than specified seconds as hidden
     */
    suspend fun hideOldUnits(seconds: Int) {
        val cutoffTime = Instant.now().minusSeconds(seconds.toLong()).toString()
        unitsDao.markUnitsHiddenOlderThan(cutoffTime)
    }

    /**
     * Unhide all units (restore visibility to all hidden units)
     */
    suspend fun unhideAllUnits() {
        unitsDao.unhideAllUnits()
    }

    /**
     * Delete all inactive units regardless of age
     * DEPRECATED: Use hideInactiveUnits() instead
     */
    @Deprecated("Use hideInactiveUnits() to preserve data", ReplaceWith("hideInactiveUnits()"))
    suspend fun deleteInactiveUnits() {
        unitsDao.deleteAllInactiveUnits()
    }

    /**
     * Delete inactive units older than specified days
     * DEPRECATED: Use hideOldInactiveUnits() instead
     */
    @Deprecated("Use hideOldInactiveUnits() to preserve data", ReplaceWith("hideOldInactiveUnits(daysOld)"))
    suspend fun deleteOldInactiveUnits(daysOld: Int = 7) {
        val cutoffTime = Instant.now().minus(daysOld.toLong(), ChronoUnit.DAYS).toString()
        unitsDao.deleteInactiveUnitsOlderThan(cutoffTime)
    }

    /**
     * Delete units (both active and inactive) older than specified seconds
     * DEPRECATED: Use hideOldUnits() instead
     */
    @Deprecated("Use hideOldUnits() to preserve data", ReplaceWith("hideOldUnits(seconds)"))
    suspend fun deleteOldUnits(seconds: Int) {
        val cutoffTime = Instant.now().minusSeconds(seconds.toLong()).toString()
        unitsDao.deleteUnitsOlderThan(cutoffTime)
    }

    /**
     * Delete history entries older than specified days
     */
    suspend fun deleteOldHistory(daysOld: Int = 14) {
        val cutoffTime = Instant.now().minus(daysOld.toLong(), ChronoUnit.DAYS).toString()
        historyDao.deleteHistoryOlderThan(cutoffTime)
    }

    /**
     * Delete all units (both active and inactive) AND their history
     * This is a complete wipe - used when starting a new mission
     */
    suspend fun deleteAllUnits() {
        // CRITICAL: Complete database wipe to prevent old markers from reappearing
        // Step 1: Mark all units as inactive (prevents race conditions with incoming data)
        unitsDao.markAllUnitsInactive()
        // Step 2: Delete all history (must be before units due to foreign keys)
        historyDao.deleteAllHistory()
        // Step 3: Delete all units
        unitsDao.deleteAllUnits()
    }    
    /**
     * Toggle highlight status for a unit
     */
    suspend fun toggleUnitHighlight(unitId: Int, isHighlighted: Boolean) {
        unitsDao.setUnitHighlighted(unitId, if (isHighlighted) 1 else 0)
    }
    
    /**
     * Clear all highlights
     */
    suspend fun clearAllHighlights() {
        unitsDao.clearAllHighlights()
    }    
    /**
     * Delete all history entries
     */
    suspend fun deleteAllHistory() {
        historyDao.deleteAllHistory()
    }
    
    /**
     * Mark all currently active units as inactive
     * (Useful for session reset)
     */
    suspend fun markAllUnitsInactive() {
        unitsDao.markAllUnitsInactive()
    }
    
    // --- Utility functions ---
    
    /**
     * Get coalition name from coalition code
     */
    fun getCoalitionName(coalition: Int): String {
        return when (coalition) {
            0 -> "Neutral"
            1 -> "Red"
            2 -> "Blue"
            else -> "Unknown"
        }
    }
    
    /**
     * Get category display name
     */
    fun getCategoryDisplayName(category: String): String {
        return when (category.lowercase()) {
            "aircraft" -> "Aircraft"
            "helicopter" -> "Helicopter"
            "ground" -> "Ground"
            "ship" -> "Ship"
            "structure" -> "Structure"
            "weapon" -> "Weapon"
            "countermeasure" -> "Countermeasure"
            else -> category.capitalize()
        }
    }
}
