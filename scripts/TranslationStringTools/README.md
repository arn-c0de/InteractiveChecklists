# Android Translation Checker

An automated tool for analyzing and verifying Android string translations.

## Features

- **Auto-detection**: Scans `values` and `values-XX` folders automatically
- **Comprehensive Analysis**:
  - Missing translations (present in source but missing in translation)
  - Obsolete strings (present in translation but not in source)
  - Empty / placeholder values
  - Optional: Identical strings (likely untranslated)
- **Modular Architecture**: Cleanly structured and extensible
- **Multiple Output Formats**:
  - Console (color-coded for readability)
  - Markdown (for GitHub Issues/PRs)
  - JSON (for CI/CD integration)
- **CI/CD Ready**: Threshold-based exit codes

## Installation

No external dependencies required — the tool uses only Python's standard library.

```bash
python --version  # Python 3.7+
```

## Usage

### Basic Usage

```bash
# From the TranslationStringTools directory
python check_translations.py
```

The tool auto-detects the path to the `res` directory based on project layout.

### Options

```bash
# Check only a specific language
python check_translations.py --lang de

# Generate Markdown report
python check_translations.py --format markdown

# JSON export
python check_translations.py --format json --output report.json

# Show identical strings (likely untranslated)
python check_translations.py --show-identical

# CI mode with threshold
python check_translations.py --ci --threshold 95

# Disable colors (for logs)
python check_translations.py --no-color

# Custom res path
python check_translations.py --res-path /path/to/res
```

### All Options

```
--res-path PATH       Path to the res directory (default: auto-detect)
--lang LANG           Check a specific language (e.g., de, es, zh-rCN)
--format FORMAT       Output format: console, markdown, json
--output FILE         Output file (default: stdout for console)
--ci                  CI mode: exit 1 if threshold not met
--threshold PERCENT   Coverage threshold for CI (default: 95.0)
--no-color            Disable colored output
--show-identical      Mark strings identical to English
```

## Output Formats

### Console (default)

Color-coded console output:
- ✅ Green: Coverage ≥ 95%
- ⚠️ Yellow: Coverage 80–95%
- ❌ Red: Coverage < 80%

### Markdown

Generates a `translation_report.md` file including:
- Summary table
- Per-language detailed lists
- Missing / obsolete / empty strings

Ideal for:
- GitHub Issues
- Pull request descriptions
- Documentation

### JSON

Generates a `translation_report.json` file with structured data:

```json
{
  "metadata": {
    "generated": "2026-01-22T14:10:51",
    "total_source_strings": 1192,
    "total_languages": 3,
    "average_coverage": 95.2
  },
  "languages": {
    "de": {
      "coverage": 95.5,
      "missing": [...],
      "obsolete": [...],
      "empty": [...]
    }
  }
}
```

Great for:
- CI/CD integration
- Automated processing
- Dashboarding

## CI/CD Integration

### GitHub Actions

Example workflow (see `.github/workflows/translation-check.yml`):

```yaml
name: Translation Coverage Check

on: [pull_request, push]

jobs:
  check-translations:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Check translations
        run: |
          cd scripts/TranslationStringTools
          python check_translations.py --ci --threshold 90

      - name: Generate report (on failure)
        if: failure()
        run: |
          cd scripts/TranslationStringTools
          python check_translations.py --format markdown --output $GITHUB_STEP_SUMMARY
```

### Local pre-commit hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

cd scripts/TranslationStringTools
python check_translations.py --ci --threshold 90 || {
    echo "❌ Translation coverage below 90%!"
    echo "Run: python check_translations.py"
    exit 1
}
```

## Project Structure

```
TranslationStringTools/
├── check_translations.py     # Main CLI tool
├── modules/
│   ├── __init__.py
│   ├── scanner.py           # Automatic directory scanner
│   ├── parser.py            # XML parser for strings.xml
│   ├── analyzer.py          # Comparison and analysis logic
│   └── reporters.py         # Report generators
├── README.md                # This file
└── .github/
    └── workflows/
        └── translation-check.yml  # Example CI workflow
```

## Example Output

```
Scanning: C:\...\app\src\main\res
Source: values\strings.xml
Found 3 translation file(s)

================================================================================
  Android Translation Analysis Report
  Generated: 2026-01-22 14:10:41
  Source strings: 1192
================================================================================

Summary:
  Total languages: 3
  Average coverage: 95.2%

────────────────────────────────────────────────────────────────────────────────
Language: DE
  File: ...\values-de\strings.xml
  Coverage: 95.5%
  Strings: 1140 / 1192

  ❌ Missing (52 strings):
     - auto_highlight_all
     - auto_highlight_by_category
     ...
```

## Extensions

The modular design makes it easy to extend:

- **New output formats**: Add new reporters in `reporters.py`
- **Additional validations**: Extend `analyzer.py`
- **Automatic fixes**: Create a skeleton-file generator
- **Integration with translation services**: Add API integrations for automated translations

## Troubleshooting

### "No values/strings.xml found"

Ensure that:
- You are in the correct directory
- The path to `app/src/main/res` is correct
- Use `--res-path` to provide the path manually

### Unicode issues on Windows

The tool enables UTF-8 encoding on Windows automatically. If you run into issues:
- Use `--no-color` for better compatibility
- Redirect output to a file: `python check_translations.py > report.txt`

### XML parse errors

Check that:
- strings.xml files are valid XML
- No broken tags are present
- Files use UTF-8 encoding

## License

Internal tool for the InteractiveChecklists project.

## Support

If you need help:
1. Check the documentation above
2. Run `python check_translations.py --help`
3. Open an issue in the project repository
