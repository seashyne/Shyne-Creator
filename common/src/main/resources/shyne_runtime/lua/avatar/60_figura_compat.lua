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

--- Enhance string metatable so string item IDs can also be read with .id, :getId(), etc.
local str_mt = (debug and debug.getmetatable and debug.getmetatable("")) or getmetatable("")
if str_mt then
  local old_str_index = str_mt.__index
  str_mt.__index = function(s, k)
    if k == "id" then return s end
    if k == "getId" then return function() return s end end
    if k == "name" then return s end
    if k == "getName" then return function() return s end end
    if k == "count" then return 1 end
    if k == "getCount" then return function() return 1 end end
    if k == "isDamageable" then return function() return false end end
    if k == "hasGlint" then return function() return false end end
    if k == "hasTag" then return function() return false end end
    if k == "getTag" then return function() return {} end end
    if type(old_str_index) == "function" then return old_str_index(s, k) end
    if type(old_str_index) == "table" then return old_str_index[k] end
    return string[k]
  end
end

--- Creates a safe item proxy mimicking Figura's ItemStack object
local function make_item_proxy(raw)
  if not raw or raw.empty then
    local p = {
      id = "minecraft:air",
      count = 0,
      empty = true,
      name = "Air",
      damage = 0,
      max_damage = 0,
      glint = false
    }
    function p:getId() return self.id end
    function p:getCount() return 0 end
    function p:getName() return "Air" end
    function p:isDamageable() return false end
    function p:getDamage() return 0 end
    function p:getMaxDamage() return 0 end
    function p:hasGlint() return false end
    function p:hasTag() return false end
    function p:getTag() return {} end
    return setmetatable(p, {
      __tostring = function() return "minecraft:air" end,
      __eq = function(a, b)
        local id_b = type(b) == "table" and b.id or tostring(b)
        return a.id == id_b
      end
    })
  end
  local id = raw.id or "minecraft:air"
  local p = {
    id = id,
    count = raw.count or 1,
    empty = raw.empty or false,
    name = raw.name or "",
    damage = raw.damage or 0,
    max_damage = raw.max_damage or 0,
    glint = raw.glint or false
  }
  function p:getId() return self.id end
  function p:getCount() return self.count end
  function p:getName() return self.name end
  function p:isDamageable() return (self.max_damage or 0) > 0 end
  function p:getDamage() return self.damage or 0 end
  function p:getMaxDamage() return self.max_damage or 0 end
  function p:hasGlint() return self.glint or false end
  function p:hasTag() return false end
  function p:getTag() return {} end
  return setmetatable(p, {
    __tostring = function() return id end,
    __eq = function(a, b)
      local id_b = type(b) == "table" and b.id or tostring(b)
      return a.id == id_b
    end
  })
end

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
      if primary == "arrow_render" then
        local delta = payload and (payload.delta or payload.partial_tick) or 0
        local arrow = payload and (payload.arrow or payload.entity) or nil
        return fn(delta, arrow, payload)
      end
      if primary == "item_render" then
        local raw_item = payload and payload.item or ""
        local item = raw_item
        if type(item) == "table" and not item.id then
          item = make_item_proxy(item)
        end
        local mode = payload and payload.mode or "NONE"
        local pos = payload and payload.pos or nil
        local rot = payload and payload.rot or nil
        local scale = payload and payload.scale or nil
        local left = payload and payload.left or false
        return fn(item, mode, pos, rot, scale, left, payload)
      end
      if primary == "world_tick" then
        return fn()
      end
      local delta = payload and (payload.delta or payload.partial_tick) or 0
      local context = payload and payload.context or "render"
      local matrix = payload and payload.matrix or nil
      return fn(delta, context, matrix)
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
  function emitter:getRegisteredCount()
    local count = 0
    for _ in pairs(registered) do count = count + 1 end
    return count
  end
  function emitter:getRegistered()
    local list = {}
    for r_name in pairs(registered) do table.insert(list, r_name) end
    table.sort(list)
    return list
  end
  setmetatable(emitter, {
    __call = function(self, fn, name)
      return self:register(fn, name)
    end
  })
  return emitter
end

local _emitters = {}
local function register_emitter(name, ...)
  local em = make_figura_event_emitter(...)
  _emitters[name:upper()] = em
  _emitters[name:lower()] = em
  return em
end

register_emitter("tick", "tick")
register_emitter("world_tick", "world_tick")
register_emitter("render", "render")
register_emitter("post_render", "post_render")
register_emitter("world_render", "world_render")
register_emitter("post_world_render", "post_world_render")
register_emitter("entity_init", "entity_init")
register_emitter("damage", "damage")
register_emitter("chat_send_message", "chat_send_message")
register_emitter("chat_receive_message", "chat_receive", "chat_receive_message")
register_emitter("key_press", "key_press")
register_emitter("key_release", "key_release")
register_emitter("key_repeat", "key_repeat")
register_emitter("mouse_press", "mouse_press")
register_emitter("mouse_release", "mouse_release")
register_emitter("mouse_scroll", "mouse_scroll")
register_emitter("mouse_move", "mouse_move")
register_emitter("char_typed", "char_typed")
register_emitter("use_item", "item_use", "use_item")
register_emitter("item_use", "item_use", "use_item")
register_emitter("totem", "totem")
register_emitter("totem_pop", "totem")
register_emitter("entity_damage", "damage")
register_emitter("chat_receive", "chat_receive", "chat_receive_message")
register_emitter("skull_render", "skull_render")
register_emitter("arrow_render", "arrow_render")
register_emitter("item_render", "item_render")

local event_alias_map = {
  tick = "TICK",
  world_tick = "WORLD_TICK",
  render = "RENDER",
  post_render = "POST_RENDER",
  world_render = "WORLD_RENDER",
  post_world_render = "POST_WORLD_RENDER",
  entity_init = "ENTITY_INIT",
  damage = "DAMAGE",
  chat_receive = "CHAT_RECEIVE_MESSAGE",
  chat_receive_message = "CHAT_RECEIVE_MESSAGE",
  chat_send_message = "CHAT_SEND_MESSAGE",
  key_press = "KEY_PRESS",
  key_release = "KEY_RELEASE",
  key_repeat = "KEY_REPEAT",
  mouse_press = "MOUSE_PRESS",
  mouse_release = "MOUSE_RELEASE",
  mouse_scroll = "MOUSE_SCROLL",
  mouse_move = "MOUSE_MOVE",
  char_typed = "CHAR_TYPED",
  use_item = "USE_ITEM",
  item_use = "USE_ITEM",
  totem = "TOTEM",
  totem_pop = "TOTEM",
  entity_damage = "DAMAGE",
  skull_render = "SKULL_RENDER",
  arrow_render = "ARROW_RENDER",
  item_render = "ITEM_RENDER"
}

function events:getEvents()
  return {
    "TICK", "WORLD_TICK", "RENDER", "POST_RENDER", "WORLD_RENDER", "POST_WORLD_RENDER",
    "ENTITY_INIT", "DAMAGE", "CHAT_SEND_MESSAGE", "CHAT_RECEIVE_MESSAGE",
    "KEY_PRESS", "KEY_RELEASE", "KEY_REPEAT", "MOUSE_PRESS", "MOUSE_RELEASE",
    "MOUSE_SCROLL", "MOUSE_MOVE", "CHAR_TYPED", "USE_ITEM", "TOTEM",
    "SKULL_RENDER", "ARROW_RENDER", "ITEM_RENDER"
  }
end

local _auto_event_id = 0
local events_mt = getmetatable(events) or {}
events_mt.__index = function(t, k)
  local str_k = tostring(k)
  local mapped = event_alias_map[str_k:lower()]
  if mapped and _emitters[mapped] then
    return _emitters[mapped]
  end
  if _emitters[str_k:upper()] then
    return _emitters[str_k:upper()]
  end
  return rawget(t, k)
end

events_mt.__newindex = function(t, k, v)
  local str_k = tostring(k)
  local mapped = event_alias_map[str_k:lower()]
  local emitter = mapped and _emitters[mapped] or _emitters[str_k:upper()]
  if emitter and type(emitter.register) == "function" then
    if type(v) == "function" then
      _auto_event_id = _auto_event_id + 1
      emitter:register(v, "__auto_" .. str_k .. "_" .. tostring(_auto_event_id))
      return
    elseif v == nil then
      return
    end
  end
  rawset(t, k, v)
end
setmetatable(events, events_mt)

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
local _ping_depth = 0
local MAX_PING_DEPTH = 16

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
      if _ping_depth >= MAX_PING_DEPTH then
        if _shyne_report_error then
          _shyne_report_error("ping_recursion", tostring(name), "Maximum ping recursion depth exceeded (" .. tostring(MAX_PING_DEPTH) .. ")")
        end
        return
      end
      _ping_depth = _ping_depth + 1
      local safe_args = _validate_and_sanitize_ping_args(name, { ... })
      if _ping_handlers[name] then
        pcall(_ping_handlers[name], table.unpack(safe_args))
      end
      _enqueue_ping(name, safe_args)
      _ping_depth = _ping_depth - 1
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
function player:getBodyYaw() return _shyne_read("player.body_yaw") or 0 end
function player:getPose() return _shyne_read("player.pose") or "STANDING" end
function player:getVehicle() return _shyne_read("player.vehicle") end
function player:isSneaking() return _shyne_read("player.crouching") or false end
function player:isSprinting() return _shyne_read("player.sprinting") or false end
function player:isUnderwater() return _shyne_read("player.underwater") or false end
function player:isInWater() return _shyne_read("player.in_water") or false end
function player:isOnGround() return _shyne_read("player.on_ground") or false end
function player:isGliding() return _shyne_read("player.fall_flying") or false end
function player:isSwingingArm() return _shyne_read("player.swinging") or ((_shyne_read("player.swing") or 0) > 0.05) or false end
function player:isUsingItem() return _shyne_read("player.using_item") or false end
function player:getName() return _shyne_read("player.name") or "Player" end
function player:isAlive() return _shyne_read("player.alive") or (self:getHealth() > 0) end
function player:getHealth() return _shyne_read("player.health") or 20 end
function player:getMaxHealth() return _shyne_read("player.max_health") or 20 end
function player:isLoaded() return _shyne_read("player.loaded") or true end

function player:getHeldItem(off_hand)
  local key = (off_hand == true or off_hand == "off_hand" or off_hand == "OFF_HAND") and "player.off_hand" or "player.main_hand"
  local item = _shyne_read(key)
  return make_item_proxy(item)
end

function player:getItem(slot)
  local item = _shyne_read("player.item", slot)
  return make_item_proxy(item)
end

function player:getEquipmentItem(slot)
  return self:getItem(slot)
end

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

---@class FiguraUser
user = player



---@class FiguraHost
host = host or {}
function host:isHost() return true end
function host:isChatOpen() return _shyne_read("client.chat_open") or false end
function host:isVoiceActive() return microphone and microphone.speaking and microphone.speaking() or false end
function host:isSpeaking() return self:isVoiceActive() or self:isChatOpen() end
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

---@class FiguraVoice
voice = voice or {}
function voice.isSpeaking() return host:isVoiceActive() end
function voice.getVolume() return host:getVoiceLevel() end
function voice:isSpeaking() return host:isVoiceActive() end
function voice:getVolume() return host:getVoiceLevel() end

if avatar then
  function avatar:isSpeaking() return host:isVoiceActive() or host:isChatOpen() end
  function avatar:isChatOpen() return host:isChatOpen() end
  function avatar:getVoiceLevel() return host:getVoiceLevel() end
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
        if sound and sound.play then
          return sound.play(sound_name, { volume = vol, pitch = pitch, pos = pos })
        end
        return false
      end
    }
    rawset(t, name, entry)
    return entry
  end
})

local function _parse_sound_args(name, a2, a3, a4)
  local pos = nil
  local vol = 1
  local pitch = 1
  if type(a2) == "table" then
    if a2.x ~= nil or a2[1] ~= nil then
      pos = a2
      vol = tonumber(a3) or 1
      pitch = tonumber(a4) or 1
    else
      pos = a2.pos or a2.position
      pitch = a2.pitch or 1
      vol = a2.volume or a2.vol or 1
    end
  else
    vol = tonumber(a2) or 1
    pitch = tonumber(a3) or 1
    pos = a4
  end
  return tostring(name or ""), { volume = vol, pitch = pitch, pos = pos }
end

function sounds:playSound(name, vol, pitch, pos)
  local sound_id, opts = _parse_sound_args(name, vol, pitch, pos)
  if sound and sound.play then
    return sound.play(sound_id, opts)
  end
  return false
end

function sounds.playSound(name, vol, pitch, pos)
  local sound_id, opts = _parse_sound_args(name, vol, pitch, pos)
  if sound and sound.play then
    return sound.play(sound_id, opts)
  end
  return false
end

function sounds:playStream(url, options)
  if sound and sound.stream then
    return sound.stream(url, options)
  end
  return false
end

function sounds.playStream(url, options)
  if sound and sound.stream then
    return sound.stream(url, options)
  end
  return false
end
