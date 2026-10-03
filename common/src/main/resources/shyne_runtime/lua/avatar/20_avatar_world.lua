-- ==============================================================================
-- Shyne Avatar Runtime: Minecraft & Avatar World Bridge (avatar/20_avatar_world.lua)
-- Provides access to Minecraft player state, world data, vanilla player model hooks,
-- audio/particle systems, and input bindings.
-- ==============================================================================

local function bool(value) return value and true or false end
local function read(key, ...) return _shyne_read(key, ...) end

-- ------------------------------------------------------------------------------
-- AVATAR CONFIGURATION & NETWORK
-- ------------------------------------------------------------------------------
avatar = avatar or {}
avatar.camera = avatar.camera or {}
avatar.texture = avatar.texture or {}
avatar.network = avatar.network or {}
avatar.state = state
function avatar.id() return SHYNE_AVATAR_ID end
function avatar.path() return SHYNE_AVATAR_PATH end

function avatar.camera.configure(options)
  options = options or {}
  if options.local_only ~= nil then _avatar_camera_set("local_only", bool(options.local_only)) end
  if options.first_person_masking ~= nil then _avatar_camera_set("first_person_masking", bool(options.first_person_masking)) end
  if options.first_person_arm ~= nil then _avatar_camera_set("first_person_arm", bool(options.first_person_arm)) end
  if options.first_person_hand ~= nil then _avatar_camera_set("first_person_arm", bool(options.first_person_hand)) end
  if options.hide_head ~= nil then _avatar_camera_set("hide_head_in_first_person", bool(options.hide_head)) end
  if options.offset ~= nil then local v = vector.new(options.offset); _avatar_camera_set("offset", v.x, v.y, v.z) end
  if options.rotation ~= nil then local v = vector.new(options.rotation); _avatar_camera_set("rotation", v.x, v.y, v.z) end
end

function avatar.first_person_arm(value) _avatar_camera_set("first_person_arm", bool(value)) end
function avatar.first_person_hand(value) _avatar_camera_set("first_person_arm", bool(value)) end
function avatar:setFirstPersonArm(value) _avatar_camera_set("first_person_arm", bool(value)); return self end
function avatar:setFirstPersonHand(value) _avatar_camera_set("first_person_arm", bool(value)); return self end

avatar.nameplate = {}
local function nameplate_color(value)
  if type(value) == "number" then return math.floor(value) end
  if type(value) == "string" then
    local hex = value:gsub("#", "")
    local parsed = tonumber(hex, 16)
    if parsed then return 0xFF000000 + (parsed % 0x1000000) end
  end
  if type(value) == "table" then
    local r, g, b = tonumber(value.r or value.x or value[1]) or 1, tonumber(value.g or value.y or value[2]) or 1, tonumber(value.b or value.z or value[3]) or 1
    return 0xFF000000 + math.floor(math.max(0, math.min(1, r)) * 255) * 0x10000 + math.floor(math.max(0, math.min(1, g)) * 255) * 0x100 + math.floor(math.max(0, math.min(1, b)) * 255)
  end
  return 0xFFFFFFFF
end
function avatar.nameplate.configure(options)
  options = options or {}
  _avatar_nameplate_set(options.text or "", options.visible == nil or bool(options.visible), options.badge or "", nameplate_color(options.color), bool(options.bold), bool(options.italic))
end

function avatar.texture.sync(mode) _avatar_texture_sync(mode or "manifest") end

function avatar.network.online(value)
  _avatar_sync_policy("remote_snapshot", "", bool(value))
  _avatar_sync_policy("remote_vars", "", bool(value))
end
function avatar.network.snapshot(value) _avatar_sync_policy("remote_snapshot", "", bool(value)) end
function avatar.network.variables(value) _avatar_sync_policy("remote_vars", "", bool(value)) end
function avatar.network.allow(key) _avatar_sync_policy("allow_var", key or "", true) end
function avatar.network.local_part(path, value) _avatar_sync_policy("local_only_part", path or "", value == nil or bool(value)) end
function avatar.network.local_vanilla(part, value) _avatar_sync_policy("local_only_vanilla", part or "", value == nil or bool(value)) end

minecraft = { player = {}, world = {}, client = {}, settings = {} }
local function qvec(key) return vector.new(read(key) or {}) end

function minecraft.settings.powers_enabled() return read("settings.powers_enabled") ~= false end
function minecraft.settings.weapons_enabled() return read("settings.weapons_enabled") ~= false end
function minecraft.settings.hud_enabled() return read("settings.hud_enabled") ~= false end
avatar.settings = minecraft.settings

function minecraft.player.loaded() return read("player.loaded") or false end
function minecraft.player.name() return read("player.name") or "Player" end
function minecraft.player.uuid() return read("player.uuid") or "" end
function minecraft.player.position() return qvec("player.pos") end
function minecraft.player.velocity() return qvec("player.velocity") end
function minecraft.player.rotation() return qvec("player.rot") end
function minecraft.player.look() return qvec("player.look") end
function minecraft.player.health() return read("player.health") or 0 end
function minecraft.player.max_health() return read("player.max_health") or 0 end
function minecraft.player.alive() return read("player.alive") or (minecraft.player.health() > 0) end
function minecraft.player.set_velocity(vx, vy, vz)
  if type(vx) == "table" then
    return _shyne_player_set_velocity(vx.x or vx[1] or 0, vx.y or vx[2] or 0, vx.z or vx[3] or 0)
  end
  return _shyne_player_set_velocity(vx, vy, vz)
end
function minecraft.player.add_velocity(vx, vy, vz)
  local cur = minecraft.player.velocity()
  if type(vx) == "table" then
    return minecraft.player.set_velocity(cur.x + (vx.x or vx[1] or 0), cur.y + (vx.y or vx[2] or 0), cur.z + (vx.z or vx[3] or 0))
  end
  return minecraft.player.set_velocity(cur.x + (vx or 0), cur.y + (vy or 0), cur.z + (vz or 0))
end
function minecraft.player.body_yaw() return read("player.body_yaw") or 0 end
function minecraft.player.pose() return read("player.pose") or "STANDING" end

for _, name in ipairs({ "in_water", "underwater", "in_lava", "wet", "on_ground", "crouching", "sprinting", "swimming", "fall_flying", "sleeping", "using_item", "left_handed", "vehicle" }) do
  minecraft.player[name] = function() return read("player." .. name) or false end
end

function minecraft.player.active_item_time() return read("player.active_item_time") or 0 end
function minecraft.player.active_hand() return read("player.active_hand") or "NONE" end
function minecraft.player.held_item(hand) return read(hand == "off" and "player.off_hand" or "player.main_hand") end
function minecraft.player.vehicle() return read("player.vehicle") end
function minecraft.player.target(range) return read("player.target", tonumber(range) or 6) end
function minecraft.player.effects() return read("player.effects") or {} end
function minecraft.player.swing_progress() return read("player.swing") or 0 end
function minecraft.player.armor(slot)
  local names = { head = "player.armor_head", chest = "player.armor_chest", legs = "player.armor_legs", feet = "player.armor_feet" }
  return read(names[slot] or names.head)
end

function minecraft.world.loaded() return read("world.loaded") or false end
function minecraft.world.time() return read("world.time") or 0 end
function minecraft.world.day_time() return read("world.day_time") or 0 end
function minecraft.world.raining() return read("world.raining") or false end
function minecraft.world.light(position) position = vector.new(position or minecraft.player.position()); return read("world.light", position.x, position.y, position.z) or 0 end
function minecraft.world.block(position) position = vector.new(position or minecraft.player.position()); return read("world.block", position.x, position.y, position.z) or "minecraft:air" end
function minecraft.world.block_info(position) position = vector.new(position or minecraft.player.position()); return read("world.block_info", position.x, position.y, position.z) or { id = "minecraft:air", solid = false, fluid = false } end
function minecraft.world.raycast(range) return minecraft.player.target(range) end
function minecraft.world.probe(origin, direction, distance, radius)
  origin, direction = vector.new(origin or minecraft.player.position()), vector.new(direction or minecraft.player.look())
  return read("world.probe", origin.x, origin.y, origin.z, direction.x, direction.y, direction.z, tonumber(distance) or 1, tonumber(radius) or 0) or { hit = false, type = "MISS" }
end
function minecraft.world.biome(position) position = vector.new(position or minecraft.player.position()); return read("world.biome", position.x, position.y, position.z) or "" end
function minecraft.world.players(radius, include_self) return read("world.players", tonumber(radius) or 32, include_self == true) or {} end
function minecraft.world.entities(radius) return read("world.entities", tonumber(radius) or 16) or {} end
function minecraft.world.set_block(position, block)
  if world and world.setBlock then return world.setBlock(position, block) end
  local p = vector.new(position or minecraft.player.position())
  return _shyne_world_set_block and _shyne_world_set_block(math.floor(p.x), math.floor(p.y), math.floor(p.z), tostring(block or "minecraft:air")) or false
end
function minecraft.world.set_time(time)
  return _shyne_world_set_time and _shyne_world_set_time(tonumber(time) or 0) or false
end

function minecraft.client.paused() return read("client.paused") or false end
function minecraft.client.singleplayer() return read("client.singleplayer") or false end
function minecraft.client.first_person() return read("client.first_person") or false end
function minecraft.command(command) return _minecraft_shyne_command(command) end

-- ------------------------------------------------------------------------------
-- MICROPHONE (Voice Audio State)
-- ------------------------------------------------------------------------------
microphone = {}
function microphone.available() return type(_microphone_available) == "function" and _microphone_available() or false end
function microphone.level(target) return type(_microphone_level) == "function" and _microphone_level(target) or 0 end
function microphone.speaking(target) return type(_microphone_speaking) == "function" and _microphone_speaking(target) or false end
function microphone.muted() return type(_microphone_muted) == "function" and _microphone_muted() or false end

-- ------------------------------------------------------------------------------
-- AUDIO (Custom Avatar Sounds & Vanilla SFX)
-- ------------------------------------------------------------------------------
sound = {}

--- Plays a custom avatar sound or Minecraft sound event.
---@param id string Sound name (e.g. "my_roar" or "minecraft:entity.player.levelup")
---@param options number|table Volume number or options table { volume, pitch, pos }
---@param pitch number|nil Pitch multiplier (default 1.0)
---@param pos Vector3|table|nil 3D world position (nil for ambient / local player)
---@return boolean
function sound.play(id, options, pitch, pos)
  if type(options) == "number" then
    return _shyne_sound_play(id, options, pitch or 1, pos and (pos.x or pos[1]) or nil, pos and (pos.y or pos[2]) or nil, pos and (pos.z or pos[3]) or nil)
  end
  options = options or {}
  local p = options.pos or options.position
  return _shyne_sound_play(
    id,
    options.volume or options.vol or 1,
    options.pitch or 1,
    p and (p.x or p[1]) or nil,
    p and (p.y or p[2]) or nil,
    p and (p.z or p[3]) or nil
  )
end

--- Starts an asynchronous audio stream from a web URL (HTTP/HTTPS) or file.
---@param url string Audio stream URL
---@param options table|nil Stream options { volume, pitch, loop, pos, auto_play }
---@return table Stream handle with controls and real-time audio reactive metrics
function sound.stream(url, options)
  options = options or {}
  local p = options.pos or options.position
  local vol = options.volume or options.vol or 1.0
  local pitch = options.pitch or 1.0
  local loop = options.loop == true
  local auto_play = options.auto_play ~= false
  local stream_id = type(_shyne_audio_stream_create) == "function"
    and _shyne_audio_stream_create(url, vol, pitch, loop,
      p and (p.x or p[1]) or nil, p and (p.y or p[2]) or nil, p and (p.z or p[3]) or nil, auto_play)
    or 0

  local handle = { id = stream_id }
  function handle:play() if self.id > 0 and _shyne_audio_stream_play then _shyne_audio_stream_play(self.id) end; return self end
  function handle:pause() if self.id > 0 and _shyne_audio_stream_pause then _shyne_audio_stream_pause(self.id) end; return self end
  function handle:stop(fade) if self.id > 0 and _shyne_audio_stream_stop then _shyne_audio_stream_stop(self.id, fade or 0) end; return self end
  function handle:setVolume(v) if self.id > 0 and _shyne_audio_stream_set_volume then _shyne_audio_stream_set_volume(self.id, v) end; return self end
  function handle:setPitch(pt) if self.id > 0 and _shyne_audio_stream_set_pitch then _shyne_audio_stream_set_pitch(self.id, pt) end; return self end
  function handle:setPos(x, y, z)
    if self.id > 0 and _shyne_audio_stream_set_pos then
      if type(x) == "table" then _shyne_audio_stream_set_pos(self.id, x.x or x[1], x.y or x[2], x.z or x[3])
      else _shyne_audio_stream_set_pos(self.id, x, y, z) end
    end
    return self
  end
  function handle:getLevel() return (self.id > 0 and _shyne_audio_stream_get_level) and _shyne_audio_stream_get_level(self.id) or 0 end
  function handle:getPeak() return (self.id > 0 and _shyne_audio_stream_get_peak) and _shyne_audio_stream_get_peak(self.id) or 0 end
  function handle:isBeat() return (self.id > 0 and _shyne_audio_stream_is_beat) and _shyne_audio_stream_is_beat(self.id) or false end
  function handle:isPlaying() return (self.id > 0 and _shyne_audio_stream_is_playing) and _shyne_audio_stream_is_playing(self.id) or false end
  function handle:isBuffering() return (self.id > 0 and _shyne_audio_stream_is_buffering) and _shyne_audio_stream_is_buffering(self.id) or false end
  function handle:isPaused() return (self.id > 0 and _shyne_audio_stream_is_paused) and _shyne_audio_stream_is_paused(self.id) or false end
  function handle:isStopped() return (self.id > 0 and _shyne_audio_stream_is_stopped) and _shyne_audio_stream_is_stopped(self.id) or true end
  return handle
end

-- ------------------------------------------------------------------------------
-- PARTICLES
-- ------------------------------------------------------------------------------
particle = {}

--- Spawns a Minecraft particle with position, velocity, and options (including RGB for dust).
---@param id string Particle ID (e.g. "minecraft:portal", "minecraft:dust", "minecraft:flame")
---@param position Vector3|table|nil Spawn position
---@param options table|Vector3|nil Velocity vector OR options table { velocity, vel, color, rgb, scale }
---@param extra_options table|nil Optional extra options if 3rd arg was a velocity vector
---@return boolean
function particle.spawn(id, position, options, extra_options)
  position = vector.new(position or vector.zero())
  options = options or {}
  extra_options = extra_options or {}

  local velocity = vector.zero()
  local opt_table = extra_options

  if type(options) == "table" then
    if options.x or options[1] then
      velocity = vector.new(options)
    elseif options.velocity then
      velocity = vector.new(options.velocity)
      opt_table = options
    elseif options.vel then
      velocity = vector.new(options.vel)
      opt_table = options
    elseif options.vx or options.vy or options.vz then
      velocity = vector.new(options.vx or 0, options.vy or 0, options.vz or 0)
      opt_table = options
    else
      opt_table = options
    end
  end

  local color = opt_table.color or opt_table.rgb
  local r = color and (color.r or color[1] or 1) or opt_table.r
  local g = color and (color.g or color[2] or 1) or opt_table.g
  local b = color and (color.b or color[3] or 1) or opt_table.b
  local scale = opt_table.scale or opt_table.size or 1

  return _shyne_particle_spawn(
    tostring(id or "minecraft:crit"),
    position.x, position.y, position.z,
    velocity.x, velocity.y, velocity.z,
    r and tonumber(r) or nil,
    g and tonumber(g) or nil,
    b and tonumber(b) or nil,
    scale and tonumber(scale) or nil
  )
end

-- ------------------------------------------------------------------------------
-- INPUT BINDINGS
-- ------------------------------------------------------------------------------
input = {
  key = {
    unknown = -1, space = 32, apostrophe = 39, comma = 44, minus = 45, period = 46,
    slash = 47, zero = 48, one = 49, two = 50, three = 51, four = 52,
    five = 53, six = 54, seven = 55, eight = 56, nine = 57,
    a = 65, b = 66, c = 67, d = 68, e = 69, f = 70, g = 71, h = 72,
    i = 73, j = 74, k = 75, l = 76, m = 77, n = 78, o = 79, p = 80,
    q = 81, r = 82, s = 83, t = 84, u = 85, v = 86, w = 87, x = 88,
    y = 89, z = 90, escape = 256, enter = 257, tab = 258,
    backspace = 259, insert = 260, delete = 261, right = 262, left = 263,
    down = 264, up = 265, page_up = 266, page_down = 267, home = 268, ending = 269
  },
  mouse = { left = 0, right = 1, middle = 2, button_4 = 3, button_5 = 4 },
  modifier = { shift = 1, control = 2, ctrl = 2, alt = 4, super = 8 }
}

local function input_modifier_mask(value)
  if type(value) == "number" then return value end
  if type(value) == "string" then return input.modifier[string.lower(value)] or 0 end
  local mask = 0
  if type(value) == "table" then
    for _, name in ipairs(value) do mask = mask + (input.modifier[string.lower(tostring(name))] or 0) end
  end
  return mask
end

function input.bind(id, options)
  options = options or {}
  return _shyne_input_bind(id, options.title or id, options.key or input.key.unknown,
    options.type or "keyboard", input_modifier_mask(options.modifiers), options.on_press,
    options.on_release, options.on_hold, options["repeat"] == true,
    options.repeat_delay or 10, options.repeat_interval or 2)
end
function input.unbind(id) return _shyne_input_unbind(id) end
function input.is_down(id) return _shyne_input_is_down(id) end
function input.get_key(id) return _shyne_input_get_key(id) end
function input.set_key(id, key_name) return _shyne_input_set_key(id, key_name) end
function input.conflicts(id) return _shyne_input_conflicts(id) end
