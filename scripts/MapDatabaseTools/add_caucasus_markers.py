#!/usr/bin/env python3
"""Insert sample Caucasus airport markers into the map_data.db for testing.

Usage:
  python add_caucasus_markers.py [--replace]

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


def caucasus_samples():
    """Return a list of Location objects representing sample airports."""
    samples = [
        # Georgia
        Location(
            name="Tbilisi Intl",
            latitude=41.6692,
            longitude=44.9544,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGTB",
            iata="TBS",
            elevation_m=481.0,
            runways=[Runway(name="13R/31L", length_m=3000, width_m=45, heading=136.54, surface="concrete", ils=True)],
            frequencies={"tower":138.0, "tower_secondary":128.0, "ground":119.0, "approach":134.6, "atis":132.8, "apron":131.7},
            country="Georgia",
            tags=["airbase", "civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Batumi Intl",
            latitude=41.6100,
            longitude=41.5911,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGSB",
            iata="BUS",
            elevation_m=11.0,
            runways=[Runway(name="12/30", length_m=2500, width_m=45, heading=130.89, surface="concrete/asphalt", ils=True)],
            frequencies={"tower":131.0},
            country="Georgia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        # Russia / Kuban / North Caucasus
        Location(
            name="Anapa – Vityazevo",
            latitude=45.0006,
            longitude=37.3475,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKA",
            iata="AAQ",
            elevation_m=53.0,
            runways=[Runway(name="06/24", length_m=2800, width_m=45, heading=60.0, surface="asphalt")],
            frequencies={"tower":121.0},
            country="Russia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Beslan",
            latitude=43.2039,
            longitude=44.6056,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMO",
            iata="OGZ",
            elevation_m=510.0,
            runways=[Runway(name="10/28", length_m=3000, width_m=45, heading=100.0, surface="asphalt")],
            frequencies={"tower":141.0},
            country="Russia",
            tags=["airbase", "civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Gelendzhik",
            latitude=44.5750,
            longitude=38.0803,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKG",
            iata="GDZ",
            elevation_m=30.0,
            runways=[Runway(name="01/19", length_m=3100, width_m=45, heading=7.0, surface="hard", ils=True)],
            frequencies={"tower":126.0},
            country="Russia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Krasnodar – Pashkovsky",
            latitude=45.0355,
            longitude=39.1707,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKK",
            iata="KRR",
            elevation_m=36.0,
            runways=[Runway(name="05R/23L", length_m=3000, width_m=45, heading=45.0, surface="hard", ils=True)],
            frequencies={"tower":128.0, "tower_secondary":257.0},
            country="Russia",
            tags=["airbase", "civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Krasnodar – Center",
            latitude=45.0360,
            longitude=38.9830,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKI",
            elevation_m=15.0,
            runways=[Runway(name="09R/27L", length_m=3000, width_m=45, heading=90.0, surface="hard", ils=True)],
            frequencies={"tower":122.0, "tower_secondary":251.0},
            country="Russia",
            tags=["military"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Krymsk",
            latitude=44.9272,
            longitude=38.0569,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKW",
            elevation_m=40.0,
            runways=[Runway(name="04/22", length_m=3000, width_m=45, heading=40.0, surface="asphalt")],
            frequencies={"tower":124.0, "tower_secondary":253.0},
            country="Russia",
            tags=["airbase"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Maykop – Khanskaya",
            latitude=44.6527,
            longitude=40.1683,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKH",
            elevation_m=70.0,
            runways=[Runway(name="04/22", length_m=3000, width_m=45, heading=40.0, surface="asphalt")],
            frequencies={"tower":125.0, "tower_secondary":254.0},
            country="Russia",
            tags=["military"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Mozdok (Mozdok Air Base)",
            latitude=43.7719,
            longitude=44.6475,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMF",
            iata="",
            elevation_m=1100.0,
            runways=[Runway(name="26/08", length_m=3000, width_m=45, heading=40.0, surface="asphalt")],
            frequencies={"tower":137.0, "tower_secondary":266.0},
            country="Russia",
            tags=["airbase"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Nalchik",
            latitude=43.5383,
            longitude=43.6428,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMN",
            iata="NAL",
            elevation_m=445.0,
            runways=[Runway(name="06/24", length_m=2200, width_m=42, heading=56.0, surface="asphalt")],
            frequencies={"tower":"136.0 MHz", "tower_secondary":265.0},
            country="Russia",
            tags=["airbase", "civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Sukhumi – Babushara",
            latitude=42.8582,
            longitude=41.1281,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGSS",
            iata="SUI",
            elevation_m=20.0,
            runways=[Runway(name="12/30", length_m=3640, width_m=52, heading=122.0, surface="asphalt")],
            frequencies={"tower":"129.0 MHz", "tower_secondary":258.0},
            country="Abkhazia",
            tags=["civilian", "airbase"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Sochi – Adler",
            latitude=43.4499,
            longitude=39.9566,
            marker_type=MarkerType.AIRPORT.value,
            icao="URSS",
            iata="AER",
            elevation_m=27.0,
            runways=[
                Runway(name="02/20", length_m=2200, width_m=49, heading=29.0, surface="asphalt", ils=True),
                Runway(name="06/24", length_m=2890, width_m=50, heading=65.0, surface="asphalt", ils=True)
            ],
            frequencies={"tower":"127.0 MHz", "tower_secondary":256.0},
            country="Russia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        # Additional Caucasus small/field airstrips
        Location(
            name="Senaki – Kolkhi",
            latitude=42.2586,
            longitude=42.2309,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGKS",
            elevation_m=20.0,
            runways=[Runway(name="27/09", length_m=3640, width_m=52, heading=270.0, surface="asphalt")],
            frequencies={"tower":"132.0 MHz", "tower_secondary":261.0},
            country="Georgia",
            tags=["airbase"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Kutaisi – Kopitnari",
            latitude=42.1750,
            longitude=42.4828,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGKO",
            iata="KUT",
            elevation_m=68.0,
            runways=[Runway(name="07/28", length_m=2500, width_m=44, heading=75.0, surface="asphalt", ils=True)],
            frequencies={"tower":"134.0 MHz", "tower_secondary":263.0},
            country="Georgia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Gudauta",
            latitude=43.1000,
            longitude=40.7180,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGAD",
            elevation_m=150.0,
            # Disused / limited public data — runway present but status uncertain
            # Heading estimated from runway name "12/30" -> 120° (unverified)
            runways=[Runway(name="15/33", length_m=None, width_m=None, heading=150.0, surface="concrete", ils=False)],
            frequencies={"tower":"130.0 MHz", "tower_secondary":259.0},
            country="Abkhazia",
            tags=["military"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Kobuleti",
            latitude=41.7850,
            longitude=41.7920,
            marker_type=MarkerType.AIRPORT.value,
            icao="UG5X",
            elevation_m=5.0,
            # Runway length estimated from imagery / OSM; verify before operational use
            runways=[Runway(name="08/26", length_m=1500, width_m=30, heading=80.0, surface="asphalt", ils=False)],
            frequencies={"tower":"133.0 MHz", "tower_secondary":262.0},
            country="Georgia",
            tags=["civilian"],
            source="OurAirports / DCS-World MAP"
        ),
        Location(
            name="Vaziani",
            latitude=41.8414,
            longitude=44.6797,
            marker_type=MarkerType.AIRPORT.value,
            icao="UG27",
            elevation_m=445.0,
            # Former Soviet airfield — runway length approximated from OurAirports/imagery
            runways=[Runway(name="11/29", length_m=3000, width_m=45, heading=110.0, surface="concrete", ils=False)],
            frequencies={"tower":"140.0 MHz", "tower_secondary":269.0},
            country="Georgia",
            tags=["military"],
            source="OurAirports / DCS-World MAP"
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


def increment_db_version(db):
    """Increment database user_version to trigger Android app update detection"""
    cursor = db.conn.cursor()
    cursor.execute("PRAGMA user_version")
    current_version = cursor.fetchone()[0]
    new_version = current_version + 1
    cursor.execute(f"PRAGMA user_version = {new_version}")
    db.conn.commit()
    print(f"\nDatabase version updated: {current_version} -> {new_version}")
    print("(App will show update dialog on next start)")
    return new_version


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--replace', action='store_true', help='Delete existing location content before inserting samples')
    p.add_argument('--no-version-bump', action='store_true', help='Do not increment database version after changes')
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

    samples = caucasus_samples()
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

    # Automatically increment version unless --no-version-bump is specified
    if not args.no_version_bump:
        increment_db_version(db)

    db.close()


if __name__ == '__main__':
    main()
