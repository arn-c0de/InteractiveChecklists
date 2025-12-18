"""
Android Drawables Loader - Automatically loads map icons from Android drawable folder
Scans for ic_mapicon_*.xml files and extracts symbol information
Ensures Python tools stay in sync with Android app icons
"""
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, List, Tuple, Optional


class AndroidDrawablesLoader:
    """Loads and parses Android drawable XML files for map icons"""
    
    def __init__(self, android_project_root: Optional[Path] = None):
        """
        Initialize loader
        
        Args:
            android_project_root: Path to Android project root (auto-detected if None)
        """
        if android_project_root is None:
            # Auto-detect: go up from this file to find Android project
            current = Path(__file__).resolve()
            # Navigate up: core/ -> MapDatabaseTools/ -> scripts/ -> ChecklistInteractive/
            android_project_root = current.parent.parent.parent.parent
        
        self.android_root = Path(android_project_root)
        self.drawable_path = self.android_root / "app" / "src" / "main" / "res" / "drawable"
        self.icons: Dict[str, Dict] = {}
        
    def load_icons(self) -> Dict[str, Dict]:
        """
        Load all ic_mapicon_*.xml files from Android drawable folder
        
        Returns:
            Dict mapping symbol_entity to icon metadata
            {
                'mortar': {
                    'file': 'ic_mapicon_mortar.xml',
                    'display_name': 'Mortar',
                    'category': 'equipment',
                    'description': 'Mortar Symbol'
                },
                ...
            }
        """
        if not self.drawable_path.exists():
            print(f"Warning: Drawable path not found: {self.drawable_path}")
            return {}
        
        icon_files = list(self.drawable_path.glob("ic_mapicon_*.xml"))
        
        if not icon_files:
            print(f"Warning: No ic_mapicon_*.xml files found in {self.drawable_path}")
            return {}
        
        for icon_file in icon_files:
            symbol_entity = self._extract_symbol_entity(icon_file.name)
            metadata = self._parse_icon_metadata(icon_file)
            
            self.icons[symbol_entity] = {
                'file': icon_file.name,
                'display_name': metadata.get('display_name', self._make_display_name(symbol_entity)),
                'category': metadata.get('category', self._guess_category(symbol_entity)),
                'description': metadata.get('description', ''),
                'path': str(icon_file)
            }
        
        print(f"Loaded {len(self.icons)} map icons from Android drawables")
        return self.icons
    
    def _extract_symbol_entity(self, filename: str) -> str:
        """
        Extract symbol entity from filename
        ic_mapicon_mortar.xml -> mortar
        ic_mapicon_size_squad.xml -> size_squad
        """
        name = filename.replace('ic_mapicon_', '').replace('.xml', '')
        return name
    
    def _parse_icon_metadata(self, xml_file: Path) -> Dict:
        """
        Parse XML file to extract metadata from comments
        Looks for comment blocks with display name and description
        """
        try:
            with open(xml_file, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Look for comments containing metadata
            metadata = {}
            
            # Try to find display name in comments
            # Example: <!-- Mortar Symbol -->
            symbol_comment = re.search(r'<!--\s*(.+?)\s*Symbol\s*-->', content, re.IGNORECASE)
            if symbol_comment:
                metadata['display_name'] = symbol_comment.group(1).strip()
            
            # Try to find description in comments
            desc_comment = re.search(r'<!--\s*Description:\s*(.+?)\s*-->', content, re.IGNORECASE)
            if desc_comment:
                metadata['description'] = desc_comment.group(1).strip()
            
            return metadata
            
        except Exception as e:
            print(f"Warning: Could not parse {xml_file.name}: {e}")
            return {}
    
    def _make_display_name(self, symbol_entity: str) -> str:
        """
        Generate human-readable display name from symbol entity
        equipment_mortar -> Mortar
        unitsize_squad -> Squad
        activities_military_police -> Military Police
        aircraft_fighter -> Fighter
        """
        # Remove category prefixes
        category_prefixes = ['equipment_', 'groundunit_', 'installations_', 'activities_', 'unitsize_', 'aircraft_', 'helicopter_', 'ship_', 'vehicle_']
        display_entity = symbol_entity
        for prefix in category_prefixes:
            if display_entity.startswith(prefix):
                display_entity = display_entity[len(prefix):]
                break
        
        # Replace underscores with spaces and title case
        return display_entity.replace('_', ' ').title()
    
    def _guess_category(self, symbol_entity: str) -> str:
        """Automatic category detection from prefix"""
        if symbol_entity.startswith('equipment_'):
            return 'equipment'
        elif symbol_entity.startswith('groundunit_'):
            return 'ground_units'
        elif symbol_entity.startswith('installations_'):
            return 'installations'
        elif symbol_entity.startswith('activities_'):
            return 'activities'
        elif symbol_entity.startswith('unitsize_'):
            return 'unit_size'
        elif symbol_entity.startswith('aircraft_'):
            return 'aircraft'
        elif symbol_entity.startswith('helicopter_'):
            return 'helicopter'
        elif symbol_entity.startswith('ship_'):
            return 'ship'
        elif symbol_entity.startswith('vehicle_'):
            return 'vehicle'
        else:
            return 'equipment'  # default fallback
    
    def get_available_symbols(self) -> List[str]:
        """Get list of all available symbol entities"""
        return list(self.icons.keys())
    
    def get_symbol_metadata(self, symbol_entity: str) -> Optional[Dict]:
        """Get metadata for a specific symbol"""
        return self.icons.get(symbol_entity)
    
    def get_symbols_by_category(self) -> Dict[str, List[str]]:
        """Get symbols organized by category"""
        categories = {}
        for symbol_entity, metadata in self.icons.items():
            category = metadata['category']
            if category not in categories:
                categories[category] = []
            categories[category].append(symbol_entity)
        return categories
    
    def is_valid_symbol(self, symbol_entity: str) -> bool:
        """Check if symbol entity exists in Android drawables"""
        return symbol_entity in self.icons


# Global loader instance
_loader = None


def get_loader() -> AndroidDrawablesLoader:
    """Get or create global loader instance"""
    global _loader
    if _loader is None:
        _loader = AndroidDrawablesLoader()
        _loader.load_icons()
    return _loader


def load_android_icons() -> Dict[str, Dict]:
    """Convenience function to load all Android map icons"""
    loader = get_loader()
    return loader.icons


def is_valid_android_symbol(symbol_entity: str) -> bool:
    """Check if symbol exists in Android drawables"""
    loader = get_loader()
    return loader.is_valid_symbol(symbol_entity)


def get_available_android_symbols() -> List[str]:
    """Get list of all available Android map icon symbols"""
    loader = get_loader()
    return loader.get_available_symbols()


if __name__ == "__main__":
    # Test the loader
    print("=== Android Drawables Loader Test ===\n")
    
    loader = AndroidDrawablesLoader()
    icons = loader.load_icons()
    
    print(f"\nFound {len(icons)} map icons:\n")
    
    # Show by category
    by_category = loader.get_symbols_by_category()
    for category, symbols in sorted(by_category.items()):
        print(f"\n{category.upper().replace('_', ' ')}:")
        for symbol in sorted(symbols):
            metadata = loader.get_symbol_metadata(symbol)
            print(f"  - {symbol:25} -> {metadata['display_name']}")
    
    # Test validation
    print("\n\nValidation Tests:")
    print(f"  is_valid('mortar'): {loader.is_valid_symbol('mortar')}")
    print(f"  is_valid('fighter'): {loader.is_valid_symbol('fighter')}")
