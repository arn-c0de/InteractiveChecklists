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
    version = 1,
    exportSchema = true
)
@TypeConverters(LinkedDocumentConverter::class)
abstract class QuickNoteDatabase : RoomDatabase() {
    abstract fun quickNoteDao(): QuickNoteDao

    companion object {
        @Volatile
        private var INSTANCE: QuickNoteDatabase? = null

        fun getDatabase(context: Context): QuickNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuickNoteDatabase::class.java,
                    "quick_notes_database"
                )
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
