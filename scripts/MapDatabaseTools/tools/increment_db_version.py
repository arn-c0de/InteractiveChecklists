#!/usr/bin/env python3
"""
Increment database user_version to trigger app update detection
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

def increment_version(db_path: Path) -> tuple[int, int]:
    """Increment user_version by 1"""
    current = get_current_version(db_path)
    new = current + 1
    set_version(db_path, new)
    return current, new

def main():
    db_path = DEFAULT_DB_PATH

    # Allow custom path as argument
    if len(sys.argv) > 1:
        db_path = Path(sys.argv[1])

    if not db_path.exists():
        print(f"ERROR: Database not found: {db_path}")
        sys.exit(1)

    print(f"Database: {db_path}")

    # Show current version
    current = get_current_version(db_path)
    print(f"Current version: {current}")

    # Increment version
    old, new = increment_version(db_path)
    print(f"Version updated: {old} -> {new}")
    print(f"\nNext app start will show update dialog!")

if __name__ == "__main__":
    main()
