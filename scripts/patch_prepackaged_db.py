#!/usr/bin/env python3
"""
Patch prepackaged map_data.db to add missing military symbol columns and set user_version.
Run from repo root: python scripts/patch_prepackaged_db.py --db app/src/main/assets/databases/map_data.db
"""
import argparse
import sqlite3
import sys

ALTERS = [
    "ALTER TABLE locations ADD COLUMN symbol_set TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE locations ADD COLUMN symbol_entity TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE locations ADD COLUMN symbol_size TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE locations ADD COLUMN symbol_affiliation TEXT NOT NULL DEFAULT 'unknown'",
    "ALTER TABLE locations ADD COLUMN symbol_color TEXT NOT NULL DEFAULT '#FFFF80'",
    "ALTER TABLE locations ADD COLUMN symbol_modifier TEXT NOT NULL DEFAULT ''",
]


def patch_db(path: str):
    conn = sqlite3.connect(path)
    try:
        cur = conn.cursor()
        # Begin
        cur.execute('PRAGMA writable_schema = OFF')
        conn.isolation_level = None
        cur.execute('BEGIN')

        # Apply each alter, ignore errors for already-existing columns
        for sql in ALTERS:
            try:
                cur.execute(sql)
                print(f"Applied: {sql}")
            except sqlite3.OperationalError as e:
                # likely column already exists - print and continue
                print(f"Skipping (maybe exists): {sql} -> {e}")

        # Set user version to 3
        try:
            cur.execute('PRAGMA user_version = 3')
            print('Set PRAGMA user_version = 3')
        except Exception as e:
            print('Failed to set user_version:', e)

        cur.execute('COMMIT')
        print('Database patch completed successfully.')
    except Exception as e:
        print('Failed to patch db:', e)
        try:
            cur.execute('ROLLBACK')
        except Exception:
            pass
        raise
    finally:
        conn.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--db', required=True, help='Path to map_data.db')
    args = parser.parse_args()
    patch_db(args.db)
