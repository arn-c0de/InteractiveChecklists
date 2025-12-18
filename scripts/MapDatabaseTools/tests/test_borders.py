"""
Test script for Map Borders functionality
Tests database operations for borders
"""
import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from core.markers_database import MarkersDatabase, Border


def test_border_operations():
    """Test border CRUD operations"""
    print("🧪 Testing Map Borders functionality...\n")
    
    # Use temporary database
    db_path = Path(__file__).parent / "test_borders.db"
    if db_path.exists():
        db_path.unlink()
    
    db = MarkersDatabase(db_path)
    
    # Test 1: Create border
    print("✓ Test 1: Create border")
    border1 = Border(
        name="Caucasus North Region",
        points=[
            (43.5, 41.2),
            (43.6, 41.3),
            (43.7, 41.2),
            (43.6, 41.1)
        ],
        description="Northern section of Caucasus map",
        color="#FF0000"
    )
    
    border_id = db.add_border(border1)
    print(f"  Created border with ID: {border_id}")
    assert border_id > 0, "Border ID should be positive"
    
    # Test 2: Get border
    print("\n✓ Test 2: Get border")
    retrieved = db.get_border(border_id)
    assert retrieved is not None, "Border should be retrieved"
    assert retrieved.name == "Caucasus North Region", "Name should match"
    assert len(retrieved.points) == 4, "Should have 4 points"
    print(f"  Retrieved: {retrieved.name} with {len(retrieved.points)} points")
    
    # Test 3: Get all borders
    print("\n✓ Test 3: Get all borders")
    all_borders = db.get_all_borders()
    assert len(all_borders) == 1, "Should have 1 border"
    print(f"  Found {len(all_borders)} border(s)")
    
    # Test 4: Update border
    print("\n✓ Test 4: Update border")
    retrieved.name = "Updated Region Name"
    retrieved.color = "#00FF00"
    success = db.update_border(retrieved)
    assert success, "Update should succeed"
    
    updated = db.get_border(border_id)
    assert updated.name == "Updated Region Name", "Name should be updated"
    assert updated.color == "#00FF00", "Color should be updated"
    print(f"  Updated to: {updated.name} ({updated.color})")
    
    # Test 5: Create second border
    print("\n✓ Test 5: Create second border")
    border2 = Border(
        name="Caucasus South Region",
        points=[
            (42.5, 41.0),
            (42.8, 41.2),
            (42.6, 41.4)
        ],
        color="#0000FF"
    )
    border_id2 = db.add_border(border2)
    print(f"  Created second border with ID: {border_id2}")
    
    all_borders = db.get_all_borders()
    assert len(all_borders) == 2, "Should have 2 borders"
    print(f"  Total borders: {len(all_borders)}")
    
    # Test 6: Delete border
    print("\n✓ Test 6: Delete border")
    success = db.delete_border(border_id)
    assert success, "Delete should succeed"
    
    remaining = db.get_all_borders()
    assert len(remaining) == 1, "Should have 1 border remaining"
    print(f"  Deleted border {border_id}, {len(remaining)} remaining")
    
    # Test 7: Border to/from dict
    print("\n✓ Test 7: Serialization")
    border_dict = border2.to_dict()
    assert 'name' in border_dict, "Dict should have name"
    assert 'points' in border_dict, "Dict should have points"
    print(f"  Serialized border: {border_dict['name']}")
    
    border_from_dict = Border.from_dict(border_dict)
    assert border_from_dict.name == border2.name, "Names should match"
    print(f"  Deserialized: {border_from_dict.name}")
    
    # Cleanup
    db.close()
    db_path.unlink()
    
    print("\n✅ All tests passed!")


if __name__ == "__main__":
    try:
        test_border_operations()
    except Exception as e:
        print(f"\n❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
