#!/usr/bin/env python3
"""
Set database user_version to a specific value
Useful for resetting version to 1 when DB structure is finalized
"""
import sqlite3
import sys
from pathlib import Path

# Default path to asset database
DEFAULT_DB_PATH = Path(__file__).parent.parent.parent / "app" / "src" / "main" / "assets" / "databases" / "map_data.db"

def get_current_version(db_path: Path) -> int:
    """Get current user_version from database"""
    conn = sqlite3.connect(str(db_path))
    cursor = conn.cursor()
    cursor.execute("PRAGMA user_version")
    version = cursor.fetchone()[0]
    conn.close()
    return version

def set_version(db_path: Path, new_version: int):
    """Set user_version in database"""
    conn = sqlite3.connect(str(db_path))
    cursor = conn.cursor()
    cursor.execute(f"PRAGMA user_version = {new_version}")
    conn.commit()
    conn.close()

def main():
    db_path = DEFAULT_DB_PATH

    # Check if version argument provided
    if len(sys.argv) < 2:
        print("Usage: python set_db_version.py <version> [db_path]")
        print("\nExample:")
        print("  python set_db_version.py 1         # Reset to version 1")
        print("  python set_db_version.py 10        # Set to version 10")
        print("  python set_db_version.py 0         # Reset to 0 (first run)")
        sys.exit(1)

    try:
        new_version = int(sys.argv[1])
    except ValueError:
        print(f"ERROR: Version must be an integer, got: {sys.argv[1]}")
        sys.exit(1)

    # Allow custom DB path as second argument
    if len(sys.argv) > 2:
        db_path = Path(sys.argv[2])

    if not db_path.exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    print(f"Database: {db_path}")

    # Show current version
    current = get_current_version(db_path)
    print(f"Current version: {current}")

    # Set new version
    set_version(db_path, new_version)
    print(f"Version set to: {new_version}")

    if new_version == 0:
        print("\nNOTE: Version 0 means 'first run' - app will not show update dialog")
    elif new_version < current:
        print(f"\nWARNING: You decreased the version from {current} to {new_version}")
        print("Make sure to clear app data or the update dialog won't appear!")

if __name__ == "__main__":
    main()
