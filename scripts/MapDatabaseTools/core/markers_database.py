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
    coalition: Optional[str] = None  # BLUFOR, OPFOR, NEUTRAL (legacy, use symbol_affiliation)
    tactical_symbol: Optional[str] = None
    icon: str = "default"
    description: str = ""
    
    # NATO Military Symbol fields (new)
    symbol_set: str = ""  # e.g., "ground_unit", "equipment", "installation"
    symbol_entity: str = ""  # e.g., "infantry", "armor", "artillery", "mortar", "missile"
    symbol_size: str = ""  # e.g., "squad", "platoon", "company", "battalion", "regiment"
    symbol_affiliation: str = "unknown"  # "friendly", "hostile", "neutral", "unknown"
    symbol_color: str = "#FFFF80"  # Background color based on affiliation
    symbol_modifier: str = ""  # Additional symbol modifiers (JSON)
    
    # Airport-specific
    icao: Optional[str] = None
    iata: Optional[str] = None
    elevation_m: Optional[float] = None
    elevation_ft: Optional[int] = None
    frequencies: Optional[Dict[str, float]] = None  # {"tower": 118.1, "ground": 121.9}
    runways: Optional[List[Runway]] = None  # Deprecated - use runways table
    
    # Tactical-specific
    threat_level: Optional[int] = None  # 1-5
    unit_type: Optional[str] = None
    strength: Optional[int] = None
    
    # Geography & admin
    country: Optional[str] = None
    region: Optional[str] = None
    timezone: Optional[str] = None  # IANA timezone
    
    # Source & verification
    source: Optional[str] = None
    verified: int = 0  # 0=no, 1=yes
    last_verified_at: Optional[str] = None
    
    # Geometry
    geom: Optional[str] = None  # GeoJSON or WKT
    
    # Elevation details
    elevation_source: Optional[str] = None
    elevation_accuracy_m: Optional[float] = None
    
    # Metadata (legacy)
    created: Optional[str] = None
    modified: Optional[str] = None
    tags: Optional[List[str]] = None  # Deprecated - use tags table
    metadata: Optional[Dict[str, Any]] = None
    
    # Audit fields (new)
    created_at: Optional[str] = None
    updated_at: Optional[str] = None
    deleted_at: Optional[str] = None  # Soft delete
    
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
        
        # Main locations table (v2: extended schema)
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
                elevation_ft INTEGER DEFAULT 0,
                frequencies TEXT,  -- JSON
                runways TEXT,  -- JSON (deprecated - use runways table)
                
                -- Tactical fields
                threat_level INTEGER,
                unit_type TEXT,
                strength INTEGER,
                
                -- Geography & admin
                country TEXT,
                region TEXT,
                timezone TEXT,
                
                -- Source & verification
                source TEXT,
                verified INTEGER DEFAULT 0,
                last_verified_at TEXT,
                
                -- Geometry
                geom TEXT,
                
                -- Elevation details
                elevation_source TEXT,
                elevation_accuracy_m REAL,
                
                -- Metadata (legacy)
                created TEXT NOT NULL,
                modified TEXT NOT NULL,
                tags TEXT,  -- JSON array (deprecated - use tags table)
                metadata TEXT,  -- JSON for custom fields
                
                -- Audit fields (new)
                created_at TEXT,
                updated_at TEXT,
                deleted_at TEXT
            )
        """)
        
        # Create indices for common queries
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_marker_type ON locations(marker_type)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_coalition ON locations(coalition)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_latitude_longitude ON locations(latitude, longitude)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_name ON locations(name)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_icao ON locations(icao)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_country ON locations(country)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_locations_verified ON locations(verified)")
        
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
        
        # Runways table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS runways (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                location_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                length_m INTEGER,
                length_ft INTEGER,
                width_m INTEGER,
                width_ft INTEGER,
                surface TEXT,
                heading_deg REAL,
                ils_frequency TEXT,
                has_lighting INTEGER DEFAULT 0,
                touchdown_start_lat REAL,
                touchdown_start_lon REAL,
                touchdown_end_lat REAL,
                touchdown_end_lon REAL,
                notes TEXT,
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS index_runways_location ON runways(location_id)")
        
        # Services table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS services (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                location_id INTEGER NOT NULL,
                service_type TEXT NOT NULL,
                available INTEGER DEFAULT 1,
                details TEXT,
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS index_services_location ON services(location_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_services_type ON services(service_type)")
        
        # Media table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS media (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                location_id INTEGER NOT NULL,
                uri TEXT NOT NULL,
                caption TEXT,
                media_type TEXT,
                is_primary INTEGER DEFAULT 0,
                created_at TEXT,
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS index_media_location ON media(location_id)")
        
        # Tags table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """)
        cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
        
        # Location-Tags join table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS location_tags (
                location_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY(location_id, tag_id),
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS index_location_tags_location ON location_tags(location_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_location_tags_tag ON location_tags(tag_id)")
        
        # Navaids table (v2)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS navaids (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                location_id INTEGER,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                ident TEXT,
                frequency TEXT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                elevation_m REAL,
                range_nm REAL,
                bearing_deg REAL,
                notes TEXT,
                FOREIGN KEY(location_id) REFERENCES locations(id) ON DELETE SET NULL
            )
        """)
        cursor.execute("CREATE INDEX IF NOT EXISTS index_navaids_location ON navaids(location_id)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_navaids_type ON navaids(type)")
        cursor.execute("CREATE INDEX IF NOT EXISTS index_navaids_ident ON navaids(ident)")
        
        # Insert sample tags (v2)
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('airbase')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('civilian')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('military')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('FARP')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('landmark')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('town')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('danger_zone')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('refuel_point')")
        cursor.execute("INSERT OR IGNORE INTO tags (name) VALUES ('training_area')")
        
        self.conn.commit()
    
    def add_location(self, location: Location) -> int:
        """Add a new location to the database"""
        now = datetime.utcnow().isoformat()
        location.created = location.created or now
        location.modified = now
        location.created_at = location.created_at or now
        location.updated_at = now
        
        # Auto-calculate elevation_ft if not provided
        if location.elevation_m is not None and location.elevation_ft is None:
            location.elevation_ft = int(round(location.elevation_m * 3.28084))
        
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO locations (
                name, latitude, longitude, marker_type, coalition, tactical_symbol,
                icon, description, icao, iata, elevation_m, elevation_ft,
                frequencies, runways, threat_level, unit_type, strength,
                country, region, timezone, source, verified, last_verified_at,
                geom, elevation_source, elevation_accuracy_m,
                created, modified, tags, metadata, created_at, updated_at, deleted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            location.elevation_ft,
            json.dumps(location.frequencies) if location.frequencies else None,
            json.dumps([r.to_dict() for r in location.runways]) if location.runways else None,
            location.threat_level,
            location.unit_type,
            location.strength,
            location.country,
            location.region,
            location.timezone,
            location.source,
            location.verified,
            location.last_verified_at,
            location.geom,
            location.elevation_source,
            location.elevation_accuracy_m,
            location.created,
            location.modified,
            json.dumps(location.tags) if location.tags else None,
            json.dumps(location.metadata) if location.metadata else None,
            location.created_at,
            location.updated_at,
            location.deleted_at
        ))
        
        location_id = cursor.lastrowid
        
        # Also insert runways into the separate runways table for Android app compatibility
        if location.runways:
            for runway in location.runways:
                length_ft = int(round(runway.length_m * 3.28084)) if runway.length_m else None
                width_ft = int(round(runway.width_m * 3.28084)) if runway.width_m else None
                cursor.execute("""
                    INSERT INTO runways (
                        location_id, name, length_m, length_ft, width_m, width_ft,
                        surface, heading_deg, has_lighting, notes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    location_id,
                    runway.name,
                    int(runway.length_m) if runway.length_m else None,
                    length_ft,
                    int(runway.width_m) if runway.width_m else None,
                    width_ft,
                    runway.surface,
                    runway.heading,
                    1 if runway.ils else 0,
                    "ILS" if runway.ils else None
                ))
        
        self.conn.commit()
        return location_id
    
    def update_location(self, location: Location) -> bool:
        """Update an existing location"""
        if location.id is None:
            return False
        
        now = datetime.utcnow().isoformat()
        location.modified = now
        location.updated_at = now
        
        # Auto-calculate elevation_ft if not provided
        if location.elevation_m is not None and location.elevation_ft is None:
            location.elevation_ft = int(round(location.elevation_m * 3.28084))
        
        cursor = self.conn.cursor()
        cursor.execute("""
            UPDATE locations SET
                name=?, latitude=?, longitude=?, marker_type=?, coalition=?,
                tactical_symbol=?, icon=?, description=?, icao=?, iata=?,
                elevation_m=?, elevation_ft=?, frequencies=?, runways=?, threat_level=?,
                unit_type=?, strength=?, country=?, region=?, timezone=?,
                source=?, verified=?, last_verified_at=?, geom=?,
                elevation_source=?, elevation_accuracy_m=?,
                modified=?, tags=?, metadata=?, updated_at=?, deleted_at=?
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
            location.elevation_ft,
            json.dumps(location.frequencies) if location.frequencies else None,
            json.dumps([r.to_dict() for r in location.runways]) if location.runways else None,
            location.threat_level,
            location.unit_type,
            location.strength,
            location.country,
            location.region,
            location.timezone,
            location.source,
            location.verified,
            location.last_verified_at,
            location.geom,
            location.elevation_source,
            location.elevation_accuracy_m,
            location.modified,
            json.dumps(location.tags) if location.tags else None,
            json.dumps(location.metadata) if location.metadata else None,
            location.updated_at,
            location.deleted_at,
            location.id
        ))
        
        # Also update runways in the separate runways table for Android app compatibility
        # Delete existing runways for this location first
        cursor.execute("DELETE FROM runways WHERE location_id=?", (location.id,))
        
        # Insert updated runways
        if location.runways:
            for runway in location.runways:
                length_ft = int(round(runway.length_m * 3.28084)) if runway.length_m else None
                width_ft = int(round(runway.width_m * 3.28084)) if runway.width_m else None
                cursor.execute("""
                    INSERT INTO runways (
                        location_id, name, length_m, length_ft, width_m, width_ft,
                        surface, heading_deg, has_lighting, notes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    location.id,
                    runway.name,
                    int(runway.length_m) if runway.length_m else None,
                    length_ft,
                    int(runway.width_m) if runway.width_m else None,
                    width_ft,
                    runway.surface,
                    runway.heading,
                    1 if runway.ils else 0,
                    "ILS" if runway.ils else None
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
        location_id = row['id']
        
        # Load runways from the separate runways table (preferred)
        # Fallback to JSON field if no runways in table
        runways = None
        cursor = self.conn.cursor()
        cursor.execute("SELECT * FROM runways WHERE location_id=? ORDER BY name", (location_id,))
        runway_rows = cursor.fetchall()
        
        if runway_rows:
            # Load from runways table
            runways = []
            for rw in runway_rows:
                runways.append(Runway(
                    name=rw['name'],
                    length_m=float(rw['length_m']) if rw['length_m'] else 0,
                    width_m=float(rw['width_m']) if rw['width_m'] else 0,
                    heading=float(rw['heading_deg']) if rw['heading_deg'] else 0,
                    surface=rw['surface'] or 'unknown',
                    ils=bool(rw['has_lighting'])  # Using has_lighting as ILS indicator
                ))
        elif row['runways']:
            # Fallback to JSON field
            runways = [Runway.from_dict(r) for r in json.loads(row['runways'])]
        
        return Location(
            id=location_id,
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
            runways=runways,
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
# NOTE: Legacy global seeding has been removed to avoid inserting unrelated example locations
# such as Nellis AFB / IP Vegas / SA-10. Use the region-specific script `add_sample_markers.py`
# to seed only the desired markers (e.g., Caucasus).
def seed_example_data(db: MarkersDatabase):
    """Legacy seeding removed — no-op to prevent inserting global example data."""
    print("Note: legacy seed_example_data removed; no default seed will be applied.")
    return


if __name__ == "__main__":
    # Create database and add example data
    db = MarkersDatabase()
    
    print(f"Database created at: {db.db_path}")
    
    # Prefer seeding with the Caucasus sample markers defined in add_sample_markers.py
    sample_locations = None
    try:
        # Make sure scripts/MapDatabaseTools is on sys.path so imports like 'from core...' work
        import sys
        scripts_dir = Path(__file__).parent.parent
        if str(scripts_dir) not in sys.path:
            sys.path.insert(0, str(scripts_dir))

        # Try a normal import (preferred)
        import add_sample_markers
        sample_locations = add_sample_markers.sample_locations
    except Exception:
        # Fallback: execute the file contents directly but strip out its imports
        try:
            import re
            sample_path = Path(__file__).parent.parent / "add_sample_markers.py"
            if sample_path.exists():
                source = sample_path.read_text()

                # Remove any imports from the local 'core' package to avoid circular imports
                source = re.sub(r"^\\s*(?:from|import)\\s+core(?:\\.[^\\s]*)?[^\\n]*\\n", "", source, flags=re.MULTILINE)

                # Execute in a sandbox namespace that provides Location/Runway/MarkerType
                ns = {
                    'Location': Location,
                    'Runway': Runway,
                    'MarkerType': MarkerType,
                    '__name__': 'add_sample_markers_sandbox'
                }
                try:
                    exec(source, ns)
                    sample_locations = ns.get('sample_locations')
                    if sample_locations is None:
                        print("Debug: add_sample_markers executed but 'sample_locations' not found in namespace")
                    else:
                        print(f"Debug: loaded {len(sample_locations)} sample_locations from add_sample_markers.py")
                except Exception as e:
                    print(f"Debug: exception executing add_sample_markers.py: {e}")
                    sample_locations = None
            else:
                sample_locations = None
        except Exception as e:
            # If anything goes wrong, don't crash the installer; we'll leave DB empty
            print(f"Warning: failed to load add_sample_markers.py: {e}")
            sample_locations = None

    if sample_locations:
        print("Seeding Caucasus sample locations from add_sample_markers.py (replacing DB contents)...")
        # Delete existing data so the DB contains only the Caucasus markers
        cur = db.conn.cursor()
        cur.execute("DELETE FROM route_waypoints")
        cur.execute("DELETE FROM runways")
        cur.execute("DELETE FROM services")
        cur.execute("DELETE FROM media")
        cur.execute("DELETE FROM navaids")
        cur.execute("DELETE FROM location_tags")
        cur.execute("DELETE FROM tags")
        cur.execute("DELETE FROM routes")
        cur.execute("DELETE FROM locations")
        db.conn.commit()

        for loc in sample_locations:
            db.add_location(loc)
        print(f"✓ Seeded {len(sample_locations)} sample locations (Caucasus)")
    else:
        # No caucasus samples available; fall back to legacy seeding only when DB empty
        if len(db.get_all_locations()) == 0:
            print("Adding example data...")
            seed_example_data(db)
    
    # Display all locations
    print("\nAll locations:")
    for loc in db.get_all_locations():
        print(f"  - {loc.name} ({loc.marker_type})")
    
    db.close()
