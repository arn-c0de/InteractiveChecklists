"""
Version extractor for Android build.gradle.kts files.
"""

import re
from pathlib import Path
from typing import Optional


class VersionExtractor:
    """Extracts version information from build.gradle.kts."""

    @staticmethod
    def extract_version(project_root: Path) -> Optional[str]:
        """
        Extract versionName from app/build.gradle.kts.

        Args:
            project_root: Path to project root directory

        Returns:
            Version string (e.g., "1.0.25") or None if not found
        """
        gradle_file = project_root / 'app' / 'build.gradle.kts'

        if not gradle_file.exists():
            return None

        try:
            content = gradle_file.read_text(encoding='utf-8')

            # Match versionName = "1.0.25" or versionName="1.0.25"
            match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
            if match:
                return match.group(1)

            return None

        except Exception:
            return None
