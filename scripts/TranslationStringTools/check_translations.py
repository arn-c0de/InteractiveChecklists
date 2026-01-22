#!/usr/bin/env python3
"""
Android Strings Translation Checker
Automatically detects and analyzes all string translations in an Android project.
"""

import argparse
import sys
import io
from pathlib import Path
from datetime import datetime
from modules.scanner import ResScanner
from modules.analyzer import TranslationAnalyzer
from modules.reporters import ConsoleReporter, MarkdownReporter, JsonReporter
from modules.version_extractor import VersionExtractor

# Fix Windows console encoding for Unicode support
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(
        description='Check Android string translations for missing, obsolete, and incomplete entries',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python check_translations.py
  python check_translations.py --lang de --format markdown
  python check_translations.py --ci --threshold 95
  python check_translations.py --res-path ../app/src/main/res
        """
    )

    parser.add_argument(
        '--res-path',
        type=str,
        default=None,
        help='Path to res directory (default: auto-detect from script location)'
    )

    parser.add_argument(
        '--lang',
        type=str,
        default=None,
        help='Check specific language only (e.g., de, es, zh-rCN)'
    )

    parser.add_argument(
        '--format',
        choices=['console', 'markdown', 'json'],
        default='console',
        help='Output format (default: console)'
    )

    parser.add_argument(
        '--output',
        type=str,
        default=None,
        help='Output file path (default: stdout for console, auto-generated for others)'
    )

    parser.add_argument(
        '--ci',
        action='store_true',
        help='CI mode: exit with code 1 if threshold not met'
    )

    parser.add_argument(
        '--threshold',
        type=float,
        default=95.0,
        help='Coverage threshold percentage for CI mode (default: 95.0)'
    )

    parser.add_argument(
        '--no-color',
        action='store_true',
        help='Disable colored output'
    )

    parser.add_argument(
        '--show-identical',
        action='store_true',
        help='Flag strings identical to English (likely untranslated)'
    )

    args = parser.parse_args()

    # Determine res path
    if args.res_path:
        res_path = Path(args.res_path).resolve()
    else:
        # Auto-detect: script is in scripts/TranslationStringTools, res is in app/src/main/res
        script_dir = Path(__file__).parent
        res_path = script_dir.parent.parent / 'app' / 'src' / 'main' / 'res'

    if not res_path.exists():
        print(f"Error: res directory not found at {res_path}", file=sys.stderr)
        print("Use --res-path to specify the correct path", file=sys.stderr)
        sys.exit(1)

    # Determine project root for relative paths
    # res -> main -> src -> app -> project_root
    app_dir = res_path.parent.parent.parent
    project_root = app_dir.parent

    # Scan for values directories
    try:
        res_path_relative = res_path.relative_to(project_root)
        print(f"Scanning: {res_path_relative}")
    except ValueError:
        print(f"Scanning: {res_path}")

    scanner = ResScanner(res_path)
    source_file, translation_files = scanner.scan()

    if not source_file:
        print(f"Error: No values/strings.xml found", file=sys.stderr)
        sys.exit(1)

    try:
        source_relative = source_file.relative_to(project_root)
        print(f"Source: {source_relative}")
    except ValueError:
        print(f"Source: {source_file.relative_to(res_path)}")

    print(f"Found {len(translation_files)} translation file(s)\n")

    # Filter by language if specified
    if args.lang:
        lang_code = args.lang
        translation_files = {
            lang: path for lang, path in translation_files.items()
            if lang == lang_code
        }
        if not translation_files:
            print(f"Error: Language '{lang_code}' not found", file=sys.stderr)
            print(f"Available languages: {', '.join(scanner.scan()[1].keys())}", file=sys.stderr)
            sys.exit(1)

    # Extract version from build.gradle.kts
    app_version = VersionExtractor.extract_version(project_root)

    # Analyze translations
    analyzer = TranslationAnalyzer(source_file, args.show_identical, project_root)
    results = analyzer.analyze_all(translation_files)

    # Generate report
    use_color = not args.no_color and args.format == 'console'

    if args.format == 'console':
        reporter = ConsoleReporter(use_color)
        output = reporter.generate(results, analyzer.source_strings, app_version)
        if args.output:
            Path(args.output).write_text(output, encoding='utf-8')
        else:
            print(output)

    elif args.format == 'markdown':
        reporter = MarkdownReporter()
        output = reporter.generate(results, analyzer.source_strings, app_version)
        if args.output:
            output_path = Path(args.output)
        else:
            # Create reports directory if it doesn't exist
            reports_dir = Path(__file__).parent / 'reports'
            reports_dir.mkdir(exist_ok=True)

            # Generate filename with timestamp
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            output_path = reports_dir / f'translation_report_{timestamp}.md'

        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(output, encoding='utf-8')

        # Show relative path from project root
        try:
            output_relative = output_path.relative_to(project_root)
            print(f"Report saved to: {output_relative}")
        except ValueError:
            print(f"Report saved to: {output_path}")

    elif args.format == 'json':
        reporter = JsonReporter()
        output = reporter.generate(results, analyzer.source_strings, app_version)
        if args.output:
            output_path = Path(args.output)
        else:
            # Create reports directory if it doesn't exist
            reports_dir = Path(__file__).parent / 'reports'
            reports_dir.mkdir(exist_ok=True)

            # Generate filename with timestamp
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            output_path = reports_dir / f'translation_report_{timestamp}.json'

        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(output, encoding='utf-8')

        # Show relative path from project root
        try:
            output_relative = output_path.relative_to(project_root)
            print(f"Report saved to: {output_relative}")
        except ValueError:
            print(f"Report saved to: {output_path}")

    # CI mode: check threshold
    if args.ci:
        failed_languages = []
        for lang, result in results.items():
            if result['coverage'] < args.threshold:
                failed_languages.append(f"{lang} ({result['coverage']:.1f}%)")

        if failed_languages:
            print(f"\n❌ CI Check Failed: Languages below {args.threshold}% threshold:", file=sys.stderr)
            for lang in failed_languages:
                print(f"  - {lang}", file=sys.stderr)
            sys.exit(1)
        else:
            print(f"\n✅ CI Check Passed: All languages meet {args.threshold}% threshold")
            sys.exit(0)


if __name__ == '__main__':
    main()
