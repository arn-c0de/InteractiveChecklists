# Entity Tracking Feature

## Überblick

Das System sendet jetzt **automatisch beide Dateien** (Aircraft Data + Entity Contacts) basierend auf dem Toggle-Status in der Android App.

## Architektur

### DCS (Export.lua)
Schreibt parallel zwei separate Dateien:
- `player_aircraft_parsed.jsonl` - Flugzeugdaten (~1KB/Update)
- `entity-contacts-parsed.jsonl` - Taktische Einheiten (~30KB/Update)

### Python Forwarder (forward_parsed_udp.py)
**Ein einziger Befehl** reicht:
```bash
python forward_parsed_udp.py --interval 50 --host 192.168.178.132 --port 5010 --verbose --authorized-devices authorized_devices.json --bind-ip 192.168.178.100
```

**Was passiert automatisch:**
1. Script erkennt beide Dateien automatisch
2. Beim Handshake empfängt es `entityTrackingEnabled` Status von der App
3. **Wenn Toggle AUS:** Nur Aircraft-Daten werden gesendet
4. **Wenn Toggle AN:** Beide Dateien werden parallel gesendet

### Android App
- Toggle-Schalter in der Tactical Units Liste
- Status wird beim Handshake an den Forwarder gesendet
- Kann jederzeit ein/aus geschaltet werden (nächstes Handshake übernimmt neuen Status)

## Datenfluss

```
DCS Export.lua
    ├─→ player_aircraft_parsed.jsonl (immer)
    └─→ entity-contacts-parsed.jsonl (immer)

Python Forwarder (liest beide Dateien)
    ├─→ Aircraft Data → ALLE verbundenen Geräte
    └─→ Entity Contacts → NUR Geräte mit entityTrackingEnabled=true

Android App
    ├─→ Empfängt Aircraft Data (immer)
    └─→ Empfängt Entity Contacts (nur wenn Toggle aktiv)
```

## Vorteile

✅ **Ein einziger Python-Befehl** - keine separaten Prozesse mehr
✅ **Dynamisch steuerbar** - Toggle in der App reicht
✅ **Effizient** - Entity-Daten werden nur gesendet wenn benötigt
✅ **Keine Bandbreiten-Verschwendung** - Forwarder prüft vor dem Senden
✅ **Automatische Erkennung** - Forwarder findet entity-contacts-Datei selbst

## Technische Details

### Handshake-Erweiterung
```json
{
  "type": "ClientHello",
  "deviceId": "...",
  "deviceName": "...",
  "publicKey": "...",
  "entityTrackingEnabled": true  // NEU
}
```

### Session Management
Jede Session speichert:
- `session_id` - Eindeutige Session-ID
- `device_id` - Geräte-ID
- `session_key` - Verschlüsselungsschlüssel
- **`entity_tracking_enabled`** - Toggle-Status vom Client

### Sende-Logik im Forwarder
```python
# Aircraft data: an ALLE Sessions
for device_id, session_id in device_sessions.items():
    send(aircraft_data, device_id)

# Entity contacts: NUR wenn session.entity_tracking_enabled==true
for device_id, session_id in device_sessions.items():
    session = sessions[session_id]
    if session.entity_tracking_enabled:
        send(entity_data, device_id)
```

## Logging

Der Forwarder zeigt im Log:
```
🔑 Session created: abc123... (key derived from ECDH, with entity tracking)
📡 Entity contacts file detected: ...\entity-contacts-parsed.jsonl
📡 Sent entity data to abc123...
📡 Entity contacts sent to 1 device(s)
```

Oder wenn Toggle aus:
```
🔑 Session created: xyz789... (key derived from ECDH, aircraft data only)
```

## Migration

**Bestehende Befehle funktionieren weiterhin!**
- Wenn `entityTrackingEnabled` nicht im Handshake ist → default: `false`
- Alte Apps ohne Toggle → bekommen keine Entity-Daten
- Neue Apps mit Toggle aus → bekommen keine Entity-Daten
- Neue Apps mit Toggle an → bekommen Entity-Daten

Keine Breaking Changes! 🎉
