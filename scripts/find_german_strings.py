import os
import re

# List of file extensions to check for German strings
EXTENSIONS = ['.kt', '.java', '.xml', '.py', '.js', '.ts', '.html', '.md', '.txt']

# Regex for detecting German umlauts or common German words (expand as needed)
GERMAN_PATTERN = re.compile(r'[äöüßÄÖÜ]|\b(und|oder|nicht|mit|ohne|für|ist|die|der|das|ein|eine|auf|zu|im|am|von|aus|bei|nach|über|unter|vor|hinter|zwischen|gegen|während|wieder|noch|schon|jetzt|heute|morgen|gestern|danke|bitte|Hallo|Tschüss|ja|nein)\b', re.IGNORECASE)

results = []

for root, dirs, files in os.walk('.'):
    for file in files:
        if any(file.endswith(ext) for ext in EXTENSIONS):
            path = os.path.join(root, file)
            try:
                with open(path, encoding='utf-8', errors='ignore') as f:
                    for i, line in enumerate(f, 1):
                        if GERMAN_PATTERN.search(line):
                            results.append(f'{path}:{i}: {line.strip()}')
            except Exception as e:
                print(f'Error reading {path}: {e}')

if results:
    print('Gefundene deutsche Strings:')
    for r in results:
        print(r)
else:
    print('Keine deutschen Strings gefunden.')
