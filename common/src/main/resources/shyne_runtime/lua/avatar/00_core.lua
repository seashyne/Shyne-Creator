-- Shyne Lua API Standard 2.0 (Avatar runtime)
-- New scripts use normal globals: minecraft, model, avatar, state, events, ui, vector.

local function bool(value) return value and true or false end
-- Forward all arguments: world probes use position/direction after the key.
local function read(key, ...) return _shyne_read(key, ...) end
local vector_methods = {}
local vector_mt = {
  __index = vector_methods,
  __add = function(a, b) return vector.add(a, b) end,
  __sub = function(a, b) return vector.sub(a, b) end,
  __mul = function(a, b) return vector.mul(a, b) end,
  __div = function(a, b) return vector.div(a, b) end,
  __unm = function(a) return vector.mul(a, -1) end
}
local function vec3(x, y, z)
  local value = { x = x or 0, y = y or 0, z = z or 0 }
  value[1], value[2], value[3] = value.x, value.y, value.z
  return setmetatable(value, vector_mt)
end
vector = {}
function vector.new(x, y, z)
  if type(x) == "table" then return vec3(x.x or x[1], x.y or x[2], x.z or x[3]) end
  return vec3(x, y, z)
end
function vector.zero() return vec3(0, 0, 0) end
local function vector_components(value)
  if type(value) == "number" then return value, value, value end
  value = value or {}
  return value.x or value[1] or 0, value.y or value[2] or 0, value.z or value[3] or 0
end
function vector.add(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax + bx, ay + by, az + bz)
end
function vector.sub(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax - bx, ay - by, az - bz)
end
function vector.mul(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax * bx, ay * by, az * bz)
end
function vector.div(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  if bx == 0 or by == 0 or bz == 0 then error("vector division by zero", 2) end
  return vec3(ax / bx, ay / by, az / bz)
end
function vector.dot(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return ax * bx + ay * by + az * bz
end
function vector.cross(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
end
function vector.length(value)
  local x, y, z = vector_components(value)
  return math.sqrt(x * x + y * y + z * z)
end
function vector.normalize(value)
  local length = vector.length(value)
  if length == 0 then return vector.zero() end
  return vector.div(value, length)
end
function vector.distance(a, b) return vector.length(vector.sub(a, b)) end
function vector.lerp(a, b, amount)
  amount = math.max(0, math.min(1, tonumber(amount) or 0))
  return vector.add(a, vector.mul(vector.sub(b, a), amount))
end
function vector.clamp(value, minimum, maximum)
  local x, y, z = vector_components(value)
  local min_x, min_y, min_z = vector_components(minimum)
  local max_x, max_y, max_z = vector_components(maximum)
  return vec3(math.max(min_x, math.min(max_x, x)), math.max(min_y, math.min(max_y, y)), math.max(min_z, math.min(max_z, z)))
end
function vector_methods:add(value) return vector.add(self, value) end
function vector_methods:sub(value) return vector.sub(self, value) end
function vector_methods:mul(value) return vector.mul(self, value) end
function vector_methods:div(value) return vector.div(self, value) end
function vector_methods:dot(value) return vector.dot(self, value) end
function vector_methods:cross(value) return vector.cross(self, value) end
function vector_methods:length() return vector.length(self) end
function vector_methods:normalize() return vector.normalize(self) end
function vector_methods:distance(value) return vector.distance(self, value) end
function vector_methods:lerp(value, amount) return vector.lerp(self, value, amount) end

-- Column-major affine matrices used by exact renderer bone snapshots.
matrix4 = { api_version = "1.0" }
function matrix4.identity()
  return { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 }
end
function matrix4.copy(value)
  local result = {}
  for index = 1, 16 do
    local fallback = (index == 1 or index == 6 or index == 11 or index == 16) and 1 or 0
    result[index] = tonumber(value and value[index]) or fallback
  end
  return result
end
function matrix4.multiply(left, right)
  left, right = matrix4.copy(left), matrix4.copy(right)
  local result = {}
  for column = 0, 3 do
    for row = 0, 3 do
      local value = 0
      for index = 0, 3 do value = value + left[index * 4 + row + 1] * right[column * 4 + index + 1] end
      result[column * 4 + row + 1] = value
    end
  end
  return result
end
function matrix4.translation(value)
  value = vector.new(value)
  local result = matrix4.identity()
  result[13], result[14], result[15] = value.x, value.y, value.z
  return result
end
function matrix4.scale(value)
  value = vector.new(value or { x = 1, y = 1, z = 1 })
  local result = matrix4.identity()
  result[1], result[6], result[11] = value.x, value.y, value.z
  return result
end
function matrix4.transform_point(matrix, value)
  matrix, value = matrix4.copy(matrix), vector.new(value)
  return vector.new(
    matrix[1] * value.x + matrix[5] * value.y + matrix[9] * value.z + matrix[13],
    matrix[2] * value.x + matrix[6] * value.y + matrix[10] * value.z + matrix[14],
    matrix[3] * value.x + matrix[7] * value.y + matrix[11] * value.z + matrix[15]
  )
end
function matrix4.transform_direction(matrix, value)
  matrix, value = matrix4.copy(matrix), vector.new(value)
  return vector.new(
    matrix[1] * value.x + matrix[5] * value.y + matrix[9] * value.z,
    matrix[2] * value.x + matrix[6] * value.y + matrix[10] * value.z,
    matrix[3] * value.x + matrix[7] * value.y + matrix[11] * value.z
  )
end
function matrix4.position(matrix) matrix = matrix4.copy(matrix); return vector.new(matrix[13], matrix[14], matrix[15]) end
function matrix4.inverse(matrix)
  local m = matrix4.copy(matrix)
  local a, b, c, d, e, f, g, h, i = m[1], m[5], m[9], m[2], m[6], m[10], m[3], m[7], m[11]
  local determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
  if math.abs(determinant) < 0.0000001 then return nil end
  local inverse = matrix4.identity()
  inverse[1], inverse[5], inverse[9] = (e * i - f * h) / determinant, (c * h - b * i) / determinant, (b * f - c * e) / determinant
  inverse[2], inverse[6], inverse[10] = (f * g - d * i) / determinant, (a * i - c * g) / determinant, (c * d - a * f) / determinant
  inverse[3], inverse[7], inverse[11] = (d * h - e * g) / determinant, (b * g - a * h) / determinant, (a * e - b * d) / determinant
  local translation = matrix4.transform_direction(inverse, vector.new(-m[13], -m[14], -m[15]))
  inverse[13], inverse[14], inverse[15] = translation.x, translation.y, translation.z
  return inverse
end
