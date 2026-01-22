"""
XML parser for Android strings.xml files.
Handles string resources, string arrays, and plurals.
"""

import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Set


class StringsXmlParser:
    """Parser for Android strings.xml files."""

    def __init__(self, file_path: Path):
        """
        Initialize parser.

        Args:
            file_path: Path to strings.xml file
        """
        self.file_path = Path(file_path)
        if not self.file_path.exists():
            raise ValueError(f"File does not exist: {file_path}")

    def parse(self) -> Dict[str, str]:
        """
        Parse strings.xml file.

        Returns:
            Dictionary mapping string keys to their values
            {string_name: string_value}
        """
        try:
            tree = ET.parse(self.file_path)
            root = tree.getroot()
        except ET.ParseError as e:
            raise ValueError(f"Failed to parse XML file {self.file_path}: {e}")

        strings = {}

        # Parse <string name="...">value</string>
        for string_elem in root.findall('string'):
            name = string_elem.get('name')
            if name:
                # Get text content, handling None
                value = string_elem.text or ''
                strings[name] = value.strip()

        # Parse <string-array name="...">
        for array_elem in root.findall('string-array'):
            name = array_elem.get('name')
            if name:
                items = []
                for item in array_elem.findall('item'):
                    item_text = item.text or ''
                    items.append(item_text.strip())
                # Store as special marker to indicate it's an array
                strings[name] = f"[ARRAY:{len(items)}]"

        # Parse <plurals name="...">
        for plurals_elem in root.findall('plurals'):
            name = plurals_elem.get('name')
            if name:
                items = []
                for item in plurals_elem.findall('item'):
                    quantity = item.get('quantity', '')
                    item_text = item.text or ''
                    items.append(f"{quantity}:{item_text.strip()}")
                # Store as special marker to indicate it's a plural
                strings[name] = f"[PLURAL:{len(items)}]"

        return strings

    def get_keys(self) -> Set[str]:
        """
        Get all string keys from the file.

        Returns:
            Set of string keys
        """
        return set(self.parse().keys())

    def is_empty_or_placeholder(self, value: str) -> bool:
        """
        Check if a value is empty or a placeholder.

        Args:
            value: String value to check

        Returns:
            True if empty or placeholder
        """
        if not value:
            return True

        # Check for common placeholder patterns
        placeholders = ['TODO', 'TBD', 'FIXME', '...', 'XXX']
        return value.strip() in placeholders
