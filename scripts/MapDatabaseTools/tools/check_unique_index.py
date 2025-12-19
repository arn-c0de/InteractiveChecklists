#!/usr/bin/env python3
"""Check if index_tags_name is UNIQUE"""
import sqlite3

db_path = r'c:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\assets\databases\map_data.db'
con = sqlite3.connect(db_path)
cur = con.cursor()

# Get the SQL for creating the index
result = cur.execute("SELECT sql FROM sqlite_master WHERE name='index_tags_name'").fetchone()
if result:
    print(f"Index creation SQL:\n{result[0]}")
    if 'UNIQUE' in result[0].upper():
        print("\n✅ Index is UNIQUE")
    else:
        print("\n❌ Index is NOT UNIQUE")
else:
    print("Index not found")

con.close()
