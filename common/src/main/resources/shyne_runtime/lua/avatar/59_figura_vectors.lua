-- ==============================================================================
-- Shyne Creator: Figura Math & Algebra Compatibility Layer (59_figura_vectors.lua)
-- Provides vectors (vec2, vec3, vec4) and matrices (mat4) 100% compatible with
-- existing Figura avatar scripts and mathematics libraries.
-- ==============================================================================

---@class FiguraVectors
vectors = vectors or {}

-- ------------------------------------------------------------------------------
-- 1. 2D VECTOR (vectors.vec2)
-- ------------------------------------------------------------------------------

---@class Vec2
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

--- Creates a 2D vector.
---@param x number|table
---@param y number|nil
---@return Vec2
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

-- ------------------------------------------------------------------------------
-- 2. 3D VECTOR (vectors.vec3)
-- ------------------------------------------------------------------------------

---@class Vec3
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

--- Creates a 3D vector.
---@param x number|table
---@param y number|nil
---@param z number|nil
---@return Vec3
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

-- ------------------------------------------------------------------------------
-- 3. 4D VECTOR (vectors.vec4)
-- ------------------------------------------------------------------------------

---@class Vec4
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

--- Creates a 4D vector.
---@param x number|table
---@param y number|nil
---@param z number|nil
---@param w number|nil
---@return Vec4
function vectors.vec4(x, y, z, w)
  if type(x) == "table" then return vectors.vec4(x.x or x[1] or 0, x.y or x[2] or 0, x.z or x[3] or 0, x.w or x[4] or 0) end
  local v = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0, tonumber(w) or 0 }
  return setmetatable(v, vec4_mt)
end

function vec4_methods:copy() return vectors.vec4(self[1], self[2], self[3], self[4]) end

-- ------------------------------------------------------------------------------
-- 4. 4x4 AFFINE MATRICES (matrices.mat4)
-- ------------------------------------------------------------------------------

---@class FiguraMatrices
matrices = matrices or {}
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

--- Creates a 4x4 identity matrix wrapper.
---@return table Mat4 wrapper
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

--- Universal Figura vector constructor.
function vec(x, y, z, w)
  if w ~= nil then
    return vectors.vec4(x, y, z, w)
  elseif z ~= nil then
    return vectors.vec3(x, y, z)
  elseif y ~= nil then
    return vectors.vec2(x, y)
  elseif type(x) == "table" then
    if x[4] ~= nil or x.w ~= nil then
      return vectors.vec4(x)
    elseif x[3] ~= nil or x.z ~= nil or x.b ~= nil then
      return vectors.vec3(x)
    elseif x[2] ~= nil or x.y ~= nil or x.g ~= nil or x.v ~= nil then
      return vectors.vec2(x)
    else
      return vectors.vec3(x)
    end
  elseif type(x) == "number" then
    return vectors.vec3(x, x, x)
  else
    return vectors.vec3(0, 0, 0)
  end
end
vectors.vec = vec
vectors.of = vec

function vectors.hexToRGB(hex)
  if not hex then return vectors.vec3(0, 0, 0) end
  if type(hex) == "table" then
    local r = hex.r or hex.x or hex[1] or 0
    local g = hex.g or hex.y or hex[2] or 0
    local b = hex.b or hex.z or hex[3] or 0
    if r > 1 or g > 1 or b > 1 then
      r, g, b = r / 255, g / 255, b / 255
    end
    return vectors.vec3(r, g, b)
  end
  if type(hex) == "number" then
    local num = math.floor(hex)
    local r = math.floor(num / 65536) % 256
    local g = math.floor(num / 256) % 256
    local b = num % 256
    return vectors.vec3(r / 255, g / 255, b / 255)
  end
  local str = tostring(hex):gsub("#", ""):gsub("%s+", "")
  if #str == 3 then
    local r = tonumber(str:sub(1, 1):rep(2), 16) or 0
    local g = tonumber(str:sub(2, 2):rep(2), 16) or 0
    local b = tonumber(str:sub(3, 3):rep(2), 16) or 0
    return vectors.vec3(r / 255, g / 255, b / 255)
  elseif #str >= 6 then
    local r = tonumber(str:sub(1, 2), 16) or 0
    local g = tonumber(str:sub(3, 4), 16) or 0
    local b = tonumber(str:sub(5, 6), 16) or 0
    return vectors.vec3(r / 255, g / 255, b / 255)
  end
  return vectors.vec3(0, 0, 0)
end

function vectors.rgbToHex(rgb, g, b)
  local red, green, blue = 0, 0, 0
  if type(rgb) == "table" then
    red = rgb.r or rgb.x or rgb[1] or 0
    green = rgb.g or rgb.y or rgb[2] or 0
    blue = rgb.b or rgb.z or rgb[3] or 0
  elseif type(rgb) == "number" then
    red = rgb
    green = g or 0
    blue = b or 0
  end
  if red <= 1 and green <= 1 and blue <= 1 and (red > 0 or green > 0 or blue > 0) then
    red = red * 255
    green = green * 255
    blue = blue * 255
  end
  local ir = math.max(0, math.min(255, math.floor(red + 0.5)))
  local ig = math.max(0, math.min(255, math.floor(green + 0.5)))
  local ib = math.max(0, math.min(255, math.floor(blue + 0.5)))
  return string.format("%02x%02x%02x", ir, ig, ib)
end

math.lerp = math.lerp or function(a, b, t)
  return a + (b - a) * t
end

math.clamp = math.clamp or function(val, min, max)
  if val < min then return min end
  if val > max then return max end
  return val
end

math.sign = math.sign or function(x)
  if x > 0 then return 1 end
  if x < 0 then return -1 end
  return 0
end

math.round = math.round or function(x)
  return math.floor(x + 0.5)
end

math.map = math.map or function(val, in_min, in_max, out_min, out_max)
  return out_min + (val - in_min) * (out_max - out_min) / (in_max - in_min)
end

figuraMetatables = figuraMetatables or {
  Vector2 = vec2_mt,
  Vector3 = vec3_mt,
  Vector4 = vec4_mt,
  Matrix4 = mat4_mt
}

