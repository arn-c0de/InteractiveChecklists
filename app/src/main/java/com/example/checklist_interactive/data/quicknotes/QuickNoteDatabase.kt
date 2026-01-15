package com.example.checklist_interactive.data.quicknotes

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for QuickNotes
 * Manages database creation and version management
 */
@Database(
    entities = [QuickNoteEntity::class],
    version = 2,
    exportSchema = false
)
abstract class QuickNoteDatabase : RoomDatabase() {
    abstract fun quickNoteDao(): QuickNoteDao

    companion object {
        @Volatile
        private var INSTANCE: QuickNoteDatabase? = null

        // Migration from version 1 to 2: add 'drawing' column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE quick_notes ADD COLUMN drawing TEXT")
            }
        }

        fun getDatabase(context: Context): QuickNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuickNoteDatabase::class.java,
                    "quick_notes_database"
                )
                        // Try an additive migration first to preserve user data
                        .addMigrations(MIGRATION_1_2)
                        // Keep destructive fallback only as a last resort during development
                        .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Destroy the database instance (useful for testing)
         */
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
