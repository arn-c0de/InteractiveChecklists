package com.example.checklist_interactive.data.checklist

import android.content.Context
import com.example.checklist_interactive.data.checklist.PreferenceUtils
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ChecklistRepository(private val context: Context) {
    private fun getPreferences(checklistId: String): android.content.SharedPreferences {
        // SharedPreferences does not allow path separators in the preference name, so
        // sanitize the checklistId (which may be a file path) by hashing it.
        return context.getSharedPreferences(PreferenceUtils.getPreferenceNameForChecklist(checklistId), Context.MODE_PRIVATE)
    }

    

    suspend fun getChecklistState(checklistId: String): Map<String, Boolean> {
        val prefs = getPreferences(checklistId)
        return prefs.all.mapValues { it.value as? Boolean ?: false }
    }

    suspend fun saveChecklistItemState(checklistId: String, itemId: String, isChecked: Boolean) {
        val prefs = getPreferences(checklistId)
        prefs.edit().putBoolean(itemId, isChecked).apply()
    }

    suspend fun clearChecklistState(checklistId: String) {
        val prefs = getPreferences(checklistId)
        prefs.edit().clear().apply()
    }

    suspend fun saveLastOpenedFile(filePath: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_opened_file", filePath).apply()
    }

    suspend fun getLastOpenedFile(): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_opened_file", null)
    }

}
