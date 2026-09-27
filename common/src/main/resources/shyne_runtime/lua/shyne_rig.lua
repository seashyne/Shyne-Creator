-- ==============================================================================
-- Shyne Native Rig API 1.3 (shyne_rig.lua)
-- Procedural Rigging, Secondary Motion Springs, Inverse Kinematics (IK),
-- State-driven Animation Graphs, and Vanilla Limb / Armor Cosmetic Attachments.
--
-- Procedural controllers write through part:rot_add(), never part:rot(),
-- preserving underlying imported Blockbench animations as the base motion layer.
-- ==============================================================================

rig = rig or { _controllers = {} }

-- ------------------------------------------------------------------------------
-- 1. MATHEMATICAL HELPERS & VECTOR RESOLUTION
-- ------------------------------------------------------------------------------

--- Resolves a part proxy from string path or existing object.
local function rig_part(value)
  if type(value) == "string" then return model.part(value) end
  return value
end

--- Resolves a 3D vector from value, function, or fallback.
local function rig_vector(value, fallback)
  if type(value) == "function" then value = value() end
  if value == nil then value = fallback or vector.zero() end
  return vector.new(value)
end

--- Component-wise multiplication of two 3D vectors.
local function component_mul(a, b)
  a, b = vector.new(a), vector.new(b)
  return vector.new(a.x * b.x, a.y * b.y, a.z * b.z)
end

--- Clamps rotational degrees per axis independently.
local function limit_rotation(value, limit)
  if limit == nil then return value end
  if type(limit) == "number" then limit = vector.new(limit, limit, limit) else limit = vector.new(limit) end
  return vector.new(
    math.max(-math.abs(limit.x), math.min(math.abs(limit.x), value.x)),
    math.max(-math.abs(limit.y), math.min(math.abs(limit.y), value.y)),
    math.max(-math.abs(limit.z), math.min(math.abs(limit.z), value.z))
  )
end

--- Constrains total angular displacement within a cone of maximum degrees.
local function cone_limit(value, degrees)
  if degrees == nil then return value end
  local length = vector.length(value)
  local maximum = math.max(0, tonumber(degrees) or 0)
  return length > maximum and vector.mul(vector.normalize(value), maximum) or value
end

-- ------------------------------------------------------------------------------
-- 2. PROCEDURAL WIND & SECONDARY SPRINGS
-- ------------------------------------------------------------------------------

--- Generates a deterministic procedural wind function.
---@param options table|nil { strength, gust, speed, direction }
---@return function Zero-arg function returning a displacement vector in degrees
function rig.wind(options)
  options = options or {}
  local strength, gust, speed = tonumber(options.strength) or 1, tonumber(options.gust) or 0.35, tonumber(options.speed) or 0.08
  local direction = vector.normalize(options.direction or vector.new(1, 0, 0))
  return function()
    local pos, time = minecraft.player.position(), minecraft.world.time()
    local wave = 1 + math.sin(time * speed + pos.x * 0.73 + pos.z * 0.41) * gust
    return vector.mul(direction, strength * wave)
  end
end

--- Creates a critically damped visual spring controller attached to a model bone part.
---@param part string|table Target part proxy or name
---@param options table Spring options { target, base, gravity, motion, wind, stiffness, damping, limit, cone, collision, manual }
---@return table Spring controller
function rig.spring(part, options)
  options = options or {}
  local controller = {
    part = rig_part(part or options.part),
    options = options,
    value = rig_vector(options.initial),
    velocity = vector.zero(),
    enabled = true
  }
  if controller.part == nil then error("rig.spring requires a model part", 2) end

  function controller:target()
    local target = rig_vector(self.options.target, self.options.base)
    target = vector.add(target, rig_vector(self.options.gravity))
    if self.options.motion ~= nil then target = vector.add(target, component_mul(minecraft.player.velocity(), self.options.motion)) end
    if self.options.wind ~= nil then target = vector.add(target, rig_vector(self.options.wind)) end
    local collision = self.options.collision
    if collision then
      local origin = vector.add(minecraft.player.position(), rig_vector(collision.origin))
      local hit = minecraft.world.probe(origin, rig_vector(collision.direction, minecraft.player.look()), collision.distance or 0.5, collision.radius or 0)
      if hit.hit then
        local response = type(collision.response) == "function" and collision.response(hit, self) or vector.mul(vector.new(hit.normal), tonumber(collision.strength) or 12)
        target = vector.add(target, rig_vector(response))
      end
    end
    return cone_limit(limit_rotation(target, self.options.limit), self.options.cone)
  end

  function controller:update()
    if not self.enabled then return end
    local stiffness = math.max(0, math.min(1, tonumber(self.options.stiffness) or 0.18))
    local damping = math.max(0, math.min(1, tonumber(self.options.damping) or 0.78))
    self.velocity = vector.mul(vector.add(self.velocity, vector.mul(vector.sub(self:target(), self.value), stiffness)), damping)
    self.value = cone_limit(limit_rotation(vector.add(self.value, self.velocity), self.options.limit), self.options.cone)
    self.part:rot_add(self.value)
  end

  function controller:stop(reset)
    self.enabled = false
    if reset ~= false then self.part:rot_add(0, 0, 0) end
    return self
  end
  function controller:start() self.enabled = true; return self end
  function controller:impulse(value) self.velocity = vector.add(self.velocity, rig_vector(value)); return self end
  function controller:reset() self.value = vector.zero(); self.velocity = vector.zero(); self.part:rot_add(0, 0, 0); return self end

  if options.manual ~= true then table.insert(rig._controllers, controller) end
  return controller
end

--- Multiple progressively softer springs for tails, hair, ribbons, wings, and fins.
---@param parts table Array of part proxies or paths
---@param options table Chain options { falloff, stiffness, damping, target, inherit }
---@return table Chain controller
function rig.chain(parts, options)
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
        local base = rig_vector(segment_target, child.base)
        return vector.add(base, vector.mul(parent.value, tonumber(options.inherit) or 0.45))
      end
    end
    previous = rig.spring(part, child)
    table.insert(chain.controllers, previous)
  end
  function chain:update() for _, controller in ipairs(self.controllers) do controller:update() end return self end
  function chain:start() for _, controller in ipairs(self.controllers) do controller:start() end return self end
  function chain:stop(reset) for _, controller in ipairs(self.controllers) do controller:stop(reset) end return self end
  function chain:reset() for _, controller in ipairs(self.controllers) do controller:reset() end return self end
  return chain
end

-- ------------------------------------------------------------------------------
-- 3. TWO-BONE INVERSE KINEMATICS (IK)
-- ------------------------------------------------------------------------------

--- Analytic two-bone IK in the local Y/Z plane. Applied additively with :rot_add().
---@param upper string|table Upper bone
---@param lower string|table Lower bone
---@param target table|function Target point
---@param options table|nil { upper_length, lower_length }
---@return table IK controller
function rig.ik2(upper, lower, target, options)
  options = options or {}
  local controller = { upper = rig_part(upper), lower = rig_part(lower), target_value = target, enabled = true }
  local a, b = math.max(0.001, tonumber(options.upper_length) or 0.5), math.max(0.001, tonumber(options.lower_length) or 0.5)
  local function clamp(value, min, max) return math.max(min, math.min(max, value)) end
  function controller:update()
    if not self.enabled then return end
    local point = rig_vector(self.target_value)
    local distance = clamp(math.sqrt(point.y * point.y + point.z * point.z), math.abs(a - b) + 0.0001, a + b - 0.0001)
    local base = math.atan2(point.y, point.z)
    local upper_angle = base - math.acos(clamp((a * a + distance * distance - b * b) / (2 * a * distance), -1, 1))
    local lower_angle = math.pi - math.acos(clamp((a * a + b * b - distance * distance) / (2 * a * b), -1, 1))
    local degrees = 180 / math.pi
    self.upper:rot_add(upper_angle * degrees, 0, 0)
    self.lower:rot_add(lower_angle * degrees, 0, 0)
  end
  function controller:stop(reset) self.enabled = false; if reset ~= false then self.upper:rot_add(0, 0, 0); self.lower:rot_add(0, 0, 0) end return self end
  function controller:start() self.enabled = true; return self end
  table.insert(rig._controllers, controller)
  return controller
end

-- ------------------------------------------------------------------------------
-- 4. ANIMATION GRAPH (State Machine)
-- ------------------------------------------------------------------------------

--- Declarative animation state machine over Blockbench animation layers.
---@param definition table { states, order, default, transition }
---@return table Graph controller
function rig.animation_graph(definition)
  definition = definition or {}
  local graph = { states = definition.states or {}, active = nil, enabled = true }
  local function matches(state)
    if type(state.when) ~= "function" then return state.when == true end
    local ok, value = pcall(state.when)
    return ok and value and true or false
  end
  function graph:select()
    for _, name in ipairs(definition.order or {}) do
      local state = self.states[name]
      if state and matches(state) then return name, state end
    end
    local selected_name, selected_state, selected_priority = nil, nil, -math.huge
    for name, state in pairs(self.states) do
      if matches(state) then
        local priority = tonumber(state.priority) or 0
        if selected_state == nil or priority > selected_priority or (priority == selected_priority and tostring(name) < tostring(selected_name)) then
          selected_name, selected_state, selected_priority = name, state, priority
        end
      end
    end
    if selected_state ~= nil then return selected_name, selected_state end
    local fallback = definition.default or "idle"
    return fallback, self.states[fallback]
  end
  function graph:update()
    if not self.enabled then return end
    local name, state = self:select()
    if not state or name == self.active then return end
    if self.active and self.states[self.active] then model.animation.get(self.states[self.active].animation or self.active):stop() end
    self.active = name
    local transition = state.transition or definition.transition or 5
    model.animation.get(state.animation or name)
      :loop(state.loop ~= false)
      :weight(state.weight or 1)
      :priority(state.priority or 0)
      :fade_in(state.fade_in or definition.fade_in or transition)
      :fade_out(state.fade_out or definition.fade_out or transition)
      :transition(transition)
      :play()
  end
  function graph:play(name) self.active = nil; definition.default = name; self:update(); return self end
  function graph:stop() if self.active and self.states[self.active] then model.animation.get(self.states[self.active].animation or self.active):stop() end self.enabled = false; return self end
  table.insert(rig._controllers, graph)
  return graph
end

-- ------------------------------------------------------------------------------
-- 5. EQUIPMENT & COSMETICS
-- ------------------------------------------------------------------------------

--- Attaches custom parts to vanilla player limbs.
---@param parts table Array of part proxies or names
---@param parent string Vanilla limb name ("HEAD", "BODY", "LEFT_ARM", etc.)
---@param options table|nil { mode = "full" | "position" | "rotation" }
function rig.attach(parts, parent, options)
  options = options or {}
  for _, part in ipairs(parts or {}) do rig_part(part):vanilla_parent(parent, options.mode or "full") end
  return parts
end

--- Custom armor visibility driven by equipped armor items, materials, and trims.
---@param options table Configuration mapping armor slots to part definitions
---@return table Armor controller
function rig.armor(options)
  options = options or {}
  local defaults = {
    { key = "head", slot = "head", parent = "HEAD" },
    { key = "chest", slot = "chest", parent = "BODY" },
    { key = "left_arm", slot = "chest", parent = "LEFT_ARM" },
    { key = "right_arm", slot = "chest", parent = "RIGHT_ARM" },
    { key = "legs", slot = "legs", parent = "BODY" },
    { key = "left_leg", slot = "legs", parent = "LEFT_LEG" },
    { key = "right_leg", slot = "legs", parent = "RIGHT_LEG" },
    { key = "feet", slot = "feet", parent = "LEFT_LEG" },
    { key = "left_foot", slot = "feet", parent = "LEFT_LEG" },
    { key = "right_foot", slot = "feet", parent = "RIGHT_LEG" }
  }
  local controller = { bindings = {}, enabled = true }
  for _, default in ipairs(defaults) do
    local spec = options[default.key]
    if spec ~= nil then
      if type(spec) ~= "table" then
        spec = { parts = spec }
      elseif spec.parts == nil and spec.variants == nil and spec.parent == nil and spec.slot == nil
          and spec.mode == nil and spec.match == nil and spec.visible_when_empty == nil then
        spec = { parts = spec }
      end
      local parts = spec.parts or {}
      if type(parts) == "string" then parts = { parts } end
      rig.attach(parts, spec.parent or default.parent, { mode = spec.mode or options.mode or "full" })
      local variants = spec.variants or {}
      for _, variant_parts in pairs(variants) do
        if type(variant_parts) == "string" then variant_parts = { variant_parts } end
        rig.attach(variant_parts, spec.parent or default.parent, { mode = spec.mode or options.mode or "full" })
      end
      table.insert(controller.bindings, { slot = spec.slot or default.slot, parts = parts, variants = variants, visible_when_empty = spec.visible_when_empty == true, match = spec.match })
    end
  end
  function controller:update()
    if not self.enabled then return end
    for _, binding in ipairs(self.bindings) do
      local stack = minecraft.player.armor(binding.slot) or { empty = true }
      local visible = binding.visible_when_empty or not stack.empty
      if visible and type(binding.match) == "function" then visible = binding.match(stack) and true or false end
      for _, part in ipairs(binding.parts) do rig_part(part):visible(visible) end
      local explicit_match = false
      for item_id in pairs(binding.variants) do
        local key = tostring(item_id):lower()
        if key ~= "default" and (
          key == tostring(stack.id or ""):lower() or
          key == "material:" .. tostring(stack.material or ""):lower() or
          key == "trim_material:" .. tostring(stack.trim_material or ""):lower() or
          key == "trim_pattern:" .. tostring(stack.trim_pattern or ""):lower()
        ) then
          explicit_match = true
          break
        end
      end
      for item_id, variant_parts in pairs(binding.variants) do
        if type(variant_parts) == "string" then variant_parts = { variant_parts } end
        local key = tostring(item_id):lower()
        local selected = visible and (
          (key == "default" and not explicit_match) or
          (key ~= "default" and (
            key == tostring(stack.id or ""):lower() or
            key == "material:" .. tostring(stack.material or ""):lower() or
            key == "trim_material:" .. tostring(stack.trim_material or ""):lower() or
            key == "trim_pattern:" .. tostring(stack.trim_pattern or ""):lower()
          ))
        )
        for _, part in ipairs(variant_parts) do rig_part(part):visible(selected) end
      end
    end
  end
  function controller:start() self.enabled = true; return self end
  function controller:stop() self.enabled = false; return self end
  table.insert(rig._controllers, controller)
  return controller
end

--- Custom Elytra layer synced with player's chest armor slot and fall-flying state.
---@param parts table Array of part proxies or names
---@param options table|nil { mode, show_folded }
---@return table Elytra controller
function rig.elytra(parts, options)
  options = options or {}
  if type(parts) == "string" then parts = { parts } end
  rig.attach(parts or {}, "BODY", { mode = options.mode or "full" })
  local controller = { parts = parts or {}, enabled = true }
  function controller:update()
    if not self.enabled then return end
    local chest = minecraft.player.armor("chest") or { empty = true }
    local visible = chest.id == "minecraft:elytra" and (options.show_folded == true or minecraft.player.fall_flying())
    for _, part in ipairs(self.parts) do rig_part(part):visible(visible) end
  end
  function controller:start() self.enabled = true; return self end
  function controller:stop() self.enabled = false; return self end
  table.insert(rig._controllers, controller)
  return controller
end

-- ------------------------------------------------------------------------------
-- 6. EVENT HOOKS & CONTROLLER LIFECYCLE
-- ------------------------------------------------------------------------------
events._internal = events._internal or {}
events._internal.tick = events._internal.tick or {}
table.insert(events._internal.tick, function()
  for _, controller in ipairs(rig._controllers) do
    local ok, message = pcall(function() controller:update() end)
    if not ok then _shyne_report_error("rig", "controller", tostring(message)) end
  end
end)

events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() rig._controllers = {} end)

return rig
