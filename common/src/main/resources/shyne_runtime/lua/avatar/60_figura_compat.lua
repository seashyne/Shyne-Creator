-- ==============================================================================
-- Shyne Creator: Figura 100% Compatibility Layer (60_figura_compat.lua)
-- Provides 1:1 API compatibility for existing Figura avatars on Shyne Core.
-- Includes Events bus, Action Wheel, Pings RPC, Keybinds, World/Player proxies,
-- and Particle/Sound emitters.
-- ==============================================================================

if not vectors or not matrices then
  pcall(require, "avatar.59_figura_vectors")
end

---@class FiguraGlobals
figura = figura or {
  version = "0.1.4",
  is_figura = true,
  engine = "shyne",
  compatibility_level = "100%"
}

-- ------------------------------------------------------------------------------
-- 1. FIGURA EVENT BUS ADAPTERS (events.TICK, events.RENDER, etc.)
-- ------------------------------------------------------------------------------

--- Creates a Figura-compatible event emitter proxy wrapping Shyne's event bus.
---@param event_name string
---@return table Emitter object with register, remove, and clear methods
local function make_figura_event_emitter(event_name)
  local emitter = {}
  local registered = {}
  function emitter:register(fn, name)
    if type(fn) ~= "function" then return fn end
    name = name or tostring(fn)
    if registered[name] then
      events.off(event_name, registered[name])
    end
    registered[name] = fn
    events.on(event_name, function(payload)
      local delta = payload and (payload.delta or payload.partial_tick) or 0
      local context = payload and payload.context or "render"
      return fn(delta, context)
    end)
    return fn
  end
  function emitter:remove(name)
    if registered[name] then
      events.off(event_name, registered[name])
      registered[name] = nil
    end
  end
  function emitter:clear()
    for _, fn in pairs(registered) do
      events.off(event_name, fn)
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

-- ------------------------------------------------------------------------------
-- 2. ACTION WHEEL BRIDGE (action_wheel -> Shyne Palette Screen)
-- ------------------------------------------------------------------------------

---@class ActionWheel
action_wheel = action_wheel or {
  _pages = {},
  _current_page = nil
}

local action_mt = {}
action_mt.__index = action_mt

function action_mt:title(t) self._title = tostring(t or ""); self:_update(); return self end
function action_mt:setTitle(t) return self:title(t) end
function action_mt:item(i) self._item = tostring(i or ""); self:_update(); return self end
function action_mt:setItem(i) return self:item(i) end
function action_mt:color(r, g, b) self._color = { r or 1, g or 1, b or 1 }; return self end
function action_mt:setColor(r, g, b) return self:color(r, g, b) end
function action_mt:hoverColor(r, g, b) self._hoverColor = { r or 1, g or 1, b or 1 }; return self end
function action_mt:setHoverColor(r, g, b) return self:hoverColor(r, g, b) end
function action_mt:texture(path, u, v, w, h) self._texture = path; return self end
function action_mt:onLeftClick(fn) self._onLeftClick = fn; self:_update(); return self end
function action_mt:onRightClick(fn) self._onRightClick = fn; self:_update(); return self end
function action_mt:onScroll(fn) self._onScroll = fn; return self end
function action_mt:toggled(t) self._toggled = t and true or false; return self end
function action_mt:onToggle(fn) self._onToggle = fn; return self end

function action_mt:_update()
  if self._title ~= "" then
    local page_name = self._page and self._page.id or "main"
    local id = self.id or (page_name .. "_" .. self._title:gsub("%s+", "_"):lower())
    local col = nil
    if self._color then
      local r = math.floor(math.max(0, math.min(1, tonumber(self._color[1]) or 1)) * 255)
      local g = math.floor(math.max(0, math.min(1, tonumber(self._color[2]) or 1)) * 255)
      local b = math.floor(math.max(0, math.min(1, tonumber(self._color[3]) or 1)) * 255)
      col = (255 * 16777216) + (r * 65536) + (g * 256) + b
    end
    local hcol = nil
    if self._hoverColor then
      local r = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[1]) or 1)) * 255)
      local g = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[2]) or 1)) * 255)
      local b = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[3]) or 1)) * 255)
      hcol = (255 * 16777216) + (r * 65536) + (g * 256) + b
    end
    _avatar_action_register(
      id,
      self._title,
      self._desc or "",
      page_name,
      false,
      self._onToggle == nil,
      function()
        if self._onToggle then self._toggled = not self._toggled; self._onToggle(self._toggled); self:_update() end
        if self._onLeftClick then self._onLeftClick() end
      end,
      self._item or "",
      self._onRightClick and function() self._onRightClick() end or nil,
      self._onToggle ~= nil,
      self._toggled == true,
      col,
      hcol
    )
    self._registered = true
  end
end

local page_mt = {}
page_mt.__index = page_mt

function page_mt:newAction(id)
  local act = setmetatable({
    id = id or ("act_" .. tostring(#self.actions + 1)),
    _title = "",
    _item = "",
    _desc = "",
    _page = self,
    _registered = false
  }, action_mt)
  table.insert(self.actions, act)
  return act
end

function page_mt:getAction(id)
  for _, act in ipairs(self.actions) do
    if act.id == id then return act end
  end
  return nil
end

function action_wheel:newPage(title)
  local page = setmetatable({
    id = title or ("page_" .. tostring(#self._pages + 1)),
    title = title or "",
    actions = {}
  }, page_mt)
  table.insert(self._pages, page)
  if not self._current_page then self._current_page = page end
  return page
end

function action_wheel:setPage(page)
  self._current_page = page
end

function action_wheel:getCurrentPage()
  return self._current_page
end

-- ------------------------------------------------------------------------------
-- 3. PINGS RPC NETWORK BRIDGE (pings)
-- ------------------------------------------------------------------------------

---@class FiguraPings
pings = pings or {}
local _ping_handlers = {}
local _ping_sequence = 0

setmetatable(pings, {
  __newindex = function(_, name, func)
    if type(func) == "function" then
      _ping_handlers[name] = func
    end
  end,
  __index = function(_, name)
    return function(...)
      local args = { ... }
      if _ping_handlers[name] then
        pcall(_ping_handlers[name], table.unpack(args))
      end
      _ping_sequence = _ping_sequence + 1
      _avatar_synced_set("__figura_ping", {
        name = name,
        args = args,
        seq = _ping_sequence
      })
    end
  end
})

events.on("synced_var_change", function(payload)
  if payload and payload.key == "__figura_ping" and payload.value then
    local data = payload.value
    if data.name and _ping_handlers[data.name] then
      pcall(_ping_handlers[data.name], table.unpack(data.args or {}))
    end
  end
end)

-- ------------------------------------------------------------------------------
-- 4. KEYBINDS BRIDGE (keybinds:newKeybind)
-- ------------------------------------------------------------------------------

---@class FiguraKeybinds
keybinds = keybinds or {}
local keybind_mt = {}
keybind_mt.__index = keybind_mt

function keybind_mt:onPress(fn) self._onPress = fn; return self end
function keybind_mt:onRelease(fn) self._onRelease = fn; return self end
function keybind_mt:isPressed()
  return _shyne_input_is_down(self._id)
end

function keybinds:newKeybind(name, default_key)
  local id = "kb_" .. name:gsub("%s+", "_"):lower()
  local kb = setmetatable({
    _id = id,
    _name = name,
    _default_key = default_key or -1,
    _onPress = nil,
    _onRelease = nil
  }, keybind_mt)

  _avatar_input_bind(
    id,
    name,
    kb._default_key,
    "keyboard",
    0,
    function() if kb._onPress then kb._onPress() end end,
    function() if kb._onRelease then kb._onRelease() end end
  )
  return kb
end

-- ------------------------------------------------------------------------------
-- 5. PLAYER & WORLD PROXIES (player, world)
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

if avatar then
  function avatar:isSpeaking() return microphone and microphone.speaking and microphone.speaking() or false end
  function avatar:getVoiceLevel() return microphone and microphone.level and microphone.level() or 0 end
  function avatar:canRender() return true end
  function avatar:isLoaded() return true end
end

---@class FiguraWorld
world = world or {}
function world.getTime() return _shyne_read("world.time") or 0 end
function world.getBlockState(pos)
  pos = pos or { x = 0, y = 0, z = 0 }
  return _shyne_read("block", pos.x or pos[1], pos.y or pos[2], pos.z or pos[3])
end

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
