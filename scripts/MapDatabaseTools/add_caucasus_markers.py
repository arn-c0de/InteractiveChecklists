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


def caucasus_samples():
    """Return a list of Location objects representing sample airports."""
    return [
        # Georgia
        Location(
            name="Tbilisi Intl",
            latitude=41.6692,
            longitude=44.9544,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGTB",
            iata="TBS",
            elevation_m=490.0,
            runways=[Runway(name="13/31", length_m=3600, width_m=45, heading=130.0, surface="asphalt", ils=True)],
            frequencies={"tower":118.1, "ground":121.9},
            country="Georgia",
            tags=["airbase", "civilian"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Zvartnots (Yerevan)",
            latitude=40.1472,
            longitude=44.3959,
            marker_type=MarkerType.AIRPORT.value,
            icao="UDYZ",
            iata="EVN",
            elevation_m=862.0,
            runways=[Runway(name="04/22", length_m=3500, width_m=45, heading=40.0, surface="asphalt", ils=True)],
            frequencies={"tower":119.7},
            country="Armenia",
            tags=["airbase", "civilian"],
            source="forum.dcs.world"
        ),
        Location(
            name="Batumi Intl",
            latitude=41.6100,
            longitude=41.5911,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGSB",
            iata="BUS",
            elevation_m=8.0,
            runways=[Runway(name="13/31", length_m=3200, width_m=45, heading=130.0, surface="asphalt")],
            frequencies={"tower":118.5},
            country="Georgia",
            tags=["civilian"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Soganlug (Yerevan East) - Test FARP",
            latitude=40.2,
            longitude=44.6,
            marker_type=MarkerType.AIRPORT.value,
            icao=None,
            iata=None,
            elevation_m=900.0,
            runways=None,
            country="Armenia",
            tags=["FARP", "military"],
            source="server.3rd-wing.net"
        ),
        # Russia / Kuban / North Caucasus
        Location(
            name="Anapa – Vityazevo",
            latitude=45.0006,
            longitude=37.3475,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKA",
            iata="AAQ",
            elevation_m=12.0,
            runways=[Runway(name="06/24", length_m=2800, width_m=45, heading=60.0, surface="asphalt")],
            country="Russia",
            tags=["civilian"],
            source="forum.dcs.world"
        ),
        Location(
            name="Beslan",
            latitude=43.2039,
            longitude=44.6056,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMO",
            iata="OGZ",
            elevation_m=600.0,
            runways=[Runway(name="10/28", length_m=3000, width_m=45, heading=100.0, surface="asphalt")],
            country="Russia",
            tags=["airbase", "civilian"],
            source="forum.dcs.world"
        ),
        Location(
            name="Gelendzhik",
            latitude=44.5750,
            longitude=38.0803,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKG",
            iata="GDZ",
            elevation_m=90.0,
            runways=[Runway(name="06/24", length_m=3200, width_m=45, heading=60.0, surface="asphalt")],
            country="Russia",
            tags=["civilian"],
            source="forum.dcs.world"
        ),
        Location(
            name="Krasnodar – Pashkovsky",
            latitude=45.0355,
            longitude=39.1707,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKK",
            iata="KRR",
            elevation_m=10.0,
            runways=[Runway(name="04/22", length_m=3600, width_m=60, heading=40.0, surface="asphalt", ils=True)],
            country="Russia",
            tags=["airbase", "civilian"],
            source="forum.dcs.world"
        ),
        Location(
            name="Krasnodar – Center",
            latitude=45.0360,
            longitude=38.9830,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKI",
            elevation_m=15.0,
            country="Russia",
            tags=["military"],
            source="forum.dcs.world"
        ),
        Location(
            name="Krymsk",
            latitude=44.9272,
            longitude=38.0569,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKW",
            elevation_m=40.0,
            runways=[Runway(name="09/27", length_m=3000, width_m=45, heading=90.0, surface="asphalt")],
            country="Russia",
            tags=["airbase"],
            source="forum.dcs.world"
        ),
        Location(
            name="Maykop – Khanskaya",
            latitude=44.6527,
            longitude=40.1683,
            marker_type=MarkerType.AIRPORT.value,
            icao="URKH",
            elevation_m=70.0,
            country="Russia",
            tags=["military"],
            source="dcsviper.gr"
        ),
        Location(
            name="Mozdok (Mozdok Air Base)",
            latitude=43.7719,
            longitude=44.6475,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMF",
            iata="",
            elevation_m=1100.0,
            country="Russia",
            tags=["airbase"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Nalchik",
            latitude=43.5383,
            longitude=43.6428,
            marker_type=MarkerType.AIRPORT.value,
            icao="URMN",
            elevation_m=560.0,
            country="Russia",
            tags=["airbase", "civilian"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Sukhumi – Babushara",
            latitude=43.0316,
            longitude=41.1603,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGSS",
            elevation_m=3.0,
            country="Abkhazia",
            tags=["airbase"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Sochi – Adler",
            latitude=43.4499,
            longitude=39.9566,
            marker_type=MarkerType.AIRPORT.value,
            icao="URSS",
            iata="AER",
            elevation_m=5.0,
            runways=[Runway(name="06/24", length_m=3400, width_m=45, heading=60.0, surface="asphalt")],
            country="Russia",
            tags=["civilian"],
            source="server.3rd-wing.net"
        ),
        # Additional Caucasus small/field airstrips
        Location(
            name="Senaki – Kolkhi",
            latitude=42.2586,
            longitude=42.2309,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGKS",
            elevation_m=20.0,
            country="Georgia",
            tags=["airbase"],
            source="dcsviper.gr"
        ),
        Location(
            name="Kutaisi – Kopitnari",
            latitude=42.1750,
            longitude=42.4828,
            marker_type=MarkerType.AIRPORT.value,
            icao="UGKO",
            iata="KUT",
            elevation_m=130.0,
            country="Georgia",
            tags=["civilian"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Gudauta",
            latitude=43.1000,
            longitude=40.7180,
            marker_type=MarkerType.AIRPORT.value,
            icao="UG23",
            elevation_m=150.0,
            country="Abkhazia",
            tags=["military"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Kobuleti",
            latitude=41.7850,
            longitude=41.7920,
            marker_type=MarkerType.AIRPORT.value,
            icao="UG5X",
            elevation_m=5.0,
            country="Georgia",
            tags=["civilian"],
            source="server.3rd-wing.net"
        ),
        Location(
            name="Vaziani",
            latitude=41.8414,
            longitude=44.6797,
            marker_type=MarkerType.AIRPORT.value,
            icao="UG27",
            elevation_m=600.0,
            country="Georgia",
            tags=["military"],
            source="server.3rd-wing.net"
        ),
    ]


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
    db.close()


if __name__ == '__main__':
    main()
