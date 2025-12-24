#!/usr/bin/env python3
"""Fill missing or empty translations in values-* folders by copying from English reference.

Usage:
  python scripts/add_missing_translations.py [--res-dir path/to/res] [--ref values]

This will:
 - For locales missing `strings.xml`: copy the reference file to that locale (so all keys are present).
 - For existing locale files: add any missing <string> or <string-array> elements from the reference and
   replace empty/untranslated values with the reference text.

Backups: the original target file will be saved as `strings.xml.bak` before modification.
"""
from __future__ import annotations
import argparse
import os
import shutil
import sys
import xml.etree.ElementTree as ET
from copy import deepcopy


def collect_reference_elements(ref_path: str):
    tree = ET.parse(ref_path)
    root = tree.getroot()
    mapping = {}
    for child in root:
        tag = child.tag.split('}')[-1]
        if tag in ('string', 'string-array'):
            name = child.attrib.get('name')
            if name:
                mapping[name] = (tag, deepcopy(child))
    return tree, mapping


def ensure_dir(path: str):
    os.makedirs(path, exist_ok=True)


def backup_file(path: str):
    bak = path + '.bak'
    shutil.copy2(path, bak)
    return bak


def fill_target_from_ref(ref_map, ref_tree, target_path: str, ref_path: str):
    changed = False
    if not os.path.exists(target_path):
        # simply copy entire reference file
        ensure_dir(os.path.dirname(target_path))
        shutil.copy2(ref_path, target_path)
        return True, 'created by copying reference'

    tree = ET.parse(target_path)
    root = tree.getroot()

    # build name->element map in target
    target_map = {}
    for child in root:
        tag = child.tag.split('}')[-1]
        if tag in ('string', 'string-array'):
            name = child.attrib.get('name')
            if name:
                target_map[name] = (tag, child)

    # iterate reference map
    for name, (tag, ref_elem) in ref_map.items():
        if name not in target_map:
            # append a copy of ref_elem
            root.append(deepcopy(ref_elem))
            changed = True
        else:
            ttag, telem = target_map[name]
            if ttag != tag:
                # mismatch type; replace
                root.remove(telem)
                root.append(deepcopy(ref_elem))
                changed = True
            else:
                if tag == 'string':
                    tref = (ref_elem.text or '').strip()
                    tval = (telem.text or '').strip()
                    if tval == '' or tval == tref:
                        telem.text = ref_elem.text
                        changed = True
                elif tag == 'string-array':
                    # compare joined representation
                    ritems = [ (item.text or '').strip() for item in ref_elem.findall('item') ]
                    titems = [ (item.text or '').strip() for item in telem.findall('item') ]
                    if any(x == '' for x in titems) or titems == ritems:
                        # replace all items
                        # remove existing items
                        for item in list(telem.findall('item')):
                            telem.remove(item)
                        for item in ref_elem.findall('item'):
                            telem.append(deepcopy(item))
                        changed = True

    if changed:
        backup_file(target_path)
        tree.write(target_path, encoding='utf-8', xml_declaration=True)
        return True, 'updated'
    return False, 'no changes'


def find_values_dirs(res_root: str):
    dirs = []
    for name in os.listdir(res_root):
        path = os.path.join(res_root, name)
        if os.path.isdir(path) and name.startswith('values'):
            dirs.append(path)
    return sorted(dirs)


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--res-dir', default='app/src/main/res')
    p.add_argument('--ref', default='values')
    p.add_argument('--ref-file', default='strings.xml')
    args = p.parse_args(argv)

    res_dir = args.res_dir
    ref_dir = os.path.join(res_dir, args.ref)
    ref_path_local = os.path.join(ref_dir, args.ref_file)

    if not os.path.exists(ref_path_local):
        print(f'ERROR: Reference file not found: {ref_path_local}', file=sys.stderr)
        return 2

    ref_tree, ref_map = collect_reference_elements(ref_path_local)

    values_dirs = find_values_dirs(res_dir)

    summary = {}

    for d in values_dirs:
        if os.path.abspath(d) == os.path.abspath(ref_dir):
            continue
        target_path = os.path.join(d, args.ref_file)
        created_or_updated, reason = fill_target_from_ref(ref_map, ref_tree, target_path, ref_path_local)
        summary[d] = {'changed': created_or_updated, 'reason': reason}

    # print summary
    for d, info in summary.items():
        print(f'{d}: {info["reason"]}')

    return 0


if __name__ == '__main__':
    sys.exit(main())
