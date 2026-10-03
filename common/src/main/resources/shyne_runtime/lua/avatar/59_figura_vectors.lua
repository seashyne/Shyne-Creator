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

figuraMetatables = figuraMetatables or {
  Vector2 = vec2_mt,
  Vector3 = vec3_mt,
  Vector4 = vec4_mt,
  Matrix4 = mat4_mt
}
