#!/usr/bin/env python3
"""Inspect map_data.db schema and show all tables/indexes"""
import sqlite3

p = r'c:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\assets\databases\map_data.db'
con = sqlite3.connect(p)
cur = con.cursor()

# Helper to print table info
def inspect_table(table_name):
    print(f'\n{"="*60}')
    print(f'TABLE: {table_name}')
    print(f'{"="*60}')
    print('\nColumns:')
    for row in cur.execute(f"PRAGMA table_info({table_name})"):
        print(f'  {row[1]:25} {row[2]:15} {"NOT NULL" if row[3] else ""} {"PK" if row[5] else ""}')
    
    print('\nIndexes:')
    indexes = list(cur.execute(f"PRAGMA index_list('{table_name}')"))
    if indexes:
        for idx_row in indexes:
            idx_name = idx_row[1]
            print(f'  INDEX: {idx_name}')
            for col_row in cur.execute(f"PRAGMA index_info('{idx_name}')"):
                print(f'    {col_row}')
    else:
        print('  (none)')
    
    # Show row count
    count = cur.execute(f"SELECT COUNT(*) FROM {table_name}").fetchone()[0]
    print(f'\nRows: {count}')

# List all tables
print('Database Tables:')
tables = cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").fetchall()
for t in tables:
    print(f'  - {t[0]}')

# Inspect each table
for table in tables:
    inspect_table(table[0])

con.close()
print(f'\n{"="*60}')
print('Inspection complete ✅')
print(f'{"="*60}')
