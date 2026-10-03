-- ==============================================================================
-- Shyne Creator: Figura Tier-1 Compatibility Layer (60_figura_compat.lua)
-- Shyne Creator: ชั้นความเข้ากันกับ Figura ระดับ Tier-1 (60_figura_compat.lua)
-- Provides the supported Figura-shaped APIs; it is not a 1:1 Figura runtime.
-- ให้ API รูปแบบ Figura เฉพาะส่วนที่รองรับ; ไม่ใช่ runtime Figura แบบ 1:1.
-- Includes Events bus, Action Wheel, Pings RPC, Keybinds, World/Player proxies,
-- รวม Events, Action Wheel, Pings RPC, Keybinds และ proxy ของ World/Player.
-- ==============================================================================

if not vectors or not matrices then
  pcall(require, "avatar.59_figura_vectors")
end

---@class FiguraGlobals
figura = figura or {
  version = "0.1.4",
  is_figura = true,
  engine = "shyne",
  compatibility_level = "tier_1"
}

-- ------------------------------------------------------------------------------
-- 1. FIGURA EVENT BUS ADAPTERS (events.TICK, events.RENDER, etc.)
-- ------------------------------------------------------------------------------

--- Creates a Figura-compatible event emitter proxy wrapping Shyne's event bus.
---@param event_name string
---@return table Emitter object with register, remove, and clear methods
local function make_figura_event_emitter(...)
  local names = { ... }
  local primary = names[1]
  local emitter = {}
  local registered = {}
  function emitter:register(fn, name)
    if type(fn) ~= "function" then return fn end
    name = name or tostring(fn)
    if registered[name] then
      for _, n in ipairs(names) do events.off(n, registered[name]) end
    end
    local listener = function(payload)
      if primary == "key_press" or primary == "key_release" or primary == "key_repeat"
        or primary == "mouse_press" or primary == "mouse_release" then
        return fn(payload and payload.key or 0, payload and payload.scan_code or 0, payload and payload.modifiers or 0, payload)
      end
      if primary == "mouse_scroll" then
        return fn(payload and payload.horizontal or 0, payload and payload.vertical or 0, payload)
      end
      if primary == "mouse_move" then
        return fn(payload and payload.x or 0, payload and payload.y or 0, payload and payload.dx or 0, payload and payload.dy or 0, payload)
      end
      if primary == "char_typed" then
        return fn(payload and payload.characters or "", payload and payload.modifiers or 0, payload)
      end
      if primary == "item_use" or primary == "use_item" then
        return fn(payload and payload.item or "", payload and payload.action or "use", payload and (payload.count or payload.particle_count) or 0, payload)
      end
      if primary == "chat_receive" or primary == "chat_receive_message" then
        return fn(payload and payload.raw or "", payload and payload.text or "", payload and payload.sender_uuid or "", payload and payload.sender_name or "", payload)
      end
      if primary == "damage" then
        return fn(payload and payload.amount or 0, payload and payload.source or "", payload and payload.attacker or "", payload)
      end
      if primary == "totem" then
        return fn(payload)
      end
      if primary == "skull_render" then
        local delta = payload and (payload.delta or payload.partial_tick) or 0
        local context = payload and payload.context or "SKULL"
        return fn(delta, context, payload)
      end
      local delta = payload and (payload.delta or payload.partial_tick) or 0
      local context = payload and payload.context or "render"
      return fn(delta, context)
    end
    registered[name] = listener
    for _, n in ipairs(names) do events.on(n, listener) end
    return fn
  end
  function emitter:remove(name)
    if registered[name] then
      for _, n in ipairs(names) do events.off(n, registered[name]) end
      registered[name] = nil
    end
  end
  function emitter:clear()
    for _, fn in pairs(registered) do
      for _, n in ipairs(names) do events.off(n, fn) end
    end
    registered = {}
  end
  return emitter
end

events.TICK = make_figura_event_emitter("tick")
events.RENDER = make_figura_event_emitter("render")
events.POST_RENDER = make_figura_event_emitter("post_render")
events.WORLD_RENDER = make_figura_event_emitter("world_render")
events.POST_WORLD_RENDER = make_figura_event_emitter("post_world_render")
events.ENTITY_INIT = make_figura_event_emitter("entity_init")
events.DAMAGE = make_figura_event_emitter("damage")
events.CHAT_SEND_MESSAGE = make_figura_event_emitter("chat_send_message")
events.CHAT_RECEIVE_MESSAGE = make_figura_event_emitter("chat_receive", "chat_receive_message")
events.KEY_PRESS = make_figura_event_emitter("key_press")
events.KEY_RELEASE = make_figura_event_emitter("key_release")
events.KEY_REPEAT = make_figura_event_emitter("key_repeat")
events.MOUSE_PRESS = make_figura_event_emitter("mouse_press")
events.MOUSE_RELEASE = make_figura_event_emitter("mouse_release")
events.MOUSE_SCROLL = make_figura_event_emitter("mouse_scroll")
events.MOUSE_MOVE = make_figura_event_emitter("mouse_move")
events.CHAR_TYPED = make_figura_event_emitter("char_typed")
events.USE_ITEM = make_figura_event_emitter("item_use", "use_item")
events.ITEM_USE = events.USE_ITEM
events.TOTEM = make_figura_event_emitter("totem")
events.TOTEM_POP = events.TOTEM
events.ENTITY_DAMAGE = events.DAMAGE
events.CHAT_RECEIVE = events.CHAT_RECEIVE_MESSAGE
events.SKULL_RENDER = make_figura_event_emitter("skull_render")

-- Action Wheel is implemented in 63_figura_action_wheel.lua

-- ------------------------------------------------------------------------------
-- 3. PINGS RPC NETWORK BRIDGE (pings)
-- ------------------------------------------------------------------------------

---@class FiguraPings
pings = pings or {}
local _ping_handlers = {}
local _ping_schemas = {}
local _ping_sequence = 0
local _ping_last_received = 0
local _ping_capacity = 64
local _ping_outbox = { first = 1, events = {} }

local function _sanitize_ping_value(val, depth)
  local t = type(val)
  if t == "number" then
    if val ~= val or val == math.huge or val == -math.huge then return 0 end
    return val
  elseif t == "string" then
    if #val > 2048 then return string.sub(val, 1, 2048) end
    return val
  elseif t == "boolean" then
    return val
  elseif t == "nil" then
    return nil
  elseif t == "table" then
    if (depth or 1) > 3 then return nil end
    local clean = {}
    local count = 0
    for k, v in pairs(val) do
      count = count + 1
      if count > 64 then break end
      local clean_k = _sanitize_ping_value(k, (depth or 1) + 1)
      local clean_v = _sanitize_ping_value(v, (depth or 1) + 1)
      if clean_k ~= nil and clean_v ~= nil then
        clean[clean_k] = clean_v
      end
    end
    return clean
  end
  return nil
end

local function _validate_and_sanitize_ping_args(name, raw_args)
  local sanitized = {}
  local schema = _ping_schemas[name]
  local n = math.min(16, #raw_args)
  for i = 1, n do
    local arg = raw_args[i]
    local val = _sanitize_ping_value(arg, 1)
    if schema and schema[i] then
      local expected = schema[i]
      local actual_type = type(val)
      if expected == "number" and actual_type ~= "number" then
        val = tonumber(val) or 0
      elseif expected == "string" and actual_type ~= "string" then
        val = tostring(val or "")
      elseif expected == "boolean" and actual_type ~= "boolean" then
        val = not not val
      end
    end
    sanitized[i] = val
  end
  return sanitized
end

-- Snapshots are rate-limited, so a single value loses every ping except the
-- last one when a script calls pings.foo() more than once per tick. Keep a
-- bounded, sequence-numbered queue instead. Receivers can safely see the
-- most recent snapshot more than once because sequence numbers are deduped.
local function _publish_ping_queue()
  _avatar_synced_set("__figura_ping_queue", {
    first = _ping_outbox.first,
    last = _ping_sequence,
    events = _ping_outbox.events
  })
end

local function _dispatch_ping(data)
  if type(data) ~= "table" or type(data.name) ~= "string" then return end
  local handler = _ping_handlers[data.name]
  if handler then
    local args = _validate_and_sanitize_ping_args(data.name, data.args or {})
    pcall(handler, table.unpack(args))
  end
end

local function _receive_ping_queue(queue)
  if type(queue) ~= "table" or type(queue.events) ~= "table" then return end
  for _, data in ipairs(queue.events) do
    local sequence = tonumber(data and data.seq)
    if sequence and sequence > _ping_last_received then
      _ping_last_received = sequence
      _dispatch_ping(data)
    end
  end
end

local function _enqueue_ping(name, raw_args)
  local args = _validate_and_sanitize_ping_args(name, raw_args)
  _ping_sequence = _ping_sequence + 1
  local events = _ping_outbox.events
  table.insert(events, { name = name, args = args, seq = _ping_sequence })
  if #events > _ping_capacity then
    table.remove(events, 1)
    _ping_outbox.first = _ping_sequence - #events + 1
  end
  _publish_ping_queue()
end

local _ping_builtins = {
  define = function(self, name, types, func)
    local target = name
    local schema_types = types
    local handler = func
    if func == nil and type(self) == "string" then
      target = self
      schema_types = name
      handler = types
    elseif func == nil and type(types) == "function" then
      target = name
      schema_types = nil
      handler = types
    end
    if type(schema_types) == "table" then _ping_schemas[target] = schema_types end
    if type(handler) == "function" then _ping_handlers[target] = handler end
  end,
  schema = function(self, name, types)
    local target = (types ~= nil or type(name) ~= "table") and name or self
    local schema_types = (types ~= nil) and types or name
    if type(schema_types) == "table" then _ping_schemas[target] = schema_types end
    return _ping_schemas[target]
  end,
  get_schema = function(self, name)
    local target = (name ~= nil) and name or self
    return _ping_schemas[target]
  end,
  is_valid_type = function(self, val)
    local t = type(val)
    if t == "number" then return val == val and val ~= math.huge and val ~= -math.huge end
    if t == "string" then return #val <= 2048 end
    if t == "boolean" or t == "nil" then return true end
    if t == "table" then return true end
    return false
  end
}

setmetatable(pings, {
  __newindex = function(_, name, func)
    if _ping_builtins[name] then
      error("Cannot override reserved ping method: " .. tostring(name))
    end
    if type(func) == "function" then
      _ping_handlers[name] = func
    end
  end,
  __index = function(_, name)
    if _ping_builtins[name] then
      return _ping_builtins[name]
    end
    return function(...)
      local safe_args = _validate_and_sanitize_ping_args(name, { ... })
      if _ping_handlers[name] then
        pcall(_ping_handlers[name], table.unpack(safe_args))
      end
      _enqueue_ping(name, safe_args)
    end
  end
})

events.on("synced_var_change", function(payload)
  if not payload or not payload.value then return end
  if payload.key == "__figura_ping_queue" then
    _receive_ping_queue(payload.value)
  elseif payload.key == "__figura_ping" then
    -- Read older snapshots during the migration from the single-value format.
    _dispatch_ping(payload.value)
  end
end)

-- ------------------------------------------------------------------------------
-- 4. KEYBINDS BRIDGE (keybinds:newKeybind)
-- สะพาน Keybind ที่รองรับรูปแบบ API ของ Figura เท่าที่ Shyne มี native input
-- ------------------------------------------------------------------------------

---@class FiguraKeybinds
keybinds = keybinds or {}
local _figura_keybinds = {}
local keybind_mt = {}
keybind_mt.__index = function(self, key)
  if key == "press" then return self._onPress end
  if key == "release" then return self._onRelease end
  return keybind_mt[key]
end

function keybind_mt:setOnPress(fn) self._onPress = fn; return self end
function keybind_mt:onPress(fn) return self:setOnPress(fn) end
function keybind_mt:setOnRelease(fn) self._onRelease = fn; return self end
function keybind_mt:onRelease(fn) return self:setOnRelease(fn) end
function keybind_mt:setKey(key)
  if type(key) == "string" and _shyne_input_set_key(self._id, key) then self._key = key end
  return self
end
function keybind_mt:key(key) return self:setKey(key) end
function keybind_mt:getKey() return _shyne_input_get_key(self._id) end
function keybind_mt:getKeyName() return _shyne_input_get_key_name(self._id) end
function keybind_mt:getName() return self._name end
function keybind_mt:getID() return _shyne_input_get_id(self._id) end
function keybind_mt:isDefault() return _shyne_input_is_default(self._id) end
function keybind_mt:reset() return _shyne_input_reset(self._id) end
function keybind_mt:isEnabled() return _shyne_input_is_enabled(self._id) end
function keybind_mt:setEnabled(enabled) _shyne_input_set_enabled(self._id, enabled ~= false); return self end
function keybind_mt:enabled(enabled) return self:setEnabled(enabled) end
function keybind_mt:isGuiEnabled() return _shyne_input_is_gui(self._id) end
function keybind_mt:setGUI(enabled) _shyne_input_set_gui(self._id, enabled == true); return self end
function keybind_mt:gui(enabled) return self:setGUI(enabled) end
function keybind_mt:isPressed()
  return _shyne_input_is_down(self._id)
end

keybind_mt.__newindex = function(self, key, value)
  if key == "press" then self._onPress = value
  elseif key == "release" then self._onRelease = value
  else rawset(self, key, value) end
end

function keybinds:newKeybind(name, default_key, gui)
  local id = "kb_" .. name:gsub("%s+", "_"):lower()
  local kb = setmetatable({
    _id = id,
    _name = name,
    _default_key = default_key or "key.keyboard.unknown",
    _key = default_key or "key.keyboard.unknown",
    _onPress = nil,
    _onRelease = nil
  }, keybind_mt)

  _shyne_input_bind(
    id,
    name,
    kb._default_key,
    "keyboard",
    0,
    function() if kb._onPress then kb._onPress(0, kb) end end,
    function() if kb._onRelease then kb._onRelease(0, kb) end end,
    nil,
    false,
    10,
    2,
    gui == true
  )
  _figura_keybinds[name] = kb
  return kb
end

function keybinds:of(name, default_key, gui) return self:newKeybind(name, default_key, gui) end
function keybinds:getKeybinds() return _figura_keybinds end
function keybinds:getVanillaKey(id) return _shyne_input_vanilla_key(id) end
function keybinds:fromVanilla(id)
  local key = _shyne_input_vanilla_key(id)
  if key == nil then return nil end
  local label = _shyne_input_vanilla_name(id) or id
  return self:newKeybind("[Vanilla] " .. label, key, false)
end

-- renderer and client are implemented in 61_figura_client_renderer.lua

-- ------------------------------------------------------------------------------
-- 6. PLAYER & WORLD PROXIES (player, world)
-- ------------------------------------------------------------------------------

---@class FiguraPlayer
player = player or {}
function player:getPos()
  local pos = _shyne_read("player.pos")
  return pos and vectors.vec3(pos.x, pos.y, pos.z) or vectors.vec3(0, 0, 0)
end
function player:getVelocity()
  local vel = _shyne_read("player.velocity")
  return vel and vectors.vec3(vel.x, vel.y, vel.z) or vectors.vec3(0, 0, 0)
end
function player:getRot()
  local rot = _shyne_read("player.rot")
  return rot and vectors.vec3(rot.x, rot.y, rot.z) or vectors.vec3(0, 0, 0)
end
function player:getLookDir()
  local look = _shyne_read("player.look")
  return look and vectors.vec3(look.x, look.y, look.z) or vectors.vec3(0, 0, 1)
end
function player:isSneaking() return _shyne_read("player.crouching") or false end
function player:isSprinting() return _shyne_read("player.sprinting") or false end
function player:isUnderwater() return _shyne_read("player.underwater") or false end
function player:isInWater() return _shyne_read("player.in_water") or false end
function player:isOnGround() return _shyne_read("player.on_ground") or false end
function player:isGliding() return _shyne_read("player.fall_flying") or false end
function player:isSwingingArm() return _shyne_read("player.using_item") or false end
function player:getName() return _shyne_read("player.name") or "Player" end
function player:isAlive() return _shyne_read("player.alive") or (self:getHealth() > 0) end
function player:getHealth() return _shyne_read("player.health") or 20 end
function player:getMaxHealth() return _shyne_read("player.max_health") or 20 end
function player:setVelocity(vx, vy, vz)
  if type(vx) == "table" then
    return _shyne_player_set_velocity(vx.x or vx[1] or 0, vx.y or vx[2] or 0, vx.z or vx[3] or 0)
  end
  return _shyne_player_set_velocity(vx, vy, vz)
end
function player:addVelocity(vx, vy, vz)
  local cur = self:getVelocity()
  if type(vx) == "table" then
    return self:setVelocity(cur.x + (vx.x or vx[1] or 0), cur.y + (vx.y or vx[2] or 0), cur.z + (vx.z or vx[3] or 0))
  end
  return self:setVelocity(cur.x + (vx or 0), cur.y + (vy or 0), cur.z + (vz or 0))
end



---@class FiguraHost
host = host or {}
function host:isHost() return true end
function host:isChatOpen() return _shyne_read("client.chat_open") or false end
function host:isVoiceActive() return microphone and microphone.speaking and microphone.speaking() or false end
function host:isMuted() return microphone and microphone.muted and microphone.muted() or false end
function host:getVoiceLevel() return microphone and microphone.level and microphone.level() or 0 end
function host:getPos() return player:getPos() end
function host:isSneaking() return player:isSneaking() end
function host:isSprinting() return player:isSprinting() end
function host:isFlying() return player:isGliding() end
function host:getAir() return 300 end
function host:sendChat(message)
  return _shyne_send_chat and _shyne_send_chat(tostring(message or "")) or false
end

if avatar then
  function avatar:isSpeaking() return microphone and microphone.speaking and microphone.speaking() or false end
  function avatar:getVoiceLevel() return microphone and microphone.level and microphone.level() or 0 end
  function avatar:canRender() return true end
  function avatar:isLoaded() return true end
end

---@class FiguraSettings
settings = settings or {}
function settings:isPowersEnabled() return _shyne_read("settings.powers_enabled") ~= false end
function settings:isWeaponsEnabled() return _shyne_read("settings.weapons_enabled") ~= false end
function settings:isHudEnabled() return _shyne_read("settings.hud_enabled") ~= false end

---@class FiguraWorld
world = world or {}
function world.getTime() return _shyne_read("world.time") or 0 end

--- Returns list of nearby players (Figura compatible)
---@param radius number|nil Optional search radius in blocks (default 64)
---@return table Array of player proxies
function world.getPlayers(radius)
  local list = _shyne_read("world.players", tonumber(radius) or 64, true) or {}
  local result = {}
  for _, p in ipairs(list) do
    local obj = {
      _data = p,
      getPos = function(self) return vectors.vec3(self._data.pos.x, self._data.pos.y, self._data.pos.z) end,
      getName = function(self) return self._data.name end,
      getUUID = function(self) return self._data.uuid end,
      getHealth = function(self) return self._data.health end,
      getMaxHealth = function(self) return self._data.max_health end,
      isSneaking = function(self) return self._data.crouching end,
      isSprinting = function(self) return self._data.sprinting end,
      isOnGround = function(self) return self._data.on_ground end,
      isLoaded = function(self) return true end
    }
    table.insert(result, setmetatable(obj, { __index = p }))
  end
  return result
end

--- Returns list of nearby entities (Figura compatible)
---@param pos table|number|nil Center position or radius
---@param radius number|nil Optional radius in blocks (default 32)
---@return table Array of entity proxies
function world.getEntities(pos, radius)
  local rad = tonumber(radius) or (type(pos) == "number" and pos or 32)
  local list = _shyne_read("world.entities", rad) or {}
  local result = {}
  for _, e in ipairs(list) do
    local obj = {
      _data = e,
      getPos = function(self) return vectors.vec3(self._data.pos.x, self._data.pos.y, self._data.pos.z) end,
      getName = function(self) return self._data.name end,
      getUUID = function(self) return self._data.uuid end,
      getType = function(self) return self._data.type end,
      getHealth = function(self) return self._data.health end,
      getMaxHealth = function(self) return self._data.max_health end,
      isLiving = function(self) return self._data.is_living end,
      isMonster = function(self) return self._data.is_monster end,
      isPlayer = function(self) return self._data.is_player end,
      isOnGround = function(self) return self._data.on_ground end
    }
    table.insert(result, setmetatable(obj, { __index = e }))
  end
  return result
end

-- ------------------------------------------------------------------------------
-- 6. PARTICLES PROXY (particles:newParticle)
-- ------------------------------------------------------------------------------

---@class FiguraParticles
particles = particles or {}
function particles:newParticle(particle_type, pos, vel)
  pos = pos or { 0, 0, 0 }
  vel = vel or { 0, 0, 0 }
  if particle and particle.spawn then
    return particle.spawn(
      tostring(particle_type or "minecraft:crit"),
      vector.new(pos.x or pos[1] or 0, pos.y or pos[2] or 0, pos.z or pos[3] or 0),
      vector.new(vel.x or vel[1] or 0, vel.y or vel[2] or 0, vel.z or vel[3] or 0)
    )
  end
end

-- ------------------------------------------------------------------------------
-- 7. SOUNDS PROXY (sounds:playSound, sounds.playSound, sounds[name]:play)
-- ------------------------------------------------------------------------------

---@class FiguraSounds
sounds = sounds or setmetatable({}, {
  __index = function(t, name)
    local sound_name = tostring(name)
    local entry = {
      play = function(self, vol, pitch, pos)
        if type(vol) == "table" then
          pos = vol.pos or vol.position
          pitch = vol.pitch
          vol = vol.volume or vol.vol
        end
        return sound.play(sound_name, { volume = vol, pitch = pitch, pos = pos })
      end
    }
    rawset(t, name, entry)
    return entry
  end
})

function sounds:playSound(name, vol, pitch, pos)
  if type(vol) == "table" then
    pos = vol.pos or vol.position
    pitch = vol.pitch
    vol = vol.volume or vol.vol
  end
  return sound.play(tostring(name), { volume = vol, pitch = pitch, pos = pos })
end

function sounds.playSound(name, vol, pitch, pos)
  if type(vol) == "table" then
    pos = vol.pos or vol.position
    pitch = vol.pitch
    vol = vol.volume or vol.vol
  end
  return sound.play(tostring(name), { volume = vol, pitch = pitch, pos = pos })
end

function sounds:playStream(url, options)
  return sound.stream(url, options)
end

function sounds.playStream(url, options)
  return sound.stream(url, options)
end
