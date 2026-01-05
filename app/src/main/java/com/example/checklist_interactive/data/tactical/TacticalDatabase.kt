package com.example.checklist_interactive.data.tactical

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * Tactical database - stores locations, markers, routes, borders
 * Shared between Android app and Python tools via external storage
 */
@Database(
    entities = [
        LocationEntity::class,
        BorderEntity::class,
        RouteEntity::class,
        RouteWaypointEntity::class,
        RunwayEntity::class,
        ServiceEntity::class,
        MediaEntity::class,
        TagEntity::class,
        LocationTagCrossRef::class,
        NavaidEntity::class,
        MapDrawingEntity::class,
        FlightPathPoint::class,
        TacticalUnitEntity::class,
        TacticalUnitHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TacticalDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun borderDao(): BorderDao
    abstract fun routeDao(): RouteDao
    abstract fun runwayDao(): RunwayDao
    abstract fun serviceDao(): ServiceDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun navaidDao(): NavaidDao
    abstract fun mapDrawingDao(): MapDrawingDao
    abstract fun flightPathDao(): FlightPathDao
    abstract fun tacticalUnitsDao(): TacticalUnitsDao
    abstract fun tacticalUnitHistoryDao(): TacticalUnitHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: TacticalDatabase? = null
        
        private const val DATABASE_NAME = "map_data.db"
        
        /**
         * Get database instance
         *
         * IMPORTANT SAFETY CHANGE: By default this method will NOT attempt any destructive
         * recovery (deleting the user's installed DB file) if a schema mismatch is detected.
         * Destructive recovery is dangerous and may cause permanent data loss; it must be
         * explicitly allowed by callers by passing allowDestructiveMigration = true.
         *
         * @param context Application context
         * @param useExternalPath If true, uses external storage path for sharing with Python tools
         * @param allowDestructiveMigration When true, the method may attempt destructive
         * recovery (delete installed DB and recreate from asset). Default = false.
         */
        fun getInstance(context: Context, useExternalPath: Boolean = false, allowDestructiveMigration: Boolean = false): TacticalDatabase {
            return INSTANCE ?: synchronized(this) {
                // Attempt to create the database instance. If a pre-packaged DB schema mismatch is
                // detected (IllegalStateException from RoomOpenHelper), we will NOT automatically
                // delete the user's database unless allowDestructiveMigration is explicitly true.
                val instance = try {
                    if (useExternalPath) createExternalDatabase(context, allowDestructiveMigration) else createInternalDatabase(context, allowDestructiveMigration)
                } catch (e: IllegalStateException) {
                    android.util.Log.w("TacticalDatabase", "Schema mismatch when opening prepackaged DB: ${'$'}e")
                    android.util.Log.w("TacticalDatabase", "Automatic destructive recovery is disabled by default. Set allowDestructiveMigration=true to opt-in.")
                    // Propagate the exception so callers can decide how to proceed (backup, notify user, etc.)
                    throw e
                }

                // Validate by opening the writable DB now so schema mismatches are detected immediately
                try {
                    instance.openHelper.writableDatabase // forces Room to check identity
                    INSTANCE = instance
                } catch (e: IllegalStateException) {
                    android.util.Log.w("TacticalDatabase", "Detected schema mismatch when validating DB: ${'$'}e")
                    try {
                        instance.close()
                    } catch (_: Exception) {
                        // ignore
                    }

                    if (!allowDestructiveMigration) {
                        android.util.Log.w("TacticalDatabase", "Destructive recovery not allowed (allowDestructiveMigration=false). Rethrowing exception for caller to handle.")
                        throw e
                    }

                    android.util.Log.i("TacticalDatabase", "Destructive recovery allowed; attempting recovery by deleting installed DB and recreating from asset.")
                    try {
                        val dbFile = context.getDatabasePath(DATABASE_NAME)
                        if (dbFile.exists()) {
                            android.util.Log.i("TacticalDatabase", "Deleting installed DB at ${'$'}{dbFile.absolutePath} to recover from schema mismatch")
                            dbFile.delete()
                        }
                    } catch (ex: Exception) {
                        android.util.Log.e("TacticalDatabase", "Failed to delete installed DB during recovery", ex)
                    }

                    // Recreate the database (copy fresh asset) and validate again
                    val recreated = if (useExternalPath) createExternalDatabase(context, true) else createInternalDatabase(context, true)
                    try {
                        recreated.openHelper.writableDatabase
                        INSTANCE = recreated
                    } catch (ex: Exception) {
                        android.util.Log.e("TacticalDatabase", "Recovery failed - DB still invalid", ex)
                        // As a last resort, rethrow so caller sees the error
                        throw ex
                    }
                }

                // Backfill legacy runways JSON from `locations.runways` into the separate `runways` table
                // This runs in a background thread and only executes if the `runways` table is empty.
                Thread {
                    try {
                        val sqlite = INSTANCE!!.openHelper.writableDatabase
                        // Check if runways table already contains rows
                        var runwaysCount = 0
                        val cntCursor = sqlite.query("SELECT COUNT(*) FROM runways", emptyArray())
                        if (cntCursor.moveToFirst()) runwaysCount = cntCursor.getInt(0)
                        cntCursor.close()

                        if (runwaysCount == 0) {
                            val cursor = sqlite.query("SELECT id, runways FROM locations WHERE runways IS NOT NULL AND runways != ''", emptyArray())
                            try {
                                while (cursor.moveToNext()) {
                                    val locId = cursor.getInt(0)
                                    val runwaysJson = cursor.getString(1)
                                    if (runwaysJson.isNullOrEmpty()) continue
                                    try {
                                        val arr = org.json.JSONArray(runwaysJson)
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.getJSONObject(i)
                                            val name = if (obj.has("name")) obj.optString("name") else ""
                                            val lengthM = if (obj.has("length_m") && !obj.isNull("length_m")) obj.optInt("length_m") else if (obj.has("length") && !obj.isNull("length")) obj.optInt("length") else null
                                            val widthM = if (obj.has("width_m") && !obj.isNull("width_m")) obj.optInt("width_m") else null
                                            val surface = if (obj.has("surface")) obj.optString("surface") else null
                                            val heading = if (obj.has("heading") && !obj.isNull("heading")) obj.optDouble("heading") else null
                                            val ils = if (obj.has("ils")) obj.optBoolean("ils", false) else false
                                            val hasLighting = if (obj.has("has_lighting") && !obj.isNull("has_lighting")) if (obj.optBoolean("has_lighting", false)) 1 else 0 else 0
                                            val notes = if (ils) "ILS" else if (obj.has("notes")) obj.optString("notes") else null

                                            val insertSql = "INSERT INTO runways (location_id, name, length_m, width_m, surface, heading_deg, ils_frequency, has_lighting, notes) VALUES (?,?,?,?,?,?,?,?,?)"
                                            val args: Array<Any?> = arrayOf(locId, name, lengthM, widthM, surface, heading, null, if (ils) 1 else hasLighting, notes)
                                            try {
                                                sqlite.execSQL(insertSql, args)
                                            } catch (_: Exception) {
                                                // ignore single-row insert failures
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // ignore malformed per-row JSON
                                    }
                                }
                            } finally {
                                cursor.close()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TacticalDatabase", "Failed to backfill legacy runways: ${'$'}e")
                    }
                }.start()

                INSTANCE!!
            }
        }
        
        /**
         * Create database in internal app storage (default)
         * Copies from assets/databases/map_data.db if database doesn't exist
         */
        private fun createInternalDatabase(context: Context, allowDestructiveMigration: Boolean): TacticalDatabase {
            android.util.Log.d("TacticalDatabase", "Using prepackaged DB from assets/databases/$DATABASE_NAME (allowDestructiveMigration=${'$'}allowDestructiveMigration)")
            val builder = Room.databaseBuilder(
                context.applicationContext,
                TacticalDatabase::class.java,
                DATABASE_NAME
            )
                .createFromAsset("databases/$DATABASE_NAME")                .addMigrations(MIGRATION_1_2)
            if (allowDestructiveMigration) {
                builder.fallbackToDestructiveMigration()
            }

            return builder.build()
        }
        
        /**
         * Create database in external storage for sharing with Python tools
         * Location: /sdcard/Android/data/com.example_checklist_interactive/files/map_data.db
         */
        private fun createExternalDatabase(context: Context, allowDestructiveMigration: Boolean): TacticalDatabase {
            val externalDir = context.getExternalFilesDir(null)
            val dbFile = File(externalDir, DATABASE_NAME)
            
            val builder = Room.databaseBuilder(
                context.applicationContext,
                TacticalDatabase::class.java,
                dbFile.absolutePath
            )

            if (allowDestructiveMigration) {
                builder.fallbackToDestructiveMigration()
            }

            return builder.build()
        }
        
        /**
         * Get database file path
         */
        fun getDatabasePath(context: Context, useExternalPath: Boolean = false): String {
            return if (useExternalPath) {
                val externalDir = context.getExternalFilesDir(null)
                File(externalDir, DATABASE_NAME).absolutePath
            } else {
                context.getDatabasePath(DATABASE_NAME).absolutePath
            }
        }
        
        /**
         * Recreate database instance (used for clean import)
         */
        fun recreateInstance(context: Context): TacticalDatabase {
            try {
                INSTANCE?.close()
            } catch (e: Exception) {
                // ignore close failures
            }
            INSTANCE = null
            return getInstance(context)
        }
        
        /**
         * Migration from version 1 to 2: Add is_highlighted column to tactical_units
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tactical_units ADD COLUMN is_highlighted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
