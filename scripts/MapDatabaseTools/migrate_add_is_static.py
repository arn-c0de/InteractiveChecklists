"""
Migration script to add is_static column to existing databases
Run this to update existing map_data.db files with the new schema
"""
import sqlite3
from pathlib import Path
import sys

def migrate_database(db_path: Path):
    """Add is_static column if it doesn't exist"""
    if not db_path.exists():
        print(f"Database not found: {db_path}")
        return False
    
    conn = sqlite3.connect(str(db_path))
    cursor = conn.cursor()
    
    try:
        # Check if column already exists
        cursor.execute("PRAGMA table_info(locations)")
        columns = [row[1] for row in cursor.fetchall()]
        
        if 'is_static' in columns:
            print("✓ Column 'is_static' already exists")
            return True
        
        # Add the column
        print("Adding 'is_static' column to locations table...")
        cursor.execute("ALTER TABLE locations ADD COLUMN is_static INTEGER NOT NULL DEFAULT 0")
        
        # Mark airports as static automatically
        print("Marking airports as static...")
        cursor.execute("UPDATE locations SET is_static = 1 WHERE marker_type = 'airport'")
        affected = cursor.rowcount
        print(f"✓ Marked {affected} airports as static")
        
        # Also add military symbol columns if they don't exist
        if 'symbol_set' not in columns:
            print("Adding military symbol columns...")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_set TEXT NOT NULL DEFAULT ''")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_entity TEXT NOT NULL DEFAULT ''")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_size TEXT NOT NULL DEFAULT ''")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_affiliation TEXT NOT NULL DEFAULT 'unknown'")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_color TEXT NOT NULL DEFAULT '#FFFF80'")
            cursor.execute("ALTER TABLE locations ADD COLUMN symbol_modifier TEXT NOT NULL DEFAULT ''")
            print("✓ Added military symbol columns")
        
        conn.commit()
        print("✓ Migration completed successfully")
        return True
        
    except Exception as e:
        print(f"✗ Migration failed: {e}")
        conn.rollback()
        return False
    finally:
        conn.close()


def main():
    """Run migration on default database location"""
    # Default location: app/src/main/assets/databases/map_data.db
    project_root = Path(__file__).parents[2]
    db_path = project_root / "app" / "src" / "main" / "assets" / "databases" / "map_data.db"
    
    print(f"Migrating database: {db_path}")
    print("=" * 60)
    
    success = migrate_database(db_path)
    
    if success:
        print("\n✓ All migrations completed successfully!")
        print(f"\nDatabase location: {db_path}")
        print("\nChanges:")
        print("  • Added 'is_static' column to locations table")
        print("  • Marked existing airports as static")
        print("  • Added military symbol columns (if missing)")
        return 0
    else:
        print("\n✗ Migration failed!")
        return 1


if __name__ == "__main__":
    sys.exit(main())
