pcall(function()
	local lfs = require('lfs')
	local writeDir = lfs.writedir()

	local LOG_PATH = writeDir .. [[Scripts\player_aircraft.log]]
	local DEBUG_LOG_PATH = writeDir .. [[Scripts\player_aircraft_debug.log]]
	local JSON_PATH = writeDir .. [[Scripts\player_aircraft_parsed.jsonl]]
	local ENTITY_CONTACTS_PATH = writeDir .. [[Scripts\entity-contacts-parsed.jsonl]]  -- Batch 1: units 0-250
	local ENTITY_CONTACTS_PATH_2 = writeDir .. [[Scripts\entity-contacts-parsed-2.jsonl]]  -- Batch 2: units 250-500
	local ENTITY_CONTACTS_PATH_3 = writeDir .. [[Scripts\entity-contacts-parsed-3.jsonl]]  -- Batch 3: units 500-750
	local ENTITY_CONTACTS_PATH_4 = writeDir .. [[Scripts\entity-contacts-parsed-4.jsonl]]  -- Batch 4: units 750-1000
	local COMMAND_PATH = writeDir .. [[Scripts\forwarder_command.json]]
	local DEBUG_DUMP_TABLES = true -- set to false to disable table debug dumps
	local dumped_tables = {}
	local UPDATE_INTERVAL = 0.01 -- seconds (10 Hz update rate)
	local lastWrite = 0
	local lastCommandCheck = 0
	local COMMAND_CHECK_INTERVAL = 2.0 -- check for command file every 2 seconds
	local STREAMER_VERSION = "1.0.10"
	-- Maximum number of JSON lines to keep in the output file. Set to 0 to disable trimming.
	local MAX_JSON_LINES = 20  -- Keep only last 20 lines (2 seconds @ 10 Hz) - MINIMAL buffer
	local MAX_ENTITY_LINES = 2  -- Keep only last 2 lines for entity contacts (FAST load, minimal backlog)
	-- Trim interval: Only trim file periodically, NOT on every write (performance!)
	local TRIM_INTERVAL = 1.0  -- Trim file every 1 second for faster cleanup
	local lastTrim = 0
	local lastEntityTrim = 0
	-- If true, clear the JSON/log/debug files once when the export starts
	local CLEAR_ON_START = true

	-- State variables for smoothing/filtering telemetry values
	local lastAoA = nil
	local aoaHistory = {} -- ring buffer for moving average

	local function format_pos(p)
		if not p then return 'N/A' end
		if p.x and p.y and p.z then
			return string.format('x=%.1f y=%.1f z=%.1f', p.x, p.y, p.z)
		end
		if p.x and p.y then
			return string.format('x=%.1f y=%.1f', p.x, p.y)
		end
		return tostring(p)
	end

	local function try_field(tbl, names)
		for _,n in ipairs(names) do
			if tbl[n] then return tbl[n] end
		end
		return nil
	end

	-- JSON encoding helpers
	local function json_escape(str)
		if type(str) ~= 'string' then return tostring(str) end
		str = string.gsub(str, '\\', '\\\\')
		str = string.gsub(str, '"', '\\"')
		str = string.gsub(str, '\n', '\\n')
		str = string.gsub(str, '\r', '\\r')
		str = string.gsub(str, '\t', '\\t')
		return str
	end

	local function json_encode_value(val)
		local t = type(val)
		if t == 'string' then
			return '"' .. json_escape(val) .. '"'
		elseif t == 'number' then
			if val ~= val then return 'null' end -- NaN
			if val == math.huge or val == -math.huge then return 'null' end -- Inf
			return tostring(val)
		elseif t == 'boolean' then
			return val and 'true' or 'false'
		elseif t == 'table' then
			return json_encode_table(val)
		else
			return 'null'
		end
	end

	function json_encode_table(tbl)
		if not tbl then return 'null' end
		local isArray = false
		local maxIndex = 0
		for k, v in pairs(tbl) do
			if type(k) == 'number' and k > 0 and k == math.floor(k) then
				isArray = true
				if k > maxIndex then maxIndex = k end
			else
				isArray = false
				break
			end
		end
		
		if isArray then
			local parts = {}
			for i = 1, maxIndex do
				parts[i] = json_encode_value(tbl[i])
			end
			return '[' .. table.concat(parts, ',') .. ']'
		else
			local parts = {}
			for k, v in pairs(tbl) do
				local key = tostring(k)
				table.insert(parts, '"' .. json_escape(key) .. '":' .. json_encode_value(v))
			end
			return '{' .. table.concat(parts, ',') .. '}'
		end
	end

	local function safe_get(func, default)
		local ok, result = pcall(func)
		if ok and result ~= nil then return result end
		return default
	end

	-- Utility: round numbers to given decimals
	local function round(v, decimals)
		local m = 10^(decimals or 2)
		if type(v) ~= 'number' then return v end
		if v >= 0 then return math.floor(v*m + 0.5)/m else return math.ceil(v*m - 0.5)/m end
	end

	-- Utility: write short debug lines to the debug log when enabled
	local function debug_log(msg)
		if not DEBUG_DUMP_TABLES then return end
		pcall(function()
			local f = io.open(DEBUG_LOG_PATH, 'a')
			if not f then return end
			f:write(os.date('%Y-%m-%d %H:%M:%S') .. ' ' .. tostring(msg) .. '\n')
			f:close()
		end)
	end

	-- Check for live commands from forwarder (updates interval dynamically)
	local function check_live_commands()
		pcall(function()
			local f = io.open(COMMAND_PATH, 'r')
			if not f then return end
			local content = f:read('*all')
			f:close()

			if not content or content == '' then return end

			-- Simple JSON parsing for {updateInterval: 0.5}
			local interval = string.match(content, '"updateInterval"%s*:%s*([%d%.]+)')
			if interval then
				local newInterval = tonumber(interval)
				if newInterval and newInterval > 0 and newInterval ~= UPDATE_INTERVAL then
					UPDATE_INTERVAL = newInterval
					debug_log('Live update: UPDATE_INTERVAL changed to ' .. tostring(newInterval) .. ' seconds')
				end
			end
		end)
	end

	-- Generate high-precision timestamp with milliseconds
	local function generate_timestamp()
		local modelTime = LoGetModelTime()
		if not modelTime then modelTime = os.clock() end
		local milliseconds = math.floor((modelTime * 1000) % 1000)
		return string.format('%s.%03dZ', os.date('!%Y-%m-%dT%H:%M:%S'), milliseconds)
	end

	-- Maximum distance for unit tracking (meters) - 150km radius for better tactical awareness
	local MAX_UNIT_DISTANCE = 100000  -- 150km (increased from 50km)
	local MAX_UNITS_PER_BATCH = 250  -- Units per batch file - optimized for UDP packet size (~65KB)
	local MAX_TOTAL_UNITS = 1000  -- Maximum total units across all batches (4 batches × 250 units)
	
	-- Collect all nearby units (aircraft, ground, ships, structures)
	-- NEW APPROACH: Collect ALL units in range first, then slice by batch
	local allUnitsCache = nil  -- Cache all units to avoid recalculating
	local allUnitsCacheTime = 0
	local CACHE_DURATION = 0.1  -- Cache for 0.1 seconds (1 frame @ 10 Hz) - REDUCED for faster aircraft updates
	
	local function collect_all_nearby_units()
		local selfData = safe_get(function() return LoGetSelfData() end, nil)

		if not selfData or not selfData.LatLongAlt then
			return {}  -- No player position
		end

		local playerLat = selfData.LatLongAlt.Lat
		local playerLon = selfData.LatLongAlt.Long
		local playerAlt = selfData.LatLongAlt.Alt
		
		-- Get all world objects
		local worldObjects = safe_get(function() return LoGetWorldObjects() end, nil)
		
		if not worldObjects or type(worldObjects) ~= 'table' then
			return {}
		end
		
		local allUnits = {}
		
		-- Collect ALL units within range
		for objId, objData in pairs(worldObjects) do
			if type(objData) == 'table' and objData.LatLongAlt then
				local lat = objData.LatLongAlt.Lat or 0
				local lon = objData.LatLongAlt.Long or 0
				local alt = objData.LatLongAlt.Alt or 0
				
				-- Calculate distance
				local dLat = (lat - playerLat) * 111000
				local dLon = (lon - playerLon) * 111000 * math.cos(math.rad(playerLat))
				local dAlt = alt - playerAlt
				local distance = math.sqrt(dLat^2 + dLon^2 + dAlt^2)
				
				-- Only process units within max distance
				if distance <= MAX_UNIT_DISTANCE then
					-- Calculate bearing to target (0-360 degrees, 0=North)
					local bearing = math.deg(math.atan2(dLon, dLat))
					if bearing < 0 then bearing = bearing + 360 end
					
					-- Calculate bearing to target (0-360 degrees, 0=North)
					local bearing = math.deg(math.atan2(dLon, dLat))
					if bearing < 0 then bearing = bearing + 360 end
					
					-- Determine category from Type structure
					local categoryName = 'unknown'
					local unitName = objData.Name or ''

					-- Name-based heuristics for structures
					if unitName:find('HELIPAD') or unitName:find('FARP') or unitName:find('Invisible FARP') then
						categoryName = 'structure'
					elseif unitName:find('Bunker') or unitName:find('house') or unitName:find('Building') then
						categoryName = 'structure'
					elseif objData.Type and type(objData.Type) == 'table' then
						local level1 = objData.Type.level1 or 0
						local level3 = objData.Type.level3 or 0

						if level1 == 1 then
							if level3 == 1 then
								categoryName = 'aircraft'
							elseif level3 == 6 then
								categoryName = 'helicopter'
							else
								categoryName = 'aircraft'
							end
						elseif level1 == 2 then
							categoryName = 'ground'
						elseif level1 == 3 then
							categoryName = 'ship'
						elseif level1 == 4 then
							categoryName = 'structure'
						elseif level1 == 5 then
							categoryName = 'weapon'
						elseif level1 == 0 then
							categoryName = 'structure'
						end
					end
					
					local dcsCoal = objData.Coalition or 0
					
					-- Coalition umkehren
					if type(dcsCoal) == 'string' then
						if dcsCoal == 'Allies' then
							dcsCoal = 'Enemies'
						elseif dcsCoal == 'Enemies' then
							dcsCoal = 'Allies'
						end
					end
					
					-- Get speed (m/s)
					local speed = 0
					if objData.Velocity then
						local vx = objData.Velocity.x or 0
						local vy = objData.Velocity.y or 0
						local vz = objData.Velocity.z or 0
						speed = math.sqrt(vx^2 + vy^2 + vz^2)
					end
					
					-- Create unit entry with distance for sorting
					local unit = {
						dcsId = tostring(objId),
						name = objData.Name or 'Unknown',
						type = objData.Type and objData.Type.level4 or 'Unknown',
						category = categoryName,
						coalition = dcsCoal,
						latitude = round(lat, 6),
						longitude = round(lon, 6),
						altitude = round(alt, 2),
						heading = round(math.deg(objData.Heading or 0), 2),
						speed = round(speed, 2),
						distance = round(distance, 2),
						bearing = round(bearing, 2),
						country = objData.Country or 0,
						group = objData.GroupName or '',
						pilot = objData.UnitName or '',
						health = objData.Life and round(objData.Life, 2) or nil
					}
					
					table.insert(allUnits, unit)
				end
			end
		end
		
		-- Sort by PRIORITY then distance: Aircraft/Helicopters first (they move fast!)
		-- This ensures they're always in Batch 1+2 (sent more frequently in rotation)
		table.sort(allUnits, function(a, b)
			local priorityA = (a.category == 'aircraft' or a.category == 'helicopter') and 0 or 1
			local priorityB = (b.category == 'aircraft' or b.category == 'helicopter') and 0 or 1

			if priorityA ~= priorityB then
				return priorityA < priorityB  -- Aircraft/Heli first
			else
				return a.distance < b.distance  -- Then by distance
			end
		end)

		return allUnits
	end
	
	-- Get units for specific batch (uses cache)
	local function collect_nearby_units(batchIndex)
		batchIndex = batchIndex or 1
		
		-- Check cache
		local now = LoGetModelTime() or os.clock()
		if allUnitsCache and (now - allUnitsCacheTime) < CACHE_DURATION then
			-- Use cached data
		else
			-- Refresh cache
			allUnitsCache = collect_all_nearby_units()
			allUnitsCacheTime = now
		end
		
		-- Slice for this batch
		local startIdx = ((batchIndex - 1) * MAX_UNITS_PER_BATCH) + 1
		local endIdx = math.min(startIdx + MAX_UNITS_PER_BATCH - 1, #allUnitsCache)
		
		local batchUnits = {}
		for i = startIdx, endIdx do
			table.insert(batchUnits, allUnitsCache[i])
		end
		
		local hasMore = endIdx < #allUnitsCache
		
		return batchUnits, hasMore
	end

	-- Collect comprehensive telemetry data
	local function collect_telemetry()
		local data = {
			timestamp = generate_timestamp(),
			streamer_version = STREAMER_VERSION,
			dataAge = 0.250,
			updateRate = 1.0 / UPDATE_INTERVAL
		}

		-- Basic aircraft info from LoGetSelfData
		local selfData = safe_get(function() return LoGetSelfData() end, nil)
		if selfData and type(selfData) == 'table' then
			data.aircraft = selfData.Name or selfData.Type or selfData.type or 'UNKNOWN'
			data.unitName = selfData.UnitName or selfData.unitName or selfData.Name or ''
			data.lat = selfData.LatLongAlt and selfData.LatLongAlt.Lat or 0
			data.long = selfData.LatLongAlt and selfData.LatLongAlt.Long or 0
			data.alt = selfData.LatLongAlt and selfData.LatLongAlt.Alt or 0
			data.heading = selfData.Heading or selfData.heading or 0
			data.pitch = selfData.Pitch or selfData.pitch or 0
			data.bank = selfData.Bank or selfData.bank or 0
			
			data.country = selfData.Country or 0
			data.group = selfData.GroupName or ''
			data.unitID = tostring(selfData.ID or selfData.UnitId or 'N/A')
			
			-- Terrain elevation (for AGL calculation)
			local terrainAlt = safe_get(function() return LoGetAltitudeAboveSeaLevel() end, nil)
			local groundAlt = safe_get(function() return LoGetAltitudeAboveGroundLevel() end, nil)
			if terrainAlt and groundAlt and data.alt then
				data.terrainElevation = data.alt - groundAlt
			else
				data.terrainElevation = nil
			end
		end

		-- Position (DCS coordinates)
		local pos = safe_get(function() return LoGetWorldObjects() end, nil)
		if selfData and selfData.Position then
			data.pos = {
				x = selfData.Position.x or 0,
				y = selfData.Position.y or 0,
				z = selfData.Position.z or 0
			}
		end

		-- Speed & Vertical
		local ias = safe_get(function() return LoGetIndicatedAirSpeed() end, nil)
		local tas = safe_get(function() return LoGetTrueAirSpeed() end, nil)
		local gs = safe_get(function() return LoGetGroundSpeed() end, nil)
		local vs = safe_get(function() return LoGetVerticalVelocity() end, nil)
		local mach = safe_get(function() return LoGetMachNumber() end, nil)
		
		if ias then data.indicatedAirspeed = ias end
		if tas then data.trueAirspeed = tas end
		if gs then data.groundSpeed = gs end
		if vs then data.verticalSpeed = vs end
		if mach then data.mach = mach end

		-- Angle of Attack (AoA) with outlier filtering
		local aoa = safe_get(function() return LoGetAngleOfAttack() end, nil)
		if aoa then
			local aoa_raw = aoa
			-- DCS Export API returns AoA in degrees for most modules (FA-18C, F-16C, etc.)
			-- Only convert if value is very small (< 0.5), indicating it might be in radians
			-- Typical AoA range: -20° to +30° for normal flight
			if math.abs(aoa) < 0.5 then
				aoa = math.deg(aoa)
			end
			-- Normalize angle to [-180, 180) for edge cases
			while aoa > 180 do aoa = aoa - 360 end
			while aoa <= -180 do aoa = aoa + 360 end
			
			-- Outlier detection: if AoA changes by more than 15° from last frame, treat as suspect
			-- DCS sometimes delivers bad frames (~0°) when aircraft is taxiing or in certain states
			local aoaValid = true
			if lastAoA ~= nil then
				local delta = math.abs(aoa - lastAoA)
				if delta > 15 and math.abs(lastAoA) > 5 then
					-- Large jump detected; if new value is near zero but last was not, it's likely a bad frame
					if math.abs(aoa) < 3 and math.abs(lastAoA) > 10 then
						aoaValid = false
						debug_log('AOA outlier rejected: raw=' .. tostring(aoa_raw) .. ' computed=' .. tostring(aoa) .. ' last=' .. tostring(lastAoA) .. ' delta=' .. tostring(delta))
					end
				end
			end
			
			if aoaValid then
				-- Apply simple moving average (5 samples) for smoothing
				table.insert(aoaHistory, aoa)
				if #aoaHistory > 5 then
					table.remove(aoaHistory, 1) -- keep only last 5
				end
				local sum = 0
				for _, v in ipairs(aoaHistory) do
					sum = sum + v
				end
				aoa = sum / #aoaHistory
				lastAoA = aoa
			else
				-- Use last valid value
				aoa = lastAoA
			end
			
			-- Round to 2 decimals for compactness
			aoa = round(aoa, 2)
			data.angleOfAttack = aoa
		end

		-- G-Load (x, y, z)
		local g = safe_get(function() return LoGetAccelerationUnits() end, nil)
		if g then
			local gx = (type(g) == 'table') and (g.x or g[1]) or g
			local gy = (type(g) == 'table') and (g.y or g[2]) or nil
			local gz = (type(g) == 'table') and (g.z or g[3]) or nil
			-- Heuristic: if values look like m/s^2 (abs>20), convert to G by dividing by g0
			local function conv(v)
				if not v then return nil end
				if type(v) ~= 'number' then return tonumber(v) end
				if math.abs(v) > 20 then -- likely m/s^2
					return v / 9.80665
				end
				return v
			end
			local gxv = conv(gx)
			local gyv = conv(gy)
			local gzv = conv(gz)
			-- Round g values to 3 decimals
			data.gLoad = {
				x = gxv and round(gxv, 3) or nil,
				y = gyv and round(gyv, 3) or nil,
				z = gzv and round(gzv, 3) or nil
			}
			-- Log if conversion from m/s^2 was likely
			if (type(gx) == 'number' and math.abs(gx) > 20) or (type(gy) == 'number' and math.abs(gy) > 20) or (type(gz) == 'number' and math.abs(gz) > 20) then
				debug_log('gLoad converted from m/s^2 to Gs: ' .. tostring(data.gLoad.x) .. ',' .. tostring(data.gLoad.y) .. ',' .. tostring(data.gLoad.z))
			end
		end

		-- Fuel (attempt to derive total from internal + external when available)
		local fuelInternal = safe_get(function() return LoGetFuelWeight() end, nil)
		-- Try to read external fuel if the export API provides it; fallback to nil
		local fuelExternal = nil
		fuelExternal = fuelExternal or safe_get(function() return LoGetFuelExternalWeight() end, nil)
		fuelExternal = fuelExternal or safe_get(function() return LoGetFuelExternalWeightKg and LoGetFuelExternalWeightKg() end, nil)
		-- As a last resort, do not duplicate internal value for external; keep external nil

		if (fuelInternal and fuelInternal > 0) or (fuelExternal and fuelExternal > 0) then
			local total = 0
			if fuelInternal and fuelInternal > 0 then total = total + fuelInternal end
			if fuelExternal and fuelExternal > 0 then total = total + fuelExternal end

			local remaining = fuelInternal and fuelInternal or (fuelExternal and fuelExternal) or nil

			data.fuel = {
				total = (total > 0) and total or nil,
				remaining = remaining,
				internal = fuelInternal,
				external = fuelExternal or 0,
				endurance = nil, -- calculated externally if flow known
				fuelFlow = nil
			}

			debug_log('Fuel: internal=' .. tostring(fuelInternal) .. ' external=' .. tostring(fuelExternal) .. ' total=' .. tostring(total) .. ' remaining=' .. tostring(remaining))
		end

		-- Engine Data (RPM, EGT, Throttle, Afterburner)
		local engineInfo = safe_get(function() return LoGetEngineInfo() end, nil)
		if engineInfo then
			local rpmLeft, rpmRight, egtLeft, egtRight
			local throttleVal, afterburnerVal

			-- Helper to extract rpm/egt from engine entries
			local function extract_from_entry(e, idx)
				local r = try_field(e, {'RPM','rpm','N1','n1','rpmPercent','rpm_percent'})
				if r then
					if type(r) == 'table' then r = (r.left or r.right or r[1] or r[2]) end
					if type(r) == 'number' and r <= 1.5 then r = r * 100 end -- fraction -> percent
					if idx == 1 then rpmLeft = r else rpmRight = r end
				end
				local egt = try_field(e, {'EGT','egt','egtC','EGT_C','EGT_CELSIUS'})
				if egt then
					if type(egt) == 'table' then egt = (egt.left or egt.right or egt[1] or egt[2]) end
					if idx == 1 then egtLeft = egt else egtRight = egt end
				end
			end

			-- If engine list present, iterate
			local enginesList = engineInfo.Engines or engineInfo.engines
			if enginesList and type(enginesList) == 'table' then
				for i, ent in ipairs(enginesList) do
					extract_from_entry(ent, i)
					-- try per-engine throttle if provided
					local t = try_field(ent, {'throttle','Throttle','throttlePos','throttle_position'})
					if t and throttleVal == nil then
						if type(t) == 'number' and t > 1 and t <= 100 then t = t/100 end
						throttleVal = tonumber(t)
					end
					-- per-engine afterburner
					local ab = try_field(ent, {'afterburner','afterburn','AB','afterburnerState'})
					if ab ~= nil and afterburnerVal == nil then afterburnerVal = (ab == true or ab == 1) end
				end
			else
				-- Top-level fields
				extract_from_entry(engineInfo, 1)
				local t = try_field(engineInfo, {'Throttle','throttle','throttlePos','throttle_position'})
				if t then if type(t) == 'number' and t > 1 and t <= 100 then t = t/100 end; throttleVal = tonumber(t) end
				local ab = try_field(engineInfo, {'afterburner','afterburn','AB','afterburnerState'})
				if ab ~= nil then afterburnerVal = (ab == true or ab == 1) end
			end

			-- Populate data.engines according to FlightData.kt schema
			data.engines = data.engines or {}
			if rpmLeft or rpmRight then
				data.engines.rpm = {
					left = rpmLeft and round(rpmLeft, 1) or nil,
					right = rpmRight and round(rpmRight, 1) or nil
				}
			end
			if egtLeft or egtRight then
				data.engines.egt = {
					left = egtLeft and round(egtLeft, 0) or nil,
					right = egtRight and round(egtRight, 0) or nil
				}
			end
			if throttleVal ~= nil then data.engines.throttle = round(throttleVal, 3) end
			if afterburnerVal ~= nil then data.engines.afterburner = afterburnerVal end

			debug_log('Engines: rpm=' .. tostring(data.engines.rpm and (data.engines.rpm.left or '?')) .. ',' .. tostring(data.engines.rpm and (data.engines.rpm.right or '?')) .. ' egt=' .. tostring(data.engines.egt and (data.engines.egt.left or '?')) .. ',' .. tostring(data.engines.egt and (data.engines.egt.right or '?')) .. ' throttle=' .. tostring(data.engines.throttle) .. ' ab=' .. tostring(data.engines.afterburner))
		end

		-- Flight Controls & Trim not exported: DCS Export API does not reliably expose stick/trim across modules

		-- Navigation & Waypoints
		local route = safe_get(function() return LoGetRoute() end, nil)
		if route and route.CurrentWaypoint then
			local wp = route.CurrentWaypoint
			data.waypoint = {
				current = wp.Name or ('WP' .. (wp.number or '?')),
				distance = wp.distance or nil,
				bearing = wp.heading or nil,
				eta = nil,
				etaSeconds = wp.eta or nil
			}
			data.flightPlan = {
				currentIndex = wp.number or 0,
				totalWaypoints = route.GoToWaypointCount or 0,
				route = route.Name or nil
			}
		end

		-- Weapons & Stores
		local payload = safe_get(function() return LoGetPayloadInfo() end, nil)
		if payload then
			local stations = {}
			local totalCount = 0
			local payloadMassSum = 0
			local payloadMassFound = false
			if payload.Stations then
				for i, station in pairs(payload.Stations) do
					if station.weapon then
						-- Try to detect per-station mass (some modules expose this)
						local stmass = nil
						if station.mass then stmass = station.mass end
						if not stmass and station.weapon.mass then stmass = station.weapon.mass end
						if not stmass and station.weight then stmass = station.weight end
						local count = station.count or 1
						if stmass then
							payloadMassSum = payloadMassSum + (stmass * count)
							payloadMassFound = true
						end
						table.insert(stations, {
							station = i,
							type = station.weapon.displayName or station.weapon.level1 or 'UNKNOWN',
							count = count
						})
						totalCount = totalCount + count
					end
				end
			end
			data.weapons = {
				masterArm = safe_get(function() return LoGetMasterArmState() end, false),
				selected = payload.CurrentWeapon or nil,
				stations = #stations > 0 and stations or nil,
				totalCount = totalCount > 0 and totalCount or nil
			}
			-- payload mass not exported (not reliably available via DCS Export API)
		end

		-- RWR / Threats (limited API, placeholder)
		local threats = safe_get(function() return LoGetTWSInfo() end, nil)
		if threats then
			local contacts = {}
			-- DCS doesn't expose RWR directly; this is a placeholder
			data.rwr = {
				contacts = nil,
				threatsDetected = 0
			}
		end

		-- Radar
		local radarInfo = safe_get(function() return LoGetRadarInfo() end, nil)
		if radarInfo then
			data.radar = {
				mode = radarInfo.mode or nil,
				range = radarInfo.range or nil,
				locked = radarInfo.lock or false,
				trackCount = radarInfo.trackCount or 0
			}
		end
		data.radarActive = safe_get(function() return LoGetSelfData() end, {}).RadarOn or false

		-- Countermeasures
		local cmds = safe_get(function() return LoGetSnares() end, nil)
		if cmds then
			data.countermeasures = {
				chaffCount = cmds.chaff or 0,
				flareCount = cmds.flare or 0,
				dispenserMode = cmds.mode or nil
			}
		end

		-- Autopilot
		local apState = safe_get(function() return LoGetControlPanel_Autopilot() end, nil)
		if apState then
			data.autopilot = {
				enabled = apState.on or false,
				mode = apState.mode or nil,
				flightDirector = apState.fd or false
			}
		end

		-- Transponder
		local xpdr = safe_get(function() return LoGetTransponderInfo() end, nil)
		if xpdr then
			data.transponder = {
				code = xpdr.code or nil,
				mode = xpdr.mode or nil,
				ident = xpdr.ident or false
			}
		end

		-- Radios
		local radio = safe_get(function() return LoGetRadioFrequencies() end, nil)
		if radio then
			data.radios = {
				com1 = radio.COM1 or nil,
				com2 = radio.COM2 or nil,
				guard = radio.Guard or false,
				activeFreq = radio.active or nil
			}
		end

		-- Warnings
		local mcp = safe_get(function() return LoGetMCPState() end, nil)
		if mcp then
			data.warnings = {
				masterCaution = mcp.MasterCaution or false,
				masterWarning = mcp.MasterWarning or false,
				faults = mcp.faults or nil,
				alerts = mcp.alerts or nil
			}
		end

		-- Environment (limited)
		local wind = safe_get(function() return LoGetVectorWindVelocity() end, nil)
	local temp = safe_get(function() return LoGetTemperature() end, nil)
	local pressure = safe_get(function() return LoGetPressure() end, nil)
	if wind then
		local windSpeed = math.sqrt((wind.x or 0)^2 + (wind.z or 0)^2)
		local windDir = math.deg(math.atan2(wind.z or 0, wind.x or 0))
		if windDir < 0 then windDir = windDir + 360 end
		data.environment = {
			windDirection = windDir,
			windSpeed = windSpeed,
			temperature = temp,
			pressure = pressure,
			visibility = nil,
			clouds = nil
		}
	else
		data.environment = {
			temperature = temp,
			pressure = pressure,
			visibility = nil,
			clouds = nil
		}
	end
	-- convenience top-level fields for easy access
	data.temperature = temp
	data.pressure = pressure
	data.wind = {
		speed = wind and (math.sqrt((wind.x or 0)^2 + (wind.z or 0)^2)) or 0,
		direction = wind and (function() local d = math.deg(math.atan2((wind.z or 0),(wind.x or 0))); if d < 0 then d = d + 360 end; return d end)() or nil
	}
		data.isHuman = safe_get(function() return LoGetSelfData() end, {}).Player or false
		data.jamming = false
		data.irJamming = false
		data.invisible = false
		data.aiOn = false
		data.born = true

		-- nearbyUnits removed from FlightData for performance - now in separate entity file
		-- This allows fast player position updates without massive JSON payloads

		return data
	end

	-- Collect entity contacts (nearby units) as a separate dataset
	-- Returns: batch1_data, batch2_data, batch3_data, batch4_data (or nil if no data)
	local function collect_entity_contacts()
		local baseData = {
			timestamp = generate_timestamp(),
			streamer_version = STREAMER_VERSION,
			updateRate = 1.0 / UPDATE_INTERVAL
		}

		-- Get player position for distance calculations
		local selfData = safe_get(function() return LoGetSelfData() end, nil)
		if selfData and selfData.LatLongAlt then
			baseData.playerLat = selfData.LatLongAlt.Lat
			baseData.playerLon = selfData.LatLongAlt.Long
			baseData.playerAlt = selfData.LatLongAlt.Alt
		end

		-- Determine total batches based on total units available
		local totalBatches = 4  -- Support up to 4 batches (1000 units)

		-- Collect batch 1 (units 0-250)
		local batch1_units, hasMore1 = collect_nearby_units(1)
		local batch1_data = {}
		for k, v in pairs(baseData) do batch1_data[k] = v end
		batch1_data.nearbyUnits = batch1_units
		batch1_data.batchIndex = 1
		batch1_data.totalBatches = totalBatches

		-- Collect batch 2 (units 250-500)
		local batch2_data = nil
		if hasMore1 then
			local batch2_units, hasMore2 = collect_nearby_units(2)
			if #batch2_units > 0 then
				batch2_data = {}
				for k, v in pairs(baseData) do batch2_data[k] = v end
				batch2_data.nearbyUnits = batch2_units
				batch2_data.batchIndex = 2
				batch2_data.totalBatches = totalBatches
			end
		end

		-- Collect batch 3 (units 500-750)
		local batch3_data = nil
		if hasMore1 then
			local batch3_units, hasMore3 = collect_nearby_units(3)
			if #batch3_units > 0 then
				batch3_data = {}
				for k, v in pairs(baseData) do batch3_data[k] = v end
				batch3_data.nearbyUnits = batch3_units
				batch3_data.batchIndex = 3
				batch3_data.totalBatches = totalBatches
			end
		end

		-- Collect batch 4 (units 750-1000)
		local batch4_data = nil
		if hasMore1 then
			local batch4_units, hasMore4 = collect_nearby_units(4)
			if #batch4_units > 0 then
				batch4_data = {}
				for k, v in pairs(baseData) do batch4_data[k] = v end
				batch4_data.nearbyUnits = batch4_units
				batch4_data.batchIndex = 4
				batch4_data.totalBatches = totalBatches
			end
		end

		return batch1_data, batch2_data, batch3_data, batch4_data
	end

	local function write_log(aircraft, unitID, pos)
		pcall(function()
			local f = io.open(LOG_PATH, 'a')
			if not f then return end
			f:write(string.format('%s - Aircraft: %s - UnitID: %s - Pos: %s\n', os.date('%Y-%m-%d %H:%M:%S'), tostring(aircraft), tostring(unitID), format_pos(pos)))
			f:close()
		end)
	end

	local function write_json(data)
		pcall(function()
			local json = json_encode_table(data)
			local f = io.open(JSON_PATH, 'a')
			if not f then return end
			f:write(json .. '\n')
			f:flush()  -- Force immediate flush to disk
			f:close()
		end)
		-- NOTE: File trimming moved to separate function, called periodically (not on every write!)
	end

	local function write_entity_json(data)
		pcall(function()
			local json = json_encode_table(data)
			local f = io.open(ENTITY_CONTACTS_PATH, 'a')
			if not f then return end
			f:write(json .. '\n')
			f:flush()  -- Force immediate flush to disk
			f:close()
		end)
	end

	local function write_entity_json_2(data)
		pcall(function()
			local json = json_encode_table(data)
			local f = io.open(ENTITY_CONTACTS_PATH_2, 'a')
			if not f then return end
			f:write(json .. '\n')
			f:flush()  -- Force immediate flush to disk
			f:close()
		end)
	end

	local function write_entity_json_3(data)
		pcall(function()
			local json = json_encode_table(data)
			local f = io.open(ENTITY_CONTACTS_PATH_3, 'a')
			if not f then return end
			f:write(json .. '\n')
			f:flush()  -- Force immediate flush to disk
			f:close()
		end)
	end

	local function write_entity_json_4(data)
		pcall(function()
			local json = json_encode_table(data)
			local f = io.open(ENTITY_CONTACTS_PATH_4, 'a')
			if not f then return end
			f:write(json .. '\n')
			f:flush()  -- Force immediate flush to disk
			f:close()
		end)
	end

	local function trim_json_file()
		-- Trim the JSONL file to keep only the last MAX_JSON_LINES entries (if enabled).
		-- IMPORTANT: This is called PERIODICALLY (every TRIM_INTERVAL seconds), NOT on every write!
		-- This prevents blocking the write path with expensive file I/O.
		if not MAX_JSON_LINES or MAX_JSON_LINES <= 0 then return end
		
		pcall(function()
			-- Read and keep only the last MAX_JSON_LINES lines to bound memory usage
			local tmp = {}
			local count = 0
			local ok, r = pcall(io.open, JSON_PATH, 'r')
			if not ok or not r then return end
			
			-- Fast path: count lines first to avoid unnecessary memory usage
			for _ in r:lines() do
				count = count + 1
			end
			r:close()
			
			-- Only trim if file exceeds threshold (no work needed if file is small)
			if count <= MAX_JSON_LINES then return end
			
			-- Reopen and keep only last MAX_JSON_LINES entries
			local skip = count - MAX_JSON_LINES
			local ok2, r2 = pcall(io.open, JSON_PATH, 'r')
			if not ok2 or not r2 then return end
			
			local lineNum = 0
			for line in r2:lines() do
				lineNum = lineNum + 1
				if lineNum > skip then
					table.insert(tmp, line)
				end
			end
			r2:close()
			
			-- Write trimmed data back
			local ok3, w = pcall(io.open, JSON_PATH, 'w')
			if not ok3 or not w then return end
			for _, l in ipairs(tmp) do
				w:write(l .. '\n')
			end
			w:flush()
			w:close()
		end)
	end

	local function trim_entity_file()
		-- Trim the entity contacts JSONL file to keep only the last MAX_ENTITY_LINES entries
		if not MAX_ENTITY_LINES or MAX_ENTITY_LINES <= 0 then return end

		pcall(function()
			local tmp = {}
			local count = 0
			local ok, r = pcall(io.open, ENTITY_CONTACTS_PATH, 'r')
			if not ok or not r then return end

			-- Fast path: count lines first
			for _ in r:lines() do
				count = count + 1
			end
			r:close()

			-- Only trim if file exceeds threshold
			if count <= MAX_ENTITY_LINES then return end

			-- Reopen and keep only last MAX_ENTITY_LINES entries
			local skip = count - MAX_ENTITY_LINES
			local ok2, r2 = pcall(io.open, ENTITY_CONTACTS_PATH, 'r')
			if not ok2 or not r2 then return end

			local lineNum = 0
			for line in r2:lines() do
				lineNum = lineNum + 1
				if lineNum > skip then
					table.insert(tmp, line)
				end
			end
			r2:close()

			-- Write trimmed data back
			local ok3, w = pcall(io.open, ENTITY_CONTACTS_PATH, 'w')
			if not ok3 or not w then return end
			for _, l in ipairs(tmp) do
				w:write(l .. '\n')
			end
			w:flush()
			w:close()
		end)
	end

	local function trim_entity_file_2()
		-- Trim the second entity contacts JSONL file
		if not MAX_ENTITY_LINES or MAX_ENTITY_LINES <= 0 then return end

		pcall(function()
			local tmp = {}
			local count = 0
			local ok, r = pcall(io.open, ENTITY_CONTACTS_PATH_2, 'r')
			if not ok or not r then return end

			for _ in r:lines() do
				count = count + 1
			end
			r:close()

			if count <= MAX_ENTITY_LINES then return end

			local skip = count - MAX_ENTITY_LINES
			local ok2, r2 = pcall(io.open, ENTITY_CONTACTS_PATH_2, 'r')
			if not ok2 or not r2 then return end

			local lineNum = 0
			for line in r2:lines() do
				lineNum = lineNum + 1
				if lineNum > skip then
					table.insert(tmp, line)
				end
			end
			r2:close()

			local ok3, w = pcall(io.open, ENTITY_CONTACTS_PATH_2, 'w')
			if not ok3 or not w then return end
			for _, l in ipairs(tmp) do
				w:write(l .. '\n')
			end
			w:flush()
			w:close()
		end)
	end

	local function trim_entity_file_3()
		-- Trim the third entity contacts JSONL file
		if not MAX_ENTITY_LINES or MAX_ENTITY_LINES <= 0 then return end

		pcall(function()
			local tmp = {}
			local count = 0
			local ok, r = pcall(io.open, ENTITY_CONTACTS_PATH_3, 'r')
			if not ok or not r then return end

			for _ in r:lines() do
				count = count + 1
			end
			r:close()

			if count <= MAX_ENTITY_LINES then return end

			local skip = count - MAX_ENTITY_LINES
			local ok2, r2 = pcall(io.open, ENTITY_CONTACTS_PATH_3, 'r')
			if not ok2 or not r2 then return end

			local lineNum = 0
			for line in r2:lines() do
				lineNum = lineNum + 1
				if lineNum > skip then
					table.insert(tmp, line)
				end
			end
			r2:close()

			local ok3, w = pcall(io.open, ENTITY_CONTACTS_PATH_3, 'w')
			if not ok3 or not w then return end
			for _, l in ipairs(tmp) do
				w:write(l .. '\n')
			end
			w:flush()
			w:close()
		end)
	end

	local function trim_entity_file_4()
		-- Trim the fourth entity contacts JSONL file
		if not MAX_ENTITY_LINES or MAX_ENTITY_LINES <= 0 then return end

		pcall(function()
			local tmp = {}
			local count = 0
			local ok, r = pcall(io.open, ENTITY_CONTACTS_PATH_4, 'r')
			if not ok or not r then return end

			for _ in r:lines() do
				count = count + 1
			end
			r:close()

			if count <= MAX_ENTITY_LINES then return end

			local skip = count - MAX_ENTITY_LINES
			local ok2, r2 = pcall(io.open, ENTITY_CONTACTS_PATH_4, 'r')
			if not ok2 or not r2 then return end

			local lineNum = 0
			for line in r2:lines() do
				lineNum = lineNum + 1
				if lineNum > skip then
					table.insert(tmp, line)
				end
			end
			r2:close()

			local ok3, w = pcall(io.open, ENTITY_CONTACTS_PATH_4, 'w')
			if not ok3 or not w then return end
			for _, l in ipairs(tmp) do
				w:write(l .. '\n')
			end
			w:flush()
			w:close()
		end)
	end

	local function serialize_table(obj, depth, maxDepth, seen)
		depth = depth or 0
		maxDepth = maxDepth or 3
		seen = seen or {}
		if depth > maxDepth then return string.rep('  ', depth) .. '...\n' end
		if seen[obj] then return string.rep('  ', depth) .. '*cycle*\n' end
		seen[obj] = true
		local s = ''
		for k, v in pairs(obj) do
			local key = tostring(k)
			if type(v) == 'table' then
				s = s .. string.rep('  ', depth) .. key .. ':\n' .. serialize_table(v, depth+1, maxDepth, seen)
			else
				s = s .. string.rep('  ', depth) .. key .. ': ' .. tostring(v) .. '\n'
			end
		end
		return s
	end

	local function debug_dump_once(tbl)
		if not DEBUG_DUMP_TABLES or type(tbl) ~= 'table' then return end
		local id = tostring(tbl)
		if dumped_tables[id] then return end
		dumped_tables[id] = true
		pcall(function()
			local f = io.open(DEBUG_LOG_PATH, 'a')
			if not f then return end
			f:write('--- ' .. os.date('%Y-%m-%d %H:%M:%S') .. ' DUMP ' .. id .. '\n')
			f:write(serialize_table(tbl, 0, 4, {}))
			f:write('\n')
			f:close()
		end)
	end

	function LuaExportStart()
		lastWrite = 0
		lastTrim = 0  -- Initialize trim timer
		lastEntityTrim = 0  -- Initialize entity trim timer
		-- clear files once at start if requested
		if CLEAR_ON_START then
			pcall(function()
				local f = io.open(JSON_PATH, 'w')
				if f then f:write('') f:close() end
				local ef = io.open(ENTITY_CONTACTS_PATH, 'w')
				if ef then ef:write('') ef:close() end
				local ef2 = io.open(ENTITY_CONTACTS_PATH_2, 'w')
				if ef2 then ef2:write('') ef2:close() end
				local ef3 = io.open(ENTITY_CONTACTS_PATH_3, 'w')
				if ef3 then ef3:write('') ef3:close() end
				local ef4 = io.open(ENTITY_CONTACTS_PATH_4, 'w')
				if ef4 then ef4:write('') ef4:close() end
				local lf = io.open(LOG_PATH, 'w')
				if lf then lf:write('') lf:close() end
				local df = io.open(DEBUG_LOG_PATH, 'w')
				if df then df:write('') df:close() end
			end)
		end
		if DEBUG_DUMP_TABLES then
			debug_dump_once(telemetry)
		end
	end

	function LuaExportAfterNextFrame()
		local now = LoGetModelTime()
		if not now then now = os.clock() end

		-- Check for live commands from forwarder every COMMAND_CHECK_INTERVAL seconds
		if now - lastCommandCheck >= COMMAND_CHECK_INTERVAL then
			check_live_commands()
			lastCommandCheck = now
		end

		-- Periodic file trimming (separate from write path for performance)
		if now - lastTrim >= TRIM_INTERVAL then
			trim_json_file()
			lastTrim = now
		end

		-- Periodic entity file trimming
		if now - lastEntityTrim >= TRIM_INTERVAL then
			trim_entity_file()
			trim_entity_file_2()
			trim_entity_file_3()
			trim_entity_file_4()
			lastEntityTrim = now
		end

		if now - lastWrite >= UPDATE_INTERVAL then
			-- Write aircraft telemetry (lean, no nearbyUnits for fast updates)
			local telemetry = collect_telemetry()
			write_json(telemetry)
			-- Write entity contacts to SEPARATE files (batch 1-4)
			local entityBatch1, entityBatch2, entityBatch3, entityBatch4 = collect_entity_contacts()
			write_entity_json(entityBatch1)
			if entityBatch2 then
				write_entity_json_2(entityBatch2)
			end
			if entityBatch3 then
				write_entity_json_3(entityBatch3)
			end
			if entityBatch4 then
				write_entity_json_4(entityBatch4)
			end

			lastWrite = now
		end
	end

	function LuaExportStop()
		-- Write final aircraft telemetry
		local telemetry = collect_telemetry()
		write_json(telemetry)
		-- Write final entity contacts (all 4 batches)
		local entityBatch1, entityBatch2, entityBatch3, entityBatch4 = collect_entity_contacts()
		write_entity_json(entityBatch1)
		if entityBatch2 then
			write_entity_json_2(entityBatch2)
		end
		if entityBatch3 then
			write_entity_json_3(entityBatch3)
		end
		if entityBatch4 then
			write_entity_json_4(entityBatch4)
		end
	end
end, nil)