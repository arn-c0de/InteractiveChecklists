import sqlite3
p=r'c:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\assets\databases\map_data.db'
con=sqlite3.connect(p)
cur=con.cursor()
print('PRAGMA table_info(locations):')
for row in cur.execute("PRAGMA table_info(locations)"):
    print(row)
print('\nindexes:')
for row in cur.execute("PRAGMA index_list('locations')"):
    print(row)
print('\nindex_info for each:')
for idx in [r[1] for r in cur.execute("PRAGMA index_list('locations')")]:
    print('INDEX',idx)
    for r in cur.execute(f"PRAGMA index_info('{idx}')"):
        print('  ',r)

print('\nPRAGMA table_info(borders):')
for row in cur.execute("PRAGMA table_info(borders)"):
    print(row)
print('\nindexes:')
for row in cur.execute("PRAGMA index_list('borders')"):
    print(row)

con.close()
