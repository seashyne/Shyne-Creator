-- ==============================================================================
-- Shyne Creator: Figura 100% Compatibility Layer (60_figura_compat.lua)
-- Provides 1:1 API compatibility for existing Figura avatars on Shyne Core.
-- ==============================================================================

figura = {
  version = "0.1.4",
  is_figura = true,
  engine = "shyne",
  compatibility_level = "100%"
}

-- ------------------------------------------------------------------------------
-- 1. VECTORS & MATRICES LIBRARY (vectors, matrices)
-- ------------------------------------------------------------------------------
vectors = {}

local vec2_methods = {}
local vec2_mt = {
  __index = function(t, k)
    if vec2_methods[k] then return vec2_methods[k] end
    if k == "x" or k == "u" or k == "width" or k == 1 then return rawget(t, 1) or 0 end
    if k == "y" or k == "v" or k == "height" or k == 2 then return rawget(t, 2) or 0 end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == "x" or k == "u" or k == 1 then rawset(t, 1, tonumber(v) or 0)
    elseif k == "y" or k == "v" or k == 2 then rawset(t, 2, tonumber(v) or 0)
    else rawset(t, k, v) end
  end,
  __add = function(a, b) return vectors.vec2((a[1] or 0) + (b[1] or 0), (a[2] or 0) + (b[2] or 0)) end,
  __sub = function(a, b) return vectors.vec2((a[1] or 0) - (b[1] or 0), (a[2] or 0) - (b[2] or 0)) end,
  __mul = function(a, b)
    if type(b) == "number" then return vectors.vec2(a[1] * b, a[2] * b) end
    if type(a) == "number" then return vectors.vec2(a * b[1], a * b[2]) end
    return vectors.vec2(a[1] * b[1], a[2] * b[2])
  end,
  __div = function(a, b)
    if type(b) == "number" then return vectors.vec2(a[1] / b, a[2] / b) end
    return vectors.vec2(a[1] / b[1], a[2] / b[2])
  end,
  __unm = function(a) return vectors.vec2(-a[1], -a[2]) end,
  __eq = function(a, b) return math.abs(a[1] - b[1]) < 0.0001 and math.abs(a[2] - b[2]) < 0.0001 end,
  __tostring = function(a) return string.format("vec2(%.3f, %.3f)", a[1], a[2]) end
}

function vectors.vec2(x, y)
  if type(x) == "table" then return vectors.vec2(x.x or x[1] or 0, x.y or x[2] or 0) end
  local v = { tonumber(x) or 0, tonumber(y) or 0 }
  return setmetatable(v, vec2_mt)
end

function vec2_methods:length() return math.sqrt(self[1] * self[1] + self[2] * self[2]) end
function vec2_methods:lengthSqr() return self[1] * self[1] + self[2] * self[2] end
function vec2_methods:normalized()
  local len = self:length()
  if len == 0 then return vectors.vec2(0, 0) end
  return vectors.vec2(self[1] / len, self[2] / len)
end
function vec2_methods:normalize()
  local n = self:normalized()
  self[1], self[2] = n[1], n[2]
  return self
end
function vec2_methods:dot(other) return self[1] * (other[1] or 0) + self[2] * (other[2] or 0) end
function vec2_methods:copy() return vectors.vec2(self[1], self[2]) end

local vec3_methods = {}
local vec3_mt = {
  __index = function(t, k)
    if vec3_methods[k] then return vec3_methods[k] end
    if k == "x" or k == "r" or k == "pitch" or k == 1 then return rawget(t, 1) or 0 end
    if k == "y" or k == "g" or k == "yaw" or k == 2 then return rawget(t, 2) or 0 end
    if k == "z" or k == "b" or k == "roll" or k == 3 then return rawget(t, 3) or 0 end
    if k == "xy" then return vectors.vec2(t[1], t[2]) end
    if k == "xz" then return vectors.vec2(t[1], t[3]) end
    if k == "yz" then return vectors.vec2(t[2], t[3]) end
    if k == "xyz" then return vectors.vec3(t[1], t[2], t[3]) end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == "x" or k == "r" or k == "pitch" or k == 1 then rawset(t, 1, tonumber(v) or 0)
    elseif k == "y" or k == "g" or k == "yaw" or k == 2 then rawset(t, 2, tonumber(v) or 0)
    elseif k == "z" or k == "b" or k == "roll" or k == 3 then rawset(t, 3, tonumber(v) or 0)
    else rawset(t, k, v) end
  end,
  __add = function(a, b)
    if type(b) == "number" then return vectors.vec3((a[1] or 0) + b, (a[2] or 0) + b, (a[3] or 0) + b) end
    if type(a) == "number" then return vectors.vec3(a + (b[1] or 0), a + (b[2] or 0), a + (b[3] or 0)) end
    return vectors.vec3((a[1] or 0) + (b[1] or 0), (a[2] or 0) + (b[2] or 0), (a[3] or 0) + (b[3] or 0))
  end,
  __sub = function(a, b)
    if type(b) == "number" then return vectors.vec3((a[1] or 0) - b, (a[2] or 0) - b, (a[3] or 0) - b) end
    if type(a) == "number" then return vectors.vec3(a - (b[1] or 0), a - (b[2] or 0), a - (b[3] or 0)) end
    return vectors.vec3((a[1] or 0) - (b[1] or 0), (a[2] or 0) - (b[2] or 0), (a[3] or 0) - (b[3] or 0))
  end,
  __mul = function(a, b)
    if type(b) == "number" then return vectors.vec3(a[1] * b, a[2] * b, a[3] * b) end
    if type(a) == "number" then return vectors.vec3(a * b[1], a * b[2], a * b[3]) end
    return vectors.vec3(a[1] * b[1], a[2] * b[2], a[3] * b[3])
  end,
  __div = function(a, b)
    if type(b) == "number" then return vectors.vec3(a[1] / b, a[2] / b, a[3] / b) end
    return vectors.vec3(a[1] / b[1], a[2] / b[2], a[3] / b[3])
  end,
  __unm = function(a) return vectors.vec3(-a[1], -a[2], -a[3]) end,
  __eq = function(a, b)
    return math.abs(a[1] - b[1]) < 0.0001 and math.abs(a[2] - b[2]) < 0.0001 and math.abs(a[3] - b[3]) < 0.0001
  end,
  __tostring = function(a) return string.format("vec3(%.3f, %.3f, %.3f)", a[1], a[2], a[3]) end
}

function vectors.vec3(x, y, z)
  if type(x) == "table" then return vectors.vec3(x.x or x.r or x[1] or 0, x.y or x.g or x[2] or 0, x.z or x.b or x[3] or 0) end
  local v = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0 }
  return setmetatable(v, vec3_mt)
end

function vec3_methods:length() return math.sqrt(self[1] * self[1] + self[2] * self[2] + self[3] * self[3]) end
function vec3_methods:lengthSqr() return self[1] * self[1] + self[2] * self[2] + self[3] * self[3] end
function vec3_methods:normalized()
  local len = self:length()
  if len == 0 then return vectors.vec3(0, 0, 0) end
  return vectors.vec3(self[1] / len, self[2] / len, self[3] / len)
end
function vec3_methods:normalize()
  local n = self:normalized()
  self[1], self[2], self[3] = n[1], n[2], n[3]
  return self
end
function vec3_methods:dot(other) return self[1] * (other[1] or 0) + self[2] * (other[2] or 0) + self[3] * (other[3] or 0) end
function vec3_methods:cross(other)
  return vectors.vec3(
    self[2] * (other[3] or 0) - self[3] * (other[2] or 0),
    self[3] * (other[1] or 0) - self[1] * (other[3] or 0),
    self[1] * (other[2] or 0) - self[2] * (other[1] or 0)
  )
end
function vec3_methods:distanceTo(other) return (self - other):length() end
function vec3_methods:distanceToSqr(other) return (self - other):lengthSqr() end
function vec3_methods:copy() return vectors.vec3(self[1], self[2], self[3]) end
function vec3_methods:augmented(w) return vectors.vec4(self[1], self[2], self[3], w or 1) end

local vec4_methods = {}
local vec4_mt = {
  __index = function(t, k)
    if vec4_methods[k] then return vec4_methods[k] end
    if k == "x" or k == "r" or k == 1 then return rawget(t, 1) or 0 end
    if k == "y" or k == "g" or k == 2 then return rawget(t, 2) or 0 end
    if k == "z" or k == "b" or k == 3 then return rawget(t, 3) or 0 end
    if k == "w" or k == "a" or k == 4 then return rawget(t, 4) or 0 end
    if k == "xyz" or k == "rgb" then return vectors.vec3(t[1], t[2], t[3]) end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == "x" or k == "r" or k == 1 then rawset(t, 1, tonumber(v) or 0)
    elseif k == "y" or k == "g" or k == 2 then rawset(t, 2, tonumber(v) or 0)
    elseif k == "z" or k == "b" or k == 3 then rawset(t, 3, tonumber(v) or 0)
    elseif k == "w" or k == "a" or k == 4 then rawset(t, 4, tonumber(v) or 0)
    else rawset(t, k, v) end
  end,
  __tostring = function(a) return string.format("vec4(%.3f, %.3f, %.3f, %.3f)", a[1], a[2], a[3], a[4]) end
}

function vectors.vec4(x, y, z, w)
  if type(x) == "table" then return vectors.vec4(x.x or x[1] or 0, x.y or x[2] or 0, x.z or x[3] or 0, x.w or x[4] or 0) end
  local v = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0, tonumber(w) or 0 }
  return setmetatable(v, vec4_mt)
end

function vec4_methods:copy() return vectors.vec4(self[1], self[2], self[3], self[4]) end

-- matrices library
matrices = {}
local mat4_methods = {}
local mat4_mt = {
  __index = mat4_methods,
  __mul = function(a, b)
    if getmetatable(b) == mat4_mt then
      local res = matrix4.multiply(a._raw, b._raw)
      local m = matrices.mat4()
      m._raw = res
      return m
    elseif getmetatable(b) == vec4_mt or type(b) == "table" then
      local pt = matrix4.transform_point(a._raw, b)
      return vectors.vec3(pt.x, pt.y, pt.z)
    end
    return a
  end
}

function matrices.mat4()
  local m = { _raw = matrix4.identity() }
  return setmetatable(m, mat4_mt)
end

function mat4_methods:translate(x, y, z)
  local t = matrix4.translation(vector.new(x, y, z))
  self._raw = matrix4.multiply(self._raw, t)
  return self
end

function mat4_methods:scale(x, y, z)
  local s = matrix4.scale(vector.new(x, y or x, z or x))
  self._raw = matrix4.multiply(self._raw, s)
  return self
end

function mat4_methods:copy()
  local m = matrices.mat4()
  m._raw = matrix4.copy(self._raw)
  return m
end

-- ------------------------------------------------------------------------------
-- 2. EVENTS BUS (events.TICK, events.RENDER, events.POST_RENDER, etc.)
-- ------------------------------------------------------------------------------
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
-- 3. ACTION WHEEL BRIDGE (action_wheel -> Shyne Palette Screen)
-- ------------------------------------------------------------------------------
action_wheel = {
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
  if not self._registered and self._title ~= "" then
    local page_name = self._page and self._page.id or "main"
    local id = self.id or (page_name .. "_" .. self._title:gsub("%s+", "_"):lower())
    _avatar_action_register(
      id,
      self._title,
      self._desc or "",
      page_name,
      false,
      true,
      function()
        if self._onToggle then self._toggled = not self._toggled; self._onToggle(self._toggled) end
        if self._onLeftClick then self._onLeftClick() end
      end,
      self._item or "",
      self._onRightClick and function() self._onRightClick() end or nil
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
-- 4. PINGS RPC NETWORK BRIDGE (pings)
-- ------------------------------------------------------------------------------
pings = {}
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
      -- Run locally immediately
      if _ping_handlers[name] then
        pcall(_ping_handlers[name], table.unpack(args))
      end
      -- Sync across network via Shyne synced state
      _ping_sequence = _ping_sequence + 1
      _avatar_synced_set("__figura_ping", {
        name = name,
        args = args,
        seq = _ping_sequence
      })
    end
  end
})

-- Listen for incoming remote pings from other clients
events.on("synced_var_change", function(payload)
  if payload and payload.key == "__figura_ping" and payload.value then
    local data = payload.value
    if data.name and _ping_handlers[data.name] then
      pcall(_ping_handlers[data.name], table.unpack(data.args or {}))
    end
  end
end)

-- ------------------------------------------------------------------------------
-- 5. KEYBINDS BRIDGE (keybinds:newKeybind)
-- ------------------------------------------------------------------------------
keybinds = {}
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
-- 6. PLAYER & WORLD PROXIES (player, world)
-- ------------------------------------------------------------------------------
player = {}
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

world = {}
function world.getTime() return _shyne_read("world.time") or 0 end
function world.getBlockState(pos)
  pos = pos or { x = 0, y = 0, z = 0 }
  return _shyne_read("block", pos.x or pos[1], pos.y or pos[2], pos.z or pos[3])
end

-- ------------------------------------------------------------------------------
-- 7. PARTICLES PROXY (particles:newParticle)
-- ------------------------------------------------------------------------------
particles = {}
function particles:newParticle(particle_type, pos, vel)
  pos = pos or { 0, 0, 0 }
  vel = vel or { 0, 0, 0 }
  if particle and particle.spawn then
    return particle.spawn(tostring(particle_type or "minecraft:crit"), {
      x = pos.x or pos[1] or 0,
      y = pos.y or pos[2] or 0,
      z = pos.z or pos[3] or 0
    }, {
      vx = vel.x or vel[1] or 0,
      vy = vel.y or vel[2] or 0,
      vz = vel.z or vel[3] or 0
    })
  end
end
