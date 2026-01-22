"""
Report generators for translation analysis results.
Supports console (with colors), markdown, and JSON formats.
"""

import json
from datetime import datetime
from typing import Dict


class ConsoleReporter:
    """Generates colored console output."""

    # ANSI color codes
    COLORS = {
        'reset': '\033[0m',
        'bold': '\033[1m',
        'red': '\033[91m',
        'green': '\033[92m',
        'yellow': '\033[93m',
        'blue': '\033[94m',
        'magenta': '\033[95m',
        'cyan': '\033[96m',
    }

    def __init__(self, use_color: bool = True):
        """
        Initialize reporter.

        Args:
            use_color: Enable ANSI color codes
        """
        self.use_color = use_color

    def _color(self, text: str, color: str, bold: bool = False) -> str:
        """Apply color to text."""
        if not self.use_color:
            return text

        prefix = self.COLORS.get(color, '')
        if bold:
            prefix = self.COLORS['bold'] + prefix
        return f"{prefix}{text}{self.COLORS['reset']}"

    def _coverage_color(self, coverage: float) -> str:
        """Get color based on coverage percentage."""
        if coverage >= 95:
            return 'green'
        elif coverage >= 80:
            return 'yellow'
        else:
            return 'red'

    def generate(self, results: Dict[str, Dict], source_strings: Dict[str, str], app_version: str = None) -> str:
        """
        Generate console report.

        Args:
            results: Analysis results from analyzer
            source_strings: Source strings dictionary
            app_version: App version from build.gradle.kts

        Returns:
            Formatted console output
        """
        lines = []

        # Header
        lines.append(self._color("=" * 80, 'cyan', bold=True))
        lines.append(self._color("  Android Translation Analysis Report", 'cyan', bold=True))
        if app_version:
            lines.append(self._color(f"  App Version: {app_version}", 'cyan', bold=True))
        lines.append(self._color(f"  Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", 'cyan'))
        lines.append(self._color(f"  Source strings: {len(source_strings)}", 'cyan'))
        lines.append(self._color("=" * 80, 'cyan', bold=True))
        lines.append("")

        # Summary
        total_languages = len(results)
        avg_coverage = sum(r['coverage'] for r in results.values()) / total_languages if total_languages else 0

        lines.append(self._color("Summary:", 'bold', bold=True))
        lines.append(f"  Total languages: {total_languages}")
        lines.append(f"  Average coverage: {self._color(f'{avg_coverage:.1f}%', self._coverage_color(avg_coverage))}")
        lines.append("")

        # Per-language details
        for lang_code in sorted(results.keys()):
            result = results[lang_code]
            coverage = result['coverage']

            lines.append(self._color("─" * 80, 'blue'))
            lines.append(self._color(f"Language: {lang_code.upper()}", 'bold', bold=True))
            lines.append(f"  File: {result['file']}")
            coverage_text = f"{coverage:.1f}%"
            lines.append(f"  Coverage: {self._color(coverage_text, self._coverage_color(coverage))}")
            lines.append(f"  Strings: {result['total_translation']} / {result['total_source']}")

            # Missing strings
            if result['missing']:
                lines.append("")
                lines.append(self._color(f"  ❌ Missing ({len(result['missing'])} strings):", 'red'))
                for key in result['missing'][:10]:  # Show first 10
                    lines.append(f"     - {key}")
                if len(result['missing']) > 10:
                    lines.append(f"     ... and {len(result['missing']) - 10} more")

            # Obsolete strings
            if result['obsolete']:
                lines.append("")
                lines.append(self._color(f"  🗑️  Obsolete ({len(result['obsolete'])} strings):", 'yellow'))
                for key in result['obsolete'][:10]:
                    lines.append(f"     - {key}")
                if len(result['obsolete']) > 10:
                    lines.append(f"     ... and {len(result['obsolete']) - 10} more")

            # Empty/placeholder strings
            if result['empty']:
                lines.append("")
                lines.append(self._color(f"  ⚠️  Empty/Placeholder ({len(result['empty'])} strings):", 'yellow'))
                for key in result['empty'][:10]:
                    lines.append(f"     - {key}")
                if len(result['empty']) > 10:
                    lines.append(f"     ... and {len(result['empty']) - 10} more")

            # Identical strings (if checked)
            if 'identical' in result and result['identical']:
                lines.append("")
                lines.append(self._color(f"  🔄 Identical to English ({len(result['identical'])} strings):", 'magenta'))
                for key in result['identical'][:10]:
                    lines.append(f"     - {key}")
                if len(result['identical']) > 10:
                    lines.append(f"     ... and {len(result['identical']) - 10} more")

            # Success message if complete
            if coverage >= 99.5 and not result['empty'] and not result['obsolete']:
                lines.append("")
                lines.append(self._color("  ✅ Translation is complete!", 'green'))

            lines.append("")

        lines.append(self._color("=" * 80, 'cyan', bold=True))

        return '\n'.join(lines)


class MarkdownReporter:
    """Generates markdown reports."""

    def generate(self, results: Dict[str, Dict], source_strings: Dict[str, str], app_version: str = None) -> str:
        """
        Generate markdown report.

        Args:
            results: Analysis results from analyzer
            source_strings: Source strings dictionary
            app_version: App version from build.gradle.kts

        Returns:
            Markdown formatted report
        """
        lines = []

        # Header
        lines.append("# Android Translation Analysis Report")
        lines.append("")
        if app_version:
            lines.append(f"**App Version:** {app_version}")
        lines.append(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        lines.append(f"**Source strings:** {len(source_strings)}")
        lines.append("")

        # Summary
        total_languages = len(results)
        avg_coverage = sum(r['coverage'] for r in results.values()) / total_languages if total_languages else 0

        lines.append("## Summary")
        lines.append("")
        lines.append(f"- **Total languages:** {total_languages}")
        lines.append(f"- **Average coverage:** {avg_coverage:.1f}%")
        lines.append("")

        # Coverage table
        lines.append("## Coverage by Language")
        lines.append("")
        lines.append("| Language | Coverage | Strings | Missing | Obsolete | Empty |")
        lines.append("|----------|----------|---------|---------|----------|-------|")

        for lang_code in sorted(results.keys()):
            result = results[lang_code]
            coverage_badge = "✅" if result['coverage'] >= 95 else "⚠️" if result['coverage'] >= 80 else "❌"
            lines.append(
                f"| {lang_code} {coverage_badge} | "
                f"{result['coverage']:.1f}% | "
                f"{result['total_translation']}/{result['total_source']} | "
                f"{len(result['missing'])} | "
                f"{len(result['obsolete'])} | "
                f"{len(result['empty'])} |"
            )

        lines.append("")

        # Detailed sections for each language
        lines.append("## Detailed Analysis")
        lines.append("")

        for lang_code in sorted(results.keys()):
            result = results[lang_code]

            lines.append(f"### {lang_code.upper()}")
            lines.append("")
            lines.append(f"**File:** `{result['file']}`")
            lines.append(f"**Coverage:** {result['coverage']:.1f}%")
            lines.append("")

            # Missing
            if result['missing']:
                lines.append(f"#### ❌ Missing Strings ({len(result['missing'])})")
                lines.append("")
                for key in result['missing']:
                    lines.append(f"- `{key}`")
                lines.append("")

            # Obsolete
            if result['obsolete']:
                lines.append(f"#### 🗑️ Obsolete Strings ({len(result['obsolete'])})")
                lines.append("")
                for key in result['obsolete']:
                    lines.append(f"- `{key}`")
                lines.append("")

            # Empty
            if result['empty']:
                lines.append(f"#### ⚠️ Empty/Placeholder Strings ({len(result['empty'])})")
                lines.append("")
                for key in result['empty']:
                    lines.append(f"- `{key}`")
                lines.append("")

            # Identical
            if 'identical' in result and result['identical']:
                lines.append(f"#### 🔄 Identical to English ({len(result['identical'])})")
                lines.append("")
                for key in result['identical']:
                    lines.append(f"- `{key}`")
                lines.append("")

        return '\n'.join(lines)


class JsonReporter:
    """Generates JSON reports."""

    def generate(self, results: Dict[str, Dict], source_strings: Dict[str, str], app_version: str = None) -> str:
        """
        Generate JSON report.

        Args:
            results: Analysis results from analyzer
            source_strings: Source strings dictionary
            app_version: App version from build.gradle.kts

        Returns:
            JSON formatted report
        """
        # Convert Path objects to strings for JSON serialization
        json_results = {}
        for lang_code, result in results.items():
            json_result = result.copy()
            # Use relative path, remove absolute path
            if 'file_abs' in json_result:
                del json_result['file_abs']
            json_results[lang_code] = json_result

        # Calculate summary
        total_languages = len(results)
        avg_coverage = sum(r['coverage'] for r in results.values()) / total_languages if total_languages else 0

        metadata = {
            'generated': datetime.now().isoformat(),
            'total_source_strings': len(source_strings),
            'total_languages': total_languages,
            'average_coverage': round(avg_coverage, 2)
        }

        if app_version:
            metadata['app_version'] = app_version

        report = {
            'metadata': metadata,
            'languages': json_results
        }

        return json.dumps(report, indent=2, ensure_ascii=False)
