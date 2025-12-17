"""
Border Manager Widget - UI for managing map borders/boundaries
Allows drawing polygonal regions to mark DCS map sections
"""
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QListWidget,
    QListWidgetItem, QLabel, QLineEdit, QDialog, QFormLayout,
    QTextEdit, QMessageBox, QColorDialog, QGroupBox
)
from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QColor
from core.markers_database import MarkersDatabase, Border
from typing import Optional, List, Tuple


class BorderEditDialog(QDialog):
    """Dialog for editing border name and properties"""
    
    def __init__(self, border: Optional[Border] = None, parent=None):
        super().__init__(parent)
        self.border = border or Border()
        self.setWindowTitle("Edit Border" if border and border.id else "New Border")
        self.setup_ui()
    
    def setup_ui(self):
        layout = QFormLayout()
        
        self.name_edit = QLineEdit(self.border.name)
        self.name_edit.setPlaceholderText("e.g., Caucasus North")
        layout.addRow("Name:*", self.name_edit)
        
        self.desc_edit = QTextEdit()
        self.desc_edit.setPlaceholderText("Description...")
        self.desc_edit.setMaximumHeight(60)
        if self.border.description:
            self.desc_edit.setPlainText(self.border.description)
        layout.addRow("Description:", self.desc_edit)
        
        # Color picker
        color_layout = QHBoxLayout()
        self.color_label = QLabel("    ")
        self.color_label.setStyleSheet(f"background-color: {self.border.color}; border: 1px solid black;")
        self.color_label.setFixedSize(50, 25)
        color_btn = QPushButton("Choose Color")
        color_btn.clicked.connect(self.choose_color)
        color_layout.addWidget(self.color_label)
        color_layout.addWidget(color_btn)
        color_layout.addStretch()
        layout.addRow("Color:", color_layout)
        
        # Point count info
        if self.border.points:
            point_label = QLabel(f"{len(self.border.points)} points")
            layout.addRow("Points:", point_label)
        
        # Buttons
        button_layout = QHBoxLayout()
        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        cancel_btn = QPushButton("Cancel")
        cancel_btn.clicked.connect(self.reject)
        button_layout.addStretch()
        button_layout.addWidget(cancel_btn)
        button_layout.addWidget(save_btn)
        
        layout.addRow(button_layout)
        self.setLayout(layout)
    
    def choose_color(self):
        """Open color picker dialog"""
        color = QColorDialog.getColor(QColor(self.border.color), self)
        if color.isValid():
            self.border.color = color.name()
            self.color_label.setStyleSheet(f"background-color: {self.border.color}; border: 1px solid black;")
    
    def get_border(self) -> Border:
        """Get the edited border"""
        self.border.name = self.name_edit.text()
        self.border.description = self.desc_edit.toPlainText()
        return self.border


class BorderManagerWidget(QWidget):
    """Widget for managing map borders"""
    
    # Signals
    border_selected = Signal(object)  # Emits Border when selected
    border_added = Signal(object)     # Emits Border when added
    border_updated = Signal(object)   # Emits Border when updated
    border_deleted = Signal(int)      # Emits border_id when deleted
    draw_border_requested = Signal()  # Request to start drawing a new border
    finish_drawing_requested = Signal()  # Request to finish drawing (user clicked Finish)
    
    def __init__(self, db: MarkersDatabase, parent=None):
        super().__init__(parent)
        self.db = db
        self.is_drawing = False
        self.current_drawing_points: List[Tuple[float, float]] = []
        self.setup_ui()
        self.refresh_list()
    
    def setup_ui(self):
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        
        # Header
        header = QLabel("🗺️ Map Borders")
        header.setStyleSheet("font-weight: bold; font-size: 14pt; padding: 5px;")
        layout.addWidget(header)
        
        # Info label
        info = QLabel("Define map region boundaries")
        info.setStyleSheet("color: gray; font-size: 9pt; padding: 0 5px 5px 5px;")
        layout.addWidget(info)
        
        # Drawing status
        self.status_label = QLabel("")
        self.status_label.setStyleSheet("color: orange; font-weight: bold; padding: 5px;")
        self.status_label.setVisible(False)
        layout.addWidget(self.status_label)
        
        # Action buttons
        button_layout = QHBoxLayout()
        
        self.draw_btn = QPushButton("➕ Draw Border")
        self.draw_btn.setToolTip("Click on map to add points, click first point again to close")
        self.draw_btn.clicked.connect(self.start_drawing)
        button_layout.addWidget(self.draw_btn)
        
        self.finish_btn = QPushButton("✅ Finish")
        self.finish_btn.setVisible(False)
        self.finish_btn.setToolTip("Finish and save the currently drawn border")
        self.finish_btn.clicked.connect(self.on_finish_clicked)
        button_layout.addWidget(self.finish_btn)
        
        self.cancel_btn = QPushButton("❌ Cancel")
        self.cancel_btn.setVisible(False)
        self.cancel_btn.clicked.connect(self.cancel_drawing)
        button_layout.addWidget(self.cancel_btn)
        
        layout.addLayout(button_layout)
        
        # Border list
        self.list_widget = QListWidget()
        self.list_widget.itemClicked.connect(self.on_border_clicked)
        self.list_widget.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.list_widget.customContextMenuRequested.connect(self.show_context_menu)
        layout.addWidget(self.list_widget)
        
        # Bottom buttons
        bottom_layout = QHBoxLayout()
        
        edit_btn = QPushButton("✏️ Edit")
        edit_btn.clicked.connect(self.edit_selected_border)
        bottom_layout.addWidget(edit_btn)
        
        delete_btn = QPushButton("🗑️ Delete")
        delete_btn.clicked.connect(self.delete_selected_border)
        bottom_layout.addWidget(delete_btn)
        
        layout.addLayout(bottom_layout)
        
        self.setLayout(layout)
    
    def start_drawing(self):
        """Start drawing a new border"""
        self.is_drawing = True
        self.current_drawing_points = []
        self.draw_btn.setVisible(False)
        self.finish_btn.setVisible(True)
        self.cancel_btn.setVisible(True)
        self.status_label.setText("🖱️ Click on map to add points. Click 'Finish' or first point to close.")
        self.status_label.setVisible(True)
        self.draw_border_requested.emit()
        self.update_finish_button_state()

    def update_finish_button_state(self):
        """Enable finish button when there are at least 3 points"""
        if self.finish_btn:
            self.finish_btn.setEnabled(len(self.current_drawing_points) >= 3)
    
    def cancel_drawing(self):
        """Cancel current drawing"""
        self.is_drawing = False
        self.current_drawing_points = []
        self.draw_btn.setVisible(True)
        self.finish_btn.setVisible(False)
        self.cancel_btn.setVisible(False)
        self.status_label.setVisible(False)
        # Notify map to clear drawing
        from PySide6.QtCore import QTimer
        QTimer.singleShot(0, lambda: self.border_deleted.emit(-1))  # Use -1 to signal clear drawing
    
    def add_point(self, lat: float, lon: float) -> bool:
        """
        Add a point to the current drawing.
        Returns True if border is complete (closed).
        """
        if not self.is_drawing:
            return False
        
        # Check if this closes the polygon (clicked near first point)
        if len(self.current_drawing_points) >= 3:
            first_lat, first_lon = self.current_drawing_points[0]
            # Check if clicked close to first point (within ~100m)
            distance = ((lat - first_lat) ** 2 + (lon - first_lon) ** 2) ** 0.5
            if distance < 0.001:  # Approximately 100m
                # Close the polygon
                closed = self.finish_drawing()
                self.update_finish_button_state()
                return closed
        
        # Add point
        self.current_drawing_points.append((lat, lon))
        
        # Update status
        point_count = len(self.current_drawing_points)
        if point_count == 1:
            self.status_label.setText(f"✓ 1 point added. Continue clicking to add more points.")
        else:
            self.status_label.setText(f"✓ {point_count} points added. Click 'Finish' or first point to close border.")
        
        self.update_finish_button_state()
        return False
    
    def finish_drawing(self) -> bool:
        """Finish drawing and save the border"""
        if len(self.current_drawing_points) < 3:
            QMessageBox.warning(self, "Invalid Border", "Border must have at least 3 points.")
            return False
        
        # Create new border
        border = Border(
            name=f"Region {len(self.db.get_all_borders()) + 1}",
            points=self.current_drawing_points.copy(),
            color="#FF0000"
        )
        
        # Open edit dialog to set name and properties
        dialog = BorderEditDialog(border, self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            border = dialog.get_border()
            
            if not border.name:
                QMessageBox.warning(self, "Invalid Name", "Border name cannot be empty.")
                return False
            
            # Prevent duplicate names
            existing = [b for b in self.db.get_all_borders() if b.name == border.name]
            if existing:
                QMessageBox.warning(self, "Duplicate Name", "A border with this name already exists.")
                return False
            
            # Save to database with basic error handling
            try:
                border.id = self.db.add_border(border)
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to save border: {e}")
                return False
            
            # Reset drawing state
            self.is_drawing = False
            self.current_drawing_points = []
            self.draw_btn.setVisible(True)
            self.finish_btn.setVisible(False)
            self.cancel_btn.setVisible(False)
            self.status_label.setVisible(False)
            
            # Refresh list and notify
            self.refresh_list()
            self.border_added.emit(border)
            
            # Signal to map to clear drawing markers
            from PySide6.QtCore import QTimer
            QTimer.singleShot(0, lambda: self.border_deleted.emit(-1))  # -1 = clear drawing
            
            return True
        else:
            # Dialog cancelled, continue drawing
            return False
    
    def on_finish_clicked(self):
        """User clicked the Finish button - request closure from the GUI/map"""
        if len(self.current_drawing_points) < 3:
            QMessageBox.warning(self, "Invalid Border", "Border must have at least 3 points.")
            return
        self.finish_btn.setEnabled(False)
        self.finish_drawing_requested.emit()

    def refresh_list(self):
        """Refresh the border list from database"""
        self.list_widget.clear()
        borders = self.db.get_all_borders()
        
        for border in borders:
            item = QListWidgetItem(f"🗺️ {border.name} ({len(border.points)} pts)")
            item.setData(Qt.ItemDataRole.UserRole, border.id)
            self.list_widget.addItem(item)
        
        if not borders:
            item = QListWidgetItem("No borders defined yet")
            item.setFlags(Qt.ItemFlag.NoItemFlags)
            item.setForeground(QColor("gray"))
            self.list_widget.addItem(item)
    
    def on_border_clicked(self, item: QListWidgetItem):
        """Handle border item clicked"""
        border_id = item.data(Qt.ItemDataRole.UserRole)
        if border_id is not None:
            border = self.db.get_border(border_id)
            if border:
                self.border_selected.emit(border)
    
    def edit_selected_border(self):
        """Edit the selected border"""
        current_item = self.list_widget.currentItem()
        if not current_item:
            QMessageBox.information(self, "No Selection", "Please select a border to edit.")
            return
        
        border_id = current_item.data(Qt.ItemDataRole.UserRole)
        if border_id is None:
            return
        
        border = self.db.get_border(border_id)
        if not border:
            return
        
        dialog = BorderEditDialog(border, self)
        if dialog.exec() == QDialog.DialogCode.Accepted:
            border = dialog.get_border()
            
            if not border.name:
                QMessageBox.warning(self, "Invalid Name", "Border name cannot be empty.")
                return
            
            if self.db.update_border(border):
                self.refresh_list()
                self.border_updated.emit(border)
                QMessageBox.information(self, "Success", "Border updated successfully.")
            else:
                QMessageBox.warning(self, "Error", "Failed to update border.")
    
    def delete_selected_border(self):
        """Delete the selected border"""
        current_item = self.list_widget.currentItem()
        if not current_item:
            QMessageBox.information(self, "No Selection", "Please select a border to delete.")
            return
        
        border_id = current_item.data(Qt.ItemDataRole.UserRole)
        if border_id is None:
            return
        
        border = self.db.get_border(border_id)
        if not border:
            return
        
        reply = QMessageBox.question(
            self,
            "Confirm Delete",
            f"Delete border '{border.name}'?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        
        if reply == QMessageBox.StandardButton.Yes:
            if self.db.delete_border(border_id):
                self.refresh_list()
                self.border_deleted.emit(border_id)
                QMessageBox.information(self, "Success", "Border deleted successfully.")
            else:
                QMessageBox.warning(self, "Error", "Failed to delete border.")
    
    def show_context_menu(self, pos):
        """Show context menu for border list"""
        item = self.list_widget.itemAt(pos)
        if not item or item.data(Qt.ItemDataRole.UserRole) is None:
            return
        
        from PySide6.QtGui import QAction
        from PySide6.QtWidgets import QMenu
        
        menu = QMenu(self)
        
        edit_action = QAction("✏️ Edit", self)
        edit_action.triggered.connect(self.edit_selected_border)
        menu.addAction(edit_action)
        
        delete_action = QAction("🗑️ Delete", self)
        delete_action.triggered.connect(self.delete_selected_border)
        menu.addAction(delete_action)
        
        menu.exec(self.list_widget.mapToGlobal(pos))
