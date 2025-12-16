# License Information - MapDatabaseTools

## Project License
This tool is part of **InteractiveChecklists** and is licensed under:
**Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**

See the root `LICENSE` file for full details.

## Third-Party Dependencies

All Python dependencies used in this tool are **commercial-friendly** and compatible with CC BY-NC-SA 4.0:

### Runtime Dependencies

| Library | License | Commercial Use | Notes |
|---------|---------|----------------|-------|
| **PySide6** | LGPL v3 | ✅ Yes | Qt Company's official Python bindings. LGPL allows commercial use via dynamic linking (no GPL contamination). |
| **cryptography** | Apache 2.0 / BSD | ✅ Yes | Permissive license, fully compatible. |
| **requests** | Apache 2.0 | ✅ Yes | Permissive license, fully compatible. |

### Frontend Assets (Loaded at Runtime)

| Library | License | Commercial Use | Attribution Required |
|---------|---------|----------------|---------------------|
| **Leaflet.js** | BSD-2-Clause | ✅ Yes | Yes - "© 2010-2023 Vladimir Agafonkin, © 2010-2011 CloudMade" |
| **Leaflet.RotatedMarker** | MIT | ✅ Yes | No strict requirement, but recommended |
| **OpenStreetMap Tiles** | ODbL 1.0 | ✅ Yes (with attribution) | Required - "© OpenStreetMap contributors" |

## Commercial Use Notes

While the **InteractiveChecklists project** itself is licensed under CC BY-NC-SA 4.0 (non-commercial), all underlying **libraries and dependencies** in this tool use permissive licenses (LGPL/Apache/MIT/BSD).

**What this means:**
- ✅ If you obtain commercial permission or create a derivative work, there are **no GPL-style restrictions** from the dependencies.
- ✅ PySide6 (LGPL) allows commercial use when dynamically linked (which is the standard way Python uses it).
- ✅ All other dependencies are Apache 2.0, MIT, or BSD — fully permissive.

**If you plan to use this commercially:**
1. Obtain appropriate permissions for the CC BY-NC-SA 4.0 code (or relicense your derivative).
2. Comply with LGPL requirements for PySide6 (provide LGPL license text, allow users to replace PySide6 library).
3. Maintain attribution for Leaflet.js and OpenStreetMap.

## Full License Texts

- PySide6 (LGPL v3): https://www.gnu.org/licenses/lgpl-3.0.html
- Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0
- MIT: https://opensource.org/licenses/MIT
- BSD-2-Clause: https://opensource.org/licenses/BSD-2-Clause
- ODbL 1.0: https://opendatacommons.org/licenses/odbl/1.0/

For detailed third-party license information, see `THIRD_PARTY_LICENSES.md` in the project root.
