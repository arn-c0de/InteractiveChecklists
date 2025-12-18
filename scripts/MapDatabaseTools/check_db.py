import json
from core.markers_database import MarkersDatabase

with MarkersDatabase() as db:
    print('DB path:', db.db_path)
    locs = db.get_all_locations()
    print('Locations count:', len(locs))
    for i, loc in enumerate(locs, start=1):
        print(f"{i}. id={loc.id} name={loc.name} type={loc.marker_type} coord={loc.latitude},{loc.longitude}")
        # Print all stored fields for this marker as pretty JSON
        print(json.dumps(loc.to_dict(), indent=2, ensure_ascii=False))
        print('-' * 80)
