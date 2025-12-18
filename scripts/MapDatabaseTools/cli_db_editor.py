"""CLI DB Editor for MapDatabaseTools

Features:
- list locations
- show location
- search locations
- edit location (opens JSON in $EDITOR / notepad)
- set simple fields via CLI
- delete location
- list/add/edit/delete runways (stored in `runways` table)
- optional AI-assisted runway suggestion (requires OPENAI_API_KEY and openai package)
- export/import JSON

Usage examples:
  python cli_db_editor.py list
  python cli_db_editor.py show 42
  python cli_db_editor.py search "Kabul"
  python cli_db_editor.py edit 42
  python cli_db_editor.py add-runway 42 --name 09/27 --length-m 2500 --surface asphalt
  python cli_db_editor.py ai-suggest-runway 42

"""
from __future__ import annotations
import argparse
import json
import os
import subprocess
import tempfile
import sys
from typing import Any, Dict, List, Optional
from pathlib import Path

# Local imports
from core.markers_database import MarkersDatabase, Location

try:
    import openai
    OPENAI_AVAILABLE = True
except Exception:
    OPENAI_AVAILABLE = False


def open_in_editor(initial_content: str) -> Optional[str]:
    """Open initial_content in user's editor and return edited content as string (or None on cancel)"""
    editor = os.environ.get('EDITOR')
    if not editor and sys.platform.startswith('win'):
        editor = 'notepad'

    with tempfile.NamedTemporaryFile('w+', delete=False, suffix='.json', encoding='utf-8') as tf:
        tf.write(initial_content)
        tf.flush()
        tf_name = tf.name

    try:
        if editor:
            subprocess.call([editor, tf_name])
        else:
            # Fallback to printing path and ask user to edit manually
            print(f"Edit the file: {tf_name}")
            input("Press Enter when done...")

        with open(tf_name, 'r', encoding='utf-8') as f:
            return f.read()
    finally:
        try:
            os.unlink(tf_name)
        except Exception:
            pass


def confirm(prompt: str) -> bool:
    ans = input(f"{prompt} [y/N]: ").strip().lower()
    return ans in ('y', 'yes')


def cmd_list(args: argparse.Namespace):
    with MarkersDatabase() as db:
        locs = db.get_all_locations(marker_type=args.type)
        for loc in locs:
            print(f"{loc.id:5d}  {loc.name} ({loc.marker_type}) {loc.icao or ''} @ {loc.latitude:.4f},{loc.longitude:.4f}")


def cmd_show(args: argparse.Namespace):
    with MarkersDatabase() as db:
        loc = db.get_location(args.id)
        if not loc:
            print("Location not found")
            return
        print(json.dumps(loc.to_dict(), indent=2, ensure_ascii=False))


def cmd_search(args: argparse.Namespace):
    with MarkersDatabase() as db:
        locs = db.search_locations(args.query)
        if not locs:
            print("No matches")
            return
        for loc in locs:
            print(f"{loc.id:5d}  {loc.name} ({loc.marker_type}) {loc.icao or ''}")


def cmd_edit(args: argparse.Namespace):
    with MarkersDatabase() as db:
        loc = db.get_location(args.id)
        if not loc:
            print("Location not found")
            return

        original = loc.to_dict()
        content = json.dumps(original, indent=2, ensure_ascii=False)
        edited = open_in_editor(content)
        if edited is None:
            print("Edit cancelled")
            return

        try:
            new_data = json.loads(edited)
        except Exception as e:
            print(f"Failed to parse JSON: {e}")
            return

        # Merge new_data into Location object
        for k, v in new_data.items():
            if hasattr(loc, k):
                setattr(loc, k, v)

        success = db.update_location(loc)
        print("Updated" if success else "No changes or update failed")


def cmd_set(args: argparse.Namespace):
    with MarkersDatabase() as db:
        loc = db.get_location(args.id)
        if not loc:
            print("Location not found")
            return
        if args.name:
            loc.name = args.name
        if args.icao:
            loc.icao = args.icao
        if args.iata:
            loc.iata = args.iata
        if args.lat is not None:
            loc.latitude = args.lat
        if args.lon is not None:
            loc.longitude = args.lon
        success = db.update_location(loc)
        print("Updated" if success else "No changes or update failed")


def cmd_delete(args: argparse.Namespace):
    with MarkersDatabase() as db:
        if not confirm(f"Delete location {args.id}?"):
            print("Aborted")
            return
        ok = db.delete_location(args.id)
        print("Deleted" if ok else "Delete failed or not found")


# Runways operations (direct with runways table)
def list_runways(db: MarkersDatabase, location_id: int) -> List[Dict[str, Any]]:
    cur = db.conn.cursor()
    cur.execute("SELECT * FROM runways WHERE location_id=? ORDER BY id", (location_id,))
    rows = cur.fetchall()
    return [dict(r) for r in rows]


def print_runways(rows: List[Dict[str, Any]]):
    if not rows:
        print("No runways found")
        return
    for r in rows:
        print(f"ID {r['id']}: {r['name']} length {r.get('length_m') or 'N/A'} m surface {r.get('surface')}")


def cmd_list_runways(args: argparse.Namespace):
    with MarkersDatabase() as db:
        rows = list_runways(db, args.location_id)
        print_runways(rows)


def cmd_add_runway(args: argparse.Namespace):
    with MarkersDatabase() as db:
        # basic insert
        cur = db.conn.cursor()
        length_ft = int(round(args.length_m * 3.28084)) if args.length_m else None
        cur.execute(
            """INSERT INTO runways (location_id, name, length_m, length_ft, width_m, width_ft, surface, heading_deg, ils_frequency, has_lighting, touchdown_start_lat, touchdown_start_lon, touchdown_end_lat, touchdown_end_lon, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                args.location_id,
                args.name,
                args.length_m,
                length_ft,
                args.width_m,
                int(round(args.width_m * 3.28084)) if args.width_m else None,
                args.surface,
                args.heading_deg,
                args.ils_frequency,
                1 if args.has_lighting else 0,
                args.touchdown_start_lat,
                args.touchdown_start_lon,
                args.touchdown_end_lat,
                args.touchdown_end_lon,
                args.notes,
            )
        )
        db.conn.commit()
        print(f"Inserted runway id {cur.lastrowid}")


def cmd_edit_runway(args: argparse.Namespace):
    with MarkersDatabase() as db:
        cur = db.conn.cursor()
        cur.execute("SELECT * FROM runways WHERE id=?", (args.runway_id,))
        row = cur.fetchone()
        if not row:
            print("Runway not found")
            return
        data = dict(row)
        content = json.dumps(data, indent=2, ensure_ascii=False)
        edited = open_in_editor(content)
        if edited is None:
            print("Edit cancelled")
            return
        try:
            new_data = json.loads(edited)
        except Exception as e:
            print(f"Failed to parse JSON: {e}")
            return
        # Build update
        set_clause = []
        params = []
        for k, v in new_data.items():
            if k == 'id' or k == 'location_id':
                continue
            set_clause.append(f"{k}=?")
            params.append(v)
        params.append(args.runway_id)
        cur.execute(f"UPDATE runways SET {', '.join(set_clause)} WHERE id=?", params)
        db.conn.commit()
        print("Updated runway")


def cmd_delete_runway(args: argparse.Namespace):
    with MarkersDatabase() as db:
        cur = db.conn.cursor()
        cur.execute("DELETE FROM runways WHERE id=?", (args.runway_id,))
        db.conn.commit()
        print("Deleted" if cur.rowcount > 0 else "Not found")


# AI integration (optional)
def cmd_ai_suggest_runway(args: argparse.Namespace):
    if not OPENAI_AVAILABLE:
        print("OpenAI package not available. Install with `pip install openai` to use AI suggestions.")
        return
    api_key = os.environ.get('OPENAI_API_KEY')
    if not api_key:
        print("OPENAI_API_KEY not set. Export your key to use AI suggestions.")
        return

    openai.api_key = api_key

    with MarkersDatabase() as db:
        loc = db.get_location(args.location_id)
        if not loc:
            print("Location not found")
            return
        prompt = (
            f"You are an assistant that suggests runway data for airports.\n"
            f"Here is the airport data: {json.dumps(loc.to_dict(), ensure_ascii=False)}\n"
            "Suggest up to 2 runway entries as a JSON array. Each runway should contain: name, length_m, width_m, heading, surface, ils (true/false), notes. Only return valid JSON array."
        )
        try:
            resp = openai.Completion.create(
                model=getattr(args, 'model', 'text-davinci-003'),
                prompt=prompt,
                max_tokens=400,
                temperature=0.3,
            )
            text = resp.choices[0].text.strip()
            # Try to extract JSON from the response
            start = text.find('[')
            end = text.rfind(']')
            if start == -1 or end == -1:
                print("No JSON found in model response:")
                print(text)
                return
            json_text = text[start:end+1]
            runways = json.loads(json_text)
        except Exception as e:
            print(f"AI request failed: {e}")
            return

        print(json.dumps(runways, indent=2, ensure_ascii=False))
        if not confirm("Apply these runways to the location (will insert into runways table)?"):
            print("Aborted")
            return
        cur = db.conn.cursor()
        for r in runways:
            length_m = r.get('length_m')
            length_ft = int(round(length_m * 3.28084)) if length_m else None
            cur.execute(
                "INSERT INTO runways (location_id, name, length_m, length_ft, width_m, surface, heading_deg, ils_frequency, has_lighting, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    args.location_id,
                    r.get('name'),
                    length_m,
                    length_ft,
                    r.get('width_m'),
                    r.get('surface'),
                    r.get('heading'),
                    r.get('ils') or None,
                    1 if r.get('has_lighting') else 0,
                    r.get('notes') or None,
                )
            )
        db.conn.commit()
        print("Inserted suggested runways")


def cmd_export(args: argparse.Namespace):
    with MarkersDatabase() as db:
        db.export_to_json(Path(args.filepath))
        print(f"Exported to {args.filepath}")


def cmd_import(args: argparse.Namespace):
    with MarkersDatabase() as db:
        count = db.import_from_json(Path(args.filepath))
        print(f"Imported {count} locations")


def main(argv: Optional[List[str]] = None):
    p = argparse.ArgumentParser(prog='cli_db_editor')
    sub = p.add_subparsers(dest='cmd')

    sp = sub.add_parser('list')
    sp.add_argument('--type', help='Filter by marker_type')
    sp.set_defaults(func=cmd_list)

    sp = sub.add_parser('show')
    sp.add_argument('id', type=int)
    sp.set_defaults(func=cmd_show)

    sp = sub.add_parser('search')
    sp.add_argument('query')
    sp.set_defaults(func=cmd_search)

    sp = sub.add_parser('edit')
    sp.add_argument('id', type=int)
    sp.set_defaults(func=cmd_edit)

    sp = sub.add_parser('set')
    sp.add_argument('id', type=int)
    sp.add_argument('--name')
    sp.add_argument('--icao')
    sp.add_argument('--iata')
    sp.add_argument('--lat', type=float)
    sp.add_argument('--lon', type=float)
    sp.set_defaults(func=cmd_set)

    sp = sub.add_parser('delete')
    sp.add_argument('id', type=int)
    sp.set_defaults(func=cmd_delete)

    # Runways
    sp = sub.add_parser('list-runways')
    sp.add_argument('location_id', type=int)
    sp.set_defaults(func=cmd_list_runways)

    sp = sub.add_parser('add-runway')
    sp.add_argument('location_id', type=int)
    sp.add_argument('--name', required=True)
    sp.add_argument('--length-m', type=float)
    sp.add_argument('--width-m', type=float)
    sp.add_argument('--surface', default='asphalt')
    sp.add_argument('--heading-deg', type=float)
    sp.add_argument('--ils-frequency')
    sp.add_argument('--has-lighting', action='store_true')
    sp.add_argument('--touchdown-start-lat', type=float)
    sp.add_argument('--touchdown-start-lon', type=float)
    sp.add_argument('--touchdown-end-lat', type=float)
    sp.add_argument('--touchdown-end-lon', type=float)
    sp.add_argument('--notes')
    sp.set_defaults(func=cmd_add_runway)

    sp = sub.add_parser('edit-runway')
    sp.add_argument('runway_id', type=int)
    sp.set_defaults(func=cmd_edit_runway)

    sp = sub.add_parser('delete-runway')
    sp.add_argument('runway_id', type=int)
    sp.set_defaults(func=cmd_delete_runway)

    sp = sub.add_parser('ai-suggest-runway')
    sp.add_argument('location_id', type=int)
    sp.add_argument('--model', default='text-davinci-003')
    sp.set_defaults(func=cmd_ai_suggest_runway)

    sp = sub.add_parser('export')
    sp.add_argument('filepath')
    sp.set_defaults(func=cmd_export)

    sp = sub.add_parser('import')
    sp.add_argument('filepath')
    sp.set_defaults(func=cmd_import)

    args = p.parse_args(argv)
    if not hasattr(args, 'func'):
        p.print_help()
        return
    args.func(args)


if __name__ == '__main__':
    main()
