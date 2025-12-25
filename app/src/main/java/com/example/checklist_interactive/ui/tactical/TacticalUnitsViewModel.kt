package com.example.checklist_interactive.ui.tactical

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.tactical.TacticalUnitEntity
import com.example.checklist_interactive.data.tactical.TacticalUnitsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Tactical Units List Screen
 */
class TacticalUnitsViewModel(
    application: Application,
    private val repository: TacticalUnitsRepository,
    private val dataPadManager: com.example.checklist_interactive.data.datapad.DataPadManager
) : AndroidViewModel(application) {

    // --- UI State ---
    
    private val _uiState = MutableStateFlow(TacticalUnitsUiState())
    val uiState: StateFlow<TacticalUnitsUiState> = _uiState.asStateFlow()
    
    // --- Units List (filtered) ---
    
    val units: StateFlow<List<TacticalUnitEntity>> = combine(
        repository.getAllUnits(),
        repository.getLiveUnits(),
        _uiState
    ) { allUnits, liveUnits, state ->
        // Use live units if live filter is enabled, otherwise all units
        val sourceUnits = if (state.showLiveOnly) liveUnits else allUnits
        
        sourceUnits.filter { unit ->
            // Filter by active status
            if (state.showActiveOnly && unit.isActive != 1) return@filter false
            
            // Filter by categories
            if (state.selectedCategories.isNotEmpty() && !state.selectedCategories.contains(unit.category)) {
                return@filter false
            }
            
            // Filter by coalitions
            if (state.selectedCoalitions.isNotEmpty() && !state.selectedCoalitions.contains(unit.coalition)) {
                return@filter false
            }
            
            // Filter by search query
            if (state.searchQuery.isNotEmpty()) {
                val query = state.searchQuery.lowercase()
                val matchesName = unit.name.lowercase().contains(query)
                val matchesGroup = unit.groupName?.lowercase()?.contains(query) ?: false
                val matchesPilot = unit.pilotName?.lowercase()?.contains(query) ?: false
                if (!matchesName && !matchesGroup && !matchesPilot) return@filter false
            }
            
            true
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // --- Statistics ---
    
    // Statistics based on currently visible (filtered) units
    val stats: StateFlow<UnitStatistics> = units.map { filteredUnits ->
        val categoryDist = filteredUnits.groupingBy { it.category }.eachCount()
        val coalitionDist = filteredUnits.groupingBy { it.coalition }.eachCount()
        
        UnitStatistics(
            totalActive = filteredUnits.size,
            aircraftCount = categoryDist["aircraft"] ?: 0,
            helicopterCount = categoryDist["helicopter"] ?: 0,
            groundCount = categoryDist["ground"] ?: 0,
            shipCount = categoryDist["ship"] ?: 0,
            structureCount = categoryDist["structure"] ?: 0,
            weaponCount = categoryDist["weapon"] ?: 0,
            neutralCount = coalitionDist[0] ?: 0,
            redCount = coalitionDist[1] ?: 0,
            blueCount = coalitionDist[2] ?: 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UnitStatistics()
    )
    
    // --- Actions ---
    
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun toggleCategory(category: String) {
        _uiState.update { state ->
            val newCategories = if (state.selectedCategories.contains(category)) {
                state.selectedCategories - category
            } else {
                state.selectedCategories + category
            }
            state.copy(selectedCategories = newCategories)
        }
    }
    
    fun toggleCoalition(coalition: Int) {
        _uiState.update { state ->
            val newCoalitions = if (state.selectedCoalitions.contains(coalition)) {
                state.selectedCoalitions - coalition
            } else {
                state.selectedCoalitions + coalition
            }
            state.copy(selectedCoalitions = newCoalitions)
        }
    }
    
    fun setShowActiveOnly(activeOnly: Boolean) {
        _uiState.update { it.copy(showActiveOnly = activeOnly) }
    }
    
    fun setShowLiveOnly(liveOnly: Boolean) {
        _uiState.update { it.copy(showLiveOnly = liveOnly) }
        dataPadManager.setTacticalUnitsShowLiveOnly(liveOnly)
    }
    
    fun toggleLiveOnly() {
        setShowLiveOnly(!_uiState.value.showLiveOnly)
    }
    
    fun clearFilters() {
        _uiState.update {
            TacticalUnitsUiState(
                searchQuery = "",
                selectedCategories = emptySet(),
                selectedCoalitions = emptySet(),
                showActiveOnly = true
            )
        }
    }
    
    fun toggleFilterDialog() {
        _uiState.update { it.copy(showFilterDialog = !it.showFilterDialog) }
    }
    
    // --- Cleanup Actions ---
    
    fun deleteAllUnits() {
        viewModelScope.launch {
            repository.deleteAllUnits()
        }
    }
    
    fun deleteOldInactiveUnits(daysOld: Int = 7) {
        viewModelScope.launch {
            repository.deleteOldInactiveUnits(daysOld)
        }
    }
    
    fun deleteOldHistory(daysOld: Int = 14) {
        viewModelScope.launch {
            repository.deleteOldHistory(daysOld)
        }
    }
    
    // --- Utility ---
    
    fun getCoalitionName(coalition: Int): String {
        return repository.getCoalitionName(coalition)
    }
    
    fun getCategoryDisplayName(category: String): String {
        return repository.getCategoryDisplayName(category)
    }
}

/**
 * UI State for Tactical Units Screen
 */
data class TacticalUnitsUiState(
    val searchQuery: String = "",
    val selectedCategories: Set<String> = emptySet(),
    val selectedCoalitions: Set<Int> = emptySet(),
    val showActiveOnly: Boolean = true,
    val showLiveOnly: Boolean = false,  // Filter: only show units seen in last 10 seconds
    val showFilterDialog: Boolean = false
)

/**
 * Statistics for unit counts
 */
data class UnitStatistics(
    val totalActive: Int = 0,
    val aircraftCount: Int = 0,
    val helicopterCount: Int = 0,
    val groundCount: Int = 0,
    val shipCount: Int = 0,
    val structureCount: Int = 0,
    val weaponCount: Int = 0,
    val neutralCount: Int = 0,
    val redCount: Int = 0,
    val blueCount: Int = 0
)

/**
 * Factory for creating TacticalUnitsViewModel
 */
class TacticalUnitsViewModelFactory(
    private val application: Application,
    private val repository: TacticalUnitsRepository,
    private val dataPadManager: com.example.checklist_interactive.data.datapad.DataPadManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TacticalUnitsViewModel::class.java)) {
            return TacticalUnitsViewModel(application, repository, dataPadManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
