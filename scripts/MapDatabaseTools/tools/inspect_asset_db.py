import sqlite3
p = r'app/src/main/assets/databases/map_data.db'
con = sqlite3.connect(p)
c = con.cursor()
print('user_version =', c.execute('PRAGMA user_version').fetchone()[0])
print('\n--- locations table columns ---')
for row in c.execute("PRAGMA table_info(locations)"): print(row)
print('\n--- indices ---')
for row in c.execute("PRAGMA index_list('locations')"): print(row)
con.close()
