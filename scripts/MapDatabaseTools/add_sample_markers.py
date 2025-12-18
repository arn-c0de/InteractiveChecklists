#!/usr/bin/env python3
"""Add DCS World Caucasus region airbase markers to tactical_data.db (stored in app/database) for development/testing"""

from core.markers_database import MarkersDatabase, Location, MarkerType

# Precise coordinates from real-world data (matching DCS World Caucasus map positions closely)
# Focused on key airbases in Georgia and surroundings, commonly used in DCS missions

sample_locations = [
    Location(
        name="Kutaisi (Kopitnari)",
        latitude=42.1767,
        longitude=42.4826,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="David the Builder Kutaisi International Airport (DCS: Kutaisi)",
        icao="UGKO",
        iata="KUT",
        elevation_m=68
    ),
    Location(
        name="Senaki-Kolkhi",
        latitude=42.2403,
        longitude=42.0455,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Military airbase near Senaki (DCS: Senaki-Kolkhi)",
        icao="",
        iata="",
        elevation_m=13
    ),
    Location(
        name="Kobuleti",
        latitude=41.9289,
        longitude=41.8667,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Airbase near Kobuleti (popular BLUFOR base in DCS)",
        icao="",
        iata="",
        elevation_m=20
    ),
    Location(
        name="Batumi",
        latitude=41.6103,
        longitude=41.5997,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Batumi International Airport (DCS: Batumi)",
        icao="UGSB",
        iata="BUS",
        elevation_m=32
    ),
    Location(
        name="Sukhumi (Babushara)",
        latitude=42.8581,
        longitude=41.1281,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Sukhumi Babushara Airport (Abkhazia region)",
        icao="UGSS",
        iata="SUI",
        elevation_m=18
    ),
    Location(
        name="Gudauta",
        latitude=43.1039,
        longitude=40.5806,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Military airbase in Abkhazia",
        icao="",
        iata="",
        elevation_m=20
    ),
    Location(
        name="Tbilisi (Lochini)",
        latitude=41.6692,
        longitude=44.9547,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Tbilisi International Airport (DCS: Lochini)",
        icao="UGTB",
        iata="TBS",
        elevation_m=494
    ),
    Location(
        name="Vaziani",
        latitude=41.6283,
        longitude=45.0289,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Military airbase near Tbilisi",
        icao="",
        iata="",
        elevation_m=500
    ),
    Location(
        name="Sochi-Adler",
        latitude=43.45,
        longitude=39.95,
        marker_type=MarkerType.AIRPORT.value,
        coalition="NEUTRAL",
        description="Sochi International Airport (Russian side)",
        icao="URSS",
        iata="AER",
        elevation_m=27
    )
]

with MarkersDatabase() as db:  # This connects to tactical_data.db in app/database
    for loc in sample_locations:
        new_id = db.add_location(loc)
        print(f"Added: {loc.name} (id={new_id})")
    
    locs = db.get_all_locations()
    print(f"\nTotal locations now in database: {len(locs)}")
    for i, l in enumerate(locs, start=1):
        print(f"{i}. {l.name} @ {l.latitude:.4f},{l.longitude:.4f} ({l.marker_type})")