#!/usr/bin/env python3
"""
Route Management Tool - Create and manage flight routes
"""

import sys
from pathlib import Path
from core.markers_database import MarkersDatabase, Location, Route, MarkerType

def create_example_route(db: MarkersDatabase):
    """Create an example route through Nevada"""
    
    # Create waypoints if they don't exist
    waypoints = []
    
    # Waypoint 1: Nellis AFB
    nellis = Location(
        name="Nellis AFB",
        latitude=36.2361,
        longitude=-115.0342,
        marker_type=MarkerType.AIRPORT.value,
        description="Starting point"
    )
    nellis_id = db.add_location(nellis)
    waypoints.append(nellis_id)
    
    # Waypoint 2: IP North
    ip_north = Location(
        name="IP North",
        latitude=36.5,
        longitude=-115.2,
        marker_type=MarkerType.WAYPOINT.value,
        description="Initial Point North"
    )
    ip_north_id = db.add_location(ip_north)
    waypoints.append(ip_north_id)
    
    # Waypoint 3: Target Area
    target = Location(
        name="Target Area Bravo",
        latitude=36.7,
        longitude=-115.5,
        marker_type=MarkerType.TARGET.value,
        description="Primary target area"
    )
    target_id = db.add_location(target)
    waypoints.append(target_id)
    
    # Waypoint 4: Egress Point
    egress = Location(
        name="Egress Point East",
        latitude=36.4,
        longitude=-115.0,
        marker_type=MarkerType.WAYPOINT.value,
        description="Exit route"
    )
    egress_id = db.add_location(egress)
    waypoints.append(egress_id)
    
    # Waypoint 5: Return to Nellis
    waypoints.append(nellis_id)
    
    # Create route
    route = Route(
        name="Training Mission Alpha",
        description="Standard training route - Nellis to target area and back",
        color="#00A8FF"
    )
    
    route_id = db.add_route(route, waypoints)
    
    print(f"✓ Created route: {route.name}")
    print(f"  Route ID: {route_id}")
    print(f"  Waypoints: {len(waypoints)}")
    
    # Display route details
    route_data = db.get_route_with_waypoints(route_id)
    if route_data:
        print(f"\nRoute Details:")
        print(f"  Total Distance: {route_data['total_distance_nm']:.1f} NM")
        print(f"\n  Waypoint Sequence:")
        for i, (loc, wp) in enumerate(route_data['waypoints']):
            print(f"    {i+1}. {loc.name:25} @ {loc.latitude:.4f}, {loc.longitude:.4f}")
            if wp.distance_nm and wp.heading_mag:
                print(f"       → Next: {wp.distance_nm:.1f} NM on heading {wp.heading_mag:.0f}°")

def list_routes(db: MarkersDatabase):
    """List all routes"""
    routes = db.get_all_routes()
    
    if not routes:
        print("No routes found.")
        return
    
    print(f"\n{'='*70}")
    print(f"{'ID':<5} {'Name':<30} {'Waypoints':<12} {'Total Dist':<12}")
    print(f"{'='*70}")
    
    for route in routes:
        route_data = db.get_route_with_waypoints(route.id)
        if route_data:
            wp_count = route_data['waypoint_count']
            total_dist = route_data['total_distance_nm']
            print(f"{route.id:<5} {route.name:<30} {wp_count:<12} {total_dist:<12.1f} NM")
    
    print(f"{'='*70}\n")

def show_route(db: MarkersDatabase, route_id: int):
    """Show detailed route information"""
    route_data = db.get_route_with_waypoints(route_id)
    
    if not route_data:
        print(f"Route {route_id} not found")
        return
    
    route = route_data['route']
    waypoints = route_data['waypoints']
    
    print(f"\n{'='*70}")
    print(f"Route: {route.name}")
    print(f"{'='*70}")
    print(f"Description: {route.description}")
    print(f"Color: {route.color}")
    print(f"Total Distance: {route_data['total_distance_nm']:.1f} NM")
    print(f"Waypoints: {len(waypoints)}")
    print(f"Created: {route.created}")
    print(f"Modified: {route.modified}")
    
    print(f"\n{'Seq':<5} {'Waypoint':<25} {'Position':<25} {'To Next':<20}")
    print(f"{'-'*75}")
    
    for i, (loc, wp) in enumerate(waypoints):
        pos = f"{loc.latitude:.4f}, {loc.longitude:.4f}"
        if wp.distance_nm and wp.heading_mag:
            to_next = f"{wp.distance_nm:.1f} NM @ {wp.heading_mag:.0f}°"
        else:
            to_next = "(end)"
        
        print(f"{i+1:<5} {loc.name:<25} {pos:<25} {to_next:<20}")
    
    print(f"{'='*70}\n")

def delete_route(db: MarkersDatabase, route_id: int):
    """Delete a route"""
    route = db.get_route(route_id)
    if not route:
        print(f"Route {route_id} not found")
        return
    
    confirm = input(f"Delete route '{route.name}'? (y/N): ")
    if confirm.lower() == 'y':
        db.delete_route(route_id)
        print(f"✓ Deleted route: {route.name}")
    else:
        print("Cancelled")

def main():
    """Main entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Route Management Tool")
    parser.add_argument('--db', type=str, help='Database path (default: app/maps/map_data.db in repo)')
    
    subparsers = parser.add_subparsers(dest='command', help='Command')
    
    # List routes
    subparsers.add_parser('list', help='List all routes')
    
    # Show route
    show_parser = subparsers.add_parser('show', help='Show route details')
    show_parser.add_argument('route_id', type=int, help='Route ID')
    
    # Create example
    subparsers.add_parser('create-example', help='Create example route')
    
    # Delete route
    delete_parser = subparsers.add_parser('delete', help='Delete route')
    delete_parser.add_argument('route_id', type=int, help='Route ID')
    
    args = parser.parse_args()
    
    # Open database
    db_path = Path(args.db) if args.db else None
    with MarkersDatabase(db_path) as db:
        if args.command == 'list':
            list_routes(db)
        elif args.command == 'show':
            show_route(db, args.route_id)
        elif args.command == 'create-example':
            create_example_route(db)
        elif args.command == 'delete':
            delete_route(db, args.route_id)
        else:
            parser.print_help()

if __name__ == "__main__":
    main()
