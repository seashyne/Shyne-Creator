-- ==============================================================================
-- Shyne Rig Spring & Secondary Motion Module (rig/rig_spring.lua)
-- Implements critically-damped spring-damper physics, multi-segment chains,
-- collision raycast response, and environmental wind synthesis.
-- All rotation writes pass through part:rot_add() to layer cleanly over base Blockbench animations.
-- ==============================================================================

local rig_spring = {}

--- Constructs a deterministic wind generator function based on player position and world time.
---@param options table|nil Configuration table { strength, gust, speed, direction }
---@return function Zero-argument function returning a displacement vector in degrees
function rig_spring.wind(options)
  options = options or {}
  local strength = tonumber(options.strength) or 1
  local gust = tonumber(options.gust) or 0.35
  local speed = tonumber(options.speed) or 0.08
  local direction = vector.normalize(options.direction or vector.new(1, 0, 0))
  return function()
    local pos = minecraft.player.position()
    local time = minecraft.world.time()
    local wave = 1 + math.sin(time * speed + pos.x * 0.73 + pos.z * 0.41) * gust
    return vector.mul(direction, strength * wave)
  end
end

--- Creates a critically damped visual spring controller attached to a model bone part.
---@param part string|table Target bone part or part path
---@param options table Spring options { target, base, gravity, motion, wind, stiffness, damping, limit, cone, collision, manual }
---@param rig_registry table The shared rig._controllers table to register this controller into
---@param rig_math table Math helper module
---@return table Spring controller instance with :start(), :stop(), :impulse(), :reset() methods
function rig_spring.create_spring(part, options, rig_registry, rig_math)
  options = options or {}
  local target_part = rig_math.part(part or options.part)
  if target_part == nil then error("rig.spring requires a model part", 2) end

  local controller = {
    part = target_part,
    options = options,
    value = rig_math.vector(options.initial),
    velocity = vector.zero(),
    enabled = true
  }

  --- Computes the instantaneous target equilibrium angle including gravity, motion drag, wind, and collisions.
  function controller:target()
    local target = rig_math.vector(self.options.target, self.options.base)
    target = vector.add(target, rig_math.vector(self.options.gravity))
    if self.options.motion ~= nil then
      target = vector.add(target, rig_math.component_mul(minecraft.player.velocity(), self.options.motion))
    end
    if self.options.wind ~= nil then
      target = vector.add(target, rig_math.vector(self.options.wind))
    end
    local collision = self.options.collision
    if collision then
      local origin = vector.add(minecraft.player.position(), rig_math.vector(collision.origin))
      local hit = minecraft.world.probe(origin, rig_math.vector(collision.direction, minecraft.player.look()), collision.distance or 0.5, collision.radius or 0)
      if hit.hit then
        local response = type(collision.response) == "function" and collision.response(hit, self) or vector.mul(vector.new(hit.normal), tonumber(collision.strength) or 12)
        target = vector.add(target, rig_math.vector(response))
      end
    end
    return rig_math.cone_limit(rig_math.limit_rotation(target, self.options.limit), self.options.cone)
  end

  --- Advances spring simulation by one tick using semi-implicit Euler integration.
  function controller:update()
    if not self.enabled then return end
    local stiffness = math.max(0, math.min(1, tonumber(self.options.stiffness) or 0.18))
    local damping = math.max(0, math.min(1, tonumber(self.options.damping) or 0.78))
    local displacement = vector.sub(self:target(), self.value)
    self.velocity = vector.mul(vector.add(self.velocity, vector.mul(displacement, stiffness)), damping)
    self.value = rig_math.cone_limit(rig_math.limit_rotation(vector.add(self.value, self.velocity), self.options.limit), self.options.cone)
    self.part:rot_add(self.value)
  end

  --- Disables the spring controller.
  ---@param reset boolean|nil If false, retains current offset; otherwise resets rot_add to (0,0,0)
  function controller:stop(reset)
    self.enabled = false
    if reset ~= false then self.part:rot_add(0, 0, 0) end
    return self
  end

  --- Enables the spring controller.
  function controller:start()
    self.enabled = true
    return self
  end

  --- Injects an angular velocity impulse (e.g. on landing, attack swing, or taking damage).
  ---@param value table 3D impulse vector in degrees/tick
  function controller:impulse(value)
    self.velocity = vector.add(self.velocity, rig_math.vector(value))
    return self
  end

  --- Resets position and velocity to zero.
  function controller:reset()
    self.value = vector.zero()
    self.velocity = vector.zero()
    self.part:rot_add(0, 0, 0)
    return self
  end

  if options.manual ~= true and rig_registry ~= nil then
    table.insert(rig_registry, controller)
  end
  return controller
end

--- Creates a linked chain of progressively softer springs for multi-segment appendages.
---@param parts table Array of part paths or part proxy objects from root to tip
---@param options table Chain configuration { stiffness, damping, falloff, inherit, target, limit }
---@param rig_registry table The shared rig._controllers table
---@param rig_math table Math helper module
---@return table Chain controller with :update(), :start(), :stop(), :reset() methods
function rig_spring.create_chain(parts, options, rig_registry, rig_math)
  options = options or {}
  local chain = { controllers = {} }
  local falloff = math.max(0, math.min(0.9, tonumber(options.falloff) or 0.12))
  local previous = nil

  for index, part in ipairs(parts or {}) do
    local child = {}
    for key, value in pairs(options) do child[key] = value end
    child.stiffness = (tonumber(options.stiffness) or 0.18) * (1 - (index - 1) * falloff)
    child.damping = math.max(0, math.min(1, (tonumber(options.damping) or 0.78) - (index - 1) * falloff * 0.16))
    local parent = previous

    local source_target = options.target
    if type(source_target) == "function" then
      child.target = function() return source_target(index, parent, child) end
    else
      child.target = source_target
    end

    if parent then
      local segment_target = child.target
      child.target = function()
        local base = rig_math.vector(segment_target, child.base)
        return vector.add(base, vector.mul(parent.value, tonumber(options.inherit) or 0.45))
      end
    end

    previous = rig_spring.create_spring(part, child, rig_registry, rig_math)
    table.insert(chain.controllers, previous)
  end

  function chain:update()
    for _, controller in ipairs(self.controllers) do controller:update() end
    return self
  end

  function chain:start()
    for _, controller in ipairs(self.controllers) do controller:start() end
    return self
  end

  function chain:stop(reset)
    for _, controller in ipairs(self.controllers) do controller:stop(reset) end
    return self
  end

  function chain:reset()
    for _, controller in ipairs(self.controllers) do controller:reset() end
    return self
  end

  return chain
end

return rig_spring
