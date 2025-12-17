"""fetch_map_assets.py — Downloads Leaflet and plugin assets locally

Usage:
    python fetch_map_assets.py

This script saves the files to ./leaflet/ so `map.html` can load them locally
(avoids CDN/network issues inside QWebEngineView when offline or blocked).
"""
from pathlib import Path
import sys
import requests

ASSETS = {
    "leaflet.css": "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
    "leaflet.js": "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
    "leaflet.rotatedMarker.js": "https://raw.githubusercontent.com/bbecquet/Leaflet.RotatedMarker/master/leaflet.rotatedMarker.js",
}

# Leaflet assets are in the parent MapDatabaseTools directory
OUT_DIR = Path(__file__).parent.parent / "leaflet"
OUT_DIR.mkdir(parents=True, exist_ok=True)


def download(url: str, dest: Path) -> bool:
    try:
        print(f"Downloading {url} -> {dest}")
        resp = requests.get(url, timeout=30)
        resp.raise_for_status()
        dest.write_bytes(resp.content)
        print(f"Saved {dest} ({len(resp.content)} bytes)")
        return True
    except Exception as e:
        print(f"Failed to download {url}: {e}")
        return False


def main():
    success = True
    for name, url in ASSETS.items():
        dest = OUT_DIR / name
        ok = download(url, dest)
        success = success and ok

    if not success:
        print("Some assets failed to download. You can run the script again later.")
        sys.exit(2)

    print("All assets downloaded successfully to:", OUT_DIR)


if __name__ == "__main__":
    main()
