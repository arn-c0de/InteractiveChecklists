"""
Marker Icon System - Unicode and color-coded tactical symbols
Provides icons for various marker types with NATO-style symbology
"""
from enum import Enum
from typing import Dict, Tuple


class IconColor:
    """Standard NATO APP-6 colors"""
    BLUFOR = "#00A8FF"  # Blue (Friendly)
    OPFOR = "#FF4444"   # Red (Hostile)
    NEUTRAL = "#00FF00" # Green (Neutral)
    UNKNOWN = "#FFFF00" # Yellow (Unknown)
    AIRPORT = "#8B4513"  # Brown (Infrastructure)
    WAYPOINT = "#FFA500" # Orange (Navigation)
    POI = "#9370DB"      # Purple (Points of Interest)
    THREAT = "#FF0000"   # Bright Red (Threats)


class MarkerIcon:
    """Icon definitions for different marker types"""
    
    # Unicode symbols for various marker types
    AIRPORT = "✈"
    HELICOPTER = "🚁"
    WAYPOINT = "📍"
    FLAG = "🚩"
    
    # Military symbols (simplified Unicode representation)
    FIGHTER = "✈"
    BOMBER = "✈"
    TRANSPORT = "🛫"
    UAV = "🛸"
    
    # Ground units
    TANK = "🛡"
    ARTILLERY = "💥"
    SAM = "🚀"
    RADAR = "📡"
    INFANTRY = "👥"
    AAA = "⚡"
    HQ = "🏛"
    SUPPLY = "📦"
    
    # Naval
    SHIP = "🚢"
    SUBMARINE = "🔱"
    
    # Generic
    STAR = "⭐"
    CIRCLE = "⚫"
    SQUARE = "◼"
    TRIANGLE = "▲"
    DIAMOND = "◆"
    
    # Special
    TARGET = "🎯"
    EXPLOSION = "💥"
    WARNING = "⚠"


class TacticalMarkerStyle:
    """Style definitions for tactical markers combining symbol and color"""
    
    @staticmethod
    def get_style(marker_type: str, coalition: str, tactical_symbol: str = None) -> Dict[str, str]:
        """
        Get marker style (icon + color) based on type and coalition
        
        Returns:
            Dict with 'icon', 'color', 'size' keys
        """
        style = {
            'icon': MarkerIcon.CIRCLE,
            'color': IconColor.UNKNOWN,
            'size': 24,
            'symbol': '?'
        }
        
        # Determine color by coalition
        if coalition:
            coalition_upper = coalition.upper()
            if coalition_upper == "BLUFOR":
                style['color'] = IconColor.BLUFOR
            elif coalition_upper == "OPFOR":
                style['color'] = IconColor.OPFOR
            elif coalition_upper == "NEUTRAL":
                style['color'] = IconColor.NEUTRAL
        
        # Determine icon by marker type
        if marker_type == "airport":
            style['icon'] = MarkerIcon.AIRPORT
            style['color'] = IconColor.AIRPORT
            style['size'] = 28
            style['symbol'] = '✈'
        
        elif marker_type == "waypoint":
            style['icon'] = MarkerIcon.WAYPOINT
            style['color'] = IconColor.WAYPOINT
            style['size'] = 20
            style['symbol'] = '📍'
        
        elif marker_type == "poi":
            style['icon'] = MarkerIcon.FLAG
            style['color'] = IconColor.POI
            style['size'] = 22
            style['symbol'] = '🚩'
        
        elif marker_type == "threat":
            style['icon'] = MarkerIcon.WARNING
            style['color'] = IconColor.THREAT
            style['size'] = 26
            style['symbol'] = '⚠'
        
        elif marker_type == "target":
            style['icon'] = MarkerIcon.TARGET
            style['color'] = IconColor.THREAT
            style['size'] = 24
            style['symbol'] = '🎯'
        
        # Tactical symbols
        elif marker_type.startswith("tactical_"):
            if tactical_symbol:
                symbol_map = {
                    'fighter': (MarkerIcon.FIGHTER, '✈'),
                    'bomber': (MarkerIcon.BOMBER, '✈'),
                    'transport': (MarkerIcon.TRANSPORT, '🛫'),
                    'helicopter': (MarkerIcon.HELICOPTER, '🚁'),
                    'uav': (MarkerIcon.UAV, '🛸'),
                    'infantry': (MarkerIcon.INFANTRY, '👥'),
                    'armor': (MarkerIcon.TANK, '🛡'),
                    'artillery': (MarkerIcon.ARTILLERY, '💥'),
                    'sam': (MarkerIcon.SAM, '🚀'),
                    'aaa': (MarkerIcon.AAA, '⚡'),
                    'radar': (MarkerIcon.RADAR, '📡'),
                    'ship': (MarkerIcon.SHIP, '🚢'),
                    'submarine': (MarkerIcon.SUBMARINE, '🔱'),
                    'hq': (MarkerIcon.HQ, '🏛'),
                    'supply': (MarkerIcon.SUPPLY, '📦'),
                    'unit': (MarkerIcon.SQUARE, '◼'),
                }
                
                if tactical_symbol in symbol_map:
                    style['icon'], style['symbol'] = symbol_map[tactical_symbol]
                    style['size'] = 26
        
        return style
    
    @staticmethod
    def get_svg_marker(marker_type: str, coalition: str, tactical_symbol: str = None) -> str:
        """
        Generate SVG marker icon for Leaflet
        Returns data URI for inline SVG
        """
        style = TacticalMarkerStyle.get_style(marker_type, coalition, tactical_symbol)
        
        # Create SVG with border and shadow for better visibility
        svg = f"""
        <svg width="{style['size']*2}" height="{style['size']*2}" xmlns="http://www.w3.org/2000/svg">
            <defs>
                <filter id="shadow">
                    <feDropShadow dx="0" dy="1" stdDeviation="2" flood-opacity="0.5"/>
                </filter>
            </defs>
            <circle cx="{style['size']}" cy="{style['size']}" r="{style['size']-4}" 
                    fill="{style['color']}" stroke="white" stroke-width="2" filter="url(#shadow)"/>
            <text x="{style['size']}" y="{style['size']+6}" 
                  font-size="{style['size']-4}" text-anchor="middle" fill="white">
                {style['symbol']}
            </text>
        </svg>
        """.strip()
        
        # Return as data URI
        import base64
        encoded = base64.b64encode(svg.encode('utf-8')).decode('utf-8')
        return f"data:image/svg+xml;base64,{encoded}"
    
    @staticmethod
    def get_leaflet_icon_config(marker_type: str, coalition: str, tactical_symbol: str = None) -> Dict:
        """
        Get Leaflet icon configuration for JavaScript
        
        Returns config dict that can be passed to L.divIcon or L.icon
        """
        style = TacticalMarkerStyle.get_style(marker_type, coalition, tactical_symbol)
        
        return {
            'html': f'<div style="font-size: {style["size"]}px; color: {style["color"]}; text-shadow: 0 0 3px black, 0 0 3px black;">{style["symbol"]}</div>',
            'className': f'marker-{marker_type}',
            'iconSize': [style['size'], style['size']],
            'iconAnchor': [style['size'] // 2, style['size'] // 2],
            'popupAnchor': [0, -style['size'] // 2]
        }


# Pre-defined icon sets for quick access
ICON_PRESETS = {
    # Airports
    'airport_military': ('airport', None, None),
    'airport_civilian': ('airport', 'NEUTRAL', None),
    'heliport': ('waypoint', None, 'helicopter'),
    
    # Waypoints
    'waypoint_nav': ('waypoint', None, None),
    'waypoint_ip': ('waypoint', 'BLUFOR', None),
    'waypoint_target': ('waypoint', 'OPFOR', None),
    
    # BLUFOR Air
    'blufor_fighter': ('tactical_blufor', 'BLUFOR', 'fighter'),
    'blufor_bomber': ('tactical_blufor', 'BLUFOR', 'bomber'),
    'blufor_transport': ('tactical_blufor', 'BLUFOR', 'transport'),
    'blufor_helo': ('tactical_blufor', 'BLUFOR', 'helicopter'),
    'blufor_uav': ('tactical_blufor', 'BLUFOR', 'uav'),
    
    # BLUFOR Ground
    'blufor_infantry': ('tactical_blufor', 'BLUFOR', 'infantry'),
    'blufor_armor': ('tactical_blufor', 'BLUFOR', 'armor'),
    'blufor_artillery': ('tactical_blufor', 'BLUFOR', 'artillery'),
    'blufor_hq': ('tactical_blufor', 'BLUFOR', 'hq'),
    
    # OPFOR Air
    'opfor_fighter': ('tactical_opfor', 'OPFOR', 'fighter'),
    'opfor_bomber': ('tactical_opfor', 'OPFOR', 'bomber'),
    'opfor_helo': ('tactical_opfor', 'OPFOR', 'helicopter'),
    'opfor_uav': ('tactical_opfor', 'OPFOR', 'uav'),
    
    # OPFOR Ground
    'opfor_sam': ('tactical_opfor', 'OPFOR', 'sam'),
    'opfor_aaa': ('tactical_opfor', 'OPFOR', 'aaa'),
    'opfor_radar': ('tactical_opfor', 'OPFOR', 'radar'),
    'opfor_armor': ('tactical_opfor', 'OPFOR', 'armor'),
    'opfor_infantry': ('tactical_opfor', 'OPFOR', 'infantry'),
    
    # Neutral
    'neutral_ship': ('tactical_neutral', 'NEUTRAL', 'ship'),
    'neutral_hq': ('tactical_neutral', 'NEUTRAL', 'hq'),
    
    # Special
    'target': ('target', 'OPFOR', None),
    'threat': ('threat', 'OPFOR', None),
    'poi': ('poi', None, None),
}


def get_icon_preset(preset_name: str) -> Dict:
    """Get Leaflet icon config for a preset"""
    if preset_name not in ICON_PRESETS:
        preset_name = 'waypoint_nav'  # Default fallback
    
    marker_type, coalition, tactical_symbol = ICON_PRESETS[preset_name]
    return TacticalMarkerStyle.get_leaflet_icon_config(marker_type, coalition, tactical_symbol)


def get_available_icons() -> Dict[str, list]:
    """Get list of all available icon presets organized by category"""
    categories = {
        'Airports': [],
        'Waypoints': [],
        'BLUFOR Air': [],
        'BLUFOR Ground': [],
        'OPFOR Air': [],
        'OPFOR Ground': [],
        'OPFOR Defense': [],
        'Neutral': [],
        'Special': []
    }
    
    for preset_name in ICON_PRESETS.keys():
        if preset_name.startswith('airport'):
            categories['Airports'].append(preset_name)
        elif preset_name.startswith('waypoint') or preset_name == 'heliport':
            categories['Waypoints'].append(preset_name)
        elif preset_name.startswith('blufor') and any(x in preset_name for x in ['fighter', 'bomber', 'transport', 'helo', 'uav']):
            categories['BLUFOR Air'].append(preset_name)
        elif preset_name.startswith('blufor'):
            categories['BLUFOR Ground'].append(preset_name)
        elif preset_name.startswith('opfor') and any(x in preset_name for x in ['fighter', 'bomber', 'helo', 'uav']):
            categories['OPFOR Air'].append(preset_name)
        elif preset_name.startswith('opfor') and any(x in preset_name for x in ['sam', 'aaa', 'radar']):
            categories['OPFOR Defense'].append(preset_name)
        elif preset_name.startswith('opfor'):
            categories['OPFOR Ground'].append(preset_name)
        elif preset_name.startswith('neutral'):
            categories['Neutral'].append(preset_name)
        else:
            categories['Special'].append(preset_name)
    
    # Remove empty categories
    return {k: v for k, v in categories.items() if v}


if __name__ == "__main__":
    # Test icon generation
    print("Available Icon Categories:")
    for category, icons in get_available_icons().items():
        print(f"\n{category}:")
        for icon in icons:
            preset = ICON_PRESETS[icon]
            style = TacticalMarkerStyle.get_style(*preset)
            print(f"  {icon:25} -> {style['symbol']} ({style['color']})")
    
    # Example SVG generation
    print("\n\nExample SVG marker (BLUFOR Fighter):")
    svg_uri = TacticalMarkerStyle.get_svg_marker('tactical_blufor', 'BLUFOR', 'fighter')
    print(f"Length: {len(svg_uri)} chars")
    print(f"Preview: {svg_uri[:100]}...")
