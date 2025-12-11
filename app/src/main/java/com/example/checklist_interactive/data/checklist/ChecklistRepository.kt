package com.example.checklist_interactive.data.checklist

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "checklist_states"
private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)
private val LAST_OPENED_FILE_KEY = stringPreferencesKey("last_opened_file")
private val ROOT_FOLDER_KEY = stringPreferencesKey("root_folder")

class ChecklistRepository(private val context: Context) {
    private val parser = MarkdownChecklistParser()

    suspend fun loadChecklistFromAssets(assetPath: String, id: String): Checklist {
        val markdown = readAsset(assetPath)
        val checklist = parser.parse(id, markdown)
        val checkedSet = loadCheckedSet(id)
        // Apply checked state to items
        val updatedSections = checklist.sections.map { section ->
            section.copy(items = section.items.map { item ->
                item.copy(isChecked = checkedSet.contains(item.id))
            })
        }
        return checklist.copy(sections = updatedSections)
    }

    suspend fun toggleItem(checklistId: String, itemId: String, checked: Boolean) {
        val key = stringSetPreferencesKey("checklist_$checklistId")
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (checked) {
                current.add(itemId)
            } else {
                current.remove(itemId)
            }
            prefs[key] = current
        }
    }

    suspend fun resetChecklist(checklistId: String) {
        val key = stringSetPreferencesKey("checklist_$checklistId")
        context.dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }

    suspend fun saveLastOpenedFile(filePath: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_OPENED_FILE_KEY] = filePath
        }
    }

    suspend fun getLastOpenedFile(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[LAST_OPENED_FILE_KEY]
        }.first()
    }

    suspend fun saveRootFolder(folderPath: String) {
        context.dataStore.edit { prefs ->
            prefs[ROOT_FOLDER_KEY] = folderPath
        }
    }

    suspend fun getRootFolder(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[ROOT_FOLDER_KEY]
        }.first()
    }

    fun exportChecklistToMarkdown(checklist: Checklist): String {
        val sb = StringBuilder()
        checklist.sections.forEach { section ->
            // Heading
            sb.append("## ").append(section.title).append("\n\n")
            // Items
            section.items.forEach { item ->
                val mark = if (item.isChecked) "[x]" else "[ ]"
                sb.append("- ").append(mark).append(" ").append(item.text).append("\n")
            }
            sb.append("\n")
        }
        return sb.toString().trimEnd()
    }

    private suspend fun loadCheckedSet(checklistId: String): Set<String> {
        val key = stringSetPreferencesKey("checklist_$checklistId")
        val prefs = context.dataStore.data.map { it[key] ?: emptySet() }.first()
        return prefs
    }

    private fun readAsset(path: String): String {
        context.assets.open(path).use { stream ->
            return stream.bufferedReader().use { it.readText() }
        }
    }
}
