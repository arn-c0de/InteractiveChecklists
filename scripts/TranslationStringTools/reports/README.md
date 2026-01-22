# Translation Reports

This directory contains automatically generated translation reports.

## File format

Reports are generated with timestamps:
- `translation_report_YYYYMMDD_HHMMSS.md` - Markdown format
- `translation_report_YYYYMMDD_HHMMSS.json` - JSON format

## Contents

Each report contains:
- **App version** — version number from `app/build.gradle.kts`
- **Generation timestamp**
- **Number of source strings** (from `values/strings.xml`)
- **Coverage statistics** per language
- **Detailed lists** of missing / obsolete / empty strings

## Usage in CI/CD

These reports are used for:
- Pull request reviews
- Release documentation
- Translation tracking
- Team communication

## Cleanup

Old reports can be removed manually. The tool does not overwrite existing files.
