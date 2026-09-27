-- ==============================================================================
-- Shyne Rig Math & Utility Module (rig/rig_math.lua)
-- Provides foundational math transformations, vector resolution, and angle
-- clamping algorithms for procedural avatar animations.
-- ==============================================================================

local rig_math = {}

--- Resolves a model part proxy from a part name or existing part reference.
---@param value string|table Part path (e.g. "Head", "model.Head") or proxy object
---@return table|nil Resolved part proxy
function rig_math.part(value)
  if type(value) == "string" then return model.part(value) end
  return value
end

--- Resolves a 3D vector from a value, callable function, or fallback vector.
---@param value any Vector, table with xyz/123, or zero-arg function returning vector
---@param fallback table|nil Fallback vector if value is nil (defaults to vector.zero())
---@return table Validated 3D vector object
function rig_math.vector(value, fallback)
  if type(value) == "function" then value = value() end
  if value == nil then value = fallback or vector.zero() end
  return vector.new(value)
end

--- Multiplies two vectors component-by-component (Hadamard product).
---@param a table First vector
---@param b table Second vector
---@return table Resulting vector (a.x*b.x, a.y*b.y, a.z*b.z)
function rig_math.component_mul(a, b)
  a, b = vector.new(a), vector.new(b)
  return vector.new(a.x * b.x, a.y * b.y, a.z * b.z)
end

--- Clamps rotational degrees on each axis independently according to the specified limits.
---@param value table Rotational vector (pitch, yaw, roll in degrees)
---@param limit number|table Maximum allowable degrees (scalar or per-axis vector)
---@return table Clamped rotation vector
function rig_math.limit_rotation(value, limit)
  if limit == nil then return value end
  if type(limit) == "number" then limit = vector.new(limit, limit, limit) else limit = vector.new(limit) end
  return vector.new(
    math.max(-math.abs(limit.x), math.min(math.abs(limit.x), value.x)),
    math.max(-math.abs(limit.y), math.min(math.abs(limit.y), value.y)),
    math.max(-math.abs(limit.z), math.min(math.abs(limit.z), value.z))
  )
end

--- Constrains total angular displacement within a cone of maximum degrees.
--- Unlike per-axis clamping, cone limits preserve radial direction while bounding magnitude.
---@param value table Displacement vector in degrees
---@param degrees number|nil Maximum cone radius angle in degrees
---@return table Constrained vector
function rig_math.cone_limit(value, degrees)
  if degrees == nil then return value end
  local length = vector.length(value)
  local maximum = math.max(0, tonumber(degrees) or 0)
  return length > maximum and vector.mul(vector.normalize(value), maximum) or value
end

return rig_math
