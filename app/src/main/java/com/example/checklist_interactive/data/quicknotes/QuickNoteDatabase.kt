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
    version = 4,
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

        // Migration from version 2 to 3: add 'lastModified' column
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Check if column already exists to avoid duplicate column error
                val cursor = database.query("PRAGMA table_info(quick_notes)")
                var columnExists = false
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndex("name"))
                    if (columnName == "lastModified") {
                        columnExists = true
                        break
                    }
                }
                cursor.close()

                // Only add column if it doesn't exist
                if (!columnExists) {
                    database.execSQL("ALTER TABLE quick_notes ADD COLUMN lastModified INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
                }
            }
        }

        // Migration from version 3 to 4: remove 'linkedDocuments' column if it exists
        // This fixes schema mismatch issues
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Check if linkedDocuments column exists
                val cursor = database.query("PRAGMA table_info(quick_notes)")
                var linkedDocumentsExists = false
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndex("name"))
                    if (columnName == "linkedDocuments") {
                        linkedDocumentsExists = true
                        break
                    }
                }
                cursor.close()

                // Only rebuild table if linkedDocuments exists
                if (linkedDocumentsExists) {
                    // Create new table with correct schema
                    database.execSQL("""
                        CREATE TABLE quick_notes_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            drawing TEXT,
                            timestamp INTEGER NOT NULL,
                            lastModified INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Copy data from old table, excluding linkedDocuments
                    database.execSQL("""
                        INSERT INTO quick_notes_new (id, title, content, drawing, timestamp, lastModified)
                        SELECT id, title, content, drawing, timestamp, lastModified
                        FROM quick_notes
                    """.trimIndent())

                    // Drop old table
                    database.execSQL("DROP TABLE quick_notes")

                    // Rename new table to original name
                    database.execSQL("ALTER TABLE quick_notes_new RENAME TO quick_notes")
                }
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
