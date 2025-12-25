#!/usr/bin/env python3
"""
Verify the map column schema in the database
"""
import sys
from pathlib import Path

# Add core to Python path
sys.path.append(str(Path(__file__).parent / 'core'))

from markers_database import MarkersDatabase

def verify_schema():
    """Check the schema of the map column"""
    db_path = Path(__file__).parent.parent.parent / "app" / "src" / "main" / "assets" / "databases" / "map_data.db"
    
    if not db_path.exists():
        print(f"❌ Database not found: {db_path}")
        return False
    
    db = MarkersDatabase(db_path)
    cursor = db.conn.cursor()
    
    # Get table schema
    cursor.execute("PRAGMA table_info(locations)")
    columns = cursor.fetchall()
    
    # Find map column
    map_column = None
    for col in columns:
        if col[1] == 'map':
            map_column = col
            break
    
    if not map_column:
        print("❌ Map column not found!")
        return False
    
    print(f"✅ Map column found:")
    print(f"   Column ID: {map_column[0]}")
    print(f"   Name: {map_column[1]}")
    print(f"   Type: {map_column[2]}")
    print(f"   Not Null: {map_column[3]}")
    print(f"   Default Value: {map_column[4]}")
    print(f"   Primary Key: {map_column[5]}")
    
    # Check if default is NULL or None
    if map_column[4] is None:
        print("\n✅ DEFAULT value is NULL (correct - Room expects 'undefined')")
    else:
        print(f"\n❌ DEFAULT value is '{map_column[4]}' (should be NULL)")
        return False
    
    # Verify data
    cursor.execute("SELECT COUNT(*) FROM locations WHERE map = 'Caucasus'")
    caucasus_count = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM locations")
    total_count = cursor.fetchone()[0]
    
    print(f"\n📊 Data verification:")
    print(f"   Total locations: {total_count}")
    print(f"   Caucasus markers: {caucasus_count}")
    
    if caucasus_count == total_count and total_count == 19:
        print("\n✅ All checks passed! Database schema is correct.")
        return True
    else:
        print(f"\n⚠️ Expected 19 Caucasus markers, found {caucasus_count}")
        return False

if __name__ == '__main__':
    success = verify_schema()
    sys.exit(0 if success else 1)
