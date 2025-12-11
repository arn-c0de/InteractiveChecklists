# Auto-Import von Dateien aus Imports-Ordner

Die App lädt automatisch beim Start alle PDF- und Markdown-Dateien aus dem `Imports/` Ordner.

## Ordnerstruktur

Erstelle einen `Imports/` Ordner an einem der folgenden Orte:

1. **App-spezifisch** (empfohlen, keine Berechtigung nötig):
   ```
   Android/data/com.example.checklist_interactive/files/Imports/
   ```

2. **Download-Ordner**:
   ```
   Download/Imports/
   ```

3. **Externer Speicher Root**:
   ```
   /storage/emulated/0/Imports/
   ```

## Kategorie-System

Jeder Unterordner im `Imports/` Ordner wird automatisch zu einer Kategorie in "My Files".
Verschachtelte Unterordner werden in der App beibehalten und als aufklappbare Ordner angezeigt (z. B. `Checklists/F-16_Viper`).

### Beispiel-Struktur:

```
Imports/
├── Checklists/
│   ├── Checklist1.pdf
│   └── Checklist2.pdf
├── RadioCommunications/
│   ├── Radio-Guide.pdf
│   └── Frequencies.pdf
├── Procedures/
│   ├── Emergency.pdf
│   └── Standard.pdf
└── Manuals/
    └── Aircraft-Manual.pdf
```

### Ergebnis in der App:

- **Checklists** (2 Dateien)
  - Checklist1.pdf
  - Checklist2.pdf

- **Radiocommunications** (2 Dateien)
  - Radio-Guide.pdf
  - Frequencies.pdf

- **Procedures** (2 Dateien)
  - Emergency.pdf
  - Standard.pdf

- **Manuals** (1 Datei)
  - Aircraft-Manual.pdf

## Unterstützte Dateiformate

- **PDF** (.pdf)
- **Markdown** (.md, .markdown)

## Automatischer Import

- Der Import erfolgt **automatisch beim App-Start**
- Bereits importierte Dateien werden **nicht erneut** importiert
- Neue Dateien werden beim nächsten App-Start erkannt
- Kategorien werden **dynamisch erstellt**, wenn sie noch nicht existieren

## Hinweise

- Ordnernamen werden in **Kleinbuchstaben** konvertiert
- Bestehende Kategorien (Checklists, Comms, Charts, Procedures, Manuals) werden beibehalten
- Neue Kategorien können durch einfaches Erstellen neuer Unterordner hinzugefügt werden
- Doppelte Dateinamen werden **nicht** überschrieben
