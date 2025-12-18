from core.markers_database import MarkersDatabase

with MarkersDatabase() as db:
    print('DB path:', db.db_path)
    locs = db.get_all_locations()
    print('Locations count:', len(locs))
    for i, loc in enumerate(locs, start=1):
        print(f"{i}. {loc.name} @ {loc.latitude},{loc.longitude} ({loc.marker_type})")
