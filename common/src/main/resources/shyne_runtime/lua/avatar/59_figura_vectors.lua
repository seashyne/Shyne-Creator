-- ==============================================================================
-- Shyne Creator: Figura Math & Algebra Compatibility Layer (59_figura_vectors.lua)
-- Provides vectors (vec2, vec3, vec4) and matrices (mat2, mat3, mat4) 100% compatible with
-- existing Figura avatar scripts and mathematics libraries (such as SquAPI).
-- ==============================================================================

---@class FiguraVectors
vectors = vectors or {}

-- ------------------------------------------------------------------------------
-- HELPER: DYNAMIC VECTOR SWIZZLING
-- ------------------------------------------------------------------------------
local function swizzle_vec(t, k, max_dim)
  if type(k) ~= "string" or #k < 2 or #k > 4 then return nil end
  local map = {
    x = 1, y = 2, z = 3, w = 4,
    r = 1, g = 2, b = 3, a = 4,
    u = 1, v = 2,
    pitch = 1, yaw = 2, roll = 3
  }
  local res = {}
  for i = 1, #k do
    local ch = k:sub(i, i)
    local idx = map[ch]
    if not idx or idx > max_dim then return nil end
    res[i] = t[idx] or 0
  end
  if #res == 2 then return vectors.vec2(res[1], res[2])
  elseif #res == 3 then return vectors.vec3(res[1], res[2], res[3])
  elseif #res == 4 then return vectors.vec4(res[1], res[2], res[3], res[4])
  end
  return nil
end

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
    return swizzle_vec(t, k, 2)
  end,
  __newindex = function(t, k, v)
    if k == "x" or k == "u" or k == 1 then rawset(t, 1, tonumber(v) or 0)
    elseif k == "y" or k == "v" or k == 2 then rawset(t, 2, tonumber(v) or 0)
    else rawset(t, k, v) end
  end,
  __add = function(a, b)
    if type(b) == "number" then return vectors.vec2((a[1] or 0) + b, (a[2] or 0) + b) end
    if type(a) == "number" then return vectors.vec2(a + (b[1] or 0), a + (b[2] or 0)) end
    return vectors.vec2((a[1] or 0) + (b[1] or 0), (a[2] or 0) + (b[2] or 0))
  end,
  __sub = function(a, b)
    if type(b) == "number" then return vectors.vec2((a[1] or 0) - b, (a[2] or 0) - b) end
    if type(a) == "number" then return vectors.vec2(a - (b[1] or 0), a - (b[2] or 0)) end
    return vectors.vec2((a[1] or 0) - (b[1] or 0), (a[2] or 0) - (b[2] or 0))
  end,
  __mul = function(a, b)
    if type(b) == "number" then return vectors.vec2((a[1] or 0) * b, (a[2] or 0) * b) end
    if type(a) == "number" then return vectors.vec2(a * (b[1] or 0), a * (b[2] or 0)) end
    return vectors.vec2((a[1] or 0) * (b[1] or 0), (a[2] or 0) * (b[2] or 0))
  end,
  __div = function(a, b)
    if type(b) == "number" then return vectors.vec2((a[1] or 0) / b, (a[2] or 0) / b) end
    return vectors.vec2((a[1] or 0) / (b[1] or 1), (a[2] or 0) / (b[2] or 1))
  end,
  __unm = function(a) return vectors.vec2(-(a[1] or 0), -(a[2] or 0)) end,
  __eq = function(a, b) return math.abs((a[1] or 0) - (b[1] or 0)) < 0.0001 and math.abs((a[2] or 0) - (b[2] or 0)) < 0.0001 end,
  __tostring = function(a) return string.format("vec2(%.3f, %.3f)", a[1] or 0, a[2] or 0) end
}

--- Creates a 2D vector.
---@param x number|table
---@param y number|nil
---@return Vec2
function vectors.vec2(x, y)
  if type(x) == "table" then return vectors.vec2(x.x or x.u or x[1] or 0, x.y or x.v or x[2] or 0) end
  local v = { tonumber(x) or 0, tonumber(y) or 0 }
  return setmetatable(v, vec2_mt)
end

function vec2_methods:length() return math.sqrt(self[1] * self[1] + self[2] * self[2]) end
function vec2_methods:lengthSqr() return self[1] * self[1] + self[2] * self[2] end
function vec2_methods:lengthSquared() return self:lengthSqr() end
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
function vec2_methods:dot(other) return self[1] * (other[1] or other.x or 0) + self[2] * (other[2] or other.y or 0) end
function vec2_methods:distanceTo(other) return (self - other):length() end
function vec2_methods:distanceToSqr(other) return (self - other):lengthSqr() end
function vec2_methods:copy() return vectors.vec2(self[1], self[2]) end
function vec2_methods:augmented(z) return vectors.vec3(self[1], self[2], z or 0) end
function vec2_methods:add(b) return self + b end
function vec2_methods:sub(b) return self - b end
function vec2_methods:mul(b) return self * b end
function vec2_methods:div(b) return self / b end
function vec2_methods:floor() return vectors.vec2(math.floor(self[1]), math.floor(self[2])) end
function vec2_methods:ceil() return vectors.vec2(math.ceil(self[1]), math.ceil(self[2])) end
function vec2_methods:abs() return vectors.vec2(math.abs(self[1]), math.abs(self[2])) end
function vec2_methods:lerp(target, t)
  local tx = target[1] or target.x or 0
  local ty = target[2] or target.y or 0
  return vectors.vec2(self[1] + (tx - self[1]) * t, self[2] + (ty - self[2]) * t)
end
function vec2_methods:clamped(min, max)
  local min_v = type(min) == "table" and min or { min, min }
  local max_v = type(max) == "table" and max or { max, max }
  return vectors.vec2(
    math.max(min_v[1] or min_v.x or -math.huge, math.min(max_v[1] or max_v.x or math.huge, self[1])),
    math.max(min_v[2] or min_v.y or -math.huge, math.min(max_v[2] or max_v.y or math.huge, self[2]))
  )
end
function vec2_methods:clampLength(min, max)
  local len = self:length()
  if len == 0 then return self:copy() end
  local target_len = math.max(min or 0, math.min(max or len, len))
  return self:normalized() * target_len
end
function vec2_methods:applyFunc(fn) return vectors.vec2(fn(self[1]), fn(self[2])) end
function vec2_methods:unpack() return self[1], self[2] end
function vec2_methods:toTable() return { self[1], self[2] } end
function vec2_methods:transform(mat)
  if mat and mat._raw and #mat._raw == 4 then
    local m = mat._raw
    return vectors.vec2(m[1] * self[1] + m[3] * self[2], m[2] * self[1] + m[4] * self[2])
  end
  return self:copy()
end

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
    return swizzle_vec(t, k, 3)
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
    if type(b) == "number" then return vectors.vec3((a[1] or 0) * b, (a[2] or 0) * b, (a[3] or 0) * b) end
    if type(a) == "number" then return vectors.vec3(a * (b[1] or 0), a * (b[2] or 0), a * (b[3] or 0)) end
    return vectors.vec3((a[1] or 0) * (b[1] or 0), (a[2] or 0) * (b[2] or 0), (a[3] or 0) * (b[3] or 0))
  end,
  __div = function(a, b)
    if type(b) == "number" then return vectors.vec3((a[1] or 0) / b, (a[2] or 0) / b, (a[3] or 0) / b) end
    return vectors.vec3((a[1] or 0) / (b[1] or 1), (a[2] or 0) / (b[2] or 1), (a[3] or 0) / (b[3] or 1))
  end,
  __unm = function(a) return vectors.vec3(-(a[1] or 0), -(a[2] or 0), -(a[3] or 0)) end,
  __eq = function(a, b)
    return math.abs((a[1] or 0) - (b[1] or 0)) < 0.0001
      and math.abs((a[2] or 0) - (b[2] or 0)) < 0.0001
      and math.abs((a[3] or 0) - (b[3] or 0)) < 0.0001
  end,
  __tostring = function(a) return string.format("vec3(%.3f, %.3f, %.3f)", a[1] or 0, a[2] or 0, a[3] or 0) end
}

--- Creates a 3D vector.
---@param x number|table
---@param y number|nil
---@param z number|nil
---@return Vec3
function vectors.vec3(x, y, z)
  if type(x) == "table" then
    return vectors.vec3(x.x or x.r or x.pitch or x[1] or 0, x.y or x.g or x.yaw or x[2] or 0, x.z or x.b or x.roll or x[3] or 0)
  end
  local v = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0 }
  return setmetatable(v, vec3_mt)
end

function vec3_methods:length() return math.sqrt(self[1] * self[1] + self[2] * self[2] + self[3] * self[3]) end
function vec3_methods:lengthSqr() return self[1] * self[1] + self[2] * self[2] + self[3] * self[3] end
function vec3_methods:lengthSquared() return self:lengthSqr() end
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
function vec3_methods:dot(other)
  return self[1] * (other[1] or other.x or 0) + self[2] * (other[2] or other.y or 0) + self[3] * (other[3] or other.z or 0)
end
function vec3_methods:cross(other)
  local ox = other[1] or other.x or 0
  local oy = other[2] or other.y or 0
  local oz = other[3] or other.z or 0
  return vectors.vec3(
    self[2] * oz - self[3] * oy,
    self[3] * ox - self[1] * oz,
    self[1] * oy - self[2] * ox
  )
end
function vec3_methods:distanceTo(other) return (self - other):length() end
function vec3_methods:distanceToSqr(other) return (self - other):lengthSqr() end
function vec3_methods:copy() return vectors.vec3(self[1], self[2], self[3]) end
function vec3_methods:augmented(w) return vectors.vec4(self[1], self[2], self[3], w or 1) end
function vec3_methods:add(b) return self + b end
function vec3_methods:sub(b) return self - b end
function vec3_methods:mul(b) return self * b end
function vec3_methods:div(b) return self / b end
function vec3_methods:offset(x, y, z)
  if type(x) == "table" then return self + vectors.vec3(x) end
  return vectors.vec3(self[1] + (x or 0), self[2] + (y or 0), self[3] + (z or 0))
end
function vec3_methods:floor() return vectors.vec3(math.floor(self[1]), math.floor(self[2]), math.floor(self[3])) end
function vec3_methods:ceil() return vectors.vec3(math.ceil(self[1]), math.ceil(self[2]), math.ceil(self[3])) end
function vec3_methods:abs() return vectors.vec3(math.abs(self[1]), math.abs(self[2]), math.abs(self[3])) end
function vec3_methods:lerp(target, t)
  local tx = target[1] or target.x or 0
  local ty = target[2] or target.y or 0
  local tz = target[3] or target.z or 0
  return vectors.vec3(
    self[1] + (tx - self[1]) * t,
    self[2] + (ty - self[2]) * t,
    self[3] + (tz - self[3]) * t
  )
end
function vec3_methods:clampLength(min, max)
  local len = self:length()
  if len == 0 then return self:copy() end
  local target_len = math.max(min or 0, math.min(max or len, len))
  return self:normalized() * target_len
end
function vec3_methods:applyFunc(fn) return vectors.vec3(fn(self[1]), fn(self[2]), fn(self[3])) end
function vec3_methods:unpack() return self[1], self[2], self[3] end
function vec3_methods:toTable() return { self[1], self[2], self[3] } end
function vec3_methods:transform(mat)
  if mat and mat._raw then
    if #mat._raw == 16 then
      local pt = matrix4.transform_point(mat._raw, self)
      return vectors.vec3(pt.x, pt.y, pt.z)
    elseif #mat._raw == 9 then
      local m = mat._raw
      return vectors.vec3(
        m[1] * self[1] + m[4] * self[2] + m[7] * self[3],
        m[2] * self[1] + m[5] * self[2] + m[8] * self[3],
        m[3] * self[1] + m[6] * self[2] + m[9] * self[3]
      )
    end
  end
  return self:copy()
end

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
    return swizzle_vec(t, k, 4)
  end,
  __newindex = function(t, k, v)
    if k == "x" or k == "r" or k == 1 then rawset(t, 1, tonumber(v) or 0)
    elseif k == "y" or k == "g" or k == 2 then rawset(t, 2, tonumber(v) or 0)
    elseif k == "z" or k == "b" or k == 3 then rawset(t, 3, tonumber(v) or 0)
    elseif k == "w" or k == "a" or k == 4 then rawset(t, 4, tonumber(v) or 0)
    else rawset(t, k, v) end
  end,
  __add = function(a, b)
    if type(b) == "number" then return vectors.vec4((a[1] or 0) + b, (a[2] or 0) + b, (a[3] or 0) + b, (a[4] or 0) + b) end
    if type(a) == "number" then return vectors.vec4(a + (b[1] or 0), a + (b[2] or 0), a + (b[3] or 0), a + (b[4] or 0)) end
    return vectors.vec4((a[1] or 0) + (b[1] or 0), (a[2] or 0) + (b[2] or 0), (a[3] or 0) + (b[3] or 0), (a[4] or 0) + (b[4] or 0))
  end,
  __sub = function(a, b)
    if type(b) == "number" then return vectors.vec4((a[1] or 0) - b, (a[2] or 0) - b, (a[3] or 0) - b, (a[4] or 0) - b) end
    if type(a) == "number" then return vectors.vec4(a - (b[1] or 0), a - (b[2] or 0), a - (b[3] or 0), a - (b[4] or 0)) end
    return vectors.vec4((a[1] or 0) - (b[1] or 0), (a[2] or 0) - (b[2] or 0), (a[3] or 0) - (b[3] or 0), (a[4] or 0) - (b[4] or 0))
  end,
  __mul = function(a, b)
    if type(b) == "number" then return vectors.vec4((a[1] or 0) * b, (a[2] or 0) * b, (a[3] or 0) * b, (a[4] or 0) * b) end
    if type(a) == "number" then return vectors.vec4(a * (b[1] or 0), a * (b[2] or 0), a * (b[3] or 0), a * (b[4] or 0)) end
    return vectors.vec4((a[1] or 0) * (b[1] or 0), (a[2] or 0) * (b[2] or 0), (a[3] or 0) * (b[3] or 0), (a[4] or 0) * (b[4] or 0))
  end,
  __div = function(a, b)
    if type(b) == "number" then return vectors.vec4((a[1] or 0) / b, (a[2] or 0) / b, (a[3] or 0) / b, (a[4] or 0) / b) end
    return vectors.vec4((a[1] or 0) / (b[1] or 1), (a[2] or 0) / (b[2] or 1), (a[3] or 0) / (b[3] or 1), (a[4] or 0) / (b[4] or 1))
  end,
  __unm = function(a) return vectors.vec4(-(a[1] or 0), -(a[2] or 0), -(a[3] or 0), -(a[4] or 0)) end,
  __eq = function(a, b)
    return math.abs((a[1] or 0) - (b[1] or 0)) < 0.0001
      and math.abs((a[2] or 0) - (b[2] or 0)) < 0.0001
      and math.abs((a[3] or 0) - (b[3] or 0)) < 0.0001
      and math.abs((a[4] or 0) - (b[4] or 0)) < 0.0001
  end,
  __tostring = function(a) return string.format("vec4(%.3f, %.3f, %.3f, %.3f)", a[1] or 0, a[2] or 0, a[3] or 0, a[4] or 0) end
}

--- Creates a 4D vector.
---@param x number|table
---@param y number|nil
---@param z number|nil
---@param w number|nil
---@return Vec4
function vectors.vec4(x, y, z, w)
  if type(x) == "table" then
    return vectors.vec4(x.x or x.r or x[1] or 0, x.y or x.g or x[2] or 0, x.z or x.b or x[3] or 0, x.w or x.a or x[4] or 0)
  end
  local v = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0, tonumber(w) or 0 }
  return setmetatable(v, vec4_mt)
end

function vec4_methods:length() return math.sqrt(self[1] * self[1] + self[2] * self[2] + self[3] * self[3] + self[4] * self[4]) end
function vec4_methods:lengthSqr() return self[1] * self[1] + self[2] * self[2] + self[3] * self[3] + self[4] * self[4] end
function vec4_methods:lengthSquared() return self:lengthSqr() end
function vec4_methods:normalized()
  local len = self:length()
  if len == 0 then return vectors.vec4(0, 0, 0, 0) end
  return vectors.vec4(self[1] / len, self[2] / len, self[3] / len, self[4] / len)
end
function vec4_methods:normalize()
  local n = self:normalized()
  self[1], self[2], self[3], self[4] = n[1], n[2], n[3], n[4]
  return self
end
function vec4_methods:dot(other)
  return self[1] * (other[1] or other.x or 0) + self[2] * (other[2] or other.y or 0)
    + self[3] * (other[3] or other.z or 0) + self[4] * (other[4] or other.w or 0)
end
function vec4_methods:copy() return vectors.vec4(self[1], self[2], self[3], self[4]) end
function vec4_methods:add(b) return self + b end
function vec4_methods:sub(b) return self - b end
function vec4_methods:mul(b) return self * b end
function vec4_methods:div(b) return self / b end
function vec4_methods:floor() return vectors.vec4(math.floor(self[1]), math.floor(self[2]), math.floor(self[3]), math.floor(self[4])) end
function vec4_methods:ceil() return vectors.vec4(math.ceil(self[1]), math.ceil(self[2]), math.ceil(self[3]), math.ceil(self[4])) end
function vec4_methods:abs() return vectors.vec4(math.abs(self[1]), math.abs(self[2]), math.abs(self[3]), math.abs(self[4])) end
function vec4_methods:lerp(target, t)
  local tx = target[1] or target.x or 0
  local ty = target[2] or target.y or 0
  local tz = target[3] or target.z or 0
  local tw = target[4] or target.w or 0
  return vectors.vec4(
    self[1] + (tx - self[1]) * t,
    self[2] + (ty - self[2]) * t,
    self[3] + (tz - self[3]) * t,
    self[4] + (tw - self[4]) * t
  )
end
function vec4_methods:applyFunc(fn) return vectors.vec4(fn(self[1]), fn(self[2]), fn(self[3]), fn(self[4])) end
function vec4_methods:unpack() return self[1], self[2], self[3], self[4] end
function vec4_methods:toTable() return { self[1], self[2], self[3], self[4] } end
function vec4_methods:transform(mat)
  if mat and mat._raw and #mat._raw == 16 then
    local m = mat._raw
    local x, y, z, w = self[1], self[2], self[3], self[4]
    return vectors.vec4(
      m[1] * x + m[5] * y + m[9] * z + m[13] * w,
      m[2] * x + m[6] * y + m[10] * z + m[14] * w,
      m[3] * x + m[7] * y + m[11] * z + m[15] * w,
      m[4] * x + m[8] * y + m[12] * z + m[16] * w
    )
  end
  return self:copy()
end

-- ------------------------------------------------------------------------------
-- 4. MATRICES ALGEBRA (matrices: mat2, mat3, mat4)
-- ------------------------------------------------------------------------------

---@class FiguraMatrices
matrices = matrices or {}

-- ------------------------------------------------------------------------------
-- 4.1. 2x2 MATRICES (matrices.mat2)
-- ------------------------------------------------------------------------------
local mat2_methods = {}
local mat2_mt = {
  __index = function(t, k)
    if mat2_methods[k] then return mat2_methods[k] end
    if k == 1 or k == "c1" or k == "v1" then return vectors.vec2(t._raw[1], t._raw[2]) end
    if k == 2 or k == "c2" or k == "v2" then return vectors.vec2(t._raw[3], t._raw[4]) end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == 1 or k == "c1" or k == "v1" then
      local vec = vectors.vec2(v)
      t._raw[1], t._raw[2] = vec[1], vec[2]
    elseif k == 2 or k == "c2" or k == "v2" then
      local vec = vectors.vec2(v)
      t._raw[3], t._raw[4] = vec[1], vec[2]
    else rawset(t, k, v) end
  end,
  __mul = function(a, b)
    if type(a) == "table" and a._raw and type(b) == "table" and b._raw and #a._raw == 4 and #b._raw == 4 then
      local m1, m2 = a._raw, b._raw
      local res = {
        m1[1] * m2[1] + m1[3] * m2[2],
        m1[2] * m2[1] + m1[4] * m2[2],
        m1[1] * m2[3] + m1[3] * m2[4],
        m1[2] * m2[3] + m1[4] * m2[4]
      }
      local m = matrices.mat2()
      m._raw = res
      return m
    elseif type(a) == "table" and a._raw and #a._raw == 4 then
      local m = a._raw
      local v = vectors.vec2(b)
      return vectors.vec2(m[1] * v[1] + m[3] * v[2], m[2] * v[1] + m[4] * v[2])
    end
    return a
  end
}

function matrices.mat2(c1, c2)
  local m = { _raw = { 1, 0, 0, 1 } }
  if c1 ~= nil then
    local v1 = vectors.vec2(c1)
    local v2 = vectors.vec2(c2 or { 0, 1 })
    m._raw = { v1[1], v1[2], v2[1], v2[2] }
  end
  return setmetatable(m, mat2_mt)
end

function mat2_methods:determinant()
  return self._raw[1] * self._raw[4] - self._raw[2] * self._raw[3]
end

function mat2_methods:copy()
  local m = matrices.mat2()
  m._raw = { self._raw[1], self._raw[2], self._raw[3], self._raw[4] }
  return m
end

function mat2_methods:transpose()
  local r = self._raw
  self._raw = { r[1], r[3], r[2], r[4] }
  return self
end
function mat2_methods:transposed() return self:copy():transpose() end

function mat2_methods:invert()
  local a, b = self._raw[1], self._raw[3]
  local c, d = self._raw[2], self._raw[4]
  local det = a * d - b * c
  if math.abs(det) < 1e-12 then return self end
  local invDet = 1.0 / det
  self._raw = { d * invDet, -c * invDet, -b * invDet, a * invDet }
  return self
end
function mat2_methods:inverted() return self:copy():invert() end

function mat2_methods:scale(x, y)
  local sx = type(x) == "table" and (x[1] or x.x) or tonumber(x) or 1
  local sy = type(x) == "table" and (x[2] or x.y) or tonumber(y) or sx
  local s = { _raw = { sx, 0, 0, sy } }
  setmetatable(s, mat2_mt)
  local res = self * s
  self._raw = res._raw
  return self
end

function mat2_methods:rotate(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local r = { _raw = { c, s, -s, c } }
  setmetatable(r, mat2_mt)
  local res = self * r
  self._raw = res._raw
  return self
end

function mat2_methods:apply(v) return self * v end
function mat2_methods:transform(v) return self * v end

-- ------------------------------------------------------------------------------
-- 4.2. 3x3 MATRICES (matrices.mat3)
-- ------------------------------------------------------------------------------
local mat3_methods = {}
local mat3_mt = {
  __index = function(t, k)
    if mat3_methods[k] then return mat3_methods[k] end
    if k == 1 or k == "c1" or k == "v1" then return vectors.vec3(t._raw[1], t._raw[2], t._raw[3]) end
    if k == 2 or k == "c2" or k == "v2" then return vectors.vec3(t._raw[4], t._raw[5], t._raw[6]) end
    if k == 3 or k == "c3" or k == "v3" then return vectors.vec3(t._raw[7], t._raw[8], t._raw[9]) end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == 1 or k == "c1" or k == "v1" then
      local vec = vectors.vec3(v)
      t._raw[1], t._raw[2], t._raw[3] = vec[1], vec[2], vec[3]
    elseif k == 2 or k == "c2" or k == "v2" then
      local vec = vectors.vec3(v)
      t._raw[4], t._raw[5], t._raw[6] = vec[1], vec[2], vec[3]
    elseif k == 3 or k == "c3" or k == "v3" then
      local vec = vectors.vec3(v)
      t._raw[7], t._raw[8], t._raw[9] = vec[1], vec[2], vec[3]
    else rawset(t, k, v) end
  end,
  __mul = function(a, b)
    if type(a) == "table" and a._raw and type(b) == "table" and b._raw and #a._raw == 9 and #b._raw == 9 then
      local m1, m2 = a._raw, b._raw
      local res = {}
      for c = 0, 2 do
        for r = 0, 2 do
          local sum = 0
          for k = 0, 2 do sum = sum + m1[k * 3 + r + 1] * m2[c * 3 + k + 1] end
          res[c * 3 + r + 1] = sum
        end
      end
      local m = matrices.mat3()
      m._raw = res
      return m
    elseif type(a) == "table" and a._raw and #a._raw == 9 then
      local m = a._raw
      local v = vectors.vec3(b)
      return vectors.vec3(
        m[1] * v[1] + m[4] * v[2] + m[7] * v[3],
        m[2] * v[1] + m[5] * v[2] + m[8] * v[3],
        m[3] * v[1] + m[6] * v[2] + m[9] * v[3]
      )
    end
    return a
  end
}

function matrices.mat3(c1, c2, c3)
  local m = { _raw = { 1, 0, 0, 0, 1, 0, 0, 0, 1 } }
  if c1 ~= nil then
    local v1 = vectors.vec3(c1)
    local v2 = vectors.vec3(c2 or { 0, 1, 0 })
    local v3 = vectors.vec3(c3 or { 0, 0, 1 })
    m._raw = { v1[1], v1[2], v1[3], v2[1], v2[2], v2[3], v3[1], v3[2], v3[3] }
  end
  return setmetatable(m, mat3_mt)
end

function mat3_methods:copy()
  local m = matrices.mat3()
  m._raw = { table.unpack(self._raw) }
  return m
end

function mat3_methods:determinant()
  local m = self._raw
  return m[1] * (m[5] * m[9] - m[6] * m[8])
    - m[4] * (m[2] * m[9] - m[3] * m[8])
    + m[7] * (m[2] * m[6] - m[3] * m[5])
end

function mat3_methods:transpose()
  local r = self._raw
  self._raw = {
    r[1], r[4], r[7],
    r[2], r[5], r[8],
    r[3], r[6], r[9]
  }
  return self
end
function mat3_methods:transposed() return self:copy():transpose() end

function mat3_methods:invert()
  local c00, c01, c02 = self._raw[1], self._raw[2], self._raw[3]
  local c10, c11, c12 = self._raw[4], self._raw[5], self._raw[6]
  local c20, c21, c22 = self._raw[7], self._raw[8], self._raw[9]

  local A = c11 * c22 - c12 * c21
  local B = -(c01 * c22 - c02 * c21)
  local C = c01 * c12 - c02 * c11

  local D = -(c10 * c22 - c12 * c20)
  local E = c00 * c22 - c02 * c20
  local F = -(c00 * c12 - c02 * c10)

  local G = c10 * c21 - c11 * c20
  local H = -(c00 * c21 - c01 * c20)
  local I = c00 * c11 - c01 * c10

  local det = c00 * A + c10 * B + c20 * C
  if math.abs(det) < 1e-12 then return self end
  local invDet = 1.0 / det
  self._raw = {
    A * invDet, B * invDet, C * invDet,
    D * invDet, E * invDet, F * invDet,
    G * invDet, H * invDet, I * invDet
  }
  return self
end
function mat3_methods:inverted() return self:copy():invert() end

function mat3_methods:scale(x, y, z)
  local sx = type(x) == "table" and (x[1] or x.x) or tonumber(x) or 1
  local sy = type(x) == "table" and (x[2] or x.y) or tonumber(y) or sx
  local sz = type(x) == "table" and (x[3] or x.z) or tonumber(z) or sx
  local s = matrices.mat3({ sx, 0, 0 }, { 0, sy, 0 }, { 0, 0, sz })
  local res = self * s
  self._raw = res._raw
  return self
end

function mat3_methods:rotateX(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = matrices.mat3({ 1, 0, 0 }, { 0, c, s }, { 0, -s, c })
  local res = self * rot
  self._raw = res._raw
  return self
end

function mat3_methods:rotateY(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = matrices.mat3({ c, 0, -s }, { 0, 1, 0 }, { s, 0, c })
  local res = self * rot
  self._raw = res._raw
  return self
end

function mat3_methods:rotateZ(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = matrices.mat3({ c, s, 0 }, { -s, c, 0 }, { 0, 0, 1 })
  local res = self * rot
  self._raw = res._raw
  return self
end

function mat3_methods:rotate(angle, axis, ay, az)
  local ax, ay_val, az_val = 0, 1, 0
  if type(axis) == "table" then
    ax = axis[1] or axis.x or 0
    ay_val = axis[2] or axis.y or 0
    az_val = axis[3] or axis.z or 0
  elseif axis ~= nil then
    ax = tonumber(axis) or 0
    ay_val = tonumber(ay) or 0
    az_val = tonumber(az) or 0
  end
  local len = math.sqrt(ax * ax + ay_val * ay_val + az_val * az_val)
  if len > 0 then ax, ay_val, az_val = ax / len, ay_val / len, az_val / len end
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local t = 1 - c
  local rot = matrices.mat3(
    { t * ax * ax + c, t * ax * ay_val + s * az_val, t * ax * az_val - s * ay_val },
    { t * ax * ay_val - s * az_val, t * ay_val * ay_val + c, t * ay_val * az_val + s * ax },
    { t * ax * az_val + s * ay_val, t * ay_val * az_val - s * ax, t * az_val * az_val + c }
  )
  local res = self * rot
  self._raw = res._raw
  return self
end

function mat3_methods:apply(v) return self * v end
function mat3_methods:transform(v) return self * v end

-- ------------------------------------------------------------------------------
-- 4.3. 4x4 AFFINE MATRICES (matrices.mat4)
-- ------------------------------------------------------------------------------
local mat4_methods = {}
local mat4_mt = {
  __index = function(t, k)
    if mat4_methods[k] then return mat4_methods[k] end
    if k == 1 or k == "c1" or k == "v1" then return vectors.vec4(t._raw[1], t._raw[2], t._raw[3], t._raw[4]) end
    if k == 2 or k == "c2" or k == "v2" then return vectors.vec4(t._raw[5], t._raw[6], t._raw[7], t._raw[8]) end
    if k == 3 or k == "c3" or k == "v3" then return vectors.vec4(t._raw[9], t._raw[10], t._raw[11], t._raw[12]) end
    if k == 4 or k == "c4" or k == "v4" then return vectors.vec4(t._raw[13], t._raw[14], t._raw[15], t._raw[16]) end
    return nil
  end,
  __newindex = function(t, k, v)
    if k == 1 or k == "c1" or k == "v1" then
      local vec = vectors.vec4(v)
      t._raw[1], t._raw[2], t._raw[3], t._raw[4] = vec[1], vec[2], vec[3], vec[4]
    elseif k == 2 or k == "c2" or k == "v2" then
      local vec = vectors.vec4(v)
      t._raw[5], t._raw[6], t._raw[7], t._raw[8] = vec[1], vec[2], vec[3], vec[4]
    elseif k == 3 or k == "c3" or k == "v3" then
      local vec = vectors.vec4(v)
      t._raw[9], t._raw[10], t._raw[11], t._raw[12] = vec[1], vec[2], vec[3], vec[4]
    elseif k == 4 or k == "c4" or k == "v4" then
      local vec = vectors.vec4(v)
      t._raw[13], t._raw[14], t._raw[15], t._raw[16] = vec[1], vec[2], vec[3], vec[4]
    else rawset(t, k, v) end
  end,
  __mul = function(a, b)
    if type(a) == "table" and a._raw and type(b) == "table" and b._raw and #a._raw == 16 and #b._raw == 16 then
      local res = matrix4.multiply(a._raw, b._raw)
      local m = matrices.mat4()
      m._raw = res
      return m
    elseif type(a) == "table" and a._raw and #a._raw == 16 then
      if getmetatable(b) == vec4_mt or (type(b) == "table" and (b[4] ~= nil or b.w ~= nil)) then
        local m = a._raw
        local v = vectors.vec4(b)
        return vectors.vec4(
          m[1] * v[1] + m[5] * v[2] + m[9] * v[3] + m[13] * v[4],
          m[2] * v[1] + m[6] * v[2] + m[10] * v[3] + m[14] * v[4],
          m[3] * v[1] + m[7] * v[2] + m[11] * v[3] + m[15] * v[4],
          m[4] * v[1] + m[8] * v[2] + m[12] * v[3] + m[16] * v[4]
        )
      else
        local pt = matrix4.transform_point(a._raw, b)
        return vectors.vec3(pt.x, pt.y, pt.z)
      end
    end
    return a
  end
}

--- Creates a 4x4 matrix wrapper.
---@param c1 table|nil Column 1 or nil for identity
---@param c2 table|nil Column 2
---@param c3 table|nil Column 3
---@param c4 table|nil Column 4
---@return table Mat4 wrapper
function matrices.mat4(c1, c2, c3, c4)
  local m = { _raw = matrix4.identity() }
  if c1 ~= nil then
    local v1 = vectors.vec4(c1)
    local v2 = vectors.vec4(c2 or { 0, 1, 0, 0 })
    local v3 = vectors.vec4(c3 or { 0, 0, 1, 0 })
    local v4 = vectors.vec4(c4 or { 0, 0, 0, 1 })
    m._raw = {
      v1[1], v1[2], v1[3], v1[4],
      v2[1], v2[2], v2[3], v2[4],
      v3[1], v3[2], v3[3], v3[4],
      v4[1], v4[2], v4[3], v4[4]
    }
  end
  return setmetatable(m, mat4_mt)
end

function mat4_methods:translate(x, y, z)
  local tx = type(x) == "table" and (x[1] or x.x) or tonumber(x) or 0
  local ty = type(x) == "table" and (x[2] or x.y) or tonumber(y) or 0
  local tz = type(x) == "table" and (x[3] or x.z) or tonumber(z) or 0
  local t = matrix4.translation(vector.new(tx, ty, tz))
  self._raw = matrix4.multiply(self._raw, t)
  return self
end

function mat4_methods:scale(x, y, z)
  local sx = type(x) == "table" and (x[1] or x.x) or tonumber(x) or 1
  local sy = type(x) == "table" and (x[2] or x.y) or tonumber(y) or sx
  local sz = type(x) == "table" and (x[3] or x.z) or tonumber(z) or sx
  local s = matrix4.scale(vector.new(sx, sy, sz))
  self._raw = matrix4.multiply(self._raw, s)
  return self
end

function mat4_methods:rotateX(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = { 1, 0, 0, 0,  0, c, s, 0,  0, -s, c, 0,  0, 0, 0, 1 }
  self._raw = matrix4.multiply(self._raw, rot)
  return self
end

function mat4_methods:rotateY(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = { c, 0, -s, 0,  0, 1, 0, 0,  s, 0, c, 0,  0, 0, 0, 1 }
  self._raw = matrix4.multiply(self._raw, rot)
  return self
end

function mat4_methods:rotateZ(angle)
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local rot = { c, s, 0, 0,  -s, c, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1 }
  self._raw = matrix4.multiply(self._raw, rot)
  return self
end

function mat4_methods:rotate(angle, axis, ay, az)
  local ax, ay_val, az_val = 0, 1, 0
  if type(axis) == "table" then
    ax = axis[1] or axis.x or 0
    ay_val = axis[2] or axis.y or 0
    az_val = axis[3] or axis.z or 0
  elseif axis ~= nil then
    ax = tonumber(axis) or 0
    ay_val = tonumber(ay) or 0
    az_val = tonumber(az) or 0
  end
  local len = math.sqrt(ax * ax + ay_val * ay_val + az_val * az_val)
  if len > 0 then ax, ay_val, az_val = ax / len, ay_val / len, az_val / len end
  local rad = math.rad(tonumber(angle) or 0)
  local c = math.cos(rad)
  local s = math.sin(rad)
  local t = 1 - c
  local rot = {
    t * ax * ax + c, t * ax * ay_val + s * az_val, t * ax * az_val - s * ay_val, 0,
    t * ax * ay_val - s * az_val, t * ay_val * ay_val + c, t * ay_val * az_val + s * ax, 0,
    t * ax * az_val + s * ay_val, t * ay_val * az_val - s * ax, t * az_val * az_val + c, 0,
    0, 0, 0, 1
  }
  self._raw = matrix4.multiply(self._raw, rot)
  return self
end

local function mat4_full_invert(m)
  local inv = {}
  inv[1] = m[6] * m[11] * m[16] - m[6] * m[12] * m[15] - m[10] * m[7] * m[16] + m[10] * m[8] * m[15] + m[14] * m[7] * m[12] - m[14] * m[8] * m[11]
  inv[5] = -m[5] * m[11] * m[16] + m[5] * m[12] * m[15] + m[9] * m[7] * m[16] - m[9] * m[8] * m[15] - m[13] * m[7] * m[12] + m[13] * m[8] * m[11]
  inv[9] = m[5] * m[10] * m[16] - m[5] * m[12] * m[14] - m[9] * m[6] * m[16] + m[9] * m[8] * m[14] + m[13] * m[6] * m[12] - m[13] * m[8] * m[10]
  inv[13] = -m[5] * m[10] * m[15] + m[5] * m[11] * m[14] + m[9] * m[6] * m[15] - m[9] * m[7] * m[14] - m[13] * m[6] * m[11] + m[13] * m[7] * m[10]

  inv[2] = -m[2] * m[11] * m[16] + m[2] * m[12] * m[15] + m[10] * m[3] * m[16] - m[10] * m[4] * m[15] - m[14] * m[3] * m[12] + m[14] * m[4] * m[11]
  inv[6] = m[1] * m[11] * m[16] - m[1] * m[12] * m[15] - m[9] * m[3] * m[16] + m[9] * m[4] * m[15] + m[13] * m[3] * m[12] - m[13] * m[4] * m[11]
  inv[10] = -m[1] * m[10] * m[16] + m[1] * m[12] * m[14] + m[9] * m[2] * m[16] - m[9] * m[4] * m[14] - m[13] * m[2] * m[12] + m[13] * m[4] * m[10]
  inv[14] = m[1] * m[10] * m[15] - m[1] * m[11] * m[14] - m[9] * m[2] * m[15] + m[9] * m[3] * m[14] + m[13] * m[2] * m[11] - m[13] * m[3] * m[10]

  inv[3] = m[2] * m[7] * m[16] - m[2] * m[8] * m[15] - m[6] * m[3] * m[16] + m[6] * m[4] * m[15] + m[14] * m[3] * m[8] - m[14] * m[4] * m[7]
  inv[7] = -m[1] * m[7] * m[16] + m[1] * m[8] * m[15] + m[5] * m[3] * m[16] - m[5] * m[4] * m[15] - m[13] * m[3] * m[8] + m[13] * m[4] * m[7]
  inv[11] = m[1] * m[6] * m[16] - m[1] * m[8] * m[14] - m[5] * m[2] * m[16] + m[5] * m[4] * m[14] + m[13] * m[2] * m[8] - m[13] * m[4] * m[6]
  inv[15] = -m[1] * m[6] * m[15] + m[1] * m[7] * m[14] + m[5] * m[2] * m[15] - m[5] * m[3] * m[14] - m[13] * m[2] * m[7] + m[13] * m[3] * m[6]

  inv[4] = -m[2] * m[7] * m[12] + m[2] * m[8] * m[11] + m[6] * m[3] * m[12] - m[6] * m[4] * m[11] - m[10] * m[3] * m[8] + m[10] * m[4] * m[7]
  inv[8] = m[1] * m[7] * m[12] - m[1] * m[8] * m[11] - m[5] * m[3] * m[12] + m[5] * m[4] * m[11] + m[9] * m[3] * m[8] - m[9] * m[4] * m[7]
  inv[12] = -m[1] * m[6] * m[12] + m[1] * m[8] * m[10] + m[5] * m[2] * m[12] - m[5] * m[4] * m[10] - m[9] * m[2] * m[8] + m[9] * m[4] * m[6]
  inv[16] = m[1] * m[6] * m[11] - m[1] * m[7] * m[10] - m[5] * m[2] * m[11] + m[5] * m[3] * m[10] + m[9] * m[2] * m[7] - m[9] * m[3] * m[6]

  local det = m[1] * inv[1] + m[2] * inv[5] + m[3] * inv[9] + m[4] * inv[13]
  if math.abs(det) < 1e-12 then return nil, 0 end
  local invDet = 1.0 / det
  for i = 1, 16 do inv[i] = inv[i] * invDet end
  return inv, det
end

function mat4_methods:determinant()
  local _, det = mat4_full_invert(self._raw)
  return det
end

function mat4_methods:transpose()
  local raw = self._raw
  local res = {}
  for c = 0, 3 do
    for r = 0, 3 do
      res[c * 4 + r + 1] = raw[r * 4 + c + 1]
    end
  end
  self._raw = res
  return self
end
function mat4_methods:transposed() return self:copy():transpose() end

function mat4_methods:invert()
  local inv = mat4_full_invert(self._raw)
  if inv then self._raw = inv end
  return self
end
function mat4_methods:inverted() return self:copy():invert() end

function mat4_methods:copy()
  local m = matrices.mat4()
  m._raw = matrix4.copy(self._raw)
  return m
end

function mat4_methods:apply(v) return self * v end
function mat4_methods:transform(v) return self * v end

-- ------------------------------------------------------------------------------
-- 4.4. STATIC MATRIX FACTORIES
-- ------------------------------------------------------------------------------
function matrices.rotation4(angle, axis, ay, az)
  return matrices.mat4():rotate(angle, axis, ay, az)
end
function matrices.xRotation4(angle) return matrices.mat4():rotateX(angle) end
function matrices.yRotation4(angle) return matrices.mat4():rotateY(angle) end
function matrices.zRotation4(angle) return matrices.mat4():rotateZ(angle) end
function matrices.translation4(x, y, z) return matrices.mat4():translate(x, y, z) end
function matrices.scale4(x, y, z) return matrices.mat4():scale(x, y, z) end

function matrices.rotation3(angle, axis, ay, az)
  return matrices.mat3():rotate(angle, axis, ay, az)
end
function matrices.xRotation3(angle) return matrices.mat3():rotateX(angle) end
function matrices.yRotation3(angle) return matrices.mat3():rotateY(angle) end
function matrices.zRotation3(angle) return matrices.mat3():rotateZ(angle) end
function matrices.scale3(x, y, z) return matrices.mat3():scale(x, y, z) end

function matrices.rotation2(angle) return matrices.mat2():rotate(angle) end
function matrices.scale2(x, y) return matrices.mat2():scale(x, y) end

-- ------------------------------------------------------------------------------
-- 5. UNIVERSAL VECTOR CONSTRUCTOR & COLOR UTILITIES
-- ------------------------------------------------------------------------------

--- Universal Figura vector constructor.
function vec(x, y, z, w)
  if w ~= nil then
    return vectors.vec4(x, y, z, w)
  elseif z ~= nil then
    return vectors.vec3(x, y, z)
  elseif y ~= nil then
    return vectors.vec2(x, y)
  elseif type(x) == "table" then
    if x[4] ~= nil or x.w ~= nil or x.a ~= nil then
      return vectors.vec4(x)
    elseif x[3] ~= nil or x.z ~= nil or x.b ~= nil or x.roll ~= nil then
      return vectors.vec3(x)
    elseif x[2] ~= nil or x.y ~= nil or x.g ~= nil or x.v ~= nil or x.yaw ~= nil then
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

-- ------------------------------------------------------------------------------
-- 6. EXTENDED MATH EXTENSIONS
-- ------------------------------------------------------------------------------
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
  Matrix2 = mat2_mt,
  Matrix3 = mat3_mt,
  Matrix4 = mat4_mt
}
