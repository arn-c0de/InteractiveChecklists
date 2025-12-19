# 🔐 ECDH Device Key Security

## Übersicht

Die ECDH (Elliptic Curve Diffie-Hellman) Device Keys werden verwendet, um eine sichere Kommunikation zwischen dem DataPad-Client und dem DCS-Sender zu etablieren. Diese Dokumentation beschreibt die Sicherheitsmaßnahmen zum Schutz der privaten Schlüssel.

## ⚠️ Kritische Sicherheitsverbesserung (Version 2.0)

**WICHTIG**: Ab Version 2.0 werden private ECDH-Schlüssel **NICHT MEHR IM KLARTEXT** gespeichert!

### Vorheriger Zustand (Version 1.0 - UNSICHER)
- Private Schlüssel wurden unverschlüsselt in `ecdh_device.json` gespeichert
- Jeder mit Lesezugriff auf die Datei konnte den Schlüssel stehlen
- Ermöglichte Identity-Theft und Entschlüsselung der gesamten Kommunikation

### Neuer Zustand (Version 2.0 - SICHER)
- Private Schlüssel werden **verschlüsselt** gespeichert
- Plattformspezifische Schutzmechanismen werden verwendet
- Legacy-Dateien werden automatisch migriert

## Verschlüsselungsmethoden

### Windows: DPAPI (Data Protection API)
- **Automatische Verschlüsselung** ohne Benutzerinteraktion
- Verwendet Windows-Benutzeranmeldedaten zur Verschlüsselung
- Schlüssel kann nur vom gleichen Windows-Benutzer auf dem gleichen Computer entschlüsselt werden
- Kein Passwort erforderlich
- **Empfohlen für Windows-Benutzer**

**Vorteile:**
- Keine Passwortverwaltung notwendig
- Sehr hohe Sicherheit
- Nahtlose Benutzererfahrung

**Nachteile:**
- Schlüssel kann nicht auf anderen Computer übertragen werden
- Gebunden an Windows-Benutzerkonto

### macOS/Linux: Passwortbasierte Verschlüsselung
- Verwendet **AES-256-GCM** mit **PBKDF2** Key Derivation
- Benutzer muss ein starkes Passwort wählen (mindestens 8 Zeichen)
- Passwort wird beim Start der Anwendung abgefragt
- PBKDF2 mit 600.000 Iterationen (NIST 2023 Empfehlung)

**Vorteile:**
- Plattformunabhängig
- Schlüssel kann auf andere Computer übertragen werden (mit Passwort)

**Nachteile:**
- Benutzer muss Passwort merken
- Passwort muss bei jedem Start eingegeben werden

## Dateiformat

### Version 2.0 Format (Verschlüsselt)
```json
{
  "deviceId": "a1b2c3d4e5f6...",
  "version": 2,
  "encryptedPrivateKey": {
    "method": "dpapi",
    "data": "AQAAANCMnd8BFd..."
  }
}
```

oder

```json
{
  "deviceId": "a1b2c3d4e5f6...",
  "version": 2,
  "encryptedPrivateKey": {
    "method": "password",
    "salt": "base64_encoded_salt...",
    "nonce": "base64_encoded_nonce...",
    "ciphertext": "base64_encoded_ciphertext..."
  }
}
```

### Version 1.0 Format (Legacy - UNSICHER)
```json
{
  "deviceId": "a1b2c3d4e5f6...",
  "privateKeyPem": "-----BEGIN PRIVATE KEY-----\n..."
}
```

**⚠️ Warnung**: Dateien im alten Format werden automatisch beim ersten Laden migriert!

## Migration von Legacy-Keys

### Automatische Migration
Die Migration erfolgt automatisch beim ersten Laden einer unverschlüsselten Datei:

```python
from network.ecdh_device import load_device

# Lädt und migriert automatisch
device_data = load_device()
```

### Manuelle Migration
Verwenden Sie das Migrationstool:

```bash
# Aktivieren Sie die virtuelle Umgebung
cd scripts/MapDatabaseTools
source .venv/bin/activate  # Linux/macOS
# oder
.venv\Scripts\activate     # Windows

# Führen Sie das Migrationstool aus
python tools/migrate_device_key.py
```

### Migration mit benutzerdefiniertem Pfad
```bash
python tools/migrate_device_key.py /pfad/zu/ecdh_device.json
```

## Installation

### Windows
```bash
pip install -r requirements.txt
```

Das Paket `pywin32` wird automatisch auf Windows installiert.

### macOS/Linux
```bash
pip install -r requirements.txt
```

Keine zusätzlichen Abhängigkeiten erforderlich.

## Verwendung

### Neues Device erstellen
```python
from network.ecdh_device import generate_device

# Erstellt automatisch einen verschlüsselten Schlüssel
device_data = generate_device()
print(f"Device ID: {device_data['deviceId']}")
```

Auf Windows: Sofort einsatzbereit (DPAPI)  
Auf macOS/Linux: Fordert zur Eingabe eines Passworts auf

### Bestehendes Device laden
```python
from network.ecdh_device import load_device

# Lädt und entschlüsselt automatisch
device_data = load_device()

if device_data:
    print(f"Device ID: {device_data['deviceId']}")
    # privateKeyPem ist entschlüsselt und verwendungsbereit
else:
    print("Kein Device gefunden")
```

Auf Windows: Automatische Entschlüsselung  
Auf macOS/Linux: Fordert zur Eingabe des Passworts auf

### Device laden oder erstellen
```python
from network.ecdh_device import get_or_create_device

# Lädt bestehendes oder erstellt neues Device
device_data = get_or_create_device()
```

## Sicherheits-Best-Practices

### ✅ Empfohlen
1. **Windows-Benutzer**: Verwenden Sie DPAPI (Standard)
2. **macOS/Linux-Benutzer**: Wählen Sie ein **starkes Passwort** (mindestens 12 Zeichen, Sonderzeichen, Zahlen)
3. **Backup**: Erstellen Sie ein Backup der `ecdh_device.json` (verschlüsselt!) an einem sicheren Ort
4. **Passwort-Manager**: Speichern Sie Ihr Passwort in einem Passwort-Manager
5. **Regelmäßige Rotation**: Erwägen Sie die Rotation von Schlüsseln alle 6-12 Monate

### ❌ Nicht empfohlen
1. Speichern Sie das Passwort NIEMALS in einer Textdatei
2. Teilen Sie den privaten Schlüssel NIEMALS mit anderen
3. Committen Sie `ecdh_device.json` NIEMALS in ein öffentliches Git-Repository
4. Verwenden Sie KEINE schwachen Passwörter (z.B. "password", "123456")

## Fehlerbehebung

### Problem: "pywin32 is required for Windows DPAPI encryption"
**Lösung**: Installieren Sie pywin32:
```bash
pip install pywin32
```

### Problem: "Decryption failed. Incorrect password or corrupted data"
**Ursachen**:
1. Falsches Passwort eingegeben
2. Datei beschädigt

**Lösung**:
1. Versuchen Sie das richtige Passwort erneut
2. Falls Sie das Passwort vergessen haben, müssen Sie einen neuen Schlüssel generieren:
   ```bash
   # Backup der alten Datei erstellen
   mv ecdh_device.json ecdh_device.json.backup
   
   # Neuen Schlüssel generieren
   python tools/generate_device_key.py
   ```

### Problem: "Cannot decrypt DPAPI-encrypted key on non-Windows platform"
**Ursache**: Versuch, eine mit DPAPI verschlüsselte Datei auf macOS/Linux zu verwenden

**Lösung**: 
1. Auf Windows: Exportieren Sie den unverschlüsselten Schlüssel temporär
2. Auf Zielplattform: Importieren und mit passwortbasierter Verschlüsselung speichern

**ACHTUNG**: Dies sollte nur in gesicherten Umgebungen durchgeführt werden!

### Problem: Legacy-Datei wird nicht automatisch migriert
**Lösung**: Verwenden Sie das Migrationstool explizit:
```bash
python tools/migrate_device_key.py
```

## Technische Details

### Verschlüsselungsalgorithmen
- **DPAPI**: Windows-native CryptProtectData API
- **AES-256-GCM**: Authenticated Encryption with Associated Data
- **PBKDF2-HMAC-SHA256**: 600.000 Iterationen, 32-Byte-Schlüssel
- **Salt**: 32 zufällige Bytes pro Verschlüsselung
- **Nonce**: 12 zufällige Bytes (GCM-Standard)

### Sicherheitseigenschaften
- **Vertraulichkeit**: AES-256 bietet starke Verschlüsselung
- **Integrität**: GCM-Modus bietet Authentifizierung
- **Schutz vor Brute-Force**: PBKDF2 mit hoher Iterationszahl
- **Forward Secrecy**: Jede Verschlüsselung verwendet neuen Salt/Nonce

### Datenschutz
- Passwörter werden **NUR im Arbeitsspeicher** gehalten
- Passwörter werden **NIEMALS** auf Festplatte gespeichert
- Passwort-Cache wird beim Beenden der Anwendung gelöscht

## Compliance und Standards

Diese Implementierung folgt:
- **NIST SP 800-132**: Empfehlungen für passwortbasierte Key Derivation
- **NIST SP 800-38D**: GCM-Modus für authentifizierte Verschlüsselung
- **OWASP**: Best Practices für Kryptographie und Schlüsselverwaltung

## Support

Bei Fragen oder Problemen:
1. Lesen Sie diese Dokumentation
2. Prüfen Sie die Logs für Fehlermeldungen
3. Kontaktieren Sie das Entwicklungsteam

## Changelog

### Version 2.0 (2025-12-19)
- **BREAKING CHANGE**: Private Schlüssel werden jetzt verschlüsselt gespeichert
- Hinzugefügt: Windows DPAPI-Unterstützung
- Hinzugefügt: Passwortbasierte Verschlüsselung für macOS/Linux
- Hinzugefügt: Automatische Migration von Legacy-Keys
- Hinzugefügt: Migrationstool (`migrate_device_key.py`)
- Sicherheitsverbesserung: AES-256-GCM mit PBKDF2 (600k Iterationen)

### Version 1.0 (Legacy)
- ⚠️ UNSICHER: Private Schlüssel wurden unverschlüsselt gespeichert
- Nicht mehr empfohlen für den produktiven Einsatz
