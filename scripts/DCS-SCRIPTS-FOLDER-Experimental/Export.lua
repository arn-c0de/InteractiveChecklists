pcall(function()
	local lfs = require('lfs')
	local writeDir = lfs.writedir()

	local LOG_PATH = writeDir .. [[Scripts\player_aircraft.log]]
	local DEBUG_LOG_PATH = writeDir .. [[Scripts\player_aircraft_debug.log]]
	local JSON_PATH = writeDir .. [[Scripts\player_aircraft_parsed.jsonl]]
	local DEBUG_DUMP_TABLES = true -- set to false to disable table debug dumps
	local dumped_tables = {}
	local UPDATE_INTERVAL = 0.2 -- seconds (5 Hz update rate)
	local lastWrite = 0
	local STREAMER_VERSION = "1.0.1"
	-- Maximum number of JSON lines to keep in the output file. Set to 0 to disable trimming.
	local MAX_JSON_LINES = 10000
	-- If true, clear the JSON/log/debug files once when the export starts
	local CLEAR_ON_START = true

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

	-- Collect comprehensive telemetry data
	local function collect_telemetry()
		local data = {
			timestamp = os.date('!%Y-%m-%dT%H:%M:%SZ'),
			streamer_version = STREAMER_VERSION,
			dataAge = 0.0,
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
			data.coalition = selfData.Coalition or ''
			data.country = selfData.Country or 0
			data.group = selfData.GroupName or ''
			data.unitID = tostring(selfData.ID or selfData.UnitId or 'N/A')
		end

		-- AoA & G-Load (try common draw arguments and API)
		local aoa = safe_get(function() return LoGetAngleOfAttack() end, nil)
		if type(aoa) == 'number' then data.angleOfAttack = aoa end
		local gload = safe_get(function() return LoGetAccelerationUnits() end, nil)
		if gload and type(gload) == 'table' and gload.x and gload.y and gload.z then
			data.gLoad = {
				x = gload.x or 0,
				y = gload.y or 0,
				z = gload.z or 0
			}
		end

		-- Aircraft Mass (if available)
		local mass = safe_get(function() return LoGetAircraftMass() end, nil)
		if type(mass) == 'number' then
			data.aircraftMass = {
				total = mass,
				empty = nil,
				payload = nil
			}
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

		-- Fuel & Fuel Flow
		local fuelInternal = safe_get(function() return LoGetFuelWeight() end, 0)
		local fuelExternal = safe_get(function() return LoGetFuelWeight() end, 0) -- DCS doesn't separate easily
		local engineInfo = safe_get(function() return LoGetEngineInfo() end, nil)
		local fuelFlow = nil
		if engineInfo and type(engineInfo) == 'table' and engineInfo.FuelConsumption and type(engineInfo.FuelConsumption) == 'number' then
			fuelFlow = engineInfo.FuelConsumption
		end
		if type(fuelInternal) == 'number' and fuelInternal > 0 then
			data.fuel = {
				total = fuelInternal,
				remaining = fuelInternal,
				internal = fuelInternal,
				external = 0,
				endurance = (fuelFlow and fuelFlow > 0) and (fuelInternal / fuelFlow) or nil,
				fuelFlow = fuelFlow
			}
		end

		-- Engine Data (N1, RPM, EGT, Oil Pressure - varies by aircraft)
		if engineInfo and type(engineInfo) == 'table' then
			data.engines = {}
			if engineInfo.RPM and type(engineInfo.RPM) == 'table' then
				data.engines.rpm = {
					left = engineInfo.RPM.left or nil,
					right = engineInfo.RPM.right or nil
				}
			end
			if engineInfo.Temperature and type(engineInfo.Temperature) == 'table' then
				data.engines.egt = {
					left = engineInfo.Temperature.left or nil,
					right = engineInfo.Temperature.right or nil
				}
			end
			data.engines.throttle = engineInfo.throttle or nil
			data.engines.afterburner = engineInfo.afterburner or false
		end

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
			if payload.Stations then
				for i, station in pairs(payload.Stations) do
					if station.weapon then
						table.insert(stations, {
							station = i,
							type = station.weapon.displayName or station.weapon.level1 or 'UNKNOWN',
							count = station.count or 1
						})
						totalCount = totalCount + (station.count or 1)
					end
				end
			end
			data.weapons = {
				masterArm = safe_get(function() return LoGetMasterArmState() end, false),
				selected = payload.CurrentWeapon or nil,
				stations = #stations > 0 and stations or nil,
				totalCount = totalCount > 0 and totalCount or nil
			}
		end

		-- Systems Status (Electrical, Hydraulic, APU, Generator)
		local electrical = safe_get(function() return LoGetElectricalSystemInfo() end, nil)
		local hydraulic = safe_get(function() return LoGetHydraulicSystemInfo() end, nil)
		local apuOn = safe_get(function() return LoGetAPUInfo() end, nil)
		local generatorOn = safe_get(function() return LoGetGeneratorInfo() end, nil)
		data.systems = {
			electrical = (type(electrical) == 'string' or type(electrical) == 'number') and electrical or nil,
			hydraulic = (type(hydraulic) == 'string' or type(hydraulic) == 'number') and hydraulic or nil,
			apuOn = (type(apuOn) == 'boolean') and apuOn or nil,
			generatorOn = (type(generatorOn) == 'boolean') and generatorOn or nil
		}

		-- Radar (detailed)
		local radarInfo = safe_get(function() return LoGetRadarInfo() end, nil)
		if radarInfo and type(radarInfo) == 'table' then
			data.radar = {
				mode = radarInfo.mode or nil,
				range = radarInfo.range or nil,
				locked = radarInfo.lock or false,
				trackCount = radarInfo.trackCount or 0,
				azimuth = radarInfo.azimuth or nil,
				elevation = radarInfo.elevation or nil,
				scan = radarInfo.scan or nil
			}
			-- Track details if available
			if radarInfo.tracks and type(radarInfo.tracks) == 'table' then
				data.radar.tracks = {}
				for i, track in pairs(radarInfo.tracks) do
					if type(track) == 'table' then
						table.insert(data.radar.tracks, {
							id = i,
							range = track.range or nil,
							azimuth = track.azimuth or nil,
							elevation = track.elevation or nil,
							locked = track.locked or false
						})
					end
				end
			end
		end
		local selfRadar = safe_get(function() return LoGetSelfData() end, {})
		data.radarActive = (type(selfRadar) == 'table' and selfRadar.RadarOn) or false

		-- RWR / Threat Warning (limited exposure, placeholder for TWS)
		local twsInfo = safe_get(function() return LoGetTWSInfo() end, nil)
		if twsInfo and type(twsInfo) == 'table' then
			data.rwr = {
				contacts = {},
				threatsDetected = 0
			}
			for id, contact in pairs(twsInfo) do
				if type(contact) == 'table' then
					table.insert(data.rwr.contacts, {
						id = id,
						type = contact.type or 'UNKNOWN',
						azimuth = contact.azimuth or nil,
						priority = contact.priority or 0
					})
					data.rwr.threatsDetected = data.rwr.threatsDetected + 1
				end
			end
		end

		-- Nearby Units / Ground Objects (World Objects)
		local worldObjects = safe_get(function() return LoGetWorldObjects() end, nil)
		if worldObjects and type(worldObjects) == 'table' then
			data.nearbyUnits = {}
			local count = 0
			for id, obj in pairs(worldObjects) do
				if type(obj) == 'table' and count < 20 then -- limit to 20 nearest
					local distance = nil
					if obj.Position and selfData and selfData.Position then
						local dx = (obj.Position.x or 0) - (selfData.Position.x or 0)
						local dz = (obj.Position.z or 0) - (selfData.Position.z or 0)
						distance = math.sqrt(dx*dx + dz*dz)
					end
					table.insert(data.nearbyUnits, {
						id = tostring(id),
						name = obj.Name or obj.UnitName or 'UNKNOWN',
						type = obj.Type or 'UNKNOWN',
						coalition = obj.Coalition or '',
						distance = distance
					})
					count = count + 1
				end
			end
		end

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

		-- Flight Controls & Trim (try common draw arguments)
		local controls = safe_get(function() return LoGetControlsState() end, nil)
		if controls then
			data.flightControls = {
				pitch = controls.pitch or nil,
				roll = controls.roll or nil,
				yaw = controls.yaw or nil,
				trimPitch = controls.trimPitch or nil,
				trimRoll = controls.trimRoll or nil,
				trimYaw = controls.trimYaw or nil
			}
		end

		-- Gear, Flaps, Speedbrake, Canopy, Hook
		local mechInfo = safe_get(function() return LoGetMechInfo() end, nil)
		if mechInfo and type(mechInfo) == 'table' then
			data.mechanical = {
				gear = (mechInfo.gear and type(mechInfo.gear) == 'table') and {
					nose = mechInfo.gear.nose or nil,
					left = mechInfo.gear.left or nil,
					right = mechInfo.gear.right or nil
				} or nil,
				flaps = mechInfo.flaps or nil,
				speedbrake = mechInfo.speedbrake or nil,
				canopy = mechInfo.canopy or nil,
				hook = mechInfo.hook or nil,
				wheelBrake = mechInfo.wheelbrake or nil,
				noseGearSteeringEnabled = mechInfo.noseGearSteeringEnabled or nil
			}
			-- Weight on wheels (squat switch)
			data.weightOnWheels = (mechInfo.gear and type(mechInfo.gear) == 'table' and (mechInfo.gear.nose or 0) > 0.9) or false
		end

		-- Lights (various states)
		local function safe_draw_arg(idx)
			local v = safe_get(function() return LoGetAircraftDrawArgumentValue(idx) end, nil)
			return (type(v) == 'number') and v or nil
		end
		data.lights = {
			landing = safe_draw_arg(208),
			taxi = safe_draw_arg(209),
			navigation = safe_draw_arg(190),
			strobe = safe_draw_arg(192),
			formation = safe_draw_arg(88)
		}

		-- Mission Time
		data.missionTime = safe_get(function() return LoGetModelTime() end, 0)

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

		return data
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
			f:close()
		end)

		-- Trim the JSONL file to keep only the last MAX_JSON_LINES entries (if enabled).
		if MAX_JSON_LINES and MAX_JSON_LINES > 0 then
			pcall(function()
				-- Read and keep only the last MAX_JSON_LINES lines to bound memory usage
				local tmp = {}
				local ok, r = pcall(io.open, JSON_PATH, 'r')
				if not ok or not r then return end
				for line in r:lines() do
					table.insert(tmp, line)
					if #tmp > MAX_JSON_LINES then
						table.remove(tmp, 1)
					end
				end
				r:close()
				local ok2, w = pcall(io.open, JSON_PATH, 'w')
				if not ok2 or not w then return end
				for _, l in ipairs(tmp) do
					w:write(l .. '\n')
				end
				w:close()
			end)
		end
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
	-- clear files once at start if requested
	if CLEAR_ON_START then
		pcall(function()
			local f = io.open(JSON_PATH, 'w')
			if f then f:write('') f:close() end
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
		if now - lastWrite >= UPDATE_INTERVAL then
			local telemetry = collect_telemetry()
			write_json(telemetry)
			lastWrite = now
		end
	end

	function LuaExportStop()
		local telemetry = collect_telemetry()
		write_json(telemetry)
	end
end, nil)
