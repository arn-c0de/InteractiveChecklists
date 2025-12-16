# AES-GCM Verschlüsselung für DataPad

## Übersicht
Die Kommunikation zwischen dem Python-Skript und der Android-App ist jetzt mit **AES-GCM** (Authenticated Encryption with Associated Data) verschlüsselt.

## Implementierte Änderungen

### 1. Python-Skript (`forward_parsed_udp.py`)
- ✅ AES-GCM Verschlüsselung mit 256-bit Pre-Shared Key
- ✅ Zufällige 12-Byte Nonce für jedes Paket
- ✅ Automatische Authentifizierung (AEAD)
- ✅ Optional deaktivierbar mit `--no-encrypt` Flag

### 2. Android App (`DataPadManager.kt`)
- ✅ AES-GCM Entschlüsselung mit javax.crypto
- ✅ Automatische Validierung der Authentizität
- ✅ Fehlerbehandlung bei ungültigen Paketen

### 3. UI Updates (`DataPadPopup.kt`)
- ✅ "🔒 AES-GCM encrypted" Status-Anzeige
- ✅ Aktualisierte Kommandozeilen-Hinweise

## Installation

### Python-Seite
```bash
pip install cryptography
```

### Android-Seite
Keine zusätzlichen Dependencies erforderlich - verwendet javax.crypto (Standard Android API).

> Hinweise: Verwenden Sie die Anleitung in diesem Dokument, um den Pre-Shared Key sicher zu generieren und in beiden Seiten zu konfigurieren.

## Verwendung

### Normal (verschlüsselt)
```bash
python forward_parsed_udp.py --host 192.168.178.100 --port 5010
```

### Unverschlüsselt (nicht empfohlen)
```bash
python forward_parsed_udp.py --host 192.168.178.100 --port 5010 --no-encrypt
```

## Pre-Shared Key

**Wichtig:** Der Pre-Shared Key **muss** auf beiden Seiten identisch sein. Verwenden Sie am besten einen zufälligen 32‑Byte (256‑bit) Schlüssel und geben Sie ihn als Hex-String an, damit er nicht unabsichtlich als Klartext eingecheckt wird.

Beispiel: Generieren eines 32-Byte Schlüssels (Hex):

```bash
python - <<'PY'
import os
print(os.urandom(32).hex())
PY
```

Konfiguration in Python (sicherer: Hex-to-bytes):

```python
# forward_parsed_udp.py
PRE_SHARED_KEY = bytes.fromhex('your_64_char_hex_key_here')
```

Konfiguration in Android/Kotlin:

```kotlin
// Helper to convert hex string to byte array
private fun hexStringToByteArray(hex: String): ByteArray {
    val len = hex.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

// Use the hex key generated above
private val PRE_SHARED_KEY = hexStringToByteArray("your_64_char_hex_key_here")
```

### In Produktion
- Verteilen und speichern Sie Schlüssel sicher (Out-of-band), rotieren Sie bei Bedarf und vermeiden Sie das Einchecken von Geheimnissen ins Repository.

## Sicherheitsmerkmale

✅ **Vertraulichkeit**: Daten sind verschlüsselt mit AES-256
✅ **Integrität**: GCM-Tag verhindert Manipulation
✅ **Authentizität**: Nur Clients mit dem richtigen Key können entschlüsseln
✅ **Replay-Schutz**: Jedes Paket hat eine einzigartige Nonce

## Paketformat

```
[12 Bytes Nonce][Verschlüsselter JSON][16 Bytes GCM Tag]
```

Minimale Paketgröße: 28 Bytes (Nonce + Tag)

## Fehlerbehebung

### "Failed to decrypt packet - check Pre-Shared Key!"
- Ensure the Pre-Shared Key is identical in both files
- Ensure the Python script uses the `cryptography` library

### Import-Fehler in Python
```bash
pip install --upgrade cryptography
```

## Hinweise

- Die Pylance-Warnung über unaufgelöste Imports in VSCode ist normal, wenn `cryptography` nicht in der aktuellen Python-Umgebung installiert ist
- Verschlüsselung ist standardmäßig aktiviert - verwenden Sie `--no-encrypt` nur zu Debug-Zwecken
- Die App zeigt "🔒 AES-GCM encrypted" in der Verbindungsanzeige
