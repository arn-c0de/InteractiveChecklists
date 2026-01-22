# Quick Start Guide

## 1. First run (30 seconds)

```bash
cd scripts/TranslationStringTools
python check_translations.py
```

That's it. The tool will show:
- Detected languages (auto-detected)
- Coverage percentage per language
- Missing translations
- Obsolete strings (present in translation but not in source)
- Empty values

## 2. Common commands

```bash
# Check only German
python check_translations.py --lang de

# Generate Markdown report for GitHub
python check_translations.py --format markdown

# JSON export for automation
python check_translations.py --format json
```

## 3. For CI/CD

```bash
# Exit with code 1 when coverage is below 90%
python check_translations.py --ci --threshold 90
```

## 4. Understanding the report example

```
Language: DE
  Coverage: 95.5%              ← Percentage of translated strings
  Strings: 1140 / 1192         ← 1140 translated out of 1192 total

  Missing (52 strings):        ← Missing in the German translation
     - auto_highlight_all
     - category_helicopter
     ...

  Empty/Placeholder (1):       ← Empty or placeholder values
     - common_ellipsis

  Obsolete (0):                ← Present in DE but not in EN (can be removed)
```

## 5. Recommended workflow

### While developing new features

1. Add new strings to `values/strings.xml`
2. Run the check: `python check_translations.py`
3. Inspect missing strings per language
4. Translate or file an issue for translators

### Before a release

```bash
# Check with a high threshold
python check_translations.py --ci --threshold 95

# Generate a report for release notes
python check_translations.py --format markdown --output release-translations.md
```

### In pull requests

1. GitHub Actions runs automatically (see `.github/workflows/translation-check.yml`)
2. A PR comment will be posted if coverage is low
3. Review and fix missing translations

## 6. Adding missing strings

When the tool reports missing strings:

1. Open the corresponding `values-XX/strings.xml` file
2. Add the string, for example:

```xml
<string name="auto_highlight_all">Highlight all automatically</string>
```

3. Run the check again to see progress

## 7. Removing obsolete strings

When the tool reports obsolete strings:

1. These strings exist in the translation but not in the English source
2. They were likely removed from `values/strings.xml`
3. Remove them from the translation file to keep it clean

## 8. Tips

- Find identical strings: `python check_translations.py --show-identical`
  - Finds strings that are identical to English (likely untranslated)

- Disable colors (for logs): `python check_translations.py --no-color`

- Show help: `python check_translations.py --help`

## 9. Integrating into your setup

### Pre-commit hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash
cd scripts/TranslationStringTools
python check_translations.py --ci --threshold 90 || {
    echo ""
    echo "Tip: Run to see detailed output:"
    echo "   cd scripts/TranslationStringTools && python check_translations.py"
    exit 1
}
```

Make it executable:
```bash
chmod +x .git/hooks/pre-commit
```

### VS Code task

Add to `.vscode/tasks.json`:

```json
{
    "label": "Check Translations",
    "type": "shell",
    "command": "cd scripts/TranslationStringTools && python check_translations.py",
    "problemMatcher": [],
    "group": {
        "kind": "test",
        "isDefault": false
    }
}
```

Then: `Ctrl+Shift+P` → "Run Task" → "Check Translations"


You're ready to use the Translation Checker tool.

See [README.md](README.md) for more details.
