package com.example.checklist_interactive.ui.checklist

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.checklist.ChecklistItem
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChecklistViewModel(
    private val repository: ChecklistRepository,
    private val initialChecklist: Checklist
) : ViewModel() {

    private val _checklistState = MutableStateFlow(initialChecklist)
    val checklistState: StateFlow<Checklist> = _checklistState.asStateFlow()

    init {
        loadChecklistState()
    }

    private fun loadChecklistState() {
        viewModelScope.launch {
            val savedStates = repository.getChecklistState(initialChecklist.id)
            val updatedSections = initialChecklist.sections.map { section ->
                val updatedItems = section.items.map { item ->
                    item.copy(isChecked = savedStates[item.id] ?: item.isChecked)
                }
                section.copy(items = updatedItems)
            }
            _checklistState.value = initialChecklist.copy(sections = updatedSections)
        }
    }

    fun onCheckboxChange(itemId: String, isChecked: Boolean) {
        android.util.Log.d("ChecklistViewModel", "onCheckboxChange called: itemId=$itemId, isChecked=$isChecked")
        viewModelScope.launch {
            // Update local state immediately for UI responsiveness
            _checklistState.update { currentState ->
                val updatedSections = currentState.sections.map { section ->
                    val updatedItems = section.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(isChecked = isChecked)
                        } else {
                            item
                        }
                    }
                    section.copy(items = updatedItems)
                }
                currentState.copy(sections = updatedSections)
            }
            android.util.Log.d("ChecklistViewModel", "State updated, new value: ${_checklistState.value}")

            // Save the state to the repository
            repository.saveChecklistItemState(initialChecklist.id, itemId, isChecked)
        }
    }

    fun resetChecklist() {
        viewModelScope.launch {
            // Clear saved state from repository
            repository.clearChecklistState(initialChecklist.id)

            // Reset the state to the initial default state from the markdown file
            val updatedSections = initialChecklist.sections.map { section ->
                val updatedItems = section.items.map { item ->
                    item.copy(isChecked = item.isChecked) // Restore to original parsed state
                }
                section.copy(items = updatedItems)
            }
             _checklistState.value = initialChecklist.copy(sections = updatedSections)
        }
    }

    fun exportChecklistMarkdown(): String {
        val checklist = _checklistState.value
        val sb = StringBuilder()
        sb.append("# ").append(checklist.title).append("\n\n")
        checklist.sections.forEach { section ->
            sb.append("## ").append(section.title).append("\n\n")
            section.items.forEach { item ->
                val indent = "    ".repeat(item.indent)
                if (item.isTask) {
                    val checkbox = if (item.isChecked) "[x]" else "[ ]"
                    sb.append(indent).append("- ").append(checkbox).append(" ").append(item.text).append("\n")
                } else {
                    sb.append(indent).append("- ").append(item.text).append("\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

class ChecklistViewModelFactory(
    private val repository: ChecklistRepository,
    private val checklist: Checklist
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChecklistViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChecklistViewModel(repository, checklist) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}