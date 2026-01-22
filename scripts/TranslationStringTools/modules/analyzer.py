"""
Translation analyzer - compares source and translation files.
Detects missing, obsolete, and incomplete translations.
"""

from pathlib import Path
from typing import Dict, List, Set, Optional
from .parser import StringsXmlParser


class TranslationAnalyzer:
    """Analyzes translations against source strings."""

    def __init__(self, source_file: Path, check_identical: bool = False, project_root: Optional[Path] = None):
        """
        Initialize analyzer.

        Args:
            source_file: Path to source strings.xml (usually values/strings.xml)
            check_identical: Flag strings identical to source (untranslated)
            project_root: Project root path for relative path calculation
        """
        self.source_file = Path(source_file)
        self.check_identical = check_identical
        self.project_root = Path(project_root) if project_root else self._find_project_root()
        self.parser = StringsXmlParser(self.source_file)
        self.source_strings = self.parser.parse()
        self.source_keys = set(self.source_strings.keys())

    def _find_project_root(self) -> Path:
        """
        Find project root (directory containing 'app' folder).

        Returns:
            Path to project root
        """
        # Start from source file and traverse up to find 'app' directory
        current = self.source_file.parent
        while current.parent != current:  # Not at filesystem root
            if (current / 'app').exists() and (current / 'app').is_dir():
                return current
            current = current.parent
        # Fallback to source file's parent
        return self.source_file.parent

    def _get_relative_path(self, file_path: Path) -> str:
        """
        Get path relative to project root.

        Args:
            file_path: Absolute file path

        Returns:
            Relative path from project root
        """
        try:
            return str(file_path.relative_to(self.project_root))
        except ValueError:
            # If file is not under project root, return as-is
            return str(file_path)

    def analyze(self, translation_file: Path) -> Dict:
        """
        Analyze a single translation file.

        Args:
            translation_file: Path to translation strings.xml

        Returns:
            Dictionary with analysis results:
            {
                'file': str (relative path),
                'file_abs': Path (absolute path),
                'total_source': int,
                'total_translation': int,
                'missing': List[str],
                'obsolete': List[str],
                'empty': List[str],
                'identical': List[str],  # if check_identical is True
                'coverage': float
            }
        """
        parser = StringsXmlParser(translation_file)
        translation_strings = parser.parse()
        translation_keys = set(translation_strings.keys())

        # Find missing keys (in source but not in translation)
        missing = sorted(self.source_keys - translation_keys)

        # Find obsolete keys (in translation but not in source)
        obsolete = sorted(translation_keys - self.source_keys)

        # Find empty or placeholder values
        empty = []
        for key in translation_keys:
            if key in self.source_keys:  # Only check keys that should exist
                value = translation_strings[key]
                if parser.is_empty_or_placeholder(value):
                    empty.append(key)
        empty.sort()

        # Find identical strings (likely untranslated)
        identical = []
        if self.check_identical:
            for key in translation_keys:
                if key in self.source_keys:
                    source_val = self.source_strings[key]
                    trans_val = translation_strings[key]
                    # Only check regular strings, not arrays/plurals
                    if not (source_val.startswith('[') or trans_val.startswith('[')):
                        if source_val == trans_val and source_val:
                            identical.append(key)
            identical.sort()

        # Calculate coverage
        # Coverage = (keys present and not empty) / total source keys * 100
        valid_translations = len(translation_keys & self.source_keys) - len(empty)
        coverage = (valid_translations / len(self.source_keys) * 100) if self.source_keys else 0.0

        result = {
            'file': self._get_relative_path(translation_file),
            'file_abs': translation_file,
            'total_source': len(self.source_keys),
            'total_translation': len(translation_keys),
            'missing': missing,
            'obsolete': obsolete,
            'empty': empty,
            'coverage': round(coverage, 2)
        }

        if self.check_identical:
            result['identical'] = identical

        return result

    def analyze_all(self, translation_files: Dict[str, Path]) -> Dict[str, Dict]:
        """
        Analyze all translation files.

        Args:
            translation_files: {language_code: path_to_strings.xml}

        Returns:
            {language_code: analysis_result}
        """
        results = {}
        for lang_code, file_path in translation_files.items():
            results[lang_code] = self.analyze(file_path)
        return results

    def get_summary(self, results: Dict[str, Dict]) -> Dict:
        """
        Generate summary statistics.

        Args:
            results: Results from analyze_all()

        Returns:
            Summary dictionary with aggregate stats
        """
        total_languages = len(results)
        avg_coverage = sum(r['coverage'] for r in results.values()) / total_languages if total_languages else 0

        languages_below_90 = [
            lang for lang, result in results.items()
            if result['coverage'] < 90.0
        ]

        return {
            'total_languages': total_languages,
            'average_coverage': round(avg_coverage, 2),
            'languages_below_90': languages_below_90,
            'total_source_strings': len(self.source_keys)
        }
