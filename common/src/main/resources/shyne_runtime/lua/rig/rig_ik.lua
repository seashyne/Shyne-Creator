-- ==============================================================================
-- Shyne Rig Inverse Kinematics Module (rig/rig_ik.lua)
-- Provides analytic Two-Bone Inverse Kinematics (IK) solvers in local coordinate planes.
-- Rotations are applied additively via :rot_add() so that keyframed animations
-- (such as idle or walk cycles) blend smoothly with procedural reaching/foot placement.
-- ==============================================================================

local rig_ik = {}

--- Clamps a numeric scalar between minimum and maximum bounds.
---@param value number Input number
---@param min number Minimum threshold
---@param max number Maximum threshold
---@return number Clamped number
local function clamp(value, min, max)
  return math.max(min, math.min(max, value))
end

--- Creates an analytic two-bone IK solver for limbs (arms/legs).
--- Solves limb joint angles using the law of cosines in the local Y/Z plane.
---@param upper string|table Upper limb bone part proxy or path (e.g. "UpperArm", "Thigh")
---@param lower string|table Lower limb bone part proxy or path (e.g. "Forearm", "Shin")
---@param target table|function Target position vector or function returning target vector
---@param options table|nil IK options { upper_length, lower_length }
---@param rig_registry table The shared rig._controllers table
---@param rig_math table Math helper module
---@return table IK controller with :update(), :start(), :stop() methods
function rig_ik.create_ik2(upper, lower, target, options, rig_registry, rig_math)
  options = options or {}
  local controller = {
    upper = rig_math.part(upper),
    lower = rig_math.part(lower),
    target_value = target,
    enabled = true
  }

  local a = math.max(0.001, tonumber(options.upper_length) or 0.5)
  local b = math.max(0.001, tonumber(options.lower_length) or 0.5)

  --- Evaluates distance to target and computes joint angles using trigonometric triangulation.
  function controller:update()
    if not self.enabled then return end
    local point = rig_math.vector(self.target_value)
    local distance = clamp(math.sqrt(point.y * point.y + point.z * point.z), math.abs(a - b) + 0.0001, a + b - 0.0001)
    local base = math.atan2(point.y, point.z)

    -- Law of cosines:
    -- cos(A) = (b^2 + c^2 - a^2) / (2*b*c)
    local upper_angle = base - math.acos(clamp((a * a + distance * distance - b * b) / (2 * a * distance), -1, 1))
    local lower_angle = math.pi - math.acos(clamp((a * a + b * b - distance * distance) / (2 * a * b), -1, 1))
    local degrees = 180 / math.pi

    self.upper:rot_add(upper_angle * degrees, 0, 0)
    self.lower:rot_add(lower_angle * degrees, 0, 0)
  end

  --- Disables the IK controller.
  ---@param reset boolean|nil If false, retains current offset; otherwise resets rot_add
  function controller:stop(reset)
    self.enabled = false
    if reset ~= false then
      self.upper:rot_add(0, 0, 0)
      self.lower:rot_add(0, 0, 0)
    end
    return self
  end

  --- Enables the IK controller.
  function controller:start()
    self.enabled = true
    return self
  end

  if rig_registry ~= nil then
    table.insert(rig_registry, controller)
  end
  return controller
end

return rig_ik
