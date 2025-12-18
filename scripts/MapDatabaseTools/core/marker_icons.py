"""
Marker Icon System - Android Drawable compatible tactical symbols
Automatically loads ONLY symbols available in Android app drawables (ic_mapicon_*.xml)
NO fallback symbols - maintains consistency between Python tools and Android app
"""
from typing import Dict, Tuple, List
from .android_drawables_loader import (
    load_android_icons, 
    is_valid_android_symbol, 
    get_available_android_symbols,
    get_loader
)


class IconColor:
    """Standard NATO APP-6 colors (matching Android app)"""
    FRIENDLY = "#0080FF"  # Blue (Friendly/BLUFOR)
    HOSTILE = "#FF4444"   # Red (Hostile/OPFOR)
    NEUTRAL = "#00FF00"   # Green (Neutral)
    UNKNOWN = "#FFFF80"   # Yellow (Unknown)


# Load Android icons dynamically at module import
_android_icons = load_android_icons()
AVAILABLE_ANDROID_SYMBOLS = {
    symbol: metadata['display_name'] 
    for symbol, metadata in _android_icons.items()
}


def get_affiliation_color(affiliation: str) -> str:
    """Get color for symbol affiliation (matching Android app)"""
    colors = {
        'friendly': IconColor.FRIENDLY,
        'hostile': IconColor.HOSTILE,
        'neutral': IconColor.NEUTRAL,
        'unknown': IconColor.UNKNOWN,
    }
    return colors.get(affiliation.lower(), IconColor.UNKNOWN)


def is_valid_symbol(symbol_entity: str) -> bool:
    """Check if symbol entity exists in Android drawables"""
    return is_valid_android_symbol(symbol_entity)


def get_available_symbols() -> Dict[str, str]:
    """Get all available Android drawable symbols"""
    return AVAILABLE_ANDROID_SYMBOLS.copy()


def get_symbols_by_category() -> Dict[str, List[Tuple[str, str]]]:
    """
    Get symbols organized by category
    Returns: {category: [(symbol_entity, display_name), ...]}
    """
    loader = get_loader()
    by_category = loader.get_symbols_by_category()
    
    result = {}
    for category, symbols in by_category.items():
        result[category] = [
            (symbol, AVAILABLE_ANDROID_SYMBOLS.get(symbol, symbol))
            for symbol in symbols
        ]
    return result


class TacticalMarkerStyle:
    """Style definitions for tactical markers using ONLY Android drawable symbols"""
    
    @staticmethod
    def get_style(symbol_entity: str, affiliation: str = 'unknown') -> Tuple[str, str]:
        """
        Get marker style for Android-compatible symbol
        Returns: (symbol_text, color_hex)
        
        ONLY returns symbols that exist in Android drawables!
        No fallbacks - raises ValueError if symbol not available.
        """
        if not is_valid_symbol(symbol_entity):
            available = ', '.join(AVAILABLE_ANDROID_SYMBOLS.keys())
            raise ValueError(
                f"Symbol '{symbol_entity}' not available in Android drawables. "
                f"Available symbols: {available}"
            )
        
        color = get_affiliation_color(affiliation)
        symbol_display = AVAILABLE_ANDROID_SYMBOLS[symbol_entity]
        
        return (symbol_display, color)
    
    @staticmethod
    def get_leaflet_icon_config(symbol_entity: str, affiliation: str = 'unknown') -> Dict:
        """
        Get Leaflet/Folium marker configuration
        Uses ONLY Android-compatible symbols
        """
        symbol_text, color = TacticalMarkerStyle.get_style(symbol_entity, affiliation)
        
        return {
            'icon': 'info-sign',  # Bootstrap Glyphicon fallback for Leaflet
            'color': color,
            'prefix': 'glyphicon',
            'extraClasses': 'tactical-marker',
        }
    
    @staticmethod
    def get_svg_marker(symbol_entity: str, affiliation: str = 'unknown', size: int = 40) -> str:
        """
        Generate SVG marker for web display
        Displays symbol name as text (actual icon rendering done by Android app)
        """
        symbol_text, color = TacticalMarkerStyle.get_style(symbol_entity, affiliation)
        
        svg = f'''<svg width="{size}" height="{size}" xmlns="http://www.w3.org/2000/svg">
            <rect width="{size}" height="{size}" fill="{color}" opacity="0.7" rx="3"/>
            <text x="50%" y="50%" text-anchor="middle" dominant-baseline="middle" 
                  font-size="10" fill="#000000" font-weight="bold">
                {symbol_entity.upper()}
            </text>
        </svg>'''
        
        import base64
        encoded = base64.b64encode(svg.encode('utf-8')).decode('utf-8')
        return f"data:image/svg+xml;base64,{encoded}"


def get_icon_preset(preset_name: str) -> Dict:
    """Get Leaflet icon config for a preset

    Preset names are parsed dynamically - no hardcoded presets stored in the code.
    Supported formats:
      - "<affiliation>_<symbol>" (e.g., "friendly_mortar")
      - "<symbol>_<affiliation>" (e.g., "mortar_friendly")

    Affiliations: friendly, hostile, neutral, unknown
    """
    affiliations = {'friendly', 'hostile', 'neutral', 'unknown'}

    if not isinstance(preset_name, str) or '_' not in preset_name:
        raise ValueError('preset_name must be a string containing "_" separator')

    parts = preset_name.split('_')

    # Try parse affiliation at start
    if parts[0] in affiliations:
        affiliation = parts[0]
        symbol_entity = '_'.join(parts[1:])
    # Or affiliation at end
    elif parts[-1] in affiliations:
        affiliation = parts[-1]
        symbol_entity = '_'.join(parts[:-1])
    else:
        raise ValueError(f"Could not parse affiliation from preset '{preset_name}'")

    # Validate symbol exists in Android drawables
    if not is_valid_symbol(symbol_entity):
        raise ValueError(f"Symbol '{symbol_entity}' not available in Android drawables")

    return TacticalMarkerStyle.get_leaflet_icon_config(symbol_entity, affiliation)


def get_available_icons() -> Dict[str, list]:
    """
    Get all available Android drawable symbols organized by category
    Returns: {category: [(symbol_entity, display_name, affiliation), ...]}
    """
    categories = {
        'Equipment': [],
        'Unit Sizes': [],
    }
    
    # Automatically load from Android drawables - no hardcoded symbols!
    by_category = get_symbols_by_category()
    affiliations = ['friendly', 'hostile', 'neutral', 'unknown']
    
    result = {}
    for category, symbols in by_category.items():
        # Format category name nicely
        category_name = category.replace('_', ' ').title()
        result[category_name] = []
        
        for symbol_entity, display_name in symbols:
            # For unit sizes, only add once with neutral
            if category == 'unit_size':
                result[category_name].append((symbol_entity, display_name, 'neutral'))
            else:
                # For other symbols, add all affiliations
                for affiliation in affiliations:
                    display = f"{display_name} ({affiliation.title()})"
                    result[category_name].append((symbol_entity, display, affiliation))
    
    return result


if __name__ == "__main__":
    # Test Android drawable symbol system
    print("=== Android Drawable Icons Only ===")
    print("\nAvailable Symbols:")
    for symbol, name in get_available_symbols().items():
        print(f"  - {symbol}: {name}")
    
    print("\n\nAvailable Icon Categories:")
    for category, icons in get_available_icons().items():
        print(f"\n{category}:")
        for symbol_entity, display_name, affiliation in icons[:3]:  # Show first 3
            print(f"  - {symbol_entity} ({affiliation})")
        if len(icons) > 3:
            print(f"  ... and {len(icons) - 3} more")
    
    # Example SVG generation
    print("\n\nExample SVG marker (Hostile Mortar):")
    try:
        svg_uri = TacticalMarkerStyle.get_svg_marker('mortar', 'hostile')
        print(f"Length: {len(svg_uri)} chars")
        print(f"Preview: {svg_uri[:100]}...")
    except ValueError as e:
        print(f"Error: {e}")
    
    # Test invalid symbol
    print("\n\nTest invalid symbol (should raise error):")
    try:
        TacticalMarkerStyle.get_style('fighter', 'friendly')
    except ValueError as e:
        print(f"Correctly rejected: {e}")
