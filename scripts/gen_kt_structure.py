#!/usr/bin/env python3
"""
Generate a JSON or tree of Kotlin files and a simplified structure (package, imports, classes, functions, properties).
Usage:
  python scripts/gen_kt_structure.py --root . --format json --out kt-structure.json
  python scripts/gen_kt_structure.py --root app/src/main/java --format tree

This script doesn't fully parse Kotlin grammar; it's a heuristic-based extractor suitable for quick inspections and for making the codebase machine-readable for AI assistance.
"""

import os
import re
import json
import argparse
from pathlib import Path

def extract_structure_from_file(path: Path):
    text = path.read_text(encoding='utf-8')

    # package
    package = None
    m = re.search(r'^\s*package\s+([\w\.]+)', text, re.MULTILINE)
    if m:
        package = m.group(1)

    # imports
    imports = re.findall(r'^\s*import\s+([\w\.*]+)', text, re.MULTILINE)

    # Regex to find top-level declarations
    # class/object/interface declarations
    classes = []
    class_pattern = re.compile(r'^\s*(?P<prefix>(?:(?:public|private|protected|internal|open|abstract|sealed|data|annotation|expect|actual|final|suspend|inline|tailrec|operator|infix)\s+)*)(?P<type>class|object|interface)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)', re.MULTILINE)
    for cm in class_pattern.finditer(text):
        classes.append({'name': cm.group('name'), 'type': cm.group('type'), 'line': text[:cm.start()].count('\n') + 1, 'methods': [], 'properties': []})

    # Find top-level functions and properties and map them to nearest preceding class (simple approach)
    # We'll do a simple brace-aware scan to associate functions with classes when inside braces.
    lines = text.splitlines()
    stack = []  # (scope_type, name, brace_count)
    # Build initial class ranges from text positions
    class_indices = []
    for cm in class_pattern.finditer(text):
        start = cm.start()
        line_no = text[:start].count('\n') + 1
        class_indices.append((line_no, cm.group('type'), cm.group('name')))

    # Alternative: scan line-by-line tracking brace depth to detect when inside a class.
    brace_depth = 0
    current_class = None
    class_lookup = { (c['type'], c['name']): c for c in classes }
    decl_fun_pattern = re.compile(r'^\s*(?:(?:public|private|protected|internal|open|abstract|suspend|inline|tailrec|operator|infix|external|expect|actual)\s+)*fun\s+([A-Za-z_][A-Za-z0-9_]*)')
    decl_prop_pattern = re.compile(r'^\s*(?:(?:public|private|protected|internal|lateinit|const|open|override|val|var|vararg|expect|actual)\s+)*(val|var)\s+([A-Za-z_][A-Za-z0-9_]*)')
    class_header_pattern = re.compile(r'^\s*(?:(?:public|private|protected|internal|open|abstract|sealed|data|annotation|expect|actual|final|suspend|inline|tailrec|operator|infix)\s+)*(class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)')

    # Track a stack of class names with brace counts
    class_stack = []

    for idx, line in enumerate(lines, start=1):
        # opening braces
        opens = line.count('{')
        closes = line.count('}')

        ch = class_header_pattern.search(line)
        if ch:
            # Entering a new class/object/interface declaration
            ctype = ch.group(1)
            cname = ch.group(2)
            class_stack.append({'type': ctype, 'name': cname, 'depth_at_start': brace_depth + opens - closes})

        # detect function
        fm = decl_fun_pattern.search(line)
        if fm:
            fname = fm.group(1)
            if class_stack:
                class_key = (class_stack[-1]['type'], class_stack[-1]['name'])
                # add to methods
                cls = class_lookup.get(class_key)
                if cls is not None:
                    cls['methods'].append({'name': fname, 'line': idx})
            else:
                # top-level function
                pass

        # detect property
        pm = decl_prop_pattern.search(line)
        if pm:
            propname = pm.group(2)
            if class_stack:
                cls = class_lookup.get((class_stack[-1]['type'], class_stack[-1]['name']))
                if cls is not None:
                    cls['properties'].append({'name': propname, 'line': idx})

        brace_depth += opens - closes

        # If we closed beyond the start depth of the current class, pop
        while class_stack and brace_depth < class_stack[-1]['depth_at_start']:
            class_stack.pop()

    # Top-level functions
    top_level_functions = []
    for m in decl_fun_pattern.finditer(text):
        # detect if function is top-level by counting braces before it and after; for simplicity, we'll count if it appears before any class header or if not inside a class stack
        line_no = text[:m.start()].count('\n') + 1
        # simple check: is it inside a class? We'll compute the number of open braces between file start and m.start()
        prefix = text[:m.start()]
        depth_before = prefix.count('{') - prefix.count('}')
        if depth_before <= 0:
            top_level_functions.append({'name': m.group(1), 'line': line_no})

    # Top-level properties
    top_level_properties = []
    for m in decl_prop_pattern.finditer(text):
        line_no = text[:m.start()].count('\n') + 1
        prefix = text[:m.start()]
        depth_before = prefix.count('{') - prefix.count('}')
        if depth_before <= 0:
            top_level_properties.append({'name': m.group(2), 'line': line_no})

    # For quick AI consumption, let's compute a short summary line for the file
    summary_parts = []
    if package:
        summary_parts.append(f'pkg:{package}')
    if classes:
        summary_parts.append('classes:' + ','.join(c['name'] for c in classes[:5]))
    if top_level_functions:
        summary_parts.append('functions:' + ','.join(f['name'] for f in top_level_functions[:5]))

    summary = '; '.join(summary_parts)

    return {
        'path': str(path),
        'relpath': str(path),
        'package': package,
        'imports': imports,
        'classes': classes,
        'top_level_functions': top_level_functions,
        'top_level_properties': top_level_properties,
        'summary': summary,
    }


def find_kt_files(root: Path):
    for p in root.rglob('*.kt'):
        yield p


def tree_output(entries):
    # Build a directory tree of files
    tree = {}
    for e in entries:
        rel = Path(e['path']).as_posix()
        # use the path from root
        parts = rel.split('/')
        node = tree
        for p in parts[:-1]:
            node = node.setdefault(p, {})
        node.setdefault('files', []).append(parts[-1])

    def render_node(node, indent=0):
        s = ''
        files = node.get('files', [])
        for f in files:
            s += '  ' * indent + '- ' + f + '\n'
        for k, v in sorted(node.items()):
            if k == 'files':
                continue
            s += '  ' * indent + k + '/\n'
            s += render_node(v, indent + 1)
        return s

    return render_node(tree)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--root', '-r', default='.', help='Root directory to search for .kt files')
    p.add_argument('--format', '-f', choices=['json', 'tree'], default='json', help='Output format')
    p.add_argument('--out', '-o', help='Output file (default prints to stdout)')
    args = p.parse_args()

    root = Path(args.root).resolve()

    kt_entries = []
    for f in find_kt_files(root):
        try:
            kt_entries.append(extract_structure_from_file(f))
        except Exception as exc:
            print(f'WARN: failed to parse {f}: {exc}')

    if args.format == 'json':
        outtxt = json.dumps(kt_entries, indent=2, ensure_ascii=False)
    else:
        outtxt = tree_output(kt_entries)

    if args.out:
        Path(args.out).write_text(outtxt, encoding='utf-8')
        print('Wrote', args.out)
    else:
        print(outtxt)

if __name__ == '__main__':
    main()
