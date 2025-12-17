"""
Map Borders Feature - README

Diese neue Funktion ermöglicht das Zeichnen und Verwalten von Kartenrahmen/Grenzen
für DCS World Kartenabschnitte.

## Features

1. **Border Zeichnen**
   - Klicke auf "➕ Draw Border" im Border Manager
   - Klicke auf die Karte, um Punkte hinzuzufügen
   - Jeder Punkt wird mit einem Emoji markiert (📍 für ersten Punkt, 📌 für weitere)
   - Punkte werden automatisch mit gestrichelten Linien verbunden
   - Klicke auf den ersten Punkt erneut, um das Polygon zu schließen
   - Ein Dialog erscheint, um Name, Beschreibung und Farbe festzulegen

2. **Border Verwalten**
   - Alle Borders werden in der Liste angezeigt
   - Klicke auf einen Border, um zur Region zu zoomen
   - Rechtsklick oder "✏️ Edit" um Name/Farbe/Beschreibung zu ändern
   - "🗑️ Delete" um einen Border zu löschen
   - "❌ Cancel" während des Zeichnens, um abzubrechen

3. **Datenbankpersistenz**
   - Alle Borders werden in markers.db gespeichert
   - Borders überleben Neustart der Anwendung
   - Export/Import Funktionalität für Backup

4. **Karten-Darstellung**
   - Borders werden als Polygone auf der Karte angezeigt
   - Farbe ist anpassbar (Standard: Rot)
   - Halbtransparente Füllung für bessere Sichtbarkeit
   - Popup zeigt Name, Beschreibung und Punktanzahl

## Verwendung

1. Starte die DataPad GUI:
   ```
   python run_datapad.py
   ```

2. Im Border Manager Panel (unten links):
   - Klicke "➕ Draw Border"
   - Klicke mindestens 3 Punkte auf der Karte
   - Schließe das Polygon durch Klick auf den ersten Punkt
   - Gib Name und Eigenschaften ein
   - Fertig! Border wird gespeichert und angezeigt

## Technische Details

### Datenbank Schema
```sql
CREATE TABLE borders (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    points TEXT NOT NULL,  -- JSON array of [lat, lon]
    description TEXT,
    color TEXT DEFAULT '#FF0000',
    created TEXT NOT NULL,
    modified TEXT NOT NULL
)
```

### Dateien
- `core/markers_database.py` - Border dataclass und CRUD-Methoden
- `gui/border_manager.py` - Border Manager Widget
- `map.html` - JavaScript für Zeichnung und Darstellung
- `gui/datapad_gui.py` - Integration in Hauptfenster

### API

**Python (MarkersDatabase):**
```python
# Border erstellen
border = Border(
    name="Caucasus North",
    points=[(43.5, 41.2), (43.6, 41.3), (43.5, 41.4)],
    color="#FF0000"
)
border_id = db.add_border(border)

# Border laden
border = db.get_border(border_id)
all_borders = db.get_all_borders()

# Border aktualisieren
border.name = "New Name"
db.update_border(border)

# Border löschen
db.delete_border(border_id)
```

**JavaScript (map.html):**
```javascript
// Zeichenmodus starten
startBorderDrawing();

// Punkt hinzufügen
addBorderPoint(lat, lon);

// Zeichenmodus beenden
stopBorderDrawing();

// Borders anzeigen
updateBorders('[{"id":1,"name":"Region 1","points":[[43.5,41.2],...]}]');
```

## Zukünftige Erweiterungen

- [ ] Import/Export von Borders als GeoJSON
- [ ] Bearbeiten von Punkten nach Erstellung
- [ ] Snap-to-Grid Funktionalität
- [ ] Mehrere Farben/Styles für verschiedene Regionen
- [ ] Integration mit DCS Missionsdaten
- [ ] Anzeige von Koordinaten beim Zeichnen

## Bekannte Limitierungen

- Mindestens 3 Punkte erforderlich für ein Polygon
- Polygon-Schließung erfordert Klick nahe (~100m) am ersten Punkt
- Keine Unterstützung für Löcher in Polygonen
- Keine Kollisionserkennung zwischen Borders

## Lizenz

Siehe LICENSE Datei im Hauptverzeichnis.
