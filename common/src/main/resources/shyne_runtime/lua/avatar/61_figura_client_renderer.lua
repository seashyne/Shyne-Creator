-- ==============================================================================
-- Shyne Creator: Figura Client & Renderer Bridges (61_figura_client_renderer.lua)
-- Provides Figura-compatible global objects: renderer, client, raycast, and textures.
-- ==============================================================================

if not vectors then
  pcall(require, "avatar.59_figura_vectors")
end

local function to_coords(x, y, z)
  if type(x) == "table" then
    return x.x or x[1] or 0, x.y or x[2] or 0, x.z or x[3] or 0
  end
  return tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0
end

local function to_vec3(x, y, z)
  local cx, cy, cz = to_coords(x, y, z)
  return vectors and vectors.vec3(cx, cy, cz) or { x = cx, y = cy, z = cz }
end

local function _renderer_set(key, ...)
  if _figura_renderer_set then return _figura_renderer_set(key, ...) end
  if _avatar_camera_set then return _avatar_camera_set(key, ...) end
end

local function _renderer_get(key)
  if _figura_renderer_get then return _figura_renderer_get(key) end
  if _avatar_camera_read then return _avatar_camera_read(key) end
end

-- ------------------------------------------------------------------------------
-- 1. GLOBAL OBJECT: renderer
-- ------------------------------------------------------------------------------

---@class FiguraRenderer
renderer = renderer or {}

function renderer:isFirstPerson()
  local val = _shyne_read("client.first_person")
  return val == nil or val == true
end

function renderer:isCamera()
  local val = _shyne_read("client.camera_is_player")
  return val == nil or val == true
end

function renderer:getShadowRadius()
  local r = _renderer_get("shadow_radius")
  if r == nil or tonumber(r) == -1 then return 0.5 end
  return tonumber(r) or 0.5
end

function renderer:setShadowRadius(radius)
  _renderer_set("shadow_radius", tonumber(radius) or -1)
  return self
end

function renderer:setCameraPivot(x, y, z)
  local cx, cy, cz = to_coords(x, y, z)
  _renderer_set("camera_pivot", cx, cy, cz)
  if _avatar_camera_set then _avatar_camera_set("offset", cx, cy, cz) end
  return self
end

function renderer:getCameraPivot()
  local pivot = _renderer_get("camera_pivot") or (_avatar_camera_read and _avatar_camera_read("offset"))
  return pivot and to_vec3(pivot) or to_vec3(0, 0, 0)
end

function renderer:setCameraPos(x, y, z)
  local cx, cy, cz = to_coords(x, y, z)
  _renderer_set("camera_pos", cx, cy, cz)
  if _avatar_camera_set then _avatar_camera_set("offset", cx, cy, cz) end
  return self
end

function renderer:getCameraPos()
  local pos = _renderer_get("camera_pos") or _shyne_read("client.camera_pos")
  return pos and to_vec3(pos) or to_vec3(0, 0, 0)
end

function renderer:setCameraRot(x, y, z)
  local cx, cy, cz = to_coords(x, y, z)
  _renderer_set("camera_rot", cx, cy, cz)
  if _avatar_camera_set then _avatar_camera_set("rotation", cx, cy, cz) end
  return self
end

function renderer:getCameraRot()
  local rot = _renderer_get("camera_rot") or _shyne_read("client.camera_rot")
  return rot and to_vec3(rot) or to_vec3(0, 0, 0)
end

function renderer:getFOV()
  local f = _renderer_get("fov") or _shyne_read("client.fov")
  return tonumber(f) or 70
end

function renderer:setFOV(fov)
  _renderer_set("fov", tonumber(fov) or 70)
  return self
end

-- Figura fluent aliases
renderer.shadowRadius = renderer.setShadowRadius
renderer.cameraPivot = renderer.setCameraPivot
renderer.cameraPos = renderer.setCameraPos
renderer.cameraRot = renderer.setCameraRot
renderer.fov = renderer.setFOV

-- ------------------------------------------------------------------------------
-- 2. GLOBAL OBJECT: client
-- ------------------------------------------------------------------------------

---@class FiguraClient
client = client or {}

function client:isFirstPerson()
  return renderer:isFirstPerson()
end

function client:getFPS()
  return _shyne_read("client.fps") or 60
end

function client:isPaused()
  return _shyne_read("client.paused") == true
end

function client:isSingleplayer()
  return _shyne_read("client.singleplayer") == true
end

function client:getMousePos()
  local x = _shyne_read("client.mouse_x") or 0
  local y = _shyne_read("client.mouse_y") or 0
  return vectors and vectors.vec2(x, y) or { x = x, y = y }
end

function client:getScaledWindowSize()
  local w = _shyne_read("client.window_w") or 854
  local h = _shyne_read("client.window_h") or 480
  return vectors and vectors.vec2(w, h) or { x = w, y = h }
end

function client:getViewer()
  return player
end

function client:getCameraPos()
  return renderer:getCameraPos()
end

function client:getCameraRot()
  return renderer:getCameraRot()
end

-- ------------------------------------------------------------------------------
-- 3. GLOBAL OBJECT: raycast
-- ------------------------------------------------------------------------------

---@class FiguraRaycast
raycast = raycast or {}

function raycast:block(from, to, shapeType, fluidHandling)
  local ppos = player and player.getPos and player:getPos() or { x = 0, y = 0, z = 0 }
  local fx, fy, fz = to_coords(from or ppos)
  local tx, ty, tz = to_coords(to or { fx, fy, fz })
  local res = _shyne_read("world.raycast_block", fx, fy, fz, tx, ty, tz, shapeType or "COLLIDER", fluidHandling or "NONE")
  if not res then return { hit = false } end
  return {
    hit = res.hit == true,
    type = res.type or "MISS",
    pos = res.position and to_vec3(res.position) or to_vec3(tx, ty, tz),
    distance = res.distance or 0,
    block = res.block or "",
    block_pos = res.block_position and to_vec3(res.block_position) or nil,
    face = res.face or "",
    normal = res.normal and to_vec3(res.normal) or to_vec3(0, 0, 0)
  }
end

function raycast:entity(from, to, radius)
  local ppos = player and player.getPos and player:getPos() or { x = 0, y = 0, z = 0 }
  local fx, fy, fz = to_coords(from or ppos)
  local tx, ty, tz = to_coords(to or { fx, fy, fz })
  local res = _shyne_read("world.raycast_entity", fx, fy, fz, tx, ty, tz, tonumber(radius) or 0.3)
  if not res then return { hit = false } end
  return {
    hit = res.hit == true,
    type = res.type or "MISS",
    pos = res.position and to_vec3(res.position) or to_vec3(tx, ty, tz),
    distance = res.distance or 0,
    entity_id = res.entity_id or "",
    uuid = res.uuid or "",
    name = res.name or "",
    normal = res.normal and to_vec3(res.normal) or to_vec3(0, 0, 0)
  }
end

function raycast:raycast(from, to)
  local b = self:block(from, to)
  local e = self:entity(from, to)
  if b.hit and e.hit then
    return b.distance <= e.distance and b or e
  end
  return b.hit and b or e
end

-- ------------------------------------------------------------------------------
-- 4. GLOBAL OBJECT: textures (Dynamic Canvas & Textures)
-- ------------------------------------------------------------------------------

---@class FiguraTextures
textures = textures or {}
local _active_textures = {}

local texture_mt = {}
texture_mt.__index = texture_mt

function texture_mt:getWidth() return self._w end
function texture_mt:getHeight() return self._h end
function texture_mt:getDimensions() return vectors and vectors.vec2(self._w, self._h) or { x = self._w, y = self._h } end

function texture_mt:setPixel(x, y, r, g, b, a)
  x = math.floor(x or 0); y = math.floor(y or 0)
  if x < 0 or x >= self._w or y < 0 or y >= self._h then return self end
  local idx = (y * self._w + x) + 1
  if type(r) == "table" then
    a = r[4] or r.a or 1; b = r[3] or r.b or 1; g = r[2] or r.g or 1; r = r[1] or r.r or 1
  end
  self._data[idx] = { r = r or 1, g = g or 1, b = b or 1, a = a or 1 }
  self._dirty = true
  return self
end

function texture_mt:getPixel(x, y)
  x = math.floor(x or 0); y = math.floor(y or 0)
  if x < 0 or x >= self._w or y < 0 or y >= self._h then return { 0, 0, 0, 0 } end
  local p = self._data[(y * self._w + x) + 1] or { r = 0, g = 0, b = 0, a = 0 }
  return { p.r, p.g, p.b, p.a }
end

function texture_mt:fill(r, g, b, a)
  for i = 1, self._w * self._h do
    self._data[i] = { r = r or 0, g = g or 0, b = b or 0, a = a or 1 }
  end
  self._dirty = true
  return self
end

function texture_mt:apply()
  self._dirty = false
  return self
end

function textures:newTexture(name, width, height)
  local w = math.max(1, math.min(1024, math.floor(tonumber(width) or 64)))
  local h = math.max(1, math.min(1024, math.floor(tonumber(height) or 64)))
  local tex = setmetatable({
    _name = tostring(name or "custom_tex"),
    _w = w,
    _h = h,
    _data = {},
    _dirty = true
  }, texture_mt)
  tex:fill(0, 0, 0, 0)
  _active_textures[tex._name] = tex
  return tex
end

function textures:getTexture(name)
  return _active_textures[tostring(name)]
end
