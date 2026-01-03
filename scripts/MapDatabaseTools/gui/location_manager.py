"""
Location Manager Widget - UI for managing tactical markers and locations
Part of DataPad GUI split-panel system
"""
import sys
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QListWidget,
    QListWidgetItem, QLabel, QLineEdit, QComboBox, QDialog, QFormLayout,
    QTextEdit, QDoubleSpinBox, QSpinBox, QGroupBox, QMessageBox,
    QInputDialog, QMenu, QCheckBox
)
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QFont, QColor, QAction
from core.markers_database import (
    MarkersDatabase, Location, MarkerType, TacticalSymbol, Runway
)
from core.marker_icons import (
    TacticalMarkerStyle, get_available_symbols, get_available_icons, 
    get_affiliation_color, is_valid_symbol
)
from typing import Optional, List


class RunwayEditDialog(QDialog):
    """Dialog for editing runway information"""
    
    def __init__(self, runway: Optional[Runway] = None, parent=None):
        super().__init__(parent)
        self.runway = runway or Runway(name="", length_m=0, width_m=0, heading=0, surface="concrete")
        self.setWindowTitle("Edit Runway" if runway else "Add Runway")
        self.setup_ui()
    
    def setup_ui(self):
        layout = QFormLayout()
        
        self.name_edit = QLineEdit(self.runway.name)
        self.name_edit.setPlaceholderText("e.g., 09/27")
        layout.addRow("Name:", self.name_edit)
        
        self.length_spin = QDoubleSpinBox()
        self.length_spin.setRange(0, 10000)
        self.length_spin.setValue(self.runway.length_m)
        self.length_spin.setSuffix(" m")
        layout.addRow("Length:", self.length_spin)
        
        self.width_spin = QDoubleSpinBox()
        self.width_spin.setRange(0, 200)
        self.width_spin.setValue(self.runway.width_m)
        self.width_spin.setSuffix(" m")
        layout.addRow("Width:", self.width_spin)
        
        self.heading_spin = QDoubleSpinBox()
        self.heading_spin.setRange(0, 360)
        self.heading_spin.setValue(self.runway.heading)
        self.heading_spin.setSuffix("°")
        layout.addRow("Heading:", self.heading_spin)
        
        self.surface_combo = QComboBox()
        self.surface_combo.addItems(["concrete", "asphalt", "grass", "dirt", "gravel"])
        self.surface_combo.setCurrentText(self.runway.surface)
        layout.addRow("Surface:", self.surface_combo)
        
        self.ils_combo = QComboBox()
        self.ils_combo.addItems(["No", "Yes"])
        self.ils_combo.setCurrentText("Yes" if self.runway.ils else "No")
        layout.addRow("ILS:", self.ils_combo)
        
        # Buttons
        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addWidget(cancel_btn)
        button_layout.addWidget(save_btn)
        
        layout.addRow(button_layout)
        self.setLayout(layout)
    
    def get_runway(self) -> Runway:
        """Get the edited runway"""
        return Runway(
            name=self.name_edit.text(),
            length_m=self.length_spin.value(),
            width_m=self.width_spin.value(),
            heading=self.heading_spin.value(),
            surface=self.surface_combo.currentText(),
            ils=self.ils_combo.currentText() == "Yes"
        )


class LocationEditDialog(QDialog):
    """Dialog for adding/editing locations"""

    # Signal to request coordinate picking from map
    pick_coordinates_requested = Signal()

    def __init__(self, db: MarkersDatabase, location: Optional[Location] = None,
                 prefill_coords: tuple = None, parent=None):
        super().__init__(parent)
        self.db = db
        self.location = location or Location()
        self.runways: List[Runway] = list(location.runways) if location and location.runways else []

        # Prefill coordinates if provided (e.g., from map click)
        if prefill_coords and not location:
            self.location.latitude, self.location.longitude = prefill_coords

        self.setWindowTitle("Edit Location" if location else "Add Location")
        self.setMinimumWidth(500)
        self.setup_ui()
    
    def setup_ui(self):
        layout = QVBoxLayout()
        
        # Basic Information
        basic_group = QGroupBox("Basic Information")
        basic_layout = QFormLayout()
        
        self.name_edit = QLineEdit(self.location.name)
        basic_layout.addRow("Name:*", self.name_edit)

        coord_layout = QHBoxLayout()
        self.lat_spin = QDoubleSpinBox()
        self.lat_spin.setRange(-90, 90)
        self.lat_spin.setDecimals(6)
        self.lat_spin.setValue(self.location.latitude)
        coord_layout.addWidget(QLabel("Lat:"))
        coord_layout.addWidget(self.lat_spin)

        self.lon_spin = QDoubleSpinBox()
        self.lon_spin.setRange(-180, 180)
        self.lon_spin.setDecimals(6)
        self.lon_spin.setValue(self.location.longitude)
        coord_layout.addWidget(QLabel("Lon:"))
        coord_layout.addWidget(self.lon_spin)

        # Add pin button to pick coordinates from map
        self.pin_btn = QPushButton("📍")
        self.pin_btn.setToolTip("Pick coordinates from map")
        self.pin_btn.setMaximumWidth(40)
        self.pin_btn.clicked.connect(self.on_pick_coordinates)
        coord_layout.addWidget(self.pin_btn)

        basic_layout.addRow("Coordinates:*", coord_layout)
        
        self.type_combo = QComboBox()
        for mt in MarkerType:
            self.type_combo.addItem(mt.value)
        self.type_combo.setCurrentText(self.location.marker_type)
        self.type_combo.currentTextChanged.connect(self.on_type_changed)
        basic_layout.addRow("Type:*", self.type_combo)
        
        self.coalition_combo = QComboBox()
        self.coalition_combo.addItems(["", "BLUFOR", "OPFOR", "NEUTRAL"])
        self.coalition_combo.setCurrentText(self.location.coalition or "")
        basic_layout.addRow("Coalition:", self.coalition_combo)
        
        self.icon_combo = QComboBox()
        self.update_icon_list()
        basic_layout.addRow("Icon:", self.icon_combo)
        
        self.desc_edit = QTextEdit()
        self.desc_edit.setPlaceholderText("Description...")
        self.desc_edit.setMaximumHeight(60)
        if self.location.description:
            self.desc_edit.setPlainText(self.location.description)
        basic_layout.addRow("Description:", self.desc_edit)
        
        # DCS Map selection
        self.map_combo = QComboBox()
        self.map_combo.addItems(["", "Caucasus", "Syria", "Persian Gulf", "Nevada", "Marianas", "Normandy", "The Channel", "South Atlantic", "Sinai"])
        self.map_combo.setCurrentText(self.location.map or "")
        basic_layout.addRow("DCS Map:", self.map_combo)
        
        # Static marker checkbox
        self.is_static_check = QCheckBox("Static Marker (Airport, Installation, etc.)")
        self.is_static_check.setChecked(self.location.is_static == 1)
        basic_layout.addRow("", self.is_static_check)
        
        basic_group.setLayout(basic_layout)
        layout.addWidget(basic_group)
        
        # Type-specific sections
        self.airport_group = self.create_airport_section()
        self.tactical_group = self.create_tactical_section()
        
        layout.addWidget(self.airport_group)
        layout.addWidget(self.tactical_group)
        
        # Update visibility based on type
        self.on_type_changed(self.location.marker_type)
        
        # Buttons
        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.save_location)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addStretch()
        button_layout.addWidget(cancel_btn)
        button_layout.addWidget(save_btn)
        layout.addLayout(button_layout)
        
        self.setLayout(layout)
    
    def update_icon_list(self):
        """Update icon combo - ONLY Android drawable symbols with category grouping!"""
        self.icon_combo.clear()
        
        # Get available Android symbols
        available = get_available_symbols()
        
        # Organize by category prefix
        categories = {
            'equipment_': [],
            'groundunit_': [],
            'installations_': [],
            'activities_': [],
            'unitsize_': [],
            'aircraft_': [],
            'helicopter_': [],
            'ship_': [],
            'vehicle_': [],
            'other': []
        }
        
        for symbol_entity, display_name in available.items():
            categorized = False
            for prefix in ['equipment_', 'groundunit_', 'installations_', 'activities_', 'unitsize_', 'aircraft_', 'helicopter_', 'ship_', 'vehicle_']:
                if symbol_entity.startswith(prefix):
                    categories[prefix].append((symbol_entity, display_name))
                    categorized = True
                    break
            if not categorized:
                categories['other'].append((symbol_entity, display_name))
        
        # Add to combo with category headers
        self.icon_combo.addItem("(None)", "")
        
        category_labels = {
            'equipment_': '━━━ EQUIPMENT ━━━',
            'groundunit_': '━━━ GROUND UNITS ━━━',
            'installations_': '━━━ INSTALLATIONS ━━━',
            'activities_': '━━━ ACTIVITIES ━━━',
            'unitsize_': '━━━ UNIT SIZE ━━━',
            'aircraft_': '━━━ AIRCRAFT ━━━',
            'helicopter_': '━━━ HELICOPTER ━━━',
            'ship_': '━━━ SHIP ━━━',
            'vehicle_': '━━━ VEHICLE ━━━',
            'other': '━━━ OTHER ━━━'
        }
        
        for prefix in ['equipment_', 'groundunit_', 'installations_', 'activities_', 'unitsize_', 'aircraft_', 'helicopter_', 'ship_', 'vehicle_', 'other']:
            items = categories[prefix]
            if items:
                # Add category header (non-selectable)
                self.icon_combo.addItem(category_labels[prefix], None)
                self.icon_combo.model().item(self.icon_combo.count() - 1).setEnabled(False)
                
                # Add items in this category
                for symbol_entity, display_name in sorted(items, key=lambda x: x[1]):
                    self.icon_combo.addItem(f"  {display_name}", symbol_entity)
        
        # Set current value if exists
        if hasattr(self.location, 'symbol_entity') and self.location.symbol_entity:
            idx = self.icon_combo.findData(self.location.symbol_entity)
            if idx >= 0:
                self.icon_combo.setCurrentIndex(idx)
    
    def create_airport_section(self) -> QGroupBox:
        """Create airport-specific fields"""
        group = QGroupBox("Airport Information")
        layout = QFormLayout()
        
        code_layout = QHBoxLayout()
        self.icao_edit = QLineEdit(self.location.icao or "")
        self.icao_edit.setPlaceholderText("ICAO")
        self.icao_edit.setMaxLength(4)
        code_layout.addWidget(QLabel("ICAO:"))
        code_layout.addWidget(self.icao_edit)
        
        self.iata_edit = QLineEdit(self.location.iata or "")
        self.iata_edit.setPlaceholderText("IATA")
        self.iata_edit.setMaxLength(3)
        code_layout.addWidget(QLabel("IATA:"))
        code_layout.addWidget(self.iata_edit)
        layout.addRow("Codes:", code_layout)
        
        self.elevation_spin = QDoubleSpinBox()
        self.elevation_spin.setRange(-500, 10000)
        self.elevation_spin.setValue(self.location.elevation_m or 0)
        self.elevation_spin.setSuffix(" m")
        layout.addRow("Elevation:", self.elevation_spin)
        
        # Runways
        runway_layout = QVBoxLayout()
        runway_header = QHBoxLayout()
        runway_header.addWidget(QLabel("Runways:"))
        add_runway_btn = QPushButton("Add Runway")
        add_runway_btn.clicked.connect(self.add_runway)
        runway_header.addWidget(add_runway_btn)
        runway_layout.addLayout(runway_header)
        
        self.runway_list = QListWidget()
        self.runway_list.setMaximumHeight(100)
        self.runway_list.itemDoubleClicked.connect(self.edit_runway)
        self.update_runway_list()
        runway_layout.addWidget(self.runway_list)
        
        remove_runway_btn = QPushButton("Remove Selected")
        remove_runway_btn.clicked.connect(self.remove_runway)
        runway_layout.addWidget(remove_runway_btn)
        
        layout.addRow(runway_layout)
        
        group.setLayout(layout)
        return group
    
    def create_tactical_section(self) -> QGroupBox:
        """Create tactical symbol section - ONLY Android drawable symbols with categories!"""
        group = QGroupBox("Military Symbol (Android Drawables Only)")
        layout = QFormLayout()
        
        # Symbol Entity (ONLY Android drawables!) - organized by category
        self.symbol_entity_combo = QComboBox()
        
        # Organize symbols by category prefix
        available = get_available_symbols()
        categories = {
            'equipment_': [],
            'groundunit_': [],
            'installations_': [],
            'activities_': [],
            'unitsize_': [],
            'aircraft_': [],
            'helicopter_': [],
            'ship_': [],
            'vehicle_': [],
            'other': []
        }
        
        for symbol_entity, display_name in available.items():
            categorized = False
            for prefix in ['equipment_', 'groundunit_', 'installations_', 'activities_', 'unitsize_', 'aircraft_', 'helicopter_', 'ship_', 'vehicle_']:
                if symbol_entity.startswith(prefix):
                    categories[prefix].append((symbol_entity, display_name))
                    categorized = True
                    break
            if not categorized:
                categories['other'].append((symbol_entity, display_name))
        
        # Add to combo with category headers
        self.symbol_entity_combo.addItem("(None)", "")
        
        category_labels = {
            'equipment_': '━━━ EQUIPMENT ━━━',
            'groundunit_': '━━━ GROUND UNITS ━━━',
            'installations_': '━━━ INSTALLATIONS ━━━',
            'activities_': '━━━ ACTIVITIES ━━━',
            'unitsize_': '━━━ UNIT SIZE ━━━',
            'aircraft_': '━━━ AIRCRAFT ━━━',
            'helicopter_': '━━━ HELICOPTER ━━━',
            'ship_': '━━━ SHIP ━━━',
            'vehicle_': '━━━ VEHICLE ━━━',
            'other': '━━━ OTHER ━━━'
        }
        
        for prefix in ['equipment_', 'groundunit_', 'installations_', 'activities_', 'unitsize_', 'aircraft_', 'helicopter_', 'ship_', 'vehicle_', 'other']:
            items = categories[prefix]
            if items:
                # Add category header (non-selectable)
                self.symbol_entity_combo.addItem(category_labels[prefix], None)
                self.symbol_entity_combo.model().item(self.symbol_entity_combo.count() - 1).setEnabled(False)
                
                # Add items in this category
                for symbol_entity, display_name in sorted(items, key=lambda x: x[1]):
                    self.symbol_entity_combo.addItem(f"  {display_name}", symbol_entity)
        
        if hasattr(self.location, 'symbol_entity') and self.location.symbol_entity:
            idx = self.symbol_entity_combo.findData(self.location.symbol_entity)
            if idx >= 0:
                self.symbol_entity_combo.setCurrentIndex(idx)
        layout.addRow("Symbol:", self.symbol_entity_combo)
        
        # Affiliation
        self.affiliation_combo = QComboBox()
        affiliations = [
            ("Friendly (Blue)", "friendly"),
            ("Hostile (Red)", "hostile"),
            ("Neutral (Green)", "neutral"),
            ("Unknown (Yellow)", "unknown")
        ]
        for display, value in affiliations:
            self.affiliation_combo.addItem(display, value)
        
        current_affiliation = getattr(self.location, 'symbol_affiliation', 'unknown')
        idx = self.affiliation_combo.findData(current_affiliation)
        if idx >= 0:
            self.affiliation_combo.setCurrentIndex(idx)
        layout.addRow("Affiliation:", self.affiliation_combo)
        
        # Legacy fields for compatibility
        self.threat_spin = QSpinBox()
        self.threat_spin.setRange(0, 5)
        self.threat_spin.setValue(self.location.threat_level or 0)
        self.threat_spin.setSpecialValueText("None")
        layout.addRow("Threat Level:", self.threat_spin)
        
        self.unit_edit = QLineEdit(self.location.unit_type or "")
        self.unit_edit.setPlaceholderText("Unit type...")
        layout.addRow("Unit Type:", self.unit_edit)
        
        self.strength_spin = QSpinBox()
        self.strength_spin.setRange(0, 100)
        self.strength_spin.setValue(self.location.strength or 0)
        self.strength_spin.setSpecialValueText("Unknown")
        layout.addRow("Strength:", self.strength_spin)
        
        group.setLayout(layout)
        return group
    
    def on_type_changed(self, marker_type: str):
        """Show/hide sections based on marker type"""
        is_airport = marker_type == MarkerType.AIRPORT.value
        is_tactical = marker_type.startswith("tactical_")
        
        self.airport_group.setVisible(is_airport)
        self.tactical_group.setVisible(is_tactical)
        self.update_icon_list()
    
    def add_runway(self):
        """Add a new runway"""
        dialog = RunwayEditDialog(parent=self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            self.runways.append(dialog.get_runway())
            self.update_runway_list()
    
    def edit_runway(self, item: QListWidgetItem):
        """Edit selected runway"""
        idx = self.runway_list.row(item)
        if 0 <= idx < len(self.runways):
            dialog = RunwayEditDialog(self.runways[idx], parent=self)
            if dialog.exec() == QDialog.DialogCode.Accepted:
                self.runways[idx] = dialog.get_runway()
                self.update_runway_list()
    
    def remove_runway(self):
        """Remove selected runway"""
        current_row = self.runway_list.currentRow()
        if 0 <= current_row < len(self.runways):
            self.runways.pop(current_row)
            self.update_runway_list()
    
    def update_runway_list(self):
        """Update runway list display"""
        self.runway_list.clear()
        for rwy in self.runways:
            item_text = f"{rwy.name} - {rwy.length_m:.0f}m x {rwy.width_m:.0f}m - {rwy.surface}"
            if rwy.ils:
                item_text += " (ILS)"
            self.runway_list.addItem(item_text)

    def on_pick_coordinates(self):
        """Handle pin button click - request coordinate picking from map"""
        # Hide dialog temporarily
        self.hide()
        # Emit signal to parent to start coordinate picking
        self.pick_coordinates_requested.emit()

    def set_coordinates(self, lat: float, lon: float):
        """Set coordinates from map click"""
        self.lat_spin.setValue(lat)
        self.lon_spin.setValue(lon)
        # Show dialog again
        self.show()

    def save_location(self):
        """Validate and save location"""
        # Validate required fields
        if not self.name_edit.text().strip():
            QMessageBox.warning(self, "Validation Error", "Name is required")
            return
        
        if self.lat_spin.value() == 0 and self.lon_spin.value() == 0:
            reply = QMessageBox.question(
                self, "Confirm", 
                "Coordinates are at 0,0. Is this correct?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
            )
            if reply == QMessageBox.StandardButton.No:
                return
        
        # Update location object
        self.location.name = self.name_edit.text().strip()
        self.location.latitude = self.lat_spin.value()
        self.location.longitude = self.lon_spin.value()
        self.location.marker_type = self.type_combo.currentText()
        self.location.coalition = self.coalition_combo.currentText() or None
        
        # Get selected Android symbol entity
        selected_symbol = self.icon_combo.currentData()
        if selected_symbol:
            self.location.symbol_entity = selected_symbol
        
        self.location.description = self.desc_edit.toPlainText()
        self.location.is_static = 1 if self.is_static_check.isChecked() else 0
        
        # Airport fields
        if self.airport_group.isVisible():
            self.location.icao = self.icao_edit.text().strip() or None
            self.location.iata = self.iata_edit.text().strip() or None
            self.location.elevation_m = self.elevation_spin.value() if self.elevation_spin.value() != 0 else None
            self.location.runways = self.runways if self.runways else None
        
        # Tactical fields - ONLY Android symbols!
        if self.tactical_group.isVisible():
            # Get symbol entity from combo
            symbol_entity = self.symbol_entity_combo.currentData()
            if symbol_entity:
                self.location.symbol_entity = symbol_entity
                # Validate it's an Android drawable
                if not is_valid_symbol(symbol_entity):
                    QMessageBox.warning(
                        self, "Invalid Symbol", 
                        f"Symbol '{symbol_entity}' is not available in Android drawables!"
                    )
                    return
            
            # Get affiliation
            affiliation = self.affiliation_combo.currentData()
            if affiliation:
                self.location.symbol_affiliation = affiliation
                self.location.symbol_color = get_affiliation_color(affiliation)
            
            # Legacy fields
            self.location.threat_level = self.threat_spin.value() if self.threat_spin.value() > 0 else None
            self.location.unit_type = self.unit_edit.text().strip() or None
            self.location.strength = self.strength_spin.value() if self.strength_spin.value() > 0 else None
        
        # Save to database
        try:
            if self.location.id:
                self.db.update_location(self.location)
            else:
                self.location.id = self.db.add_location(self.location)
            
            self.accept()
        except Exception as e:
            QMessageBox.critical(self, "Error", f"Failed to save location:\n{str(e)}")


class LocationManagerWidget(QWidget):
    """
    Widget for managing tactical markers and locations
    Displays list of locations with add/edit/delete functionality
    """
    
    # Signals
    location_selected = Signal(object)  # Emits Location when selected
    location_added = Signal(object)
    location_updated = Signal(object)
    location_deleted = Signal(int)
    
    def __init__(self, db: MarkersDatabase, parent=None):
        super().__init__(parent)
        self.db = db
        self.setup_ui()
        self.refresh_list()
    
    def setup_ui(self):
        """Setup the user interface"""
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        
        # Header
        header = QHBoxLayout()
        title = QLabel("📍 Locations & Markers")
        title.setFont(QFont("", 11, QFont.Weight.Bold))
        header.addWidget(title)
        
        # Filter combo
        self.filter_combo = QComboBox()
        self.filter_combo.addItem("All", None)
        self.filter_combo.addItem("Airports", MarkerType.AIRPORT.value)
        self.filter_combo.addItem("Waypoints", MarkerType.WAYPOINT.value)
        self.filter_combo.addItem("BLUFOR", "tactical_blufor")
        self.filter_combo.addItem("OPFOR", "tactical_opfor")
        self.filter_combo.addItem("Neutral", "tactical_neutral")
        self.filter_combo.currentIndexChanged.connect(self.refresh_list)
        header.addWidget(self.filter_combo)
        
        # Map filter combo
        self.map_filter_combo = QComboBox()
        self.map_filter_combo.addItem("All Maps", None)
        self.map_filter_combo.addItem("Caucasus", "Caucasus")
        self.map_filter_combo.addItem("Syria", "Syria")
        self.map_filter_combo.addItem("Persian Gulf", "Persian Gulf")
        self.map_filter_combo.addItem("Nevada", "Nevada")
        self.map_filter_combo.addItem("Marianas", "Marianas")
        self.map_filter_combo.addItem("Normandy", "Normandy")
        self.map_filter_combo.addItem("The Channel", "The Channel")
        self.map_filter_combo.addItem("South Atlantic", "South Atlantic")
        self.map_filter_combo.addItem("Sinai", "Sinai")
        self.map_filter_combo.addItem("(No Map)", "__NONE__")
        self.map_filter_combo.currentIndexChanged.connect(self.refresh_list)
        header.addWidget(self.map_filter_combo)
        
        layout.addLayout(header)
        
        # Search bar
        search_layout = QHBoxLayout()
        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Search locations...")
        self.search_edit.textChanged.connect(self.refresh_list)
        search_layout.addWidget(self.search_edit)
        layout.addLayout(search_layout)
        
        # Location list
        self.location_list = QListWidget()
        self.location_list.itemClicked.connect(self.on_item_clicked)
        self.location_list.itemDoubleClicked.connect(self.edit_selected_location)
        self.location_list.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.location_list.customContextMenuRequested.connect(self.show_context_menu)
        layout.addWidget(self.location_list)
        
        # Info label
        self.info_label = QLabel("0 locations")
        self.info_label.setStyleSheet("color: gray; font-size: 9pt;")
        layout.addWidget(self.info_label)
        
        # Buttons
        button_layout = QHBoxLayout()
        
        add_btn = QPushButton("➕ Add")
        add_btn.clicked.connect(self.add_location)
        button_layout.addWidget(add_btn)
        
        edit_btn = QPushButton("✏ Edit")
        edit_btn.clicked.connect(self.edit_selected_location)
        button_layout.addWidget(edit_btn)
        
        delete_btn = QPushButton("🗑 Delete")
        delete_btn.clicked.connect(self.delete_selected_location)
        button_layout.addWidget(delete_btn)
        
        layout.addLayout(button_layout)
        
        # Import/Export buttons
        io_layout = QHBoxLayout()
        import_btn = QPushButton("Import JSON")
        import_btn.clicked.connect(self.import_json)
        export_btn = QPushButton("Export JSON")
        export_btn.clicked.connect(self.export_json)
        io_layout.addWidget(import_btn)
        io_layout.addWidget(export_btn)
        layout.addLayout(io_layout)
        
        self.setLayout(layout)
    
    def refresh_list(self):
        """Refresh the location list"""
        self.location_list.clear()
        
        # Get filter
        filter_type = self.filter_combo.currentData()
        map_filter = self.map_filter_combo.currentData()
        search_query = self.search_edit.text().strip()
        
        # Get locations
        if search_query:
            locations = self.db.search_locations(search_query)
            if filter_type:
                locations = [loc for loc in locations if loc.marker_type == filter_type]
        else:
            locations = self.db.get_all_locations(marker_type=filter_type)
        
        # Apply map filter
        if map_filter:
            if map_filter == "__NONE__":
                locations = [loc for loc in locations if not loc.map]
            else:
                locations = [loc for loc in locations if loc.map == map_filter]
        
        # Sort by map (None first), then by name
        locations.sort(key=lambda loc: (loc.map or "", loc.name.lower()))
        
        # Track current map for grouping headers
        current_map = None
        
        # Populate list
        for loc in locations:
            # Add map group header if map changed
            loc_map = loc.map or "(No Map)"
            if loc_map != current_map:
                current_map = loc_map
                header_item = QListWidgetItem(f"━━━━━ {loc_map.upper()} ━━━━━")
                header_item.setFlags(Qt.ItemFlag.NoItemFlags)  # Non-selectable
                header_item.setForeground(QColor("#42A5F5"))
                header_font = QFont()
                header_font.setBold(True)
                header_item.setFont(header_font)
                self.location_list.addItem(header_item)
            
            # Check if location has Android drawable symbol
            if hasattr(loc, 'symbol_entity') and loc.symbol_entity and is_valid_symbol(loc.symbol_entity):
                # Use Android symbol
                affiliation = getattr(loc, 'symbol_affiliation', 'unknown')
                try:
                    symbol_text, color = TacticalMarkerStyle.get_style(loc.symbol_entity, affiliation)
                    symbol_display = loc.symbol_entity.upper()[:3]  # Short code
                except ValueError:
                    symbol_display = "???"
                    color = "#FFFF80"
            else:
                # No valid symbol
                symbol_display = "📍"
                color = "#808080"
            
            # Create item text with icon
            item_text = f"  [{symbol_display}]  {loc.name}"
            if loc.icao:
                item_text += f" ({loc.icao})"
            elif hasattr(loc, 'symbol_affiliation') and loc.symbol_affiliation:
                item_text += f" [{loc.symbol_affiliation}]"
            elif loc.coalition:
                item_text += f" [{loc.coalition}]"
            
            item = QListWidgetItem(item_text)
            item.setData(Qt.ItemDataRole.UserRole, loc)
            
            # Color code by type/affiliation
            if loc.marker_type == MarkerType.AIRPORT.value:
                item.setForeground(QColor("#8B4513"))
            elif loc.marker_type == "tactical_military":
                # Use symbol_affiliation for military symbols
                if hasattr(loc, 'symbol_affiliation'):
                    if loc.symbol_affiliation == "friendly":
                        item.setForeground(QColor("#00A8FF"))
                    elif loc.symbol_affiliation == "hostile":
                        item.setForeground(QColor("#FF4444"))
                    elif loc.symbol_affiliation == "neutral":
                        item.setForeground(QColor("#00FF00"))
                    else:
                        item.setForeground(QColor("#FFFF80"))
            elif "blufor" in loc.marker_type:
                item.setForeground(QColor("#00A8FF"))
            elif "opfor" in loc.marker_type:
                item.setForeground(QColor("#FF4444"))
            elif loc.marker_type == MarkerType.WAYPOINT.value:
                item.setForeground(QColor("#FFA500"))
            
            self.location_list.addItem(item)
        
        # Update info label
        self.info_label.setText(f"{len(locations)} location(s)")

    def select_location_by_id(self, location_id: int):
        """Select a location in the list by its database id and emit selection"""
        for i in range(self.location_list.count()):
            item = self.location_list.item(i)
            data = item.data(Qt.ItemDataRole.UserRole)
            if data and hasattr(data, 'id') and data.id == location_id:
                self.location_list.setCurrentItem(item)
                # Ensure visible
                self.location_list.scrollToItem(item)
                try:
                    self.location_selected.emit(data)
                except Exception:
                    pass
                return

    def on_item_clicked(self, item: QListWidgetItem):
        """Handle item click - emit signal with location"""
        location = item.data(Qt.ItemDataRole.UserRole)
        if location:
            self.location_selected.emit(location)
    
    def add_location(self):
        """Add a new location"""
        dialog = LocationEditDialog(self.db, parent=self)
        # Connect coordinate picking signal if parent supports it
        if hasattr(self.parent(), 'start_coordinate_picking'):
            dialog.pick_coordinates_requested.connect(lambda: self.parent().start_coordinate_picking(dialog))
        if dialog.exec() == QDialog.DialogCode.Accepted:
            self.refresh_list()
            self.location_added.emit(dialog.location)
    
    def edit_selected_location(self):
        """Edit the selected location"""
        current_item = self.location_list.currentItem()
        if current_item:
            location = current_item.data(Qt.ItemDataRole.UserRole)
            if location:
                dialog = LocationEditDialog(self.db, location, parent=self)
                # Connect coordinate picking signal if parent supports it
                if hasattr(self.parent(), 'start_coordinate_picking'):
                    dialog.pick_coordinates_requested.connect(lambda: self.parent().start_coordinate_picking(dialog))
                if dialog.exec() == QDialog.DialogCode.Accepted:
                    self.refresh_list()
                    self.location_updated.emit(dialog.location)
    
    def delete_selected_location(self):
        """Delete the selected location"""
        current_item = self.location_list.currentItem()
        if current_item:
            location = current_item.data(Qt.ItemDataRole.UserRole)
            if location:
                reply = QMessageBox.question(
                    self, "Confirm Delete",
                    f"Delete location '{location.name}'?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
                )
                
                if reply == QMessageBox.StandardButton.Yes:
                    self.db.delete_location(location.id)
                    self.refresh_list()
                    self.location_deleted.emit(location.id)
    
    def show_context_menu(self, pos):
        """Show context menu for location item"""
        item = self.location_list.itemAt(pos)
        if item:
            location = item.data(Qt.ItemDataRole.UserRole)
            
            menu = QMenu(self)
            
            edit_action = menu.addAction("✏ Edit")
            edit_action.triggered.connect(self.edit_selected_location)
            
            delete_action = menu.addAction("🗑 Delete")
            delete_action.triggered.connect(self.delete_selected_location)
            
            menu.addSeparator()
            
            copy_coords_action = menu.addAction("📋 Copy Coordinates")
            copy_coords_action.triggered.connect(
                lambda: self.copy_coordinates(location)
            )
            
            menu.exec(self.location_list.mapToGlobal(pos))
    
    def copy_coordinates(self, location: Location):
        """Copy location coordinates to clipboard"""
        from PySide6.QtWidgets import QApplication
        coords_text = f"{location.latitude:.6f}, {location.longitude:.6f}"
        QApplication.clipboard().setText(coords_text)
        self.info_label.setText(f"Copied: {coords_text}")
    
    def import_json(self):
        """Import locations from JSON file"""
        from PySide6.QtWidgets import QFileDialog
        filepath, _ = QFileDialog.getOpenFileName(
            self, "Import Locations", "", "JSON Files (*.json)"
        )
        
        if filepath:
            try:
                from pathlib import Path
                count = self.db.import_from_json(Path(filepath))
                QMessageBox.information(
                    self, "Import Successful",
                    f"Imported {count} location(s)"
                )
                self.refresh_list()
            except Exception as e:
                QMessageBox.critical(
                    self, "Import Failed",
                    f"Failed to import locations:\n{str(e)}"
                )
    
    def export_json(self):
        """Export locations to JSON file"""
        from PySide6.QtWidgets import QFileDialog
        filepath, _ = QFileDialog.getSaveFileName(
            self, "Export Locations", "locations_export.json", "JSON Files (*.json)"
        )
        
        if filepath:
            try:
                from pathlib import Path
                self.db.export_to_json(Path(filepath))
                QMessageBox.information(
                    self, "Export Successful",
                    f"Exported locations to:\n{filepath}"
                )
            except Exception as e:
                QMessageBox.critical(
                    self, "Export Failed",
                    f"Failed to export locations:\n{str(e)}"
                )


def main():
    """Main entry point for standalone Location Manager"""
    from PySide6.QtWidgets import QApplication
    
    app = QApplication(sys.argv)
    
    db = MarkersDatabase()
    
    # Add test data if empty
    if len(db.get_all_locations()) == 0:
        from core.markers_database import seed_example_data
        seed_example_data(db)
    
    widget = LocationManagerWidget(db)
    widget.setWindowTitle("Location Manager")
    widget.resize(400, 600)
    widget.show()
    
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
