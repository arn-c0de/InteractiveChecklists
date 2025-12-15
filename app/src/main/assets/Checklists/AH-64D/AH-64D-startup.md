# AH-64D Apache Startup Checklist

## Crew Coordination

- [ ] **Crew briefing** - Complete (mission, weather, threats, fuel, ROE)
- [ ] **Seat positions** - CPG (Front), Pilot (Rear)
- [ ] **ICS (Intercom System)** - Volume set, check communication between stations

## External Inspection (Walk-Around)

- [ ] **Main rotor head** - Check for leaks, hardware secure, no damage
- [ ] **Main rotor blades** - Check track, balance, no erosion/damage, tie-downs removed
- [ ] **Tail rotor** - Check for damage, proper pitch, gearbox secure
- [ ] **Engine cowlings** - Secure, no damage
- [ ] **Engine intakes** - Clear of FOD, screens intact
- [ ] **Exhaust IR suppressors** - Check condition
- [ ] **Fuel caps** - Secure and sealed
- [ ] **Fuel quantity** - Visual check, compare to gauge
- [ ] **Hydraulic reservoirs** - Check levels (Primary & Utility systems)
- [ ] **Landing gear** - Tires, brakes, no leaks, gear doors secure
- [ ] **Weapons pylons** - Check secure, safety pins removed (or in as appropriate)
- [ ] **TADS/PNVS turret** - Check clear, no obstructions
- [ ] **Pitot tubes** - Covers removed, no obstructions

## CPG Station - Before Battery On

- [ ] **Overhead panel circuit breakers** - All IN
- [ ] **Left console circuit breakers** - All IN
- [ ] **Right console circuit breakers** - All IN
- [ ] **TEDAC power switch** - OFF
- [ ] **MPD power switches (L/R)** - OFF
- [ ] **KU panel** - All switches OFF

## Pilot Station - Before Battery On

- [ ] **Overhead panel circuit breakers** - All IN
- [ ] **Left console circuit breakers** - All IN
- [ ] **Right console circuit breakers** - All IN
- [ ] **MPD power switches (L/R)** - OFF
- [ ] **EUFD (Engine Unit/Flight Display)** - Check OFF

## Electrical Power Up (Pilot Station)

- [ ] **BATT switch (Overhead console)** - ON
  - Wait for EUFD to power up and display "BATT ON"
- [ ] **EUFD** - Check displays properly, verify voltage ~28V DC
- [ ] **Warning/Caution panel** - Check lights test (press TEST button)
- [ ] **Master caution** - Press to reset if illuminated
- [ ] **Fire detection test switch** - FIRE DET/OVERHT TEST (hold)
  - Verify FIRE warnings illuminate for ENG1, ENG2, APU
  - Release switch, warnings should extinguish

## APU Start (Pilot Station)

- [ ] **APU button (Overhead)** - Press (light illuminates)
- [ ] **EUFD APU page** - Monitor
  - APU RPM increases from 0% to 100%
  - APU EGT (Exhaust Gas Temp) rises then stabilizes (below 700°C)
  - Wait for APU to reach 100% RPM (~60 seconds)
- [ ] **APU GEN switch** - Check moves to ON automatically when APU at speed
- [ ] **EUFD electrical page** - Verify APU GEN online (~115V AC, 400Hz)

## Engine Start (Pilot Station)

- [ ] **Rotor brake switch (Overhead)** - OFF
- [ ] **Collective** - Full down and detent locked
- [ ] **Cyclic** - Centered
- [ ] **Pedals** - Centered
- [ ] **Engine 1 power control lever (PCL)** - IDLE (forward to detent)
  - ENG 1 START light illuminates
  - Monitor EUFD Engine page:
    - NG (Gas Producer) starts increasing
    - At 15% NG: Ignition occurs, TGT (Turbine Gas Temp) rises
    - TGT limit: 810°C (avoid exceeding)
    - At 50-60% NG: Self-sustaining, starter cuts out
  - Monitor NP (Power Turbine) - begins rotating rotor system
  - Wait for stabilization: NG ~72%, NP 101%, TGT ~650-750°C
- [ ] **Engine 1 parameters (EUFD)** - Verify in green band
  - NG: ~72%
  - NP: 101% (±0.5%)
  - TGT: 650-750°C
  - Oil pressure: 40-90 psi
  - Oil temperature: Normal range
- [ ] **Engine 2 power control lever (PCL)** - IDLE (forward to detent)
  - Repeat monitoring procedure as Engine 1
  - TGT limit: 810°C
  - Wait for stabilization: NG ~72%, NP 101%, TGT ~650-750°C
- [ ] **Engine 2 parameters (EUFD)** - Verify in green band
  - NG: ~72%
  - NP: 101% (±0.5%)
  - TGT: 650-750°C
  - Oil pressure: 40-90 psi
- [ ] **Both engines synchronized** - NP should both read 101%

## After Engine Start

- [ ] **APU button (Overhead)** - Press to shut down APU (after both engines stable)
- [ ] **APU GEN** - Verify automatically goes offline
- [ ] **GEN 1 and GEN 2** - Verify both online on EUFD electrical page
- [ ] **EUFD HYD (Hydraulics) page** - Check
  - Primary system: 2,850-3,250 psi
  - Utility system: 2,850-3,250 psi
  - Accumulator pressure: 2,850-3,250 psi
- [ ] **Transmission oil pressure (EUFD)** - 40-90 psi, green band
- [ ] **Transmission oil temperature** - Check normal (rising is normal)
- [ ] **Rotor RPM (NP)** - Verify 101% both engines
- [ ] **Caution/Warning panel** - Check clear of warnings

## CPG Station Setup

- [ ] **MPD power switches (L/R)** - ON
  - Wait for MPD boot (~30 seconds)
  - Default pages load (TSD, WPN, etc.)
- [ ] **TEDAC power switch (Left console)** - ON
- [ ] **Keyboard Unit (KU) power** - ON
- [ ] **MPD Right - TSD page** - Select (press TSD button)
  - Verify GPS status (should show SAT count)
  - Check aircraft position symbol displays
- [ ] **MPD Left - WPN page** - Select (press WPN button)
  - Verify weapon inventory displays
- [ ] **TADS/PNVS panel (Left console)** - Configure
  - TADS power switch - ON
  - Wait for TADS initialization (~2 minutes for full alignment)
  - PNVS power switch - ON
- [ ] **FCR (Fire Control Radar) panel** - Configure
  - FCR power knob - ON
  - Wait for FCR self-test completion (~90 seconds)
  - Verify FCR READY indication on WPN page
- [ ] **ASE panel (Right console)** - Configure
  - ASE master switch - ON
  - RWR (Radar Warning Receiver) - ON
  - CMWS (Common Missile Warning System) - ON
  - LWS (Laser Warning System) - ON
  - Chaff/Flare program - Set as required
- [ ] **Video recorder switches** - Set as required
- [ ] **NVS mode switch** - Day or Night as required

## Pilot Station Setup

- [ ] **MPD power switches (L/R)** - ON
  - Wait for boot completion
- [ ] **MPD Right - FLT page** - Select
  - Verify attitude indicator functional
  - Check altimeter setting (press BARO, set QNH)
  - Verify airspeed indicator zero
- [ ] **MPD Left - TSD page** - Select
  - Verify GPS lock (should show 4+ satellites)
  - Check aircraft position matches known location
- [ ] **PNVS alignment** - Monitor status on MPD
  - Wait for alignment complete (DAS alignment required)
  - Alignment time: ~8 minutes for full accuracy
- [ ] **EUFD brightness** - Adjust as required
- [ ] **MPD brightness** - Adjust as required
- [ ] **Standby compass** - Check against GPS heading

## Communication Systems (Both Stations)

- [ ] **VHF radio (ARC-186)** - Configure
  - Frequency - Set as required
  - Mode - FM or AM as required
  - Squelch - Adjust
- [ ] **UHF radio (ARC-164)** - Configure
  - Frequency - Set as required
  - Mode - Set as required
  - Squelch - Adjust
- [ ] **FM1/FM2 radios (ARC-201D)** - Configure
  - Frequencies - Set as required
  - COMSEC (if required) - Load key
  - Squelch - Adjust
- [ ] **ICS panel** - Configure
  - ICS volume - Set comfortable level
  - Hot mic vs VOX - Select as preferred
  - Test communication between CPG and Pilot
- [ ] **IFF panel (Pilot station)** - Configure
  - Master switch - NORM
  - Mode 1 - Set code (if military)
  - Mode 3/A - Set assigned squawk code
  - Mode C - ON (altitude reporting)
  - Mode 4 - Set (if military/secure required)

## Flight Control System Check (Pilot)

- [ ] **FMC (Flight Management Computer) page on MPD** - Check
  - Verify FMC status normal
  - Check for any FMC cautions
- [ ] **Collective** - Full range of motion
  - Full down to full up
  - Check friction lock operates
- [ ] **Cyclic** - Full range of motion
  - Forward, aft, left, right to stops
  - Check centering and feel
- [ ] **Pedals** - Full range of motion
  - Full left and full right
  - Check centering
- [ ] **Force trim system** - Test
  - Press force trim release (paddle switch on cyclic)
  - Move cyclic slightly, release switch
  - Verify new trim position holds
- [ ] **Stabilator** - Check operation
  - Monitor stabilator position indicator on EUFD
  - Should respond automatically to longitudinal cyclic inputs
- [ ] **SAS (Stability Augmentation System)** - Verify engaged
  - Check SAS 1 and SAS 2 engaged on FMC page
  - No SAS caution lights

## Weapon Systems Check (CPG Station)

- [ ] **WPN page on MPD** - Review
  - Verify all weapon stations display correctly
  - Check inventory matches aircraft load
- [ ] **Master arm switch (Right console)** - SAFE (guard down)
- [ ] **M230 30mm Chain Gun** - Check
  - Rounds: Verify count on WPN page (typically 1,200 max)
  - Gun status: SAFE
  - Gun turret: Check moves with TADS LOS (Line of Sight)
- [ ] **Rocket inventory** - Verify on WPN page
  - Check zone/quantity (e.g., 4x 19-shot M261 launchers = 76 rockets)
  - Rocket type: M151 HE, M255 Flechette, M156 WP, etc.
- [ ] **Hellfire missiles** - Check status
  - Quantity: Verify count (typically up to 16 missiles)
  - Type: AGM-114K SAL, AGM-114L RF, etc.
  - Seeker status: Check READY on WPN page
  - Laser code: Set/verify if using SAL Hellfires
- [ ] **Stinger air-to-air missiles** - Check if loaded
  - Typically 4x Stingers on ATAS pylons
  - Verify seeker operational
- [ ] **Laser designator** - Test (ground safe)
  - Laser power - Safe until required
  - Laser code - Set/verify (1111-1788)
  - TADS laser channel - Set matching code

## Navigation System Setup (Both Stations)

- [ ] **TSD page** - Configure waypoints
  - Verify GPS satellites (minimum 4 for 3D navigation)
  - GPS accuracy: PDOP <4 ideal
  - Present position: Verify correct
- [ ] **Waypoint entry (KU)** - Enter mission waypoints
  - Home/Base waypoint
  - Route waypoints
  - Target area waypoints
  - Alternate/FARP locations
- [ ] **Route building** - Create mission route on TSD
- [ ] **Data Transfer System (DTS)** - Load mission data if available

## Final Checks Before Taxi

- [ ] **Canopy doors** - Closed and latched (or as required)
- [ ] **Seat harness** - Locked and tight (both CPG and Pilot)
  - Lap belt tight
  - Shoulder harness locked
- [ ] **Collective friction** - Adjust as desired
- [ ] **Caution/Warning panel** - Clear (no amber/red lights)
- [ ] **Fuel quantity (EUFD FUEL page)** - Check sufficient for mission
  - Internal: ~1,420 lbs (typical max)
  - External tanks: Verify if installed
- [ ] **Weight and balance** - Verify within limits
- [ ] **Flight controls** - Final check free and correct movement
- [ ] **Trim** - Release force trim and verify neutral
- [ ] **EUFD pages** - All systems green
- [ ] **MPD pages** - All systems ready
- [ ] **Parking brake (Pilot pedals)** - Set or release as required for taxi
- [ ] **Anti-collision lights** - ON
- [ ] **Position lights** - As required
- [ ] **Taxi clearance** - Request from tower/ground when ready
