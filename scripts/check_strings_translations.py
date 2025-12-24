#!/usr/bin/env python3
"""Check Android string resource translations against an English reference.

Usage:
  python scripts/check_strings_translations.py [--res-dir path/to/res] [--ref values] [--json]

Defaults:
  --res-dir: app/src/main/res
  --ref: values   (i.e. app/src/main/res/values/strings.xml)

Reports:
  - Missing keys (present in reference but absent in target language)
  - Empty translations (target key present but empty or equals reference)

Outputs a readable CLI report and exits with code 1 when problems are found.
"""

from __future__ import annotations
import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from typing import Dict, List, Tuple


def parse_strings_xml(path: str) -> Dict[str, str]:
    """Parse strings and string-arrays from an Android strings.xml file.

    Returns a dict mapping resource name -> concatenated value (simple representation).
    This is intentionally simple: it extracts <string name=...>text</string>
    and <string-array name=...> items joined by "||".
    """
    if not os.path.exists(path):
        raise FileNotFoundError(path)

    tree = ET.parse(path)
    root = tree.getroot()
    out: Dict[str, str] = {}

    for child in root:
        tag = child.tag.split('}')[-1]
        if tag == 'string':
            name = child.attrib.get('name')
            if not name:
                continue
            text = (child.text or '').strip()
            out[name] = text
        elif tag == 'string-array':
            name = child.attrib.get('name')
            if not name:
                continue
            items = [ (item.text or '').strip() for item in child.findall('item') ]
            out[name] = '||'.join(items)
        # ignore plurals and other tags for now
    return out


def find_values_dirs(res_root: str) -> List[str]:
    """Return list of values directories (absolute paths) in res_root.
    e.g., values, values-de, values-es-rUS, etc.
    """
    dirs = []
    if not os.path.isdir(res_root):
        raise FileNotFoundError(res_root)
    for name in os.listdir(res_root):
        path = os.path.join(res_root, name)
        if os.path.isdir(path) and name.startswith('values'):
            dirs.append(path)
    return sorted(dirs)


def compare(reference: Dict[str, str], target: Dict[str, str]) -> Tuple[List[str], List[str]]:
    """Return (missing_keys, empty_or_untranslated_keys) between reference and target.

    """
    missing = [k for k in reference.keys() if k not in target]
    empty = []
    for k, ref_val in reference.items():
        if k in target:
            tval = (target[k] or '').strip()
            if tval == '' or tval == ref_val:
                empty.append(k)
    return missing, empty


def detect_locale_from_dir(dir_name: str) -> str:
    # dir_name is full path; take last component
    last = os.path.basename(dir_name)
    return last  # e.g., values-de


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description='Check Android translations against reference strings.xml')
    p.add_argument('--res-dir', default='app/src/main/res', help='Path to res/ folder')
    p.add_argument('--ref', default='values', help='Reference values directory name (default: values)')
    p.add_argument('--ref-file', default='strings.xml', help='Strings file name (default: strings.xml)')
    p.add_argument('--json', action='store_true', help='Output JSON report')
    p.add_argument('--show-empty-only', action='store_true', help='Only show keys that are empty/untranslated, not missing ones')
    args = p.parse_args(argv)

    res_dir = args.res_dir
    ref_dir = os.path.join(res_dir, args.ref)
    ref_path = os.path.join(ref_dir, args.ref_file)

    if not os.path.exists(ref_path):
        print(f'ERROR: Reference file not found: {ref_path}', file=sys.stderr)
        return 2

    try:
        reference = parse_strings_xml(ref_path)
    except Exception as e:
        print(f'ERROR: Failed parsing reference: {e}', file=sys.stderr)
        return 2

    values_dirs = find_values_dirs(res_dir)

    results = {}
    has_problems = False

    for d in values_dirs:
        if os.path.abspath(d) == os.path.abspath(ref_dir):
            continue
        target_path = os.path.join(d, args.ref_file)
        if not os.path.exists(target_path):
            # treat as completely missing
            missing = list(reference.keys())
            empty = []
            results[d] = {'missing': missing, 'empty': empty}
            has_problems = True
            continue

        try:
            target = parse_strings_xml(target_path)
        except Exception as e:
            print(f'WARNING: failed to parse {target_path}: {e}', file=sys.stderr)
            continue

        missing, empty = compare(reference, target)
        if missing or empty:
            has_problems = True
        results[d] = {'missing': missing, 'empty': empty}

    # Output
    if args.json:
        print(json.dumps({'reference': ref_path, 'results': results}, ensure_ascii=False, indent=2))
        return 1 if has_problems else 0

    # Pretty print
    print('Reference: ' + ref_path)
    print('---')
    total_missing = 0
    total_empty = 0
    for d, info in results.items():
        locale = detect_locale_from_dir(d)
        missing = info['missing']
        empty = info['empty']
        if args.show_empty_only and not empty:
            continue
        if not missing and not empty:
            print(f'✅ {locale}: OK')
            continue
        print(f'⚠️  {locale}:')
        if missing:
            print(f'  - Missing ({len(missing)}):')
            for k in missing:
                print(f'    • {k}')
            total_missing += len(missing)
        if empty:
            print(f'  - Empty/Untranslated ({len(empty)}):')
            for k in empty:
                print(f'    • {k}')
            total_empty += len(empty)
        print()

    print('---')
    print(f'Summary: {len(results)} languages checked. Missing keys: {total_missing}, Empty/untranslated: {total_empty}')

    return 1 if has_problems else 0


if __name__ == '__main__':
    sys.exit(main())
