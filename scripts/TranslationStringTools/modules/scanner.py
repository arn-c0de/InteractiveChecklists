"""
Automatic scanner for Android res directories.
Finds all values and values-XX folders and their strings.xml files.
"""

from pathlib import Path
from typing import Dict, Tuple, Optional


class ResScanner:
    """Scans Android res directory for string resource files."""

    def __init__(self, res_path: Path):
        """
        Initialize scanner.

        Args:
            res_path: Path to the res directory
        """
        self.res_path = Path(res_path)
        if not self.res_path.exists():
            raise ValueError(f"Res directory does not exist: {res_path}")

    def scan(self) -> Tuple[Optional[Path], Dict[str, Path]]:
        """
        Scan for all strings.xml files in values directories.

        Returns:
            Tuple of (source_file, translation_files_dict)
            - source_file: Path to values/strings.xml (source of truth)
            - translation_files_dict: {language_code: path_to_strings.xml}
        """
        source_file = None
        translation_files = {}

        # Find all values* directories
        for path in sorted(self.res_path.iterdir()):
            if not path.is_dir():
                continue

            dir_name = path.name

            # Check if it's a values directory
            if not dir_name.startswith('values'):
                continue

            # Look for strings.xml
            strings_file = path / 'strings.xml'
            if not strings_file.exists():
                continue

            # Determine if this is source or translation
            if dir_name == 'values':
                # Source file (English)
                source_file = strings_file
            else:
                # Translation file
                # Extract language code from directory name
                # values-de -> de
                # values-zh-rCN -> zh-rCN
                lang_code = dir_name[7:]  # Remove 'values-' prefix
                translation_files[lang_code] = strings_file

        return source_file, translation_files

    def get_available_languages(self) -> list[str]:
        """
        Get list of available language codes.

        Returns:
            List of language codes (e.g., ['de', 'es', 'zh-rCN'])
        """
        _, translation_files = self.scan()
        return sorted(translation_files.keys())
