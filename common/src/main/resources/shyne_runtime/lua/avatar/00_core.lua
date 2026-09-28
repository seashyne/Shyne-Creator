-- ==============================================================================
-- Shyne Avatar Runtime: Core Math Module (avatar/00_core.lua)
-- Provides foundational 3D vectors and 4x4 affine matrices for avatar scripting.
-- ==============================================================================

local function bool(value) return value and true or false end

--- Internal forwarder for native data reads.
local function read(key, ...) return _shyne_read(key, ...) end

---@class Vector3
---@field x number X coordinate
---@field y number Y coordinate
---@field z number Z coordinate
---@field [1] number X coordinate alias
---@field [2] number Y coordinate alias
---@field [3] number Z coordinate alias
local vector_methods = {}
local vector_mt = {
  __index = vector_methods,
  __add = function(a, b) return vector.add(a, b) end,
  __sub = function(a, b) return vector.sub(a, b) end,
  __mul = function(a, b) return vector.mul(a, b) end,
  __div = function(a, b) return vector.div(a, b) end,
  __unm = function(a) return vector.mul(a, -1) end,
  __tostring = function(v) return string.format("vec3(%.3f, %.3f, %.3f)", v.x or 0, v.y or 0, v.z or 0) end
}

local function vec3(x, y, z)
  local value = { x = x or 0, y = y or 0, z = z or 0 }
  value[1], value[2], value[3] = value.x, value.y, value.z
  return setmetatable(value, vector_mt)
end

vector = {}

--- Constructs a new 3D vector.
---@param x number|table|nil X coordinate or table containing x,y,z or 1,2,3
---@param y number|nil Y coordinate
---@param z number|nil Z coordinate
---@return Vector3
function vector.new(x, y, z)
  if type(x) == "table" then return vec3(x.x or x[1], x.y or x[2], x.z or x[3]) end
  return vec3(x, y, z)
end

--- Returns a zero vector (0, 0, 0).
---@return Vector3
function vector.zero() return vec3(0, 0, 0) end

local function vector_components(value)
  if type(value) == "number" then return value, value, value end
  value = value or {}
  return value.x or value[1] or 0, value.y or value[2] or 0, value.z or value[3] or 0
end

--- Adds two vectors or adds a scalar to each component.
---@param a Vector3|table|number
---@param b Vector3|table|number
---@return Vector3
function vector.add(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax + bx, ay + by, az + bz)
end

--- Subtracts vector b from vector a.
---@param a Vector3|table|number
---@param b Vector3|table|number
---@return Vector3
function vector.sub(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax - bx, ay - by, az - bz)
end

--- Multiplies vector components. Supports scalar-vector and vector-vector multiplication.
---@param a Vector3|table|number
---@param b Vector3|table|number
---@return Vector3
function vector.mul(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax * bx, ay * by, az * bz)
end

--- Divides vector a by vector b or scalar.
---@param a Vector3|table
---@param b Vector3|table|number
---@return Vector3
function vector.div(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  if bx == 0 or by == 0 or bz == 0 then error("vector division by zero", 2) end
  return vec3(ax / bx, ay / by, az / bz)
end

--- Computes the dot product of two vectors.
---@param a Vector3|table
---@param b Vector3|table
---@return number
function vector.dot(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return ax * bx + ay * by + az * bz
end

--- Computes the cross product of two vectors (a x b).
---@param a Vector3|table
---@param b Vector3|table
---@return Vector3
function vector.cross(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
end

--- Returns the Euclidean length (magnitude) of the vector.
---@param value Vector3|table
---@return number
function vector.length(value)
  local x, y, z = vector_components(value)
  return math.sqrt(x * x + y * y + z * z)
end

--- Returns a unit vector in the same direction. Returns (0,0,0) if length is zero.
---@param value Vector3|table
---@return Vector3
function vector.normalize(value)
  local length = vector.length(value)
  if length == 0 then return vector.zero() end
  return vector.div(value, length)
end

--- Computes Euclidean distance between points a and b.
---@param a Vector3|table
---@param b Vector3|table
---@return number
function vector.distance(a, b) return vector.length(vector.sub(a, b)) end

--- Linear interpolation between vectors a and b by factor amount (0.0 to 1.0).
---@param a Vector3|table
---@param b Vector3|table
---@param amount number
---@return Vector3
function vector.lerp(a, b, amount)
  amount = math.max(0, math.min(1, tonumber(amount) or 0))
  return vector.add(a, vector.mul(vector.sub(b, a), amount))
end

--- Clamps each vector component between minimum and maximum bounds.
---@param value Vector3|table
---@param minimum Vector3|table|number
---@param maximum Vector3|table|number
---@return Vector3
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

-- ------------------------------------------------------------------------------
-- MATRIX4 (4x4 Column-Major Affine Transformations)
-- ------------------------------------------------------------------------------
matrix4 = { api_version = "1.0" }

--- Returns a 4x4 identity matrix represented as a 16-element table in column-major order.
---@return number[]
function matrix4.identity()
  return { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 }
end

--- Creates a safe copy of a 4x4 matrix table.
---@param value number[]|nil
---@return number[]
function matrix4.copy(value)
  local result = {}
  for index = 1, 16 do
    local fallback = (index == 1 or index == 6 or index == 11 or index == 16) and 1 or 0
    result[index] = tonumber(value and value[index]) or fallback
  end
  return result
end

--- Multiplies two 4x4 matrices (left * right).
---@param left number[]
---@param right number[]
---@return number[]
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

--- Creates a translation matrix from a 3D translation vector.
---@param value Vector3|table
---@return number[]
function matrix4.translation(value)
  value = vector.new(value)
  local result = matrix4.identity()
  result[13], result[14], result[15] = value.x, value.y, value.z
  return result
end

--- Creates a scale matrix from a 3D scale vector or scalar.
---@param value Vector3|table|number
---@return number[]
function matrix4.scale(value)
  value = vector.new(value or { x = 1, y = 1, z = 1 })
  local result = matrix4.identity()
  result[1], result[6], result[11] = value.x, value.y, value.z
  return result
end

--- Transforms a 3D point (w = 1) by a 4x4 matrix, including translation.
---@param matrix number[]
---@param value Vector3|table
---@return Vector3
function matrix4.transform_point(matrix, value)
  matrix, value = matrix4.copy(matrix), vector.new(value)
  return vector.new(
    matrix[1] * value.x + matrix[5] * value.y + matrix[9] * value.z + matrix[13],
    matrix[2] * value.x + matrix[6] * value.y + matrix[10] * value.z + matrix[14],
    matrix[3] * value.x + matrix[7] * value.y + matrix[11] * value.z + matrix[15]
  )
end

--- Transforms a 3D direction vector (w = 0) by a 4x4 matrix, ignoring translation.
---@param matrix number[]
---@param value Vector3|table
---@return Vector3
function matrix4.transform_direction(matrix, value)
  matrix, value = matrix4.copy(matrix), vector.new(value)
  return vector.new(
    matrix[1] * value.x + matrix[5] * value.y + matrix[9] * value.z,
    matrix[2] * value.x + matrix[6] * value.y + matrix[10] * value.z,
    matrix[3] * value.x + matrix[7] * value.y + matrix[11] * value.z
  )
end

--- Extracts the translation position component from a 4x4 matrix.
---@param matrix number[]
---@return Vector3
function matrix4.position(matrix)
  matrix = matrix4.copy(matrix)
  return vector.new(matrix[13], matrix[14], matrix[15])
end

--- Computes the algebraic inverse of a 4x4 matrix. Returns nil if non-invertible.
---@param matrix number[]
---@return number[]|nil
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

-- ------------------------------------------------------------------------------
-- OPERATOR ENHANCEMENT: '+' FOR STRING CONCATENATION
-- Allows intuitive string concatenation using '+' (e.g. "Hello " + "World" or Color.red + "Boss")
-- ------------------------------------------------------------------------------
local string_meta = getmetatable("")
if string_meta then
  string_meta.__add = function(a, b)
    return tostring(a) .. tostring(b)
  end
end

-- ------------------------------------------------------------------------------
-- MINECRAFT COLOR & FORMATTING CHEAT SHEET HELPER (Color / color)
-- Supports 3 styles:
--  1) Dual Value: Color.red is both string "§c" AND function Color.red("text")
--  2) Operator '+': Color.red + Color.bold + "TEXT" or "HP: " + 100
--  3) Tag Formatter: Color.fmt("<red><bold>...</bold></red>")
-- ------------------------------------------------------------------------------
local function make_color_node(code)
  local node = { code = code }
  local mt = {
    __tostring = function() return code end,
    __concat   = function(a, b) return tostring(a) .. tostring(b) end,
    __add      = function(a, b) return tostring(a) .. tostring(b) end,
    __call     = function(self, text) return code .. tostring(text) .. "§r" end
  }
  return setmetatable(node, mt)
end

Color = {
  black        = make_color_node("§0"),
  dark_blue    = make_color_node("§1"),
  dark_green   = make_color_node("§2"),
  dark_aqua    = make_color_node("§3"),
  dark_red     = make_color_node("§4"),
  dark_purple  = make_color_node("§5"),
  gold         = make_color_node("§6"),
  gray         = make_color_node("§7"),
  dark_gray    = make_color_node("§8"),
  blue         = make_color_node("§9"),
  green        = make_color_node("§a"),
  aqua         = make_color_node("§b"),
  red          = make_color_node("§c"),
  light_purple = make_color_node("§d"),
  yellow       = make_color_node("§e"),
  white        = make_color_node("§f"),

  matrix       = make_color_node("§k"),
  bold         = make_color_node("§l"),
  strike       = make_color_node("§m"),
  underline    = make_color_node("§n"),
  italic       = make_color_node("§o"),
  reset        = make_color_node("§r"),

  skull        = "☠",
  sword        = "🗡",
  shield       = "🛡",
  lightning    = "⚡",
  heart        = "❤",
  star         = "★",
  fire         = "🔥",
  arrow        = "➜",
}

function Color.fmt(text)
  local s = tostring(text)
  s = s:gsub("<red>", tostring(Color.red)):gsub("</red>", tostring(Color.reset))
  s = s:gsub("<green>", tostring(Color.green)):gsub("</green>", tostring(Color.reset))
  s = s:gsub("<yellow>", tostring(Color.yellow)):gsub("</yellow>", tostring(Color.reset))
  s = s:gsub("<aqua>", tostring(Color.aqua)):gsub("</aqua>", tostring(Color.reset))
  s = s:gsub("<gold>", tostring(Color.gold)):gsub("</gold>", tostring(Color.reset))
  s = s:gsub("<gray>", tostring(Color.gray)):gsub("</gray>", tostring(Color.reset))
  s = s:gsub("<white>", tostring(Color.white)):gsub("</white>", tostring(Color.reset))
  s = s:gsub("<bold>", tostring(Color.bold)):gsub("</bold>", tostring(Color.reset))
  s = s:gsub("<italic>", tostring(Color.italic)):gsub("</italic>", tostring(Color.reset))
  s = s:gsub("<reset>", tostring(Color.reset))
  s = s:gsub("<skull>", Color.skull)
  s = s:gsub("<lightning>", Color.lightning)
  s = s:gsub("<sword>", Color.sword)
  s = s:gsub("<shield>", Color.shield)
  s = s:gsub("<heart>", Color.heart)
  s = s:gsub("<star>", Color.star)
  s = s:gsub("<fire>", Color.fire)
  s = s:gsub("&([0-9a-fk-or])", "§%1")
  return s
end

color = Color
