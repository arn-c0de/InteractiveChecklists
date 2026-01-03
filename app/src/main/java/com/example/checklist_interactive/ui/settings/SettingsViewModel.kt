package com.example.checklist_interactive.ui.settings

import android.app.Application
import org.osmdroid.config.Configuration
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.checklist_interactive.data.files.InternalFileManager
import com.example.checklist_interactive.data.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import com.example.checklist_interactive.data.tags.FileTag
import java.io.File
import com.example.checklist_interactive.data.tactical.DatabaseUpdateManager
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
private val json = Json { prettyPrint = true }

class SettingsViewModel(
    private val app: Application,
    private val prefsManager: PreferencesManager,
    private val fileManager: InternalFileManager
) : AndroidViewModel(app) {

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _isClearingMapCache = MutableStateFlow(false)
    val isClearingMapCache = _isClearingMapCache.asStateFlow()

    private val _tagJsonContent = MutableStateFlow("")
    val tagJsonContent = _tagJsonContent.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    fun reimport() = viewModelScope.launch {
        _isImporting.value = true
        try {
            val imported = withContext(Dispatchers.IO) {
                fileManager.importAllBundledAssets("")
            }
            _snackbarMessages.tryEmit(app.resources.getQuantityString(com.example.checklist_interactive.R.plurals.imported_files, imported, imported))
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.import_failed, e.message ?: ""))
        } finally {
            _isImporting.value = false
        }
    }

    fun wipeAndReimport() = viewModelScope.launch {
        _isImporting.value = true
        try {
            val imported = withContext(Dispatchers.IO) {
                fileManager.wipeInternalRoot()
                fileManager.importAllBundledAssets("")
            }
            _snackbarMessages.tryEmit(app.resources.getQuantityString(com.example.checklist_interactive.R.plurals.wiped_imported_files, imported, imported))
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.wipe_import_failed, e.message ?: ""))
        } finally {
            _isImporting.value = false
        }
    }

    fun clearMapCache() = viewModelScope.launch {
        _isClearingMapCache.value = true
        try {
            withContext(Dispatchers.IO) {
                try {
                    val basePathFile = Configuration.getInstance().osmdroidBasePath
                    if (basePathFile != null && basePathFile.exists()) {
                        basePathFile.deleteRecursively()
                    }
                } catch (e: Exception) {
                    throw e
                }
            }
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_cache_cleared))
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_cache_clear_failed, e.message ?: ""))
        } finally {
            _isClearingMapCache.value = false
        }
    }

    fun importTagsFromAsset() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                app.assets.open("file_tags.json").use { input ->
                    val outFile = File(app.filesDir, "file_tags.json")
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                val content = app.assets.open("file_tags.json").bufferedReader().use { it.readText() }
                _tagJsonContent.value = content
            }
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_tags_imported))
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.import_failed, e.message ?: ""))
        }
    }

    fun loadInternalTagsToJson() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val tags = fileManager.tagManager.loadFileTags()
                val jsonStr = json.encodeToString(ListSerializer(FileTag.serializer()), tags)
                _tagJsonContent.value = jsonStr
            }
        } catch (e: Exception) {
            _tagJsonContent.value = app.getString(com.example.checklist_interactive.R.string.error_loading_tags, e.message ?: "")
        }
    }

    fun loadDefaultTagsAssetToTagJson() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val content = app.assets.open("file_tags.json").bufferedReader().use { it.readText() }
                _tagJsonContent.value = content
            }
        } catch (e: Exception) {
            _tagJsonContent.value = app.getString(com.example.checklist_interactive.R.string.no_default_tags_asset)
        }
    }

    // Map DB re-import state and restart request
    private val _isMapImporting = MutableStateFlow(false)
    val isMapImporting = _isMapImporting.asStateFlow()

    private val _requestRestart = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestRestart = _requestRestart.asSharedFlow()

    private val _assetDbVersion = MutableStateFlow(0)
    val assetDbVersion = _assetDbVersion.asStateFlow()

    private val _installedDbVersion = MutableStateFlow(0)
    val installedDbVersion = _installedDbVersion.asStateFlow()

    fun loadDbVersions() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                val manager = DatabaseUpdateManager(app)
                val assetV = manager.getAssetDatabaseVersion()
                val installedV = manager.getInstalledDatabaseVersion()
                _assetDbVersion.value = assetV
                _installedDbVersion.value = installedV
            }
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.import_failed, e.message ?: ""))
        }
    }

    // Preview of markers that would be affected by a clean import
    data class MarkerPreview(val id: Int, val name: String, val markerType: String)

    private val _mapWipePreview = MutableStateFlow<List<MarkerPreview>>(emptyList())
    val mapWipePreview = _mapWipePreview.asStateFlow()

    fun loadMapWipePreview() = viewModelScope.launch {
        try {
            val list = withContext(Dispatchers.IO) {
                val db = TacticalDatabase.getInstance(app)
                val locations = db.locationDao().getAllLocations().first()
                locations.map { MarkerPreview(it.id, it.name ?: "(unnamed)", it.markerType ?: "") }
            }
            _mapWipePreview.value = list
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.import_failed, e.message ?: ""))
        }
    }

    private suspend fun createDatabaseBackup(): String? = withContext(Dispatchers.IO) {
        try {
            val dbFile = app.getDatabasePath("map_data.db")
            if (!dbFile.exists()) return@withContext null
            val backupDir = java.io.File(app.filesDir, "backups")
            if (!backupDir.exists()) backupDir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dest = java.io.File(backupDir, "map_data_backup_$ts.db")
            dbFile.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_db_reimport_failed, e.message ?: ""))
            null
        }
    }

    fun wipeAndReimportMapDatabase(backupBefore: Boolean = true) = viewModelScope.launch {
        _isMapImporting.value = true
        try {
            if (backupBefore) {
                val backupPath = createDatabaseBackup()
                if (backupPath != null) {
                    _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_db_backup_created, backupPath))
                }
            }

            val dbManager = DatabaseUpdateManager(app)
            dbManager.importDatabaseClean {
                // Emit a message and request a restart so markers/details are reloaded
                _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_db_reimported))
                _requestRestart.tryEmit(Unit)
            }
        } catch (e: Exception) {
            _snackbarMessages.tryEmit(app.getString(com.example.checklist_interactive.R.string.msg_map_db_reimport_failed, e.message ?: ""))
        } finally {
            _isMapImporting.value = false
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            prefsManager: PreferencesManager,
            fileManager: InternalFileManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(application, prefsManager, fileManager) as T
            }
        }
    }
}
