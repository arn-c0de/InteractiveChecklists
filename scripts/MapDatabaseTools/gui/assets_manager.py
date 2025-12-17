"""
Assets Manager Widget - Unified list for Markers (Locations) and Borders
Displays items categorized (Markers / Borders) with search and central action buttons
"""
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QListWidget, QListWidgetItem, QComboBox, QMessageBox
)
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QColor
from core.markers_database import MarkersDatabase, Location, Border
from gui.location_manager import LocationEditDialog
from gui.border_manager import BorderEditDialog
from typing import Optional, Tuple


class AssetsManagerWidget(QWidget):
    """Unified manager for Locations (markers) and Borders"""

    asset_selected = Signal(object)    # Emits tuple (type, obj)
    asset_edited = Signal(object)
    asset_deleted = Signal(object)
    draw_border_requested = Signal()

    def __init__(self, db: MarkersDatabase, parent=None):
        super().__init__(parent)
        self.db = db
        self.setup_ui()
        self.refresh_list()

    def setup_ui(self):
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)

        header = QLabel("📋 Assets (Markers & Borders)")
        header.setStyleSheet("font-weight: bold; font-size: 13pt; padding: 4px;")
        layout.addWidget(header)

        # Search and filter
        filter_layout = QHBoxLayout()
        self.search_edit = QLineEdit()
        self.search_edit.setPlaceholderText("Search by name…")
        self.search_edit.textChanged.connect(self.on_search_changed)
        filter_layout.addWidget(self.search_edit)

        self.category_combo = QComboBox()
        self.category_combo.addItems(["All", "Markers", "Borders"])
        self.category_combo.currentTextChanged.connect(self.on_search_changed)
        filter_layout.addWidget(self.category_combo)

        layout.addLayout(filter_layout)

        # Main list
        self.list_widget = QListWidget()
        self.list_widget.itemClicked.connect(self.on_item_clicked)
        self.list_widget.itemDoubleClicked.connect(self.on_item_double_clicked)
        layout.addWidget(self.list_widget)

        # Buttons row
        btn_layout = QHBoxLayout()
        self.add_marker_btn = QPushButton("➕ Add Marker")
        self.add_marker_btn.clicked.connect(self.add_marker)
        btn_layout.addWidget(self.add_marker_btn)

        self.draw_border_btn = QPushButton("➕ Draw Border")
        self.draw_border_btn.clicked.connect(self.request_draw_border)
        btn_layout.addWidget(self.draw_border_btn)

        self.edit_btn = QPushButton("✏️ Edit")
        self.edit_btn.clicked.connect(self.edit_selected)
        btn_layout.addWidget(self.edit_btn)

        self.delete_btn = QPushButton("🗑️ Delete")
        self.delete_btn.clicked.connect(self.delete_selected)
        btn_layout.addWidget(self.delete_btn)

        btn_layout.addStretch()
        layout.addLayout(btn_layout)

        self.setLayout(layout)

    def refresh_list(self):
        """Load markers and borders from DB and show categorized list"""
        self.list_widget.clear()
        q = self.search_edit.text().strip().lower()
        cat = self.category_combo.currentText()

        # Markers
        markers_header = QListWidgetItem("— MARKERS —")
        markers_header.setFlags(Qt.ItemFlag.NoItemFlags)
        markers_header.setForeground(QColor("#666"))
        self.list_widget.addItem(markers_header)

        markers = self.db.get_all_locations()
        for m in markers:
            if cat in ("All", "Markers") and (not q or q in (m.name or "").lower()):
                item = QListWidgetItem(f"📍 {m.name} ({m.latitude:.4f}, {m.longitude:.4f})")
                item.setData(Qt.ItemDataRole.UserRole, ("marker", m.id))
                self.list_widget.addItem(item)

        # Borders
        borders_header = QListWidgetItem("— BORDERS —")
        borders_header.setFlags(Qt.ItemFlag.NoItemFlags)
        borders_header.setForeground(QColor("#666"))
        self.list_widget.addItem(borders_header)

        borders = self.db.get_all_borders()
        for b in borders:
            if cat in ("All", "Borders") and (not q or q in (b.name or "").lower()):
                item = QListWidgetItem(f"🗺️ {b.name} ({len(b.points)} pts)")
                item.setData(Qt.ItemDataRole.UserRole, ("border", b.id))
                self.list_widget.addItem(item)

    def on_search_changed(self, *_):
        self.refresh_list()

    def on_item_clicked(self, item: QListWidgetItem):
        data = item.data(Qt.ItemDataRole.UserRole)
        if not data:
            return
        typ, obj_id = data
        if typ == "marker":
            loc = self.db.get_location(obj_id)
            self.asset_selected.emit(("marker", loc))
        elif typ == "border":
            border = self.db.get_border(obj_id)
            self.asset_selected.emit(("border", border))

    def on_item_double_clicked(self, item: QListWidgetItem):
        # Open edit dialog
        data = item.data(Qt.ItemDataRole.UserRole)
        if not data:
            return
        typ, obj_id = data
        if typ == "marker":
            loc = self.db.get_location(obj_id)
            self.edit_location_dialog(loc)
        elif typ == "border":
            border = self.db.get_border(obj_id)
            self.edit_border_dialog(border)

    def add_marker(self):
        # Open location edit dialog with empty location
        dialog = LocationEditDialog(self.db, None, None, self)
        if dialog.exec() == dialog.DialogCode.Accepted:
            loc = dialog.location
            try:
                loc_id = self.db.add_location(loc)
                loc.id = loc_id
                QMessageBox.information(self, "Added", f"Marker '{loc.name}' added.")
                self.refresh_list()
                self.asset_edited.emit(("marker", loc))
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to add marker: {e}")

    def request_draw_border(self):
        self.draw_border_requested.emit()

    def edit_selected(self):
        item = self.list_widget.currentItem()
        if not item:
            QMessageBox.information(self, "No Selection", "Please select an item to edit.")
            return
        data = item.data(Qt.ItemDataRole.UserRole)
        if not data:
            return
        typ, obj_id = data
        if typ == "marker":
            loc = self.db.get_location(obj_id)
            self.edit_location_dialog(loc)
        elif typ == "border":
            border = self.db.get_border(obj_id)
            self.edit_border_dialog(border)

    def delete_selected(self):
        item = self.list_widget.currentItem()
        if not item:
            QMessageBox.information(self, "No Selection", "Please select an item to delete.")
            return
        data = item.data(Qt.ItemDataRole.UserRole)
        if not data:
            return
        typ, obj_id = data
        if typ == "marker":
            loc = self.db.get_location(obj_id)
            if not loc:
                return
            r = QMessageBox.question(self, "Confirm Delete", f"Delete marker '{loc.name}'?")
            if r == QMessageBox.StandardButton.Yes:
                try:
                    self.db.delete_location(obj_id)
                    self.refresh_list()
                    self.asset_deleted.emit(("marker", obj_id))
                    QMessageBox.information(self, "Deleted", "Marker deleted.")
                except Exception as e:
                    QMessageBox.warning(self, "Error", f"Failed to delete marker: {e}")
        elif typ == "border":
            border = self.db.get_border(obj_id)
            if not border:
                return
            r = QMessageBox.question(self, "Confirm Delete", f"Delete border '{border.name}'?")
            if r == QMessageBox.StandardButton.Yes:
                try:
                    self.db.delete_border(obj_id)
                    self.refresh_list()
                    self.asset_deleted.emit(("border", obj_id))
                    QMessageBox.information(self, "Deleted", "Border deleted.")
                except Exception as e:
                    QMessageBox.warning(self, "Error", f"Failed to delete border: {e}")

    def edit_location_dialog(self, loc: Location):
        dialog = LocationEditDialog(self.db, loc, None, self)
        if dialog.exec() == dialog.DialogCode.Accepted:
            try:
                success = self.db.update_location(loc)
                if success:
                    QMessageBox.information(self, "Updated", "Marker updated.")
                    self.refresh_list()
                    self.asset_edited.emit(("marker", loc))
                else:
                    QMessageBox.warning(self, "Error", "Failed to update marker.")
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to update marker: {e}")

    def edit_border_dialog(self, border: Border):
        dialog = BorderEditDialog(border, self)
        if dialog.exec() == dialog.DialogCode.Accepted:
            try:
                success = self.db.update_border(border)
                if success:
                    QMessageBox.information(self, "Updated", "Border updated.")
                    self.refresh_list()
                    self.asset_edited.emit(("border", border))
                else:
                    QMessageBox.warning(self, "Error", "Failed to update border.")
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to update border: {e}")
