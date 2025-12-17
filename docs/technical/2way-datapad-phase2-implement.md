### Minimal Concept Guide: External Command Mapping for DCS (Export.lua)

#### Goal

Send commands from an external application to DCS and trigger cockpit button actions via `Export.lua`, while keeping all command definitions in separate Lua files.

---

## 1. Architecture Overview

```
External App
   ↓ (UDP / TCP / File)
Export.lua
   ↓
Command Dispatcher
   ↓
Cockpit Device (performClickableAction)
```

* `Export.lua` handles **I/O and dispatch**
* External Lua files contain **command definitions only**
* One shared transport layer for all aircraft
* Aircraft-specific mappings kept separate

---

## 2. File Structure (Saved Games)

```
Saved Games/DCS/Scripts/
├─ Export.lua
├─ io_udp.lua          -- networking / input
├─ commands_fa18c.lua  -- FA-18C command map
├─ commands_a10c.lua   -- A-10C command map
└─ dispatcher.lua
```

---

## 3. Loading External Lua Files (Export.lua)

```lua
local lfs = require('lfs')
local BASE = lfs.writedir() .. 'Scripts/'

dofile(BASE .. 'io_udp.lua')
dofile(BASE .. 'dispatcher.lua')
dofile(BASE .. 'commands_fa18c.lua')
```

---

## 4. Command Definition File (example: FA-18C)

```lua
COMMANDS_FA18C = {
    CB_FCS_CHAN1 = { device = 13, command = 3007 },
    CB_FCS_CHAN2 = { device = 13, command = 3008 }
}
```

* Use **numeric IDs only**
* Do not reference `devices.lua` or `command_defs.lua`
* Stable across updates

---

## 5. Command Dispatcher

```lua
function dispatch_command(aircraft, name, value)
    local map = COMMANDS[aircraft]
    if not map then return end

    local c = map[name]
    if not c then return end

    local dev = GetDevice(c.device)
    if dev then
        dev:performClickableAction(c.command, value)
    end
end
```

---

## 6. Input Format (from App)

Example (JSON or text):

```
FA18C;CB_FCS_CHAN1;1
```

Fields:

1. Aircraft ID
2. Logical command name
3. Value (0 / 1 / axis)

---

## 7. Update Loop Hook

```lua
function LuaExportAfterNextFrame()
    receive_input()      -- UDP / TCP
    -- telemetry export (optional)
end
```

---

## 8. Key Rules

* Commands are **aircraft-specific**
* Transport logic is **aircraft-agnostic**
* Use `performClickableAction` for cockpit interaction
* Avoid loading cockpit Lua files directly
* Wrap everything in `pcall()` to avoid export crashes

---

## 9. Limitations

* Works only for player-controlled aircraft
* Some switches (CBs, guarded switches) are system-gated
* Multiplayer servers may restrict export

---

## 10. References

* DCS Export.lua SDK
* `Mods/aircraft/<module>/Cockpit/Scripts/clickabledata.lua`
* ED Forums: *Clickable Cockpit API*

This guide is intended as a **clean baseline** for later extension.
