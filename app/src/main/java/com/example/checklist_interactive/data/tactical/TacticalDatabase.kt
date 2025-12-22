package com.example.checklist_interactive.data.tactical

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        MapDrawingEntity::class
    ],
    version = 5,
    exportSchema = true
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
    
    companion object {
        @Volatile
        private var INSTANCE: TacticalDatabase? = null
        
        private const val DATABASE_NAME = "map_data.db"
        
        /**
         * Migration from v1 to v2: Add extended schema (runways, services, media, tags, navaids)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper function to check if column exists
                fun columnExists(tableName: String, columnName: String): Boolean {
                    db.query("PRAGMA table_info($tableName)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == columnName) {
                                return true
                            }
                        }
                    }
                    return false
                }

                // Add new columns to locations (only if they don't exist)
                if (!columnExists("locations", "elevation_ft")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN elevation_ft INTEGER DEFAULT 0")
                }
                if (!columnExists("locations", "country")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN country TEXT")
                }
                if (!columnExists("locations", "region")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN region TEXT")
                }
                if (!columnExists("locations", "timezone")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN timezone TEXT")
                }
                if (!columnExists("locations", "source")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN source TEXT")
                }
                if (!columnExists("locations", "verified")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN verified INTEGER DEFAULT 0")
                }
                if (!columnExists("locations", "last_verified_at")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN last_verified_at TEXT")
                }
                if (!columnExists("locations", "created_at")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN created_at TEXT")
                }
                if (!columnExists("locations", "updated_at")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN updated_at TEXT")
                }
                if (!columnExists("locations", "deleted_at")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN deleted_at TEXT")
                }
                if (!columnExists("locations", "geom")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN geom TEXT")
                }
                if (!columnExists("locations", "elevation_source")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN elevation_source TEXT")
                }
                if (!columnExists("locations", "elevation_accuracy_m")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN elevation_accuracy_m REAL")
                }
                
                // Backfill elevation_ft from elevation_m
                db.execSQL("UPDATE locations SET elevation_ft = ROUND(COALESCE(elevation_m, 0) * 3.28084)")
                
                // Backfill audit timestamps
                db.execSQL("UPDATE locations SET created_at = created WHERE created IS NOT NULL")
                db.execSQL("UPDATE locations SET updated_at = modified WHERE modified IS NOT NULL")
                
                // Create runways table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS runways (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        location_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        length_m INTEGER,
                        length_ft INTEGER,
                        width_m INTEGER,
                        width_ft INTEGER,
                        surface TEXT,
                        heading_deg REAL,
                        ils_frequency TEXT,
                        has_lighting INTEGER NOT NULL DEFAULT 0,
                        touchdown_start_lat REAL,
                        touchdown_start_lon REAL,
                        touchdown_end_lat REAL,
                        touchdown_end_lon REAL,
                        notes TEXT,
                        FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
                    )
                """)
                // Drop old incorrectly named index if it exists
                db.execSQL("DROP INDEX IF EXISTS idx_runways_location")
                // Create index with Room's expected name
                db.execSQL("CREATE INDEX IF NOT EXISTS index_runways_location_id ON runways(location_id)")
                
                // Create services table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS services (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        location_id INTEGER NOT NULL,
                        service_type TEXT NOT NULL,
                        available INTEGER NOT NULL DEFAULT 1,
                        details TEXT,
                        FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("DROP INDEX IF EXISTS idx_services_location")
                db.execSQL("DROP INDEX IF EXISTS idx_services_type")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_services_location_id ON services(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_services_service_type ON services(service_type)")
                
                // Create media table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS media (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        location_id INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        caption TEXT,
                        media_type TEXT,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT,
                        FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("DROP INDEX IF EXISTS idx_media_location")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_location_id ON media(location_id)")
                
                // Create tags table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL UNIQUE
                    )
                """)
                db.execSQL("DROP INDEX IF EXISTS idx_tags_name")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
                
                // Create location_tags join table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS location_tags (
                        location_id INTEGER NOT NULL,
                        tag_id INTEGER NOT NULL,
                        PRIMARY KEY(location_id, tag_id),
                        FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE,
                        FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("DROP INDEX IF EXISTS idx_location_tags_location")
                db.execSQL("DROP INDEX IF EXISTS idx_location_tags_tag")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_tags_location_id ON location_tags(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_tags_tag_id ON location_tags(tag_id)")
                
                // Create navaids table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS navaids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        location_id INTEGER,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        ident TEXT,
                        frequency TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        elevation_m REAL,
                        range_nm REAL,
                        bearing_deg REAL,
                        notes TEXT,
                        FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE SET NULL
                    )
                """)
                db.execSQL("DROP INDEX IF EXISTS idx_navaids_location")
                db.execSQL("DROP INDEX IF EXISTS idx_navaids_type")
                db.execSQL("DROP INDEX IF EXISTS idx_navaids_ident")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_navaids_location_id ON navaids(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_navaids_type ON navaids(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_navaids_ident ON navaids(ident)")
                
                // Create new indices on locations - use Room's auto-generated naming convention
                // Room generates index names as: index_<tablename>_<column(s)>
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_icao ON locations(icao)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_country ON locations(country)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_verified ON locations(verified)")
                
                // Drop old indices with idx_ prefix if they exist (from earlier versions)
                db.execSQL("DROP INDEX IF EXISTS idx_locations_icao")
                db.execSQL("DROP INDEX IF EXISTS idx_locations_country")
                db.execSQL("DROP INDEX IF EXISTS idx_locations_verified")
                
                // Insert sample tags
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('airbase')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('civilian')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('military')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('FARP')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('landmark')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('town')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('danger_zone')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('refuel_point')")
                db.execSQL("INSERT OR IGNORE INTO tags (name) VALUES ('training_area')")
            }
        }

        // Migration from v2 to v3: add military symbol columns
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper function to check if column exists
                fun columnExists(tableName: String, columnName: String): Boolean {
                    db.query("PRAGMA table_info($tableName)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == columnName) {
                                return true
                            }
                        }
                    }
                    return false
                }

                if (!columnExists("locations", "symbol_set")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_set TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists("locations", "symbol_entity")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_entity TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists("locations", "symbol_size")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_size TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists("locations", "symbol_affiliation")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_affiliation TEXT NOT NULL DEFAULT 'unknown'")
                }
                if (!columnExists("locations", "symbol_color")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_color TEXT NOT NULL DEFAULT '#FFFF80'")
                }
                if (!columnExists("locations", "symbol_modifier")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN symbol_modifier TEXT NOT NULL DEFAULT ''")
                }
            }
        }
        
        // Migration from v3 to v4: add is_static column for static markers (airports, installations)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Helper function to check if column exists
                fun columnExists(tableName: String, columnName: String): Boolean {
                    db.query("PRAGMA table_info($tableName)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == columnName) {
                                return true
                            }
                        }
                    }
                    return false
                }

                if (!columnExists("locations", "is_static")) {
                    db.execSQL("ALTER TABLE locations ADD COLUMN is_static INTEGER NOT NULL DEFAULT 0")
                    // Mark airports as static automatically
                    db.execSQL("UPDATE locations SET is_static = 1 WHERE marker_type = 'airport'")
                }
            }
        }
        
        // Migration from v4 to v5: add map_drawings table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS map_drawings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        map_region TEXT,
                        color INTEGER NOT NULL,
                        stroke_width REAL NOT NULL,
                        brush_type TEXT NOT NULL,
                        points TEXT NOT NULL,
                        is_highlight INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        modified_at TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_map_drawings_created_at ON map_drawings(created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_map_drawings_map_region ON map_drawings(map_region)")
            }
        }
        
        /**
         * Get database instance
         * 
         * @param context Application context
         * @param useExternalPath If true, uses external storage path for sharing with Python tools
         */
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
                .createFromAsset("databases/$DATABASE_NAME")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

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
    }
}
