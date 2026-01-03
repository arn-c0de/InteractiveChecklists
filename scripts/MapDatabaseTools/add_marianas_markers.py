#!/usr/bin/env python3
"""Insert sample Marianas Islands airport markers into the map_data.db for testing.

Usage:
  python add_marianas_markers.py [--replace]

Options:
  --replace   Delete existing location data before inserting samples

This script is intentionally lightweight and intended for development/testing only.
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


def marianas_samples():
    """Return a list of Location objects representing Marianas Islands airports."""
    samples = [
        # Guam (US Territory)
        Location(
            name="Antonio B. Won Pat International Airport",
            latitude=13.483889,
            longitude=144.797222,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGUM",
            iata="GUM",
            elevation_m=91.0,
            runways=[
                Runway(name="06L/24R", length_m=3255, width_m=45, heading=65.0, surface="asphalt", ils=True),
                Runway(name="06R/24L", length_m=2743, width_m=45, heading=65.0, surface="asphalt", ils=False)
            ],
            frequencies={"tower": 110.30, "ground": 121.9, "approach": 118.7, "atis": 127.25},
            country="United States (Guam)",
            tags=["civilian", "international"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="Andersen Air Force Base",
            latitude=13.583889,
            longitude=144.929722,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGUA",
            iata="UAM",
            elevation_m=184.0,
            runways=[
                Runway(name="06L/24R", length_m=3399, width_m=61, heading=65.0, surface="concrete", ils=True),
                Runway(name="06R/24L", length_m=3048, width_m=46, heading=65.0, surface="concrete", ils=False)
            ],
            frequencies={"tower": 126.2, "ground": 275.8, "approach": 327.0, "departure": 363.275},
            country="United States (Guam)",
            tags=["military", "airbase", "usaf"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="OLF Orote (Naval Base Guam)",
            latitude=13.438336,
            longitude=144.642391,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGRO",
            elevation_m=116.0,
            runways=[
                Runway(name="07/25", length_m=2743, width_m=45, heading=65.0, surface="asphalt", ils=False)
            ],
            frequencies={"tower": 340.2},
            country="United States (Guam)",
            tags=["military", "navy", "olf"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        
        # Northern Mariana Islands (US Commonwealth)                
        Location(
            name="Saipan International Airport",
            latitude=15.119444,
            longitude=145.729444,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGSN",
            iata="SPN",
            elevation_m=67.0,
            runways=[
                Runway(name="07/25", length_m=2438, width_m=46, heading=68.0, surface="asphalt", ils=True)
            ],
            frequencies={"tower": 125.7, "approach": 119.7, "atis": 126.4},
            country="United States (Northern Mariana Islands)",
            tags=["civilian", "international"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="Tinian International Airport",
            latitude=14.999722,
            longitude=145.619167,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGWT",
            iata="TIQ",
            elevation_m=83.0,
            runways=[
                Runway(name="08/26", length_m=2621, width_m=46, heading=80.0, surface="asphalt", ils=False)
            ],
            frequencies={"tower": 123.65},
            country="United States (Northern Mariana Islands)",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="North West Field (Tinian)",
            latitude=15.077842,
            longitude=145.639744,
            marker_type=MarkerType.AIRPORT.value,
            icao="TN01",
            elevation_m=187.0,
            runways=[
                Runway(name="06/24", length_m=2438, width_m=46, heading=62.0, surface="asphalt", ils=False)
            ],
            frequencies={"tower": 118.5},
            country="United States (Northern Mariana Islands)",
            tags=["military", "historical", "ww2"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="Rota International Airport",
            latitude=14.174167,
            longitude=145.242778,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGRO",
            iata="ROP",
            elevation_m=180.0,
            runways=[
                Runway(name="09/27", length_m=1829, width_m=46, heading=93.0, surface="asphalt", ils=False)
            ],
            frequencies={"tower": 123.6},
            country="United States (Northern Mariana Islands)",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
        Location(
            name="Pagan Airstrip",
            latitude=18.123207,
            longitude=145.763147,
            marker_type=MarkerType.AIRPORT.value,
            icao="PGPA",
            elevation_m=167.0,
            runways=[
                Runway(name="12/30", length_m=914, width_m=18, heading=119.0, surface="coral", ils=False)
            ],
            frequencies={},
            country="United States (Northern Mariana Islands)",
            tags=["remote", "unpaved", "volcano"],
            source="OurAirports / DCS-World MAP",
            map="Marianas"
        ),
    ]

    # Estimate missing runway headings from runway names (e.g., "12/30" -> 120.0)
    for loc in samples:
        if loc.runways:
            for rw in loc.runways:
                if getattr(rw, 'heading', None) is None:
                    est = estimate_heading_from_name(rw.name)
                    if est is not None:
                        rw.heading = est

    return samples


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--replace', action='store_true', help='Delete existing location content before inserting samples')
    args = p.parse_args()

    db = MarkersDatabase()

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

    samples = marianas_samples()
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
