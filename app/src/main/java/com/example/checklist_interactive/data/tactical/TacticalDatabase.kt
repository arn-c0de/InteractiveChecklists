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
        RouteWaypointEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TacticalDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun borderDao(): BorderDao
    abstract fun routeDao(): RouteDao
    
    companion object {
        @Volatile
        private var INSTANCE: TacticalDatabase? = null
        
        private const val DATABASE_NAME = "map_data.db"
        
        /**
         * Get database instance
         * 
         * @param context Application context
         * @param useExternalPath If true, uses external storage path for sharing with Python tools
         */
        fun getInstance(context: Context, useExternalPath: Boolean = false): TacticalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = if (useExternalPath) {
                    createExternalDatabase(context)
                } else {
                    createInternalDatabase(context)
                }
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Create database in internal app storage (default)
         * Copies from assets/databases/map_data.db if database doesn't exist
         */
        private fun createInternalDatabase(context: Context): TacticalDatabase {
            android.util.Log.d("TacticalDatabase", "Using prepackaged DB from assets/databases/$DATABASE_NAME")
            return Room.databaseBuilder(
                context.applicationContext,
                TacticalDatabase::class.java,
                DATABASE_NAME
            )
                .createFromAsset("databases/$DATABASE_NAME")
                .fallbackToDestructiveMigration()
                .build()
        }
        
        /**
         * Create database in external storage for sharing with Python tools
         * Location: /sdcard/Android/data/com.example_checklist_interactive/files/map_data.db
         */
        private fun createExternalDatabase(context: Context): TacticalDatabase {
            val externalDir = context.getExternalFilesDir(null)
            val dbFile = File(externalDir, DATABASE_NAME)
            
            return Room.databaseBuilder(
                context.applicationContext,
                TacticalDatabase::class.java,
                dbFile.absolutePath
            )
                .fallbackToDestructiveMigration()
                .build()
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
    }
}
