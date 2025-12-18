"""
Markers Database - SQLite storage for tactical markers and locations
Supports airports, waypoints, tactical military symbols (BLUFOR/OPFOR/Neutral)
Shared between Python tools and Android app
"""
import sqlite3
import json
from pathlib import Path
from typing import List, Optional, Dict, Any, Tuple
from dataclasses import dataclass, asdict
from datetime import datetime
from enum import Enum


class MarkerType(Enum):
    """Marker type classification"""
    AIRPORT = "airport"
    WAYPOINT = "waypoint"
    TACTICAL_BLUFOR = "tactical_blufor"
    TACTICAL_OPFOR = "tactical_opfor"
    TACTICAL_NEUTRAL = "tactical_neutral"
    POI = "poi"  # Point of Interest
    THREAT = "threat"
    TARGET = "target"


class TacticalSymbol(Enum):
    """Military tactical symbols (NATO APP-6)"""
    # Air units
    FIGHTER = "fighter"
    BOMBER = "bomber"
    TRANSPORT = "transport"
    HELICOPTER = "helicopter"
    UAV = "uav"
    
    # Ground units
    INFANTRY = "infantry"
    ARMOR = "armor"
    ARTILLERY = "artillery"
    SAM = "sam"
    AAA = "aaa"
    RADAR = "radar"
    
    # Naval
    SHIP = "ship"
    SUBMARINE = "submarine"
    
    # Generic
    UNIT = "unit"
    HQ = "hq"
    SUPPLY = "supply"


@dataclass
class Runway:
    """Airport runway data"""
    name: str  # e.g., "09/27"
    length_m: float
    width_m: float
    heading: float  # Magnetic heading
    surface: str  # concrete, asphalt, grass, dirt
    ils: bool = False
    
    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Runway':
        return cls(**data)


@dataclass
class Border:
    """Map border/region boundary data model"""
    id: Optional[int] = None
    name: str = ""
    points: List[Tuple[float, float]] = None  # List of (lat, lon) tuples
    description: str = ""
    color: str = "#FF0000"  # Hex color for border line
    created: Optional[str] = None
    modified: Optional[str] = None
    
    def __post_init__(self):
        if self.points is None:
            self.points = []
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            'id': self.id,
            'name': self.name,
            'points': self.points,
            'description': self.description,
            'color': self.color,
            'created': self.created,
            'modified': self.modified
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Border':
        """Create from dictionary"""
        return cls(**data)


@dataclass
class Route:
    """Flight route with multiple waypoints"""
    id: Optional[int] = None
    name: str = ""
    description: str = ""
    color: str = "#00A8FF"  # Line color
    created: Optional[str] = None
    modified: Optional[str] = None
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'color': self.color,
            'created': self.created,
            'modified': self.modified
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Route':
        return cls(**{k: v for k, v in data.items() if k in cls.__annotations__})


@dataclass
class RouteWaypoint:
    """Waypoint in a route with sequence and navigation data"""
    id: Optional[int] = None
    route_id: int = 0
    location_id: int = 0
    sequence: int = 0  # Order in route (0, 1, 2, ...)
    distance_nm: Optional[float] = None  # Distance to next waypoint in nautical miles
    heading_mag: Optional[float] = None  # Magnetic heading to next waypoint (0-360)
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'id': self.id,
            'route_id': self.route_id,
            'location_id': self.location_id,
            'sequence': self.sequence,
            'distance_nm': self.distance_nm,
            'heading_mag': self.heading_mag
        }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'RouteWaypoint':
        return cls(**{k: v for k, v in data.items() if k in cls.__annotations__})


@dataclass
class Location:
    """Location/Marker data model"""
    id: Optional[int] = None
    name: str = ""
    latitude: float = 0.0
    longitude: float = 0.0
    marker_type: str = MarkerType.WAYPOINT.value
    coalition: Optional[str] = None  # BLUFOR, OPFOR, NEUTRAL
    tactical_symbol: Optional[str] = None
    icon: str = "default"
    description: str = ""
    
    # Airport-specific
    icao: Optional[str] = None
    iata: Optional[str] = None
    elevation_m: Optional[float] = None
    frequencies: Optional[Dict[str, float]] = None  # {"tower": 118.1, "ground": 121.9}
    runways: Optional[List[Runway]] = None
    
    # Tactical-specific
    threat_level: Optional[int] = None  # 1-5
    unit_type: Optional[str] = None
    strength: Optional[int] = None
    
    # Metadata
    created: Optional[str] = None
    modified: Optional[str] = None
    tags: Optional[List[str]] = None
    metadata: Optional[Dict[str, Any]] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        d = asdict(self)
        # Convert nested objects
        if self.runways:
            d['runways'] = [r.to_dict() if isinstance(r, Runway) else r for r in self.runways]
        return d
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Location':
        """Create from dictionary"""
        # Handle runways
        if data.get('runways'):
            data['runways'] = [
                Runway.from_dict(r) if isinstance(r, dict) else r 
                for r in data['runways']
            ]
        return cls(**data)


class MarkersDatabase:
    """SQLite database for storing tactical markers and locations"""
    
    def __init__(self, db_path: Optional[Path] = None):
        """
        Initialize database connection
        
        Args:
            db_path: Path to database file. If None, uses default location
                    that can be shared with Android app
        """
        if db_path is None:
            # Default location: app/src/main/assets/databases/map_data.db
            # This gets packaged as an asset and deployed with the app
            project_root = Path(__file__).parents[3]
            default_dir = project_root / "app" / "src" / "main" / "assets" / "databases"
            default_dir.mkdir(parents=True, exist_ok=True)
            db_path = default_dir / "map_data.db"

            # Handle migrations from older locations:
            #  1. app/database/map_data.db (previous default)
            #  2. app/maps/map_data.db (older default)
            #  3. app/maps/tactical_data.db (older default)
            old_database_dir = project_root / "app" / "database"
            database_old = old_database_dir / "map_data.db"
            old_maps_dir = project_root / "app" / "maps"
            map_data_old = old_maps_dir / "map_data.db"
            tactical_old = old_maps_dir / "tactical_data.db"

            # Priority: app/database > app/maps/map_data.db > app/maps/tactical_data.db
            if database_old.exists() and not db_path.exists():
                try:
                    import shutil
                    shutil.copy2(database_old, db_path)
                except Exception as e:
                    pass
            elif map_data_old.exists() and not db_path.exists():
                try:
                    import shutil
                    shutil.copy2(map_data_old, db_path)
                except Exception:
                    pass
            elif tactical_old.exists() and not db_path.exists():
                try:
                    import shutil
                    shutil.copy2(tactical_old, db_path)
                except Exception:
                    pass

        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._init_schema()
    
    def _init_schema(self):
        """Create database schema"""
        cursor = self.conn.cursor()
        
        # Main locations table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS locations (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                marker_type TEXT NOT NULL,
                coalition TEXT,
                tactical_symbol TEXT,
                icon TEXT NOT NULL DEFAULT 'default',
                description TEXT NOT NULL DEFAULT '',
                
                -- Airport fields
                icao TEXT,
                iata TEXT,
                elevation_m REAL,
                frequencies TEXT,  -- JSON
                runways TEXT,  -- JSON
                
                -- Tactical fields
                threat_level INTEGER,
                unit_type TEXT,
                strength INTEGER,
                
                -- Metadata
                created TEXT NOT NULL,
                modified TEXT NOT NULL,
                tags TEXT,  -- JSON array
                metadata TEXT  -- JSON for custom fields
            )
        """)
        
        # Create indices for common queries
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_marker_type ON locations(marker_type)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_coalition ON locations(coalition)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_latitude_longitude ON locations(latitude, longitude)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_name ON locations(name)")
        
        # Borders table for map region boundaries
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS borders (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                points TEXT NOT NULL,  -- JSON array of [lat, lon] pairs
                description TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT '#FF0000',
                created TEXT NOT NULL,
                modified TEXT NOT NULL
            )
        """)
        
        cursor.execute("CREATE INDEX IF NOT EXISTS index_borders_name ON borders(name)")
        
        # Routes table for flight routes
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS routes (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT '#00A8FF',
                created TEXT NOT NULL,
                modified TEXT NOT NULL
            )
        """)
        
        cursor.execute("CREATE INDEX IF NOT EXISTS index_routes_name ON routes(name)")
        
        # Route waypoints table (links routes to locations with sequence)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS route_waypoints (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                route_id INTEGER NOT NULL,
                location_id INTEGER NOT NULL,
                sequence INTEGER NOT NULL,
                distance_nm REAL,
                heading_mag REAL,
                FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,
                FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE CASCADE
            )
        """)
        
        cursor.execute("CREATE INDEX IF NOT EXISTS index_route_waypoints_route_id ON route_waypoints(route_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_route_waypoints_route_id_sequence ON route_waypoints(route_id, sequence)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_route_waypoints_location_id ON route_waypoints(location_id)")
        
        self.conn.commit()
    
    def add_location(self, location: Location) -> int:
        """Add a new location to the database"""
        now = datetime.utcnow().isoformat()
        location.created = location.created or now
        location.modified = now
        
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO locations (
                name, latitude, longitude, marker_type, coalition, tactical_symbol,
                icon, description, icao, iata, elevation_m, frequencies, runways,
                threat_level, unit_type, strength, created, modified, tags, metadata
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            location.name,
            location.latitude,
            location.longitude,
            location.marker_type,
            location.coalition,
            location.tactical_symbol,
            location.icon,
            location.description,
            location.icao,
            location.iata,
            location.elevation_m,
            json.dumps(location.frequencies) if location.frequencies else None,
            json.dumps([r.to_dict() for r in location.runways]) if location.runways else None,
            location.threat_level,
            location.unit_type,
            location.strength,
            location.created,
            location.modified,
            json.dumps(location.tags) if location.tags else None,
            json.dumps(location.metadata) if location.metadata else None
        ))
        
        self.conn.commit()
        return cursor.lastrowid
    
    def update_location(self, location: Location) -> bool:
        """Update an existing location"""
        if location.id is None:
            return False
        
        location.modified = datetime.utcnow().isoformat()
        
        cursor = self.conn.cursor()
        cursor.execute("""
            UPDATE locations SET
                name=?, latitude=?, longitude=?, marker_type=?, coalition=?,
                tactical_symbol=?, icon=?, description=?, icao=?, iata=?,
                elevation_m=?, frequencies=?, runways=?, threat_level=?,
                unit_type=?, strength=?, modified=?, tags=?, metadata=?
            WHERE id=?
        """, (
            location.name,
            location.latitude,
            location.longitude,
            location.marker_type,
            location.coalition,
            location.tactical_symbol,
            location.icon,
            location.description,
            location.icao,
            location.iata,
            location.elevation_m,
            json.dumps(location.frequencies) if location.frequencies else None,
            json.dumps([r.to_dict() for r in location.runways]) if location.runways else None,
            location.threat_level,
            location.unit_type,
            location.strength,
            location.modified,
            json.dumps(location.tags) if location.tags else None,
            json.dumps(location.metadata) if location.metadata else None,
            location.id
        ))
        
        self.conn.commit()
        return cursor.rowcount > 0
    
    def delete_location(self, location_id: int) -> bool:
        """Delete a location by ID"""
        cursor = self.conn.cursor()
        cursor.execute("DELETE FROM locations WHERE id=?", (location_id,))
        self.conn.commit()
        return cursor.rowcount > 0
    
    def get_location(self, location_id: int) -> Optional[Location]:
        """Get a location by ID"""
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM locations WHERE id=?", (location_id,))
        row = cursor.fetchone()
        return self._row_to_location(row) if row else None
    
    def get_all_locations(self, 
                         marker_type: Optional[str] = None,
                         coalition: Optional[str] = None) -> List[Location]:
        """Get all locations with optional filtering"""
        cursor = self.conn.cursor()
        
        query = "SELECT * FROM locations WHERE 1=1"
        params = []
        
        if marker_type:
            query += " AND marker_type=?"
            params.append(marker_type)
        
        if coalition:
            query += " AND coalition=?"
            params.append(coalition)
        
        query += " ORDER BY name"
        
        cursor.execute(query, params)
        return [self._row_to_location(row) for row in cursor.fetchall()]
    
    def search_locations(self, query: str) -> List[Location]:
        """Search locations by name, ICAO, or description"""
        cursor = self.conn.cursor()
        search_pattern = f"%{query}%"
        cursor.execute("""
            SELECT * FROM locations 
            WHERE name LIKE ? OR icao LIKE ? OR iata LIKE ? OR description LIKE ?
            ORDER BY name
        """, (search_pattern, search_pattern, search_pattern, search_pattern))
        return [self._row_to_location(row) for row in cursor.fetchall()]
    
    def get_nearby_locations(self, 
                           latitude: float, 
                           longitude: float, 
                           radius_km: float = 50) -> List[Tuple[Location, float]]:
        """
        Get locations near a point with distance
        Returns list of (Location, distance_km) tuples
        """
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM locations")
        
        results = []
        for row in cursor.fetchall():
            location = self._row_to_location(row)
            distance = self._haversine_distance(
                latitude, longitude,
                location.latitude, location.longitude
            )
            if distance <= radius_km:
                results.append((location, distance))
        
        # Sort by distance
        results.sort(key=lambda x: x[1])
        return results
    
    def _row_to_location(self, row: sqlite3.Row) -> Location:
        """Convert database row to Location object"""
        return Location(
            id=row['id'],
            name=row['name'],
            latitude=row['latitude'],
            longitude=row['longitude'],
            marker_type=row['marker_type'],
            coalition=row['coalition'],
            tactical_symbol=row['tactical_symbol'],
            icon=row['icon'],
            description=row['description'],
            icao=row['icao'],
            iata=row['iata'],
            elevation_m=row['elevation_m'],
            frequencies=json.loads(row['frequencies']) if row['frequencies'] else None,
            runways=[Runway.from_dict(r) for r in json.loads(row['runways'])] if row['runways'] else None,
            threat_level=row['threat_level'],
            unit_type=row['unit_type'],
            strength=row['strength'],
            created=row['created'],
            modified=row['modified'],
            tags=json.loads(row['tags']) if row['tags'] else None,
            metadata=json.loads(row['metadata']) if row['metadata'] else None
        )
    
    @staticmethod
    def _haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculate distance between two points in kilometers using Haversine formula"""
        from math import radians, sin, cos, sqrt, atan2
        
        R = 6371  # Earth radius in kilometers
        
        lat1_rad = radians(lat1)
        lat2_rad = radians(lat2)
        delta_lat = radians(lat2 - lat1)
        delta_lon = radians(lon2 - lon1)
        
        a = sin(delta_lat / 2) ** 2 + cos(lat1_rad) * cos(lat2_rad) * sin(delta_lon / 2) ** 2
        c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c    
    @staticmethod
    def calculate_bearing(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculate initial bearing from point 1 to point 2 in degrees (0-360)"""
        from math import radians, degrees, sin, cos, atan2
        
        lat1_rad = radians(lat1)
        lat2_rad = radians(lat2)
        delta_lon = radians(lon2 - lon1)
        
        x = sin(delta_lon) * cos(lat2_rad)
        y = cos(lat1_rad) * sin(lat2_rad) - sin(lat1_rad) * cos(lat2_rad) * cos(delta_lon)
        
        bearing = degrees(atan2(x, y))
        # Normalize to 0-360
        return (bearing + 360) % 360
    
    @staticmethod
    def km_to_nautical_miles(km: float) -> float:
        """Convert kilometers to nautical miles"""
        return km * 0.539957    
    def export_to_json(self, filepath: Path):
        """Export all locations to JSON file"""
        locations = self.get_all_locations()
        data = {
            'version': '1.0',
            'exported': datetime.utcnow().isoformat(),
            'locations': [loc.to_dict() for loc in locations]
        }
        
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
    
    def import_from_json(self, filepath: Path) -> int:
        """Import locations from JSON file. Returns count of imported locations"""
        with open(filepath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        count = 0
        for loc_dict in data.get('locations', []):
            loc = Location.from_dict(loc_dict)
            loc.id = None  # Let database assign new ID
            self.add_location(loc)
            count += 1
        
        return count
    
    # Border CRUD operations
    def add_border(self, border: Border) -> int:
        """Add a new border to the database"""
        now = datetime.utcnow().isoformat()
        border.created = border.created or now
        border.modified = now
        
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO borders (name, points, description, color, created, modified)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (
            border.name,
            json.dumps(border.points),
            border.description,
            border.color,
            border.created,
            border.modified
        ))
        
        self.conn.commit()
        return cursor.lastrowid
    
    def update_border(self, border: Border) -> bool:
        """Update an existing border"""
        if border.id is None:
            return False
        
        border.modified = datetime.utcnow().isoformat()
        
        cursor = self.conn.cursor()
        cursor.execute("""
            UPDATE borders SET name=?, points=?, description=?, color=?, modified=?
            WHERE id=?
        """, (
            border.name,
            json.dumps(border.points),
            border.description,
            border.color,
            border.modified,
            border.id
        ))
        
        self.conn.commit()
        return cursor.rowcount > 0
    
    def delete_border(self, border_id: int) -> bool:
        """Delete a border by ID"""
        cursor = self.conn.cursor()
        cursor.execute("DELETE FROM borders WHERE id=?", (border_id,))
        self.conn.commit()
        return cursor.rowcount > 0
    
    def get_border(self, border_id: int) -> Optional[Border]:
        """Get a border by ID"""
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM borders WHERE id=?", (border_id,))
        row = cursor.fetchone()
        return self._row_to_border(row) if row else None
    
    def get_all_borders(self) -> List[Border]:
        """Get all borders"""
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM borders ORDER BY name")
        return [self._row_to_border(row) for row in cursor.fetchall()]
    
    def _row_to_border(self, row: sqlite3.Row) -> Border:
        """Convert database row to Border object"""
        return Border(
            id=row['id'],
            name=row['name'],
            points=json.loads(row['points']) if row['points'] else [],
            description=row['description'],
            color=row['color'],
            created=row['created'],
            modified=row['modified']
        )
    
    # Route CRUD operations
    def add_route(self, route: Route, waypoint_location_ids: List[int]) -> int:
        """
        Add a new route with waypoints
        
        Args:
            route: Route object
            waypoint_location_ids: List of location IDs in sequence order
            
        Returns:
            Route ID
        """
        now = datetime.utcnow().isoformat()
        route.created = route.created or now
        route.modified = now
        
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO routes (name, description, color, created, modified)
            VALUES (?, ?, ?, ?, ?)
        """, (route.name, route.description, route.color, route.created, route.modified))
        
        route_id = cursor.lastrowid
        
        # Add waypoints with calculated distances and headings
        for i, location_id in enumerate(waypoint_location_ids):
            distance_nm = None
            heading = None
            
            # Calculate distance and heading to next waypoint
            if i < len(waypoint_location_ids) - 1:
                loc1 = self.get_location(location_id)
                loc2 = self.get_location(waypoint_location_ids[i + 1])
                
                if loc1 and loc2:
                    distance_km = self._haversine_distance(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
                    distance_nm = self.km_to_nautical_miles(distance_km)
                    heading = self.calculate_bearing(
                        loc1.latitude, loc1.longitude,
                        loc2.latitude, loc2.longitude
                    )
            
            cursor.execute("""
                INSERT INTO route_waypoints (route_id, location_id, sequence, distance_nm, heading_mag)
                VALUES (?, ?, ?, ?, ?)
            """, (route_id, location_id, i, distance_nm, heading))
        
        self.conn.commit()
        return route_id
    
    def update_route(self, route: Route, waypoint_location_ids: Optional[List[int]] = None) -> bool:
        """
        Update an existing route
        
        Args:
            route: Route object with ID
            waypoint_location_ids: Optional new list of waypoints. If None, keeps existing waypoints
            
        Returns:
            True if successful
        """
        if route.id is None:
            raise ValueError("Route must have an ID to update")
        
        route.modified = datetime.utcnow().isoformat()
        
        cursor = self.conn.cursor()
        cursor.execute("""
            UPDATE routes SET name=?, description=?, color=?, modified=?
            WHERE id=?
        """, (route.name, route.description, route.color, route.modified, route.id))
        
        # Update waypoints if provided
        if waypoint_location_ids is not None:
            # Delete old waypoints
            cursor.execute("DELETE FROM route_waypoints WHERE route_id=?", (route.id,))
            
            # Add new waypoints
            for i, location_id in enumerate(waypoint_location_ids):
                distance_nm = None
                heading = None
                
                if i < len(waypoint_location_ids) - 1:
                    loc1 = self.get_location(location_id)
                    loc2 = self.get_location(waypoint_location_ids[i + 1])
                    
                    if loc1 and loc2:
                        distance_km = self._haversine_distance(
                            loc1.latitude, loc1.longitude,
                            loc2.latitude, loc2.longitude
                        )
                        distance_nm = self.km_to_nautical_miles(distance_km)
                        heading = self.calculate_bearing(
                            loc1.latitude, loc1.longitude,
                            loc2.latitude, loc2.longitude
                        )
                
                cursor.execute("""
                    INSERT INTO route_waypoints (route_id, location_id, sequence, distance_nm, heading_mag)
                    VALUES (?, ?, ?, ?, ?)
                """, (route.id, location_id, i, distance_nm, heading))
        
        self.conn.commit()
        return cursor.rowcount > 0
    
    def delete_route(self, route_id: int) -> bool:
        """Delete a route and its waypoints"""
        cursor = self.conn.cursor()
        cursor.execute("DELETE FROM route_waypoints WHERE route_id=?", (route_id,))
        cursor.execute("DELETE FROM routes WHERE id=?", (route_id,))
        self.conn.commit()
        return cursor.rowcount > 0
    
    def get_route(self, route_id: int) -> Optional[Route]:
        """Get a route by ID"""
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM routes WHERE id=?", (route_id,))
        row = cursor.fetchone()
        return self._row_to_route(row) if row else None
    
    def get_all_routes(self) -> List[Route]:
        """Get all routes"""
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM routes ORDER BY name")
        return [self._row_to_route(row) for row in cursor.fetchall()]
    
    def get_route_waypoints(self, route_id: int) -> List[Tuple[Location, RouteWaypoint]]:
        """
        Get all waypoints for a route with their location data
        
        Returns:
            List of (Location, RouteWaypoint) tuples ordered by sequence
        """
        cursor = self.conn.cursor()
        cursor.execute("""
            SELECT l.*, rw.id as rw_id, rw.route_id, rw.sequence, rw.distance_nm, rw.heading_mag
            FROM route_waypoints rw
            JOIN locations l ON rw.location_id = l.id
            WHERE rw.route_id = ?
            ORDER BY rw.sequence
        """, (route_id,))
        
        results = []
        for row in cursor.fetchall():
            location = self._row_to_location(row)
            waypoint = RouteWaypoint(
                id=row['rw_id'],
                route_id=row['route_id'],
                location_id=location.id,
                sequence=row['sequence'],
                distance_nm=row['distance_nm'],
                heading_mag=row['heading_mag']
            )
            results.append((location, waypoint))
        
        return results
    
    def get_route_with_waypoints(self, route_id: int) -> Optional[Dict[str, Any]]:
        """
        Get route with all waypoint details
        
        Returns:
            Dict with 'route' and 'waypoints' keys, or None if not found
        """
        route = self.get_route(route_id)
        if not route:
            return None
        
        waypoints = self.get_route_waypoints(route_id)
        
        # Calculate total distance
        total_distance_nm = sum(wp[1].distance_nm for wp in waypoints if wp[1].distance_nm)
        
        return {
            'route': route,
            'waypoints': waypoints,
            'total_distance_nm': total_distance_nm,
            'waypoint_count': len(waypoints)
        }
    
    def _row_to_route(self, row: sqlite3.Row) -> Route:
        """Convert database row to Route object"""
        return Route(
            id=row['id'],
            name=row['name'],
            description=row['description'],
            color=row['color'],
            created=row['created'],
            modified=row['modified']
        )
    
    def close(self):
        """Close database connection"""
        self.conn.close()
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


# Example usage and seed data
def seed_example_data(db: MarkersDatabase):
    """Add example airports and tactical markers"""
    
    # Example airport: Nellis AFB
    nellis = Location(
        name="Nellis Air Force Base",
        latitude=36.2361,
        longitude=-115.0342,
        marker_type=MarkerType.AIRPORT.value,
        coalition="BLUFOR",
        icon="airport_military",
        description="USAF fighter base, home of Red Flag exercises",
        icao="KLSV",
        iata="LSV",
        elevation_m=573,
        frequencies={
            "tower": 327.0,
            "ground": 360.2,
            "atis": 269.4
        },
        runways=[
            Runway(name="03L/21R", length_m=3048, width_m=46, heading=30, surface="concrete", ils=True),
            Runway(name="03R/21L", length_m=3048, width_m=46, heading=30, surface="concrete", ils=False)
        ],
        tags=["military", "fighter", "red_flag"]
    )
    
    # Example tactical marker: SAM site
    sam_site = Location(
        name="SA-10 Site Alpha",
        latitude=36.5,
        longitude=-115.5,
        marker_type=MarkerType.TACTICAL_OPFOR.value,
        coalition="OPFOR",
        tactical_symbol=TacticalSymbol.SAM.value,
        icon="sam_opfor",
        description="S-300 (SA-10) surface-to-air missile battery",
        threat_level=5,
        unit_type="SAM",
        strength=4,
        tags=["sam", "threat", "iads"]
    )
    
    # Example waypoint
    waypoint = Location(
        name="IP Vegas",
        latitude=36.1,
        longitude=-115.2,
        marker_type=MarkerType.WAYPOINT.value,
        icon="waypoint",
        description="Initial Point for north approach",
        tags=["waypoint", "navigation"]
    )
    
    db.add_location(nellis)
    db.add_location(sam_site)
    db.add_location(waypoint)
    
    print("✓ Seeded 3 example locations")


if __name__ == "__main__":
    # Create database and add example data
    db = MarkersDatabase()
    
    print(f"Database created at: {db.db_path}")
    
    # Check if empty, add example data
    if len(db.get_all_locations()) == 0:
        print("Adding example data...")
        seed_example_data(db)
    
    # Display all locations
    print("\nAll locations:")
    for loc in db.get_all_locations():
        print(f"  - {loc.name} ({loc.marker_type}) at {loc.latitude:.4f}, {loc.longitude:.4f}")
    
    db.close()
