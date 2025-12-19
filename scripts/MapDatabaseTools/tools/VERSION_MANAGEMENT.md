# Database Version Management

Die Android-App verwendet die `user_version` der SQLite-Datenbank, um zu erkennen, ob neue Daten verfügbar sind.

## Automatisches Versionsmanagement

### add_caucasus_markers.py
Das Script erhöht automatisch die Version nach jeder Änderung:

```bash
# Normal - Version wird automatisch erhöht
python add_caucasus_markers.py

# Mit --no-version-bump - Version bleibt unverändert
python add_caucasus_markers.py --no-version-bump
```

## Manuelle Version-Tools

### 1. Version erhöhen (increment)
```bash
python increment_db_version.py
```
Erhöht die aktuelle Version um 1 (z.B. 2 → 3)

### 2. Version manuell setzen
```bash
# Zurück auf 1 setzen (finale DB)
python set_db_version.py 1

# Auf beliebige Version setzen
python set_db_version.py 10

# Auf 0 zurücksetzen (First Run)
python set_db_version.py 0
```

## Workflow

### Während der Entwicklung
```bash
# Daten ändern
python cli_db_editor.py --add-navaid ...
python add_caucasus_markers.py

# Version wird automatisch erhöht
# App zeigt beim nächsten Start Update-Dialog
```

### DB-Struktur ist final
```bash
# Version auf 1 zurücksetzen (für finale Release)
python set_db_version.py 1

# WICHTIG: App-Daten löschen oder SharedPreferences zurücksetzen!
# Android: Settings > Apps > ChecklistInteractive > Storage > Clear Data
# Oder in der App: SharedPreferences löschen (db_update_prefs.xml)
```

### Version auf 0 setzen (First Run simulieren)
```bash
# Version auf 0 zurücksetzen
python set_db_version.py 0

# Die App wird beim nächsten Start die Version speichern, aber KEINEN Dialog anzeigen
# Nützlich, wenn du testen willst, ob die App korrekt initialisiert
```

## Wie funktioniert es?

Die App speichert die letzte gesehene Version in SharedPreferences:
- **Erster Start**: Version wird gespeichert, kein Dialog
- **Update erkannt**: `asset_version > gespeicherte_version` → Dialog anzeigen
- **Nach Import**: Gespeicherte Version wird auf `asset_version` gesetzt

## Wichtige Hinweise

⚠️ **Version verringern**: Wenn du die Version verringerst (z.B. von 5 auf 1), musst du die App-Daten löschen, damit der Update-Dialog erscheint!

⚠️ **SharedPreferences**: Die App speichert in `db_update_prefs.xml`:
```xml
<int name="last_asset_version" value="2" />
```

✅ **Best Practice**:
- Während Entwicklung: Automatisch inkrementieren lassen
- Bei Release: Version auf sinnvollen Wert setzen (z.B. 1 für erste Release)

## Praktisches Beispiel: Version zurücksetzen

Wenn deine Datenbankstruktur final ist und du für den Release starten willst:

```bash
# 1. Version auf 1 zurücksetzen
python set_db_version.py 1

# 2. App neu installieren oder App-Daten löschen
# Android: Settings > Apps > ChecklistInteractive > Storage > Clear Data

# 3. App starten - kein Dialog, da es der erste Start ist
# Die App speichert nun "Version 1" als Referenz

# 4. Beim nächsten Update (z.B. neue Marker):
python add_caucasus_markers.py
# Version wird automatisch auf 2 erhöht

# 5. App-Update installieren
# Die App erkennt Version 2 > 1 und zeigt Update-Dialog!
```

## Troubleshooting

**Problem**: Dialog erscheint nicht, obwohl Version erhöht wurde

**Lösung 1**: Prüfe gespeicherte Version in der App
```bash
# Android: Verbinde per adb und prüfe SharedPreferences
adb shell
cat /data/data/com.example.checklist_interactive/shared_prefs/db_update_prefs.xml
```

**Lösung 2**: App-Daten komplett löschen
```bash
# Android Settings > Apps > ChecklistInteractive > Storage > Clear Data
# Oder via adb:
adb shell pm clear com.example.checklist_interactive
```

**Problem**: Ich will die Version während der Entwicklung nicht ständig erhöhen

**Lösung**: Verwende `--no-version-bump` Flag
```bash
python add_caucasus_markers.py --no-version-bump
python cli_db_editor.py edit 42 --no-version-bump
```
