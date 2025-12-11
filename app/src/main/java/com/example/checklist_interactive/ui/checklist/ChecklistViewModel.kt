package com.example.checklist_interactive.ui.checklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.checklist.Checklist
import com.example.checklist_interactive.data.checklist.ChecklistRepository
import com.example.checklist_interactive.data.checklist.ChecklistSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChecklistViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChecklistRepository(application.applicationContext)
    private val _checklist = MutableStateFlow<Checklist?>(null)
    val checklist: StateFlow<Checklist?> = _checklist

    fun loadChecklist(assetPath: String, id: String) {
        viewModelScope.launch {
            val c = repository.loadChecklistFromAssets(assetPath, id)
            _checklist.value = c
        }
    }

    fun toggleItem(checklistId: String, itemId: String, checked: Boolean) {
        viewModelScope.launch {
            repository.toggleItem(checklistId, itemId, checked)
            // update in-memory
            _checklist.value = _checklist.value?.copy(sections = _checklist.value?.sections?.map { section ->
                section.copy(items = section.items.map { item -> if (item.id == itemId) item.copy(isChecked = checked) else item })
            } ?: emptyList())
        }
    }

    fun resetChecklist(checklistId: String) {
        viewModelScope.launch {
            repository.resetChecklist(checklistId)
            // reload to reflect reset
            _checklist.value?.let { c -> loadChecklist("checklists/$checklistId.pdf", c.id) }
        }
    }

    fun exportChecklistMarkdown(): String? {
        val c = _checklist.value ?: return null
        return repository.exportChecklistToMarkdown(c)
    }
}
