import sqlite3, sys
p = 'app/src/main/assets/databases/map_data.db'
EXPECTED = 4
try:
    con=sqlite3.connect(p)
    v = con.execute('PRAGMA user_version').fetchone()[0]
    con.close()
    print('asset DB user_version =', v)
    if v != EXPECTED:
        print(f'ERROR: expected user_version {EXPECTED}, found {v}')
        sys.exit(2)
    print('OK: asset DB version matches expected')
except Exception as e:
    print('Error checking asset DB:', e)
    sys.exit(1)
