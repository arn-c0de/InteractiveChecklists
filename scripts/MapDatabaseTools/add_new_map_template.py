#!/usr/bin/env python3
"""Insert sample markers for [MAP_NAME] into the map_data.db for testing.

Usage:
  python add_new_map_template.py [--replace]

Options:
  --replace   Delete existing location data before inserting samples

This script is intentionally lightweight and intended for development/testing only.
Replace [MAP_NAME] with the actual map name (e.g., "Syria", "Persian Gulf", etc.)
"""
from pathlib import Path
import argparse
import sys

scripts_dir = Path(__file__).parent
if str(scripts_dir) not in sys.path:
    sys.path.insert(0, str(scripts_dir))

from core.markers_database import MarkersDatabase, Location, Runway, MarkerType


def estimate_heading_from_name(name: str):
    """Estimate runway heading from runway name (e.g., '12/30' -> 120.0). Returns None if it cannot parse a numeric runway designation."""
    import re
    if not name:
        return None
    m = re.search(r"(\d{1,2})", name)
    if not m:
        return None
    try:
        num = int(m.group(1))
        return float(num * 10.0)
    except Exception:
        return None


def map_samples():
    """Return a list of Location objects representing [MAP_NAME] airports and points of interest.
    
    Replace this function with your actual map data.
    """
    samples = [
        # Example Airport Entry - Copy and modify this template for each location
        Location(
            name="Example Airport Name",
            latitude=0.0,  # Replace with actual coordinates
            longitude=0.0,  # Replace with actual coordinates
            marker_type=MarkerType.AIRPORT.value,  # Can be AIRPORT, AIRBASE, HELIPAD, POINT_OF_INTEREST, WAYPOINT
            icao="XXXX",  # ICAO code (4 letters)
            iata="XXX",  # IATA code (3 letters) - optional
            elevation_m=0.0,  # Elevation in meters
            runways=[
                Runway(
                    name="09/27",  # Runway designation
                    length_m=3000,  # Length in meters
                    width_m=45,  # Width in meters
                    heading=90.0,  # Magnetic heading (0-360)
                    surface="asphalt",  # Surface type: asphalt, concrete, grass, dirt, etc.
                    ils=False  # ILS available: True or False
                ),
                # Add more runways as needed
            ],
            frequencies={
                "tower": 118.5,  # Tower frequency
                "ground": 121.9,  # Ground frequency
                "approach": 119.1,  # Approach frequency
                "atis": 127.0  # ATIS frequency
            },
            country="Country Name",  # Country
            tags=["civilian", "international"],  # Tags: military, civilian, airbase, helipad, etc.
            source="OurAirports / DCS-World MAP",  # Data source
            map="MAP_NAME"  # Replace with actual map name
        ),
        
        # Example Point of Interest Entry
        Location(
            name="Example City or Landmark",
            latitude=0.0,
            longitude=0.0,
            marker_type=MarkerType.POINT_OF_INTEREST.value,
            elevation_m=0.0,
            country="Country Name",
            tags=["city", "landmark"],
            source="DCS-World MAP",
            map="MAP_NAME"
        ),
        
        # Example Waypoint Entry
        Location(
            name="Example Waypoint",
            latitude=0.0,
            longitude=0.0,
            marker_type=MarkerType.WAYPOINT.value,
            elevation_m=0.0,
            tags=["navigation"],
            source="DCS-World MAP",
            map="MAP_NAME"
        ),
        
        # Add more locations here...
    ]
    
    return samples


def main():
    parser = argparse.ArgumentParser(description="Insert [MAP_NAME] sample markers into map database.")
    parser.add_argument('--replace', action='store_true', help='Delete all existing location data')
    args = parser.parse_args()

    db_file = Path(__file__).parent.parent.parent / 'app' / 'src' / 'main' / 'assets' / 'databases' / 'map_data.db'
    if not db_file.exists():
        print(f'ERROR: Database not found at {db_file}')
        return

    print(f'Using database: {db_file}')
    db = MarkersDatabase(str(db_file))

    if args.replace:
        print('Clearing existing location-related tables (routes, locations, runways, services, media, navaids, location_tags, tags)')
        cur = db.conn.cursor()
        cur.execute("DELETE FROM route_waypoints")
        cur.execute("DELETE FROM runways")
        cur.execute("DELETE FROM services")
        cur.execute("DELETE FROM media")
        cur.execute("DELETE FROM navaids")
        cur.execute("DELETE FROM location_tags")
        cur.execute("DELETE FROM tags")
        cur.execute("DELETE FROM routes")
        cur.execute("DELETE FROM locations")
        db.conn.commit()

    samples = map_samples()
    inserted = 0
    for loc in samples:
        try:
            new_id = db.add_location(loc)
            print(f"Inserted: {loc.name} (id={new_id})")
            inserted += 1
        except Exception as e:
            print(f"Failed inserting {loc.name}: {e}")

    print(f"\nInserted {inserted} sample locations")
    print(f"Total locations now: {len(db.get_all_locations())}")
    db.close()


if __name__ == '__main__':
    main()
