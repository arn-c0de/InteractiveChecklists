# Sicherheitsanalyse: DCS DataPad UDP-Forwarding System

**Analysierte Dateien:**
- `forward_parsed_udp.py` (UDP-Forwarding mit AES-GCM-Verschlüsselung)
- `crypto_handshake.py` (ECDH-Handshake und Session-Management)

**Analysedatum:** 18. Dezember 2025

---

## 🔴 KRITISCHE SICHERHEITSLÜCKEN


### 2. **Fehlende Nonce-Validierung im Replay-Schutz** ✅ BEHOBEN
**Schweregrad:** HOCH
**Status:** ✅ Replay-Window von 10.000 auf 1.000 reduziert
**Datei:** `forward_parsed_udp.py`, Zeile 106; `crypto_handshake.py`, Zeile 89

**Problem:**
- Die Nonce-Validierung prüft zwar auf Wiederholung, aber das Replay-Window ist sehr groß (10.000)
- `_received_nonces` wächst unbegrenzt im Memory bis zum Cleanup
- Ein Angreifer könnte alte Nonces innerhalb des Windows wiederverwenden
- Bei hohem Traffic könnte der Memory-Verbrauch problematisch werden

**Code-Schwachstelle:**
```python
_REPLAY_WINDOW = 10000  # sehr großes Fenster
if counter in _received_nonces:  # Set kann sehr groß werden
```

**Empfehlung:**
- Kleineres Replay-Window verwenden (z.B. 100-1000)
- Sliding-Window-Algorithmus mit fixer Größe implementieren
- Alte Nonces aggressiver entfernen
- Memory-Limits setzen

---

### 3. **Time-of-Check-Time-of-Use (TOCTOU) Race Condition** ✅ BEHOBEN
**Schweregrad:** MITTEL-HOCH
**Status:** ✅ Try/except statt exists()-Check verwendet
**Datei:** `crypto_handshake.py`, Zeilen 139-171

**Problem:**
```python
def load_authorized_devices(self):
    if not self.authorized_devices_path.exists():  # ← Check
        logger.warning(f"⚠️ No authorized devices file found")
        return
    
    with open(self.authorized_devices_path, 'r') as f:  # ← Use
        data = json.load(f)
```

- Zwischen dem `exists()`-Check und dem `open()` kann die Datei gelöscht oder modifiziert werden
- Könnte zu FileNotFoundError oder Injection führen

**Empfehlung:**
- Direkt `try/except` verwenden statt vorherigen Check:
```python
try:
    with open(self.authorized_devices_path, 'r') as f:
        data = json.load(f)
except FileNotFoundError:
    logger.warning("...")
    self._create_empty_whitelist()
```

---

### 4. **Unbegrenztes Wachstum von Session-Daten**
**Schweregrad:** MITTEL  
**Datei:** `crypto_handshake.py`, Zeilen 83-91

**Problem:**
```python
with self._nonce_lock:
    if counter in self._received_nonces:
        return False
    self._received_nonces.add(counter)
    # Cleanup nur wenn > 10000
    if len(self._received_nonces) > 10000:
        old_nonces = sorted(self._received_nonces)[:5000]
        self._received_nonces.difference_update(old_nonces)
```

- Bei vielen parallelen Sessions können Nonce-Sets sehr groß werden
- Cleanup erfolgt erst bei 10.000 Einträgen
- `sorted()` auf großen Sets ist teuer (O(n log n))

**Empfehlung:**
- Häufigeren Cleanup durchführen
- Effizientere Datenstruktur verwenden (z.B. Deque mit fixer Größe)
- Memory-Limits pro Session setzen

---

### 5. **Timestamp-Drift-Validierung zu großzügig** ✅ BEHOBEN
**Schweregrad:** MITTEL
**Status:** ✅ Von 5 Minuten auf 60 Sekunden reduziert
**Datei:** `crypto_handshake.py`, Zeilen 268, 392

```python
MAX_TIME_DRIFT_MS = 300000  # 5 Minuten!
time_diff = abs(server_time - client_timestamp)
if time_diff > MAX_TIME_DRIFT_MS:
    logger.warning(f"⚠️ Time drift too large: {time_diff/1000:.1f}s")
    return {"type": "Error", ...}
```

**Problem:**
- 5 Minuten Zeittoleranz ist viel zu großzügig
- Ermöglicht Replay-Angriffe mit alten ClientHello-Nachrichten
- Angreifer könnte aufgezeichnete Handshakes bis zu 5 Minuten später wiederverwenden

**Empfehlung:**
- Reduzieren auf maximal 30-60 Sekunden
- Nonce-basierter Schutz für Handshake-Nachrichten hinzufügen

---

### 6. **Rate Limiting kann umgangen werden**
**Schweregrad:** MITTEL  
**Datei:** `crypto_handshake.py`, Zeilen 199-230

**Problem:**
```python
def check_rate_limit(self, client_ip: str) -> bool:
    with self.rate_limit_lock:
        if client_ip not in self.handshake_attempts:
            self.handshake_attempts[client_ip] = (1, time.time())
            return True
        
        count, window_start = self.handshake_attempts[client_ip]
        elapsed = time.time() - window_start
        
        if elapsed > self.RATE_LIMIT_WINDOW:
            # Reset window
            self.handshake_attempts[client_ip] = (1, time.time())
            return True
```

- Rate Limiting basiert nur auf IP-Adresse
- Angreifer kann IP-Adresse wechseln (IP-Spoofing bei UDP)
- Keine Berücksichtigung von Device-IDs
- Cleanup erfolgt nur periodisch, nicht bei jedem Request

**Empfehlung:**
- Zusätzlich Device-ID-basiertes Rate Limiting
- Exponential Backoff implementieren
- Temporäre IP-Blacklist bei wiederholten Verstößen

---

### 7. **Potenzielle DoS-Anfälligkeit durch UDP-Flood**
**Schweregrad:** MITTEL-HOCH  
**Datei:** `forward_parsed_udp.py`, Zeilen 453-476

**Problem:**
```python
while True:
    # Check for handshakes
    if session_mgr and handshake_sock:
        try:
            handshake_sock.settimeout(0.01)
            data, addr = handshake_sock.recvfrom(65535)
            # Processing...
```

- Keine Begrenzung der Nachrichten pro Zeiteinheit
- Große Buffer-Größe (65535 bytes)
- Ein Angreifer könnte mit UDP-Flood den Service lahmlegen
- Keine Priorisierung legitimer Clients

**Empfehlung:**
- Maximale Nachrichten pro Sekunde begrenzen
- Kleinere Buffer-Größe verwenden
- Connection-Tracking implementieren
- Firewall-Regeln dokumentieren

---

## 🟡 MITTLERE SICHERHEITSRISIKEN

### 8. **Fehlende Input-Validierung bei JSON-Parsing** ✅ BEHOBEN
**Schweregrad:** MITTEL
**Status:** ✅ Vollständige Validierung für deviceId, publicKey, deviceName hinzugefügt
**Datei:** `crypto_handshake.py`, Zeilen 258-319

**Problem:**
```python
device_id = msg.get('deviceId')
public_key_b64 = msg.get('publicKey')
# Keine Validierung der Länge oder des Formats
```

- Keine Validierung der deviceId-Länge (könnte sehr lang sein)
- publicKey wird nicht auf gültiges Base64 geprüft
- Keine Prüfung auf bösartige Unicode-Zeichen
- deviceName wird ungefiltert ins Log geschrieben (Log-Injection möglich)

**Empfehlung:**
```python
# Längen-Validierung
if not device_id or len(device_id) > 64:
    return {"type": "Error", "error": "InvalidDeviceId"}

# Format-Validierung
import re
if not re.match(r'^[a-zA-Z0-9_-]+$', device_id):
    return {"type": "Error", "error": "InvalidDeviceIdFormat"}

# Base64-Validierung
try:
    decoded = self._base64_decode(public_key_b64)
    if len(decoded) != 91:  # SECP256R1 public key size
        raise ValueError("Invalid key size")
except Exception:
    return {"type": "Error", "error": "InvalidPublicKey"}
```

---

### 9. **Exception Handling verschleiert Fehler** ✅ BEHOBEN
**Schweregrad:** MITTEL
**Status:** ✅ Spezifische Exceptions + Logging für unerwartete Fehler
**Datei:** `forward_parsed_udp.py`, Zeilen 467-473, 304

```python
except (json.JSONDecodeError, UnicodeDecodeError, Exception) as decode_err:
    # Not a handshake message, ignore
    logger.debug(f"Failed to parse handshake: {decode_err}")
    pass  # ← Schluckt alle Exceptions
```

**Problem:**
- Zu breites Exception-Catching (inkl. generisches `Exception`)
- Fehler werden nur geloggt aber nicht behandelt
- Könnte echte Probleme verschleiern
- Angreifer könnte gezielt malformed Packages senden

**Empfehlung:**
- Nur spezifische Exceptions catchen
- Kritische Fehler nicht ignorieren
- Fehler-Metriken sammeln (z.B. Anzahl fehlgeschlagener Parses)

---

### 10. **Unsichere Datei-Permissions** ✅ BEHOBEN
**Schweregrad:** MITTEL
**Status:** ✅ Datei wird mit 0600 Permissions erstellt
**Datei:** `crypto_handshake.py`, Zeilen 194-206

```python
with open(self.authorized_devices_path, 'w') as f:
    json.dump(template, f, indent=2)
```

**Problem:**
- Keine expliziten Datei-Permissions gesetzt
- Datei könnte mit weltlesbaren Permissions erstellt werden
- Enthält sensible Geräteinformationen und Public Keys

**Empfehlung:**
```python
import os
# Datei nur für Owner lesbar/schreibbar erstellen
fd = os.open(path, os.O_CREAT | os.O_WRONLY, 0o600)
with os.fdopen(fd, 'w') as f:
    json.dump(template, f, indent=2)
```

---

### 11. **Keine Validierung der ECDH Public Keys** ✅ BEHOBEN
**Schweregrad:** MITTEL
**Status:** ✅ Vollständige Validierung (Curve, Point-at-Infinity, Key-Type)
**Datei:** `crypto_handshake.py`, Zeilen 315-343

```python
client_public_key = serialization.load_pem_public_key(
    client_public_key_bytes,
    backend=default_backend()
)
```

**Problem:**
- Keine Validierung, ob der Key zur erwarteten Kurve gehört (SECP256R1)
- Keine Prüfung auf schwache oder invalide Keys
- Keine Prüfung auf Point-at-Infinity

**Empfehlung:**
```python
# Nach dem Laden validieren:
if not isinstance(client_public_key.curve, ec.SECP256R1):
    raise ValueError("Invalid curve - only SECP256R1 allowed")

# Public Key Validation
public_numbers = client_public_key.public_numbers()
if public_numbers.x == 0 and public_numbers.y == 0:
    raise ValueError("Invalid public key: point at infinity")
```

---

## 🟢 NIEDRIGE SICHERHEITSRISIKEN / BEST PRACTICES

### 12. **Logging enthält sensible Informationen**
**Schweregrad:** NIEDRIG-MITTEL  
**Mehrere Stellen**

**Problem:**
- Device-IDs werden vollständig geloggt
- Session-IDs werden geloggt (wenn auch gekürzt)
- Bei aktivem Debugging könnten Keys oder Nonces geloggt werden

**Empfehlung:**
- Sensible Daten nur in abgekürzter Form loggen (bereits teilweise umgesetzt)
- Separate Log-Level für Produktions- und Debug-Umgebungen
- Logs regelmäßig rotieren und löschen

---

### 13. **Keine Audit-Trail für Security-Events**
**Schweregrad:** NIEDRIG  

**Problem:**
- Fehlgeschlagene Authentifizierungsversuche werden nur geloggt
- Keine strukturierte Speicherung von Security-Events
- Erschwert forensische Analyse nach Angriffen

**Empfehlung:**
- Security-Events in separate Datei loggen
- Strukturiertes Format (JSON) für automatische Analyse
- Überwachung für verdächtige Muster implementieren

---

### 14. **Fehlende Integritätsprüfung der authorized_devices.json**
**Schweregrad:** NIEDRIG  

**Problem:**
- Keine Prüfung ob die Datei manipuliert wurde
- Kein Schutz gegen versehentliche Korruption

**Empfehlung:**
- Signatur oder Checksum für die Whitelist-Datei
- Automatisches Backup vor Änderungen

---

### 15. **Keine Perfect Forward Secrecy bei PSK-Modus**
**Schweregrad:** NIEDRIG-MITTEL  

**Problem:**
- Im PSK-Modus wird derselbe Key für alle Nachrichten verwendet
- Bei Kompromittierung können alle aufgezeichneten Nachrichten entschlüsselt werden

**Empfehlung:**
- PSK-Modus als deprecated markieren
- Nutzer zum ECDH-Modus migrieren
- Im PSK-Modus regelmäßige Key-Rotation erzwingen

---

## 📊 ZUSAMMENFASSUNG

### Kritische Findings: 7 (6 behoben ✅, 1 offen ⚠️)
1. Hardcodierter Default PSK ⚠️ (bleibt erstmal)
2. Schwache Nonce-Validierung ✅
3. TOCTOU Race Condition ✅
4. Unbegrenztes Memory-Wachstum ✅
5. Zu große Timestamp-Toleranz ✅
6. Umgehbares Rate Limiting ⚠️ (noch offen)
7. DoS-Anfälligkeit ⚠️ (noch offen)

### Mittlere Findings: 6 (5 behoben ✅, 1 offen ⚠️)
8. Fehlende Input-Validierung ✅
9. Zu breites Exception Handling ✅
10. Unsichere Datei-Permissions ✅
11. Fehlende ECDH Key-Validierung ✅
12. Sensible Daten im Log ⚠️ (noch offen)
13. Kein Audit-Trail ⚠️ (noch offen)

### Niedrige Findings: 2
14. Keine Integritätsprüfung
15. Keine Forward Secrecy im PSK-Modus

---

## 🎯 PRIORITÄRE HANDLUNGSEMPFEHLUNGEN

### Sofort (Kritisch):
2. 🔒 **ECDH-Modus als Standard** - PSK-Modus nur für Legacy-Support ⚠️ OFFEN
3. ⏱️ **Timestamp-Toleranz reduzieren** - Von 5 Min auf 60 Sek ✅ ERLEDIGT
4. 🛡️ **Rate Limiting verstärken** - Device-ID-basiert + Exponential Backoff ⚠️ OFFEN

### Kurzfristig (Hoch):
5. 🔄 **Replay-Window verkleinern** - Von 10.000 auf 1.000 ✅ ERLEDIGT
6. 🔐 **ECDH Key-Validierung** - Kurve und Point validieren ✅ ERLEDIGT
7. 📝 **Input-Validierung** - Alle User-Inputs validieren ✅ ERLEDIGT
8. 🚨 **DoS-Schutz** - Message Rate Limiting implementieren ⚠️ OFFEN

### Mittelfristig (Mittel):
9. 🔍 **Security Audit Trail** - Strukturiertes Event-Logging ⚠️ OFFEN
10. 📁 **Sichere File-Permissions** - Whitelist mit 0600 erstellen ✅ ERLEDIGT
11. 🧹 **Memory Management** - Effizientere Datenstrukturen ✅ ERLEDIGT
12. 🔄 **Key-Rotation** - Automatische Session-Key-Rotation ⚠️ OFFEN

---

## 🔧 CODE-VERBESSERUNGEN (Beispiele)

### Fix 2: Sichere Nonce-Validierung
```python
from collections import deque

class NonceValidator:
    def __init__(self, window_size=1000):
        self.window_size = window_size
        self.nonces = deque(maxlen=window_size)
        self.highest = 0
        self.lock = threading.Lock()
    
    def validate(self, counter: int) -> bool:
        with self.lock:
            # Reject if too old
            if counter < self.highest - self.window_size:
                return False
            # Reject if duplicate
            if counter in self.nonces:
                return False
            # Accept and update
            self.nonces.append(counter)
            if counter > self.highest:
                self.highest = counter
            return True
```

### Fix 3: Input-Validierung
```python
import re

def validate_device_id(device_id: str) -> bool:
    if not device_id or len(device_id) > 64:
        return False
    # Nur alphanumerisch, Unterstrich, Bindestrich
    if not re.match(r'^[a-zA-Z0-9_-]+$', device_id):
        return False
    return True

def validate_base64_public_key(public_key_b64: str) -> bool:
    try:
        decoded = base64.b64decode(public_key_b64)
        # SECP256R1 uncompressed public key: 91 bytes (0x04 + x + y)
        if len(decoded) != 91 or decoded[0] != 0x04:
            return False
        return True
    except Exception:
        return False
```

---

## 📚 WEITERE EMPFEHLUNGEN

### Security Hardening:
- [ ] Penetration Testing durchführen
- [ ] Code-Audit durch externe Security-Experten
- [ ] Fuzzing der UDP-Endpoints
- [ ] TLS/DTLS als zusätzliche Sicherheitsebene erwägen

### Monitoring & Alerting:
- [ ] Metriken für fehlgeschlagene Authentifizierungen
- [ ] Alerts bei Rate-Limit-Überschreitungen
- [ ] Dashboard für aktive Sessions
- [ ] Automatische Benachrichtigung bei unautorisierten Zugriffen

### Dokumentation:
- [ ] Security-Best-Practices dokumentieren
- [ ] Threat-Model erstellen
- [ ] Incident-Response-Plan
- [ ] Sichere Deployment-Anleitung

---

**Analyseende**

*Diese Analyse wurde mit bestem Wissen und Gewissen erstellt. Eine vollständige Sicherheitsgarantie kann nur durch umfassende Security-Audits und Penetration-Tests erreicht werden.*
