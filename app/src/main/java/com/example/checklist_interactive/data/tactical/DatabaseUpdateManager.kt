package com.example.checklist_interactive.data.tactical

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages detection and handling of database updates from assets
 * Shows dialog to user when database version changes
 */
class DatabaseUpdateManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("db_update_prefs", Context.MODE_PRIVATE)
    
    private val _showUpdateDialog = mutableStateOf(false)
    val showUpdateDialog: State<Boolean> = _showUpdateDialog
    
    private val _assetDbVersion = mutableStateOf(0)
    val assetDbVersion: State<Int> = _assetDbVersion
    
    private val _currentDbVersion = mutableStateOf(0)
    val currentDbVersion: State<Int> = _currentDbVersion
    
    companion object {
        private const val KEY_LAST_ASSET_VERSION = "last_asset_version"
        private const val ASSET_DB_PATH = "databases/map_data.db"
    }
    
    /**
     * Check if asset database has been updated since last check
     * Call this on app startup
     */
    fun checkForDatabaseUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get asset DB version (user_version pragma)
                val assetVersion = getAssetDatabaseVersion()
                _assetDbVersion.value = assetVersion
                
                // Get currently tracked version
                val lastTrackedVersion = prefs.getInt(KEY_LAST_ASSET_VERSION, 0)
                _currentDbVersion.value = lastTrackedVersion
                
                // If asset version is newer, show dialog
                if (assetVersion > lastTrackedVersion && lastTrackedVersion > 0) {
                    android.util.Log.i("DatabaseUpdateManager", "New database version detected: $assetVersion (was $lastTrackedVersion)")
                    _showUpdateDialog.value = true
                } else if (lastTrackedVersion == 0) {
                    // First run - just track version without showing dialog
                    prefs.edit().putInt(KEY_LAST_ASSET_VERSION, assetVersion).apply()
                    _currentDbVersion.value = assetVersion
                }
            } catch (e: Exception) {
                android.util.Log.e("DatabaseUpdateManager", "Error checking database version", e)
            }
        }
    }
    
    /**
     * Get user_version from asset database
     */
    fun getAssetDatabaseVersion(): Int {
        var version = 0
        try {
            // Copy asset to temp file and read version
            val tempFile = File(context.cacheDir, "temp_map_data.db")
            context.assets.open(ASSET_DB_PATH).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Open the DB in read-only mode to avoid SQLiteOpenHelper upgrades/downgrades
            val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = sqlite.rawQuery("PRAGMA user_version", null)
            if (cursor.moveToFirst()) {
                version = cursor.getInt(0)
            }
            cursor.close()
            sqlite.close()
            tempFile.delete()
        } catch (e: Exception) {
            android.util.Log.e("DatabaseUpdateManager", "Error reading asset DB version", e)
        }
        return version
    }

    /**
     * Get user_version from the currently installed internal database (if present)
     */
    fun getInstalledDatabaseVersion(): Int {
        var version = 0
        try {
            val dbFile = context.getDatabasePath("map_data.db")
            if (!dbFile.exists()) return 0

            // Open the DB in read-only mode to avoid triggering callbacks that may attempt downgrades
            val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = sqlite.rawQuery("PRAGMA user_version", null)
            if (cursor.moveToFirst()) {
                version = cursor.getInt(0)
            }
            cursor.close()
            sqlite.close()
        } catch (e: Exception) {
            android.util.Log.e("DatabaseUpdateManager", "Error reading installed DB version", e)
        }
        return version
    }
    
    /**
     * Import new database - merge mode (preserve user data, import new locations)
     */
    fun importDatabaseMerge(onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.i("DatabaseUpdateManager", "Importing database (merge mode)")
                
                // TODO: Implement merge logic
                // 1. Read all locations from asset DB
                // 2. Check which locations don't exist in current DB (by ICAO/name)
                // 3. Insert only new locations
                
                // For now, just update version tracking
                prefs.edit().putInt(KEY_LAST_ASSET_VERSION, _assetDbVersion.value).apply()
                _currentDbVersion.value = _assetDbVersion.value
                _showUpdateDialog.value = false
                
                CoroutineScope(Dispatchers.Main).launch {
                    onComplete()
                }
            } catch (e: Exception) {
                android.util.Log.e("DatabaseUpdateManager", "Error merging database", e)
            }
        }
    }
    
    /**
     * Import new database - clean mode (wipe internal DB, import all from asset)
     */
    fun importDatabaseClean(onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.i("DatabaseUpdateManager", "Importing database (clean mode)")
                
                // Close existing database instance if present
                try {
                    TacticalDatabase.getInstance(context).close()
                } catch (e: Exception) {
                    // ignore
                }

                // Delete internal database file
                val dbPath = context.getDatabasePath("map_data.db")
                if (dbPath.exists()) {
                    dbPath.delete()
                    android.util.Log.i("DatabaseUpdateManager", "Deleted internal database")
                }

                // Recreate database from asset
                val newDb = TacticalDatabase.recreateInstance(context)

                // Update version tracking and dismiss dialog
                prefs.edit().putInt(KEY_LAST_ASSET_VERSION, _assetDbVersion.value).apply()
                _currentDbVersion.value = _assetDbVersion.value
                _showUpdateDialog.value = false
                
                CoroutineScope(Dispatchers.Main).launch {
                    onComplete()
                }
            } catch (e: Exception) {
                android.util.Log.e("DatabaseUpdateManager", "Error cleaning database", e)
            }
        }
    }
    
    /**
     * Dismiss dialog without importing
     */
    fun dismissDialog() {
        _showUpdateDialog.value = false
        // Still update tracked version so we don't show dialog again
        prefs.edit().putInt(KEY_LAST_ASSET_VERSION, _assetDbVersion.value).apply()
        _currentDbVersion.value = _assetDbVersion.value
    }
}
