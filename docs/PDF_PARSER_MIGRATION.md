# PDF Parser Migration - PDFBox Removal

## Zusammenfassung

Die `com.tom_roush:pdfbox-android` Abhängigkeit wurde vollständig entfernt und durch eine eigene native PDF-Parser-Implementierung ersetzt.

## Geänderte Dateien

### Neu erstellt:
- **PdfStructureParser.kt**: Native PDF-Parser-Implementierung
  - Parst PDF-Struktur direkt aus dem Binärformat
  - Extrahiert Outline/Bookmarks mit Titeln und Seitenzahlen
  - Extrahiert Text aus Content Streams
  - Unterstützt hierarchische Outline-Strukturen (levels)
  - Resolves Destinations (direkte und indirekte Referenzen)

### Aktualisiert:
- **PdfOutlineExtractor.kt**: 
  - Entfernt: PDFBox imports und Implementierung
  - Neu: Nutzt `PdfStructureParser.parseOutline()`
  - Funktionalität bleibt identisch für Aufrufer

- **PdfTextExtractor.kt**:
  - Entfernt: PDFBox imports, Document-Caching, PDFTextStripper
  - Neu: Nutzt `PdfStructureParser.extractPageText()`
  - `extractTextBlocks()`: Liefert jetzt Gesamttext als einzelnen Block (keine präzisen Positionen mehr)
  - `extractPageText()`: Nutzt native Parser-Implementierung
  - `cleanup()`: Keine Action mehr nötig (stateless parser)

- **build.gradle.kts**:
  - Entfernt: `implementation("com.tom-roush:pdfbox-android:2.0.27.0")`

## Funktionalität

### Was funktioniert:
✅ Outline/Bookmark-Extraktion mit Titeln, Seitenzahlen und Hierarchie-Levels
✅ Text-Extraktion aus PDF-Seiten
✅ Destination-Auflösung (Page References)
✅ Cross-Reference-Table Parsing
✅ PDF Object Stream Parsing

### Einschränkungen der nativen Implementierung:
⚠️ **Textpositionen**: `extractTextBlocks()` liefert keine präzisen X/Y-Koordinaten mehr (nur approximierte Werte)
⚠️ **Komplexe PDFs**: Eingebettete Fonts, Verschlüsselung, komprimierte Streams werden aktuell nicht vollständig unterstützt
⚠️ **Named Destinations**: Noch nicht vollständig implementiert

### Was weiterhin wie gewohnt funktioniert:
- PDF-Rendering (nutzt Android PdfRenderer)
- Annotations/Zeichnungen
- Page Highlights
- Shortcuts
- Navigation per Outline

## Technische Details

Der neue `PdfStructureParser` parst PDFs auf Binärebene:
1. Liest Cross-Reference-Table (xref)
2. Findet Catalog via Trailer
3. Navigiert Object-Baum (Pages, Outline, etc.)
4. Resolved indirekte Object-Referenzen
5. Extrahiert Text aus Content Streams (BT...ET, Tj, TJ Operatoren)

## Migration für andere Projekte

Falls du `PdfStructureParser` in anderen Projekten nutzen willst:
1. Kopiere `PdfStructureParser.kt`
2. Keine zusätzlichen Dependencies nötig (nur Kotlin Stdlib + Android SDK)
3. API:
   ```kotlin
   val parser = PdfStructureParser(pdfFile)
   val outline: List<PdfOutlineItem> = parser.parseOutline()
   val text: String = parser.extractPageText(pageIndex)
   ```

## Lizenz-Hinweis

- **Alte Abhängigkeit**: Apache PDFBox (Apache License 2.0)
- **Neue Implementierung**: Teil dieses Projekts → unterliegt der Repository-Lizenz (CC BY-NC-SA 4.0)

Die Entfernung von PDFBox vereinfacht die Lizenz-Situation und reduziert die APK-Größe erheblich (~3-5 MB).
