pcall(function()
	local lfs = require('lfs')
	local writeDir = lfs.writedir()

	local LOG_PATH = writeDir .. [[Scripts\player_aircraft.log]]
	local DEBUG_LOG_PATH = writeDir .. [[Scripts\player_aircraft_debug.log]]
	-- Configuration
	local DEBUG_DUMP_TABLES = true -- set to false to disable table debug dumps
	local DEBUG_IS_MAIN = true -- when true, detailed debug dumps become the primary output
	local WRITE_PARSED = true -- write concise, parseable JSON lines to PARSED_LOG_PATH
    -- Version info: change `STREAMER_VERSION` when you update this script
    local STREAMER_VERSION = '1.0.0'
    local LUA_VERSION = _VERSION or 'unknown'
	local PARSED_LOG_PATH = writeDir .. [[Scripts\player_aircraft_parsed.jsonl]] -- JSON-lines file for easy parsing
	-- Trimming options: keep the parsed JSONL file from growing indefinitely
	local TRIM_PARSED = true -- when true, automatically remove old lines when file grows too large
	local PARSED_MAX_LINES = 10000 -- maximum number of lines to keep in the parsed file
	local PARSED_TMP_SUFFIX = '.tmp'
	local CLEAR_ON_START = true -- when true, truncate/clear log and parsed files on start
	local WRITE_LOG = false -- when false, do not create/write `player_aircraft.log`

	-- UDP streaming removed: Export.lua now only writes JSON and debug logs
	local UPDATE_INTERVAL = 5 -- seconds
	-- The parsed JSON tries to discover common fields (COM1/COM2/DME/NearestAirfield/etc.) by searching
	-- through the tables DCS provides. If you need additional fields, edit `build_summary()` and the
	-- candidate key lists used by `find_in_table()`.
	local lastWrite = 0

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

	local function detect_all()
		local aircraft = nil
		local unitID = nil
		local pos = nil

		-- Try LoGetSelfData
		local ok, sd = pcall(function()
			if LoGetSelfData then return LoGetSelfData() end
		end)
		if ok and sd then
			-- sd may be a table or other; extract common fields
			if type(sd) == 'table' then
				aircraft = try_field(sd, {'Name', 'type', 'Type', 'typeName', 'name'})
				unitID = try_field(sd, {'unitId', 'unitID', 'unit_id', 'id', 'ID'}) or unitID
				pos = try_field(sd, {'Position', 'pos', 'position', 'pos3d', 'p'})
				-- Sometimes position is split into x,y,z on the root
				if not pos and sd.x and sd.y then pos = { x = sd.x, y = sd.y, z = sd.z } end
			else
				-- sd is a string or number
				aircraft = tostring(sd)
			end
		end

		-- Try GetSelfID (fallback) or other ID getters
		local idv
		ok, idv = pcall(function()
			if GetSelfID then return GetSelfID() end
			if LoGetSelfID then return LoGetSelfID() end
		end)
		if ok and idv and type(idv) == 'number' then
			unitID = unitID or idv
			local ok2, u = pcall(function()
				if Unit and Unit.getById then return Unit.getById(idv) end
				if Unit and Unit.getByName then return Unit.getByName(idv) end
			end)
			if ok2 and u then
				if not aircraft then
					local ok3, t = pcall(function() return u.getTypeName and u:getTypeName() end)
					if ok3 and t then aircraft = t end
				end
				local ok4, p = pcall(function()
					if u.getPosition then return u:getPosition() end
				end)
				if ok4 and p then pos = pos or p end
			end
		end

		-- Normalize aircraft to a readable string
		if type(aircraft) == 'table' then
			-- try to stringify with known fields
			local s = try_field(aircraft, {'type', 'Type', 'typeName', 'name', 'Name'})
			if s then aircraft = s else aircraft = 'TABLE' end
		end

		aircraft = aircraft or 'UNKNOWN'
		return aircraft, unitID or 'N/A', pos, sd
	end

	local function write_log(aircraft, unitID, pos)
		if not WRITE_LOG then return end
		pcall(function()
			local f = io.open(LOG_PATH, 'a')
			if not f then return end
			f:write(string.format('%s - Aircraft: %s - UnitID: %s - Pos: %s - Streamer: %s - Lua: %s\n', os.date('%Y-%m-%d %H:%M:%S'), tostring(aircraft), tostring(unitID), format_pos(pos), STREAMER_VERSION, LUA_VERSION))
			f:close()
		end)
	end

	local function serialize_table(obj, depth, maxDepth, seen)
		depth = depth or 0
		maxDepth = maxDepth or 4
		seen = seen or {}
		if depth > maxDepth then return string.rep('  ', depth) .. '...\n' end
		if seen[obj] then return string.rep('  ', depth) .. '*cycle*\n' end
		seen[obj] = true
		local s = ''
		local keys = {}
		for k in pairs(obj) do table.insert(keys, k) end
		table.sort(keys, function(a,b) return tostring(a) < tostring(b) end)
		for _,k in ipairs(keys) do
			local v = obj[k]
			local key = tostring(k)
			if type(v) == 'table' then
				s = s .. string.rep('  ', depth) .. key .. ':\n' .. serialize_table(v, depth+1, maxDepth, seen)
			else
				s = s .. string.rep('  ', depth) .. key .. ': ' .. tostring(v) .. '\n'
			end
		end
		return s
	end

	-- Recursively search a table for any of the candidate keys and return the first match.
	local function find_in_table(obj, candidates, maxDepth, seen, depth)
		if type(obj) ~= 'table' or not candidates then return nil end
		depth = depth or 0
		maxDepth = maxDepth or 3
		seen = seen or {}
		if depth > maxDepth then return nil end
		if seen[obj] then return nil end
		seen[obj] = true
		for _,k in ipairs(candidates) do
			if obj[k] ~= nil then return obj[k] end
		end
		for _,v in pairs(obj) do
			if type(v) == 'table' then
				local r = find_in_table(v, candidates, maxDepth, seen, depth+1)
				if r ~= nil then return r end
			end
		end
		return nil
	end

	-- Simple JSON serializer for basic types and tables (avoids cycles)
	local function to_json(value, seen)
		seen = seen or {}
		local t = type(value)
		if t == 'nil' then return 'null' end
		if t == 'boolean' then return tostring(value) end
		if t == 'number' then return tostring(value) end
		if t == 'string' then return string.format('%q', value) end
		if t == 'table' then
			if seen[value] then return 'null' end
			seen[value] = true
			-- detect array-like
			local i = 0
			for _ in pairs(value) do i = i + 1 end
			local isArray = true
			for n=1,i do if value[n] == nil then isArray = false break end end
			if isArray then
				local parts = {}
				for n=1,i do table.insert(parts, to_json(value[n], seen)) end
				return '[' .. table.concat(parts, ',') .. ']'
			else
				local keys = {}
				for k in pairs(value) do table.insert(keys, k) end
				table.sort(keys, function(a,b) return tostring(a) < tostring(b) end)
				local parts = {}
				for _,k in ipairs(keys) do
					local v = value[k]
					table.insert(parts, string.format('%q:%s', tostring(k), to_json(v, seen)))
				end
				return '{' .. table.concat(parts, ',') .. '}'
			end
		end
		return string.format('%q', tostring(value))
	end

	local function write_parsed(summary)
		if not WRITE_PARSED then return end
		pcall(function()
			local f = io.open(PARSED_LOG_PATH, 'a')
			if not f then return end
			local json = to_json(summary)
			f:write(os.date('%Y-%m-%d %H:%M:%S') .. ' ' .. json .. '\n')
			f:close()
			-- UDP streaming removed: only write parsed JSON to disk
		end)
		-- keep parsed file bounded so it doesn't grow indefinitely
		trim_parsed_file()
	end

	-- Trim parsed JSONL file to keep only the last PARSED_MAX_LINES entries.
	-- Uses a simple ring buffer while reading to avoid allocating more than necessary.
	local function trim_parsed_file()
		if not WRITE_PARSED or not TRIM_PARSED then return end
		pcall(function()
			local max_lines = PARSED_MAX_LINES or 10000
			local f = io.open(PARSED_LOG_PATH, 'r')
			if not f then return end
			local buf = {}
			local count = 0
			for line in f:lines() do
				count = count + 1
				buf[#buf+1] = line
				if #buf > max_lines then table.remove(buf, 1) end
			end
			f:close()
			if count <= max_lines then return end -- nothing to do
			-- write out only the kept lines to a temporary file then atomically replace
			local tmp = PARSED_LOG_PATH .. PARSED_TMP_SUFFIX
			local w = io.open(tmp, 'w')
			if not w then return end
			for _,l in ipairs(buf) do w:write(l, '\n') end
			w:close()
			-- replace original file
			pcall(function()
				os.remove(PARSED_LOG_PATH)
				os.rename(tmp, PARSED_LOG_PATH)
			end)
		end)
	end

	local function build_summary(aircraft, unitID, pos, raw)
		local summary = {}
		-- include version info so we always know which script + Lua version produced the JSON
		summary.streamer_version = STREAMER_VERSION
		summary.lua_version = LUA_VERSION
		summary.timestamp = os.date('!%Y-%m-%dT%H:%M:%SZ') -- UTC ISO-like
		summary.aircraft = tostring(aircraft or 'UNKNOWN')
		summary.unitID = unitID or 'N/A'
		-- position: prefer provided `pos`, but fall back to nested PositionAsMatrix if available
		if type(pos) == 'table' then
			summary.pos = { x = pos.x or (pos.p and pos.p.x) or nil, y = pos.y or (pos.p and pos.p.y) or nil, z = pos.z or (pos.p and pos.p.z) or nil }
		else
			local pam = find_in_table(raw, {'PositionAsMatrix','positionAsMatrix'})
			if type(pam) == 'table' and pam.p then
				summary.pos = { x = pam.p.x, y = pam.p.y, z = pam.p.z }
			end
		end
		-- common fields to try to extract
		summary.heading = find_in_table(raw, {'Heading','heading'}) or (summary.pos and summary.pos.heading)
		summary.alt = find_in_table(raw, {'Alt','alt','Altitude','AltMsl'}) or (summary.pos and summary.pos.y)

		-- Lat/Long/Alt that sometimes exist as a nested table
		local lla = find_in_table(raw, {'LatLongAlt','latlongalt','LatLong','latlong'})
		if type(lla) == 'table' then
			summary.lat = lla.Lat or lla.lat
			summary.long = lla.Long or lla.long
			summary.alt_from_lla = lla.Alt or lla.alt
		end

		-- additional useful fields
		summary.unitName = find_in_table(raw, {'UnitName','unitName','Unit','name'})
		summary.group = find_in_table(raw, {'GroupName','groupName','Group','group'})
		summary.country = find_in_table(raw, {'Country','country','CountryID','countryID'})
		summary.coalition = find_in_table(raw, {'Coalition','coalition','CoalitionID','coalitionID'})
		summary.pitch = find_in_table(raw, {'Pitch','pitch'})
		summary.bank = find_in_table(raw, {'Bank','bank'})
		-- Extract only selected status flags (do not include the whole flags object)
		local flags_tbl = find_in_table(raw, {'Flags','flags'})
		if type(flags_tbl) == 'table' then
			summary.isHuman = flags_tbl.Human
			summary.radarActive = flags_tbl.RadarActive
			summary.aiOn = flags_tbl.AI_ON
			summary.born = flags_tbl.Born
			summary.invisible = flags_tbl.Invisible
			summary.jamming = flags_tbl.Jamming
			summary.irJamming = flags_tbl.IRJamming
		end

		-- small helpers to be defensive: don't let one bad field break everything
		local function to_number(v)
			if type(v) == 'number' then return v end
			if type(v) == 'string' then return tonumber(v) end
			return nil
		end

		-- fuel: may be a number or a table with details; keep errors localized
		pcall(function()
			local fuel = find_in_table(raw, {'Fuel','fuel','FuelTotal','fuel_total','FuelLeft','fuelLeft','FuelIndicator'})
			if type(fuel) == 'number' then
				summary.fuel = fuel
			elseif type(fuel) == 'table' then
				summary.fuel = {}
				local ft = to_number(fuel.Total) or to_number(fuel.total) or to_number(fuel.totalFuel) or to_number(fuel.TotalFuel)
				if ft then summary.fuel.total = ft end
				local fi = to_number(fuel.Internal) or to_number(fuel.internal) or to_number(fuel.Inboard) or to_number(fuel.inboard)
				if fi then summary.fuel.internal = fi end
				for k,v in pairs(fuel) do
					local n = to_number(v)
					if n then summary.fuel[k] = n end
				end
			end
		end)

		-- speed: could be a scalar or vector; compute magnitude only from numeric components
		pcall(function()
			local sp = find_in_table(raw, {'Speed','speed','IAS','ias','Velocity','velocity','speedKMH','speedKTS','GroundSpeed','groundSpeed'})
			if type(sp) == 'number' then
				summary.speed = sp
			elseif type(sp) == 'table' then
				local sx = to_number(sp.x) or to_number(sp[1])
				local sy = to_number(sp.y) or to_number(sp[2])
				local sz = to_number(sp.z) or to_number(sp[3])
				if sx or sy or sz then
					summary.speed = {}
					summary.speed.x = sx
					summary.speed.y = sy
					summary.speed.z = sz
					local sum = 0 local count = 0
					if sx then sum = sum + sx*sx; count = count + 1 end
					if sy then sum = sum + sy*sy; count = count + 1 end
					if sz then sum = sum + sz*sz; count = count + 1 end
					if count > 0 then summary.speed_m_s = math.sqrt(sum) end
				end
			end
		end)
		-- radios
		summary.com1 = find_in_table(raw, {'COM1','com1','com1Freq','COM1_Freq','com1f'})
		summary.com2 = find_in_table(raw, {'COM2','com2','com2Freq','COM2_Freq','com2f'})
		summary.nav1 = find_in_table(raw, {'NAV1','nav1','nav1Freq'})
		-- DME / navaid / nearest airfield attempts
		summary.dme = find_in_table(raw, {'DME','dme','dme_station','DME_station','Dme'})
		summary.nearest_airfield = find_in_table(raw, {'NearestAirfield','NearestAirport','nearestAirport','airportName','Airfield','airfield'})
		return summary
	end

	local function debug_dump(tbl, aircraft, unitID, pos, maxDepth)
		if not DEBUG_DUMP_TABLES then return end
		pcall(function()
			local f = io.open(DEBUG_LOG_PATH, 'a')
			if not f then return end
			local id = tostring(tbl)
			f:write('--- ' .. os.date('%Y-%m-%d %H:%M:%S') .. ' DUMP ' .. id .. '\n')
			if type(tbl) == 'table' then
				f:write(serialize_table(tbl, 0, maxDepth or 6, {}))
			else
				f:write('Value: ' .. tostring(tbl) .. '\n')
			end
			local summary = build_summary(aircraft, unitID, pos, tbl)
			f:write(string.format('Summary: Aircraft: %s - UnitID: %s - Pos: %s - Streamer: %s - Lua: %s\n\n', tostring(summary.aircraft or 'N/A'), tostring(summary.unitID or 'N/A'), format_pos(pos), tostring(summary.streamer_version or STREAMER_VERSION), tostring(summary.lua_version or LUA_VERSION)))
			f:close()
			-- write concise parseable JSON line
			write_parsed(summary)
		end)
	end

	function LuaExportStart()
		lastWrite = 0
		-- Optionally clear old data files on start
		if CLEAR_ON_START then
			pcall(function()
				if WRITE_PARSED then
					local f = io.open(PARSED_LOG_PATH, 'w')
					if f then f:close() end
				end
				if WRITE_LOG then
					local lf = io.open(LOG_PATH, 'w')
					if lf then lf:close() end
				else
					-- if logs disabled, remove any existing file to avoid lingering files
					pcall(function() os.remove(LOG_PATH) end)
				end
				if DEBUG_DUMP_TABLES or DEBUG_IS_MAIN then
					local df = io.open(DEBUG_LOG_PATH, 'w')
					if df then df:close() end
				end
				-- UDP streaming removed; no network initialization
			end)
		end
		-- write a startup marker including streamer + Lua versions so the first JSON shows what code ran
		if WRITE_PARSED then
			pcall(function()
				write_parsed({ timestamp = os.date('!%Y-%m-%dT%H:%M:%SZ'), streamer_version = STREAMER_VERSION, lua_version = LUA_VERSION, event = 'start' })
			end)
		end
		local a, id, p, raw = detect_all()
		if DEBUG_IS_MAIN then
			debug_dump(raw, a, id, p)
		else
			write_log(a, id, p)
			if type(raw) == 'table' then debug_dump(raw, a, id, p, 4) end
		end
	end

	function LuaExportAfterNextFrame()
		local now = os.time()
		if now - lastWrite >= UPDATE_INTERVAL then
			local a, id, p, raw = detect_all()
			if DEBUG_IS_MAIN then
				debug_dump(raw, a, id, p)
			else
				write_log(a, id, p)
				if type(raw) == 'table' then debug_dump(raw, a, id, p, 4) end
			end
			lastWrite = now
		end
	end

	function LuaExportStop()
		local a, id, p, raw = detect_all()
		if DEBUG_IS_MAIN then
			debug_dump(raw, a, id, p)
		else
			write_log(a, id, p)
			if type(raw) == 'table' then debug_dump(raw, a, id, p, 4) end
		end
	end
end, nil)
