-- Shyne Native Rig API 1.3.
--
-- This is intentionally a separate runtime module: shyne_avatar.lua owns the
-- stable core API, while this file owns optional procedural rig behaviour.
-- Every controller writes through part:rot_add(), never part:rot(), so imported
-- Blockbench animation remains the base layer.

rig = { _controllers = {} }

local function rig_part(value)
  if type(value) == "string" then return model.part(value) end
  return value
end

local function rig_vector(value, fallback)
  if type(value) == "function" then value = value() end
  if value == nil then value = fallback or vector.zero() end
  return vector.new(value)
end

local function component_mul(a, b)
  a, b = vector.new(a), vector.new(b)
  return vector.new(a.x * b.x, a.y * b.y, a.z * b.z)
end

local function limit_rotation(value, limit)
  if limit == nil then return value end
  if type(limit) == "number" then limit = vector.new(limit, limit, limit) else limit = vector.new(limit) end
  return vector.new(
    math.max(-math.abs(limit.x), math.min(math.abs(limit.x), value.x)),
    math.max(-math.abs(limit.y), math.min(math.abs(limit.y), value.y)),
    math.max(-math.abs(limit.z), math.min(math.abs(limit.z), value.z))
  )
end

-- Cone limits constrain total angular displacement, unlike a per-axis clamp.
local function cone_limit(value, degrees)
  if degrees == nil then return value end
  local length = vector.length(value)
  local maximum = math.max(0, tonumber(degrees) or 0)
  return length > maximum and vector.mul(vector.normalize(value), maximum) or value
end

-- Deterministic local wind. Authors can use the returned function as spring.wind
-- or add their own wind function from biome/weather/state data.
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

-- Critically damped visual spring. target/base/gravity/motion/wind are degree vectors.
function rig.spring(part, options)
  options = options or {}
  local controller = {
    part = rig_part(part or options.part), options = options,
    value = rig_vector(options.initial), velocity = vector.zero(), enabled = true
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
  -- Impulses are intended for swing, landing, damage, emotes or a collision callback.
  function controller:impulse(value) self.velocity = vector.add(self.velocity, rig_vector(value)); return self end
  function controller:reset() self.value = vector.zero(); self.velocity = vector.zero(); self.part:rot_add(0, 0, 0); return self end
  -- SquAPI-compatible objects may opt into manual updates.  This keeps the
  -- original library's `autoFunctionUpdates = false` contract meaningful
  -- without scheduling the same spring twice in one client tick.
  if options.manual ~= true then table.insert(rig._controllers, controller) end
  return controller
end

-- Multiple progressively softer springs for tails, hair, ribbons, wings and fins.
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
    -- A chain target can depend on its segment index (tail idle offsets) and
    -- the previous spring (real parent-to-child secondary motion).
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
  function chain:update()
    for _, controller in ipairs(self.controllers) do controller:update() end
    return self
  end
  function chain:start() for _, controller in ipairs(self.controllers) do controller:start() end return self end
  function chain:stop(reset) for _, controller in ipairs(self.controllers) do controller:stop(reset) end return self end
  function chain:reset() for _, controller in ipairs(self.controllers) do controller:reset() end return self end
  return chain
end

-- Analytic two-bone IK in the local Y/Z plane. It is intentionally additive,
-- so authored walk/swim animation stays visible while the limb reaches a target.
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

-- Small state graph over the existing layered Blockbench animation runtime.
-- Each state has animation, when=function(), priority, weight and transition.
function rig.animation_graph(definition)
  definition = definition or {}
  local graph = { states = definition.states or {}, active = nil, enabled = true }
  local function matches(state)
    if type(state.when) ~= "function" then return state.when == true end
    -- A broken optional condition must not prevent idle from being selected.
    local ok, value = pcall(state.when)
    return ok and value and true or false
  end
  function graph:select()
    -- Use an explicit order when states overlap (for example swim before walk).
    for _, name in ipairs(definition.order or {}) do
      local state = self.states[name]
      if state and matches(state) then return name, state end
    end
    -- Without an explicit order, priority is authoritative.  Never return the
    -- first pairs() entry: Lua deliberately makes that order unspecified.
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
    -- The fade settings live on the already-playing layer.  Calling fade_out
    -- on a fresh proxy would not affect it, so only stop it here.
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

-- Native SquAPI compatibility.  The code below was written against the public
-- call shapes used by real SquAPI avatar packs, but it deliberately does not
-- load, copy or execute Figura's library.  Each entry point maps to Shyne's
-- model/animation APIs and native rig controllers instead.
--
-- The supported surface is intentionally honest: world-space floating points
-- and UV animation are not exposed here because Shyne does not yet have an
-- equivalent transform/UV renderer.  Everything declared below performs a
-- visible native action rather than pretending to succeed.
squapi = {
  autoFunctionUpdates = true,
  eyeScale = 1,
  smoothHeadOffset = vector.zero(),
  torsoOffset = vector.zero(),
  doBlink = true,
  wagStrength = 1,
  doBounce = false,
  cancelHeadMovement = false,
  floatPointEnabled = true,
  _controllers = {}
}

local function squapi_clamp(value, minimum, maximum)
  return math.max(minimum, math.min(maximum, value))
end

local function squapi_angle_delta(current, previous)
  return ((current - previous + 180) % 360) - 180
end

local function squapi_parts(first, second)
  local result = {}
  -- Shyne model proxies have a path.  A normal Lua array has an indexed first
  -- value, so this accepts both `part` and `{ partA, partB }` forms.
  if type(first) == "table" and first.path == nil and first[1] ~= nil then
    for _, value in ipairs(first) do table.insert(result, value) end
  elseif first ~= nil then
    table.insert(result, first)
  end
  if second ~= nil then table.insert(result, second) end
  return result
end

local function squapi_part_list(value)
  return squapi_parts(value)
end

local function squapi_head_rotation()
  local rotation = minecraft.player.rotation()
  return vector.new(rotation.x or 0, squapi_angle_delta(rotation.y or 0, minecraft.player.body_yaw()), 0)
end

local function squapi_relative_velocity()
  local velocity = minecraft.player.velocity()
  local yaw = math.rad(minecraft.player.body_yaw())
  return {
    forward = -velocity.x * math.sin(yaw) + velocity.z * math.cos(yaw),
    side = velocity.x * math.cos(yaw) + velocity.z * math.sin(yaw),
    vertical = velocity.y
  }
end

local function squapi_track(collection, controller)
  if collection ~= nil then table.insert(collection, controller) end
  table.insert(squapi._controllers, controller)
  return controller
end

local function squapi_controller(controller)
  controller.enabled = controller.enabled ~= false
  function controller:enable() self.enabled = true; return self end
  function controller:disable() self.enabled = false; return self end
  function controller:toggle() self.enabled = not self.enabled; return self end
  function controller:setEnabled(value) self.enabled = value == true; return self end
  function controller:start() return self:enable() end
  function controller:stop() return self:disable() end
  function controller:zero() if self.reset ~= nil then return self:reset() end return self end
  -- Figura packs with `autoFunctionUpdates = false` commonly call :tick()
  -- themselves.  render() is intentionally a no-op because Shyne applies
  -- transforms at tick rate, avoiding a second physics step every frame.
  function controller:render() return self end
  return controller
end

-- SquAPI's current tail/ear/bewb controllers are based on this scalar BERP
-- integrator, not Shyne's generic rig.spring().  Keep it local to the shim so
-- native authors can still choose the more conventional rig.spring behaviour.
local function squapi_berp(stiffness, bounce, lower, upper, initial_position, initial_velocity)
  local spring = {
    stiff = tonumber(stiffness) or 0.1,
    bounce = tonumber(bounce) or 0.1,
    pos = tonumber(initial_position) or 0,
    vel = tonumber(initial_velocity) or 0,
    acc = 0,
    lower = lower,
    upper = upper
  }
  function spring:step(target, delta, override_stiffness, override_bounce)
    delta = tonumber(delta) or 1
    local stiff = squapi_clamp(tonumber(override_stiffness) or self.stiff, 0, 1)
    local retained = squapi_clamp(tonumber(override_bounce) or self.bounce, 0, 1)
    local difference = (tonumber(target) or 0) - self.pos
    self.acc = difference * stiff * delta
    self.vel = self.vel + self.acc
    self.pos = self.pos + (difference * (1 - retained) + self.vel) * delta
    if self.upper ~= nil and self.pos > self.upper then self.pos, self.vel = self.upper, 0 end
    if self.lower ~= nil and self.pos < self.lower then self.pos, self.vel = self.lower, 0 end
    return self.pos
  end
  function spring:reset(position)
    self.pos, self.vel, self.acc = tonumber(position) or 0, 0, 0
    return self
  end
  return spring
end

local function squapi_reset_bounce(object)
  object.pos, object.position, object.vel, object.velocity = 0, 0, 0, 0
  return object
end

local function squapi_animation(value)
  return type(value) == "string" and model.animation.get(value) or value
end

local function squapi_animation_play(animation)
  animation = squapi_animation(animation)
  if animation ~= nil and animation.play ~= nil then animation:play() end
end

local function squapi_animation_stop(animation)
  animation = squapi_animation(animation)
  if animation ~= nil and animation.stop ~= nil then animation:stop() end
end

local function squapi_animation_playing(animation)
  animation = squapi_animation(animation)
  if animation == nil then return false end
  local ok, playing = pcall(function()
    if animation.playing ~= nil then return animation:playing() end
    if animation.isPlaying ~= nil then return animation:isPlaying() end
    if animation.isStopped ~= nil then return not animation:isStopped() end
    return false
  end)
  return ok and playing == true
end

-- Same scalar spring convention as SquAPI: old packs use `.pos`/`.vel`, while
-- earlier Shyne previews documented `.position`/`.velocity`.  Keep both names
-- live so scripts can mix them safely.
squapi.bounceObject = {}
function squapi.bounceObject:new(initial)
  local position = tonumber(initial) or 0
  local object = { pos = position, position = position, vel = 0, velocity = 0 }
  function object:doBounce(target, stiff, bounce)
    local current = tonumber(self.pos)
    if current == nil then current = tonumber(self.position) or 0 end
    local velocity = tonumber(self.vel)
    if velocity == nil then velocity = tonumber(self.velocity) or 0 end
    local difference = (tonumber(target) or 0) - current
    local stiffness = squapi_clamp(tonumber(stiff) or 0.005, 0, 1)
    local bounciness = squapi_clamp(tonumber(bounce) or 0.05, 0, 1)
    velocity = velocity + ((difference - velocity * stiffness) * stiffness)
    current = current + velocity + difference * bounciness
    self.pos, self.position, self.vel, self.velocity = current, current, velocity, velocity
    return current
  end
  return object
end

function squapi.bouncetowards(current, target, velocity, stiff, bounce)
  local object = squapi.bounceObject:new(current)
  object.vel, object.velocity = tonumber(velocity) or 0, tonumber(velocity) or 0
  local position = object:doBounce(target, stiff, bounce)
  return position, object.vel
end

function squapi.lineargraph(x1, y1, x2, y2, value)
  if x1 == x2 then return y2 end
  return (y2 - y1) / (x2 - x1) * value + (y2 - (y2 - y1) / (x2 - x1) * x2)
end

function squapi.parabolagraph(x1, y1, x2, y2, x3, y3, value)
  local denominator = (x1 - x2) * (x1 - x3) * (x2 - x3)
  if denominator == 0 then return y2 end
  local a = (x3 * (y2 - y1) + x2 * (y1 - y3) + x1 * (y3 - y2)) / denominator
  local b = (x3 * x3 * (y1 - y2) + x2 * x2 * (y3 - y1) + x1 * x1 * (y2 - y3)) / denominator
  local c = (x2 * x3 * (x2 - x3) * y1 + x3 * x1 * (x3 - x1) * y2 + x1 * x2 * (x1 - x2) * y3) / denominator
  return a * value * value + b * value + c
end

function squapi.getForwardVel()
  return squapi_relative_velocity().forward
end

function squapi.getSideVelocity()
  return squapi_relative_velocity().side
end

function squapi.yvel()
  return minecraft.player.velocity().y
end

-- Modern SquAPI exposes tail:new(...), while older packs call tails(...).
-- The older function has a genuinely different contract: `intensity` controls
-- turning/vertical force and `tailVelBend` controls forward bend.  Do not fold
-- those values together or older aquatic/Catfish tails lose most of their motion.
local function squapi_tail_parts(value)
  -- SquAPI also accepts { rootGroup, segmentCount, optionalPrefix }.  Expand
  -- that compact nested-group notation before building native controllers.
  if type(value) == "table" and type(value[2]) == "number" then
    local count = math.max(1, math.floor(value[2]))
    local root = rig_part(value[1])
    local prefix = tostring(value[3] or "")
    if root == nil then return {} end
    local result = { root }
    if count == 1 then return result end
    if count == 2 then table.insert(result, root[prefix .. "tailtip"]); return result end
    local previous = root[prefix .. "tailseg"]
    table.insert(result, previous)
    for index = 2, count - 2 do
      previous = previous[prefix .. "tailseg" .. index]
      table.insert(result, previous)
    end
    table.insert(result, previous[prefix .. "tailtip"])
    return result
  end
  return squapi_part_list(value)
end

squapi.tails = setmetatable({}, { __call = function(_, parts, intensity, idle_y, idle_x, speed_y, speed_x, tail_vel_bend, initial, segment_offset, stiffness, bounce, flying, down, up)
  local legacy = squapi.tail:new(
    parts,
    tonumber(idle_x) or 5, tonumber(idle_y) or 15,
    tonumber(speed_x) or 1.2, tonumber(speed_y) or 2,
    tonumber(intensity) or 2, tonumber(tail_vel_bend) or 0,
    tonumber(initial) or 0, tonumber(segment_offset) or 1,
    tonumber(stiffness) or 0.005, tonumber(bounce) or 0.05, tonumber(flying) or 0,
    -(math.abs(tonumber(up) or 40)), math.abs(tonumber(down) or 10)
  )
  legacy.legacy = true
  return legacy
end })
squapi.tail = {}
function squapi.tail:new(parts, idle_x, idle_y, idle_x_speed, idle_y_speed, bend_strength, velocity_push, initial_offset, segment_offset, stiffness, bounce, flying_offset, down_limit, up_limit)
  local self = squapi_controller({
    parts = squapi_tail_parts(parts), idleXMovement = tonumber(idle_x) or 15, idleYMovement = tonumber(idle_y) or 5,
    idleXSpeed = tonumber(idle_x_speed) or 1.2, idleYSpeed = tonumber(idle_y_speed) or 2,
    bendStrength = tonumber(bend_strength) or 2, velocityPush = tonumber(velocity_push) or 0,
    initialMovementOffset = tonumber(initial_offset) or 0, offsetBetweenSegments = tonumber(segment_offset) or 1,
    stiffness = tonumber(stiffness) or 0.005, bounce = tonumber(bounce) or 0.9,
    flyingOffset = tonumber(flying_offset) or 90, downLimit = tonumber(down_limit) or -90, upLimit = tonumber(up_limit) or 45,
    lastBodyYaw = minecraft.player.body_yaw(), bodyTurn = 0
  })
  self.berps = {}
  self.legacyMotion = {}
  for index = 1, #self.parts do
    -- Tail groups are nested in Blockbench already.  Each BERP is independent;
    -- adding the previous segment again would double-bend the hierarchy.
    self.berps[index] = {
      pitch = squapi_berp(self.stiffness, self.bounce, self.downLimit, self.upLimit),
      yaw = squapi_berp(self.stiffness, self.bounce, -180, 180)
    }
    self.legacyMotion[index] = { pitch = 0, pitchVelocity = 0, yaw = 0, yawVelocity = 0 }
  end
  function self:target(index)
    local velocity = squapi_relative_velocity()
    local time = minecraft.world.time()
    local pitch = math.sin(time * self.idleYSpeed / 10 - index * self.offsetBetweenSegments + self.initialMovementOffset) * self.idleYMovement
    local yaw = math.sin(time * self.idleXSpeed / 10 - index * self.offsetBetweenSegments) * self.idleXMovement * (tonumber(squapi.wagStrength) or 1)
    if self.legacy then
      -- Legacy tails never used side velocity (the original line was commented
      -- out), and have their own force constants.
      pitch = pitch + velocity.vertical * 20 * self.bendStrength - velocity.forward * self.bendStrength * 50 * self.velocityPush
      yaw = yaw + self.bodyTurn * self.bendStrength * 0.5
    else
      pitch = pitch + velocity.vertical * 15 * self.bendStrength - velocity.forward * self.bendStrength * 15 * self.velocityPush
      yaw = yaw + self.bodyTurn * self.bendStrength + velocity.side * self.bendStrength * 40
    end
    if index == 1 and (minecraft.player.fall_flying() or minecraft.player.swimming()) then pitch = self.flyingOffset end
    return squapi_clamp(pitch, self.downLimit, self.upLimit), yaw
  end
  function self:tick()
    if not self.enabled then return self end
    local body_yaw = minecraft.player.body_yaw()
    self.bodyTurn = squapi_clamp(squapi_angle_delta(body_yaw, self.lastBodyYaw), -20, 20)
    self.lastBodyYaw = body_yaw
    if minecraft.player.pose() ~= "SLEEPING" then
      for index, element in ipairs(self.parts) do
        local pitch, yaw = self:target(index)
        if self.legacy then
          -- SquAPI 0.x used bounceObject/bouncetowards.  Its small bounce
          -- values mean "soft correction", unlike BERP's retention value.
          local motion = self.legacyMotion[index]
          motion.pitch, motion.pitchVelocity = squapi.bouncetowards(motion.pitch, pitch, motion.pitchVelocity, self.stiffness, self.bounce)
          motion.yaw, motion.yawVelocity = squapi.bouncetowards(motion.yaw, yaw, motion.yawVelocity, self.stiffness, self.bounce)
          rig_part(element):rot_add(motion.pitch, motion.yaw, 0)
        else
          local spring = self.berps[index]
          rig_part(element):rot_add(spring.pitch:step(pitch), spring.yaw:step(yaw), 0)
        end
      end
    end
    return self
  end
  function self:reset()
    for index, element in ipairs(self.parts) do
      self.berps[index].pitch:reset(); self.berps[index].yaw:reset()
      self.legacyMotion[index].pitch, self.legacyMotion[index].pitchVelocity = 0, 0
      self.legacyMotion[index].yaw, self.legacyMotion[index].yawVelocity = 0, 0
      rig_part(element):rot_add(0, 0, 0)
    end
    return self
  end
  function self:enable() self.enabled = true; return self end
  function self:disable() self.enabled = false; return self:reset() end
  return squapi_track(squapi.tails, self)
end

squapi.ears = {}
squapi.ear = {}
function squapi.ear:new(left, right, range, horizontal, bend_strength, do_flick, flick_chance, stiffness, bounce)
  local self = squapi_controller({
    parts = squapi_parts(left, right), rangeMultiplier = tonumber(range) or 1, horizontalEars = horizontal == true,
    bendStrength = tonumber(bend_strength) or 2, doEarFlick = do_flick ~= false, earFlickChance = math.max(1, math.floor(tonumber(flick_chance) or 400)),
    earStiffness = tonumber(stiffness) or 0.1, earBounce = tonumber(bounce) or 0.8,
    previousPose = minecraft.player.pose(), seed = #squapi.ears * 97 + 13
  })
  if self.horizontalEars then self.rangeMultiplier = self.rangeMultiplier / 2 end
  -- The original API treats left/right ears as sibling controllers.  They must
  -- not form a rig.chain: a nested chain makes the right ear inherit left-ear
  -- rotation and breaks asymmetric rabbit/elf ear models.
  for _, element in ipairs(self.parts) do rig_part(element):vanilla_parent("HEAD", "position") end
  self.leftPitch = squapi_berp(self.earStiffness, self.earBounce, -90, 90)
  self.rightPitch = squapi_berp(self.earStiffness, self.earBounce, -90, 90)
  self.leftYaw = squapi_berp(self.earStiffness, self.earBounce, -90, 90)
  self.rightYaw = squapi_berp(self.earStiffness, self.earBounce, -90, 90)
  self.legacyPitch = squapi.bounceObject:new()
  self.legacyLeftYaw = squapi.bounceObject:new()
  self.legacyRightYaw = squapi.bounceObject:new()
  function self:tick()
    if not self.enabled then return self end
    local velocity = squapi_relative_velocity()
    local head = squapi_head_rotation()
    local forward = squapi_clamp(velocity.forward, -0.75, 0.75)
    local vertical = squapi_clamp(velocity.vertical, -1.5, 1.5) * 5
    local side = squapi_clamp(velocity.side, -0.5, 0.5)
    if self.legacy then forward, vertical, side = velocity.forward, velocity.vertical, velocity.side end
    local bend = head.x < -22.5 and -self.bendStrength or self.bendStrength
    local pose = minecraft.player.pose()
    local crouch_impulse = self.legacy and 3 or 5
    if pose == "CROUCHING" and self.previousPose == "STANDING" then
      if self.legacy then
        self.legacyPitch.vel = self.legacyPitch.vel + crouch_impulse * self.bendStrength
      else
        self.leftPitch.vel = self.leftPitch.vel + crouch_impulse * self.bendStrength
        self.rightPitch.vel = self.rightPitch.vel + crouch_impulse * self.bendStrength
      end
    elseif pose == "STANDING" and self.previousPose == "CROUCHING" then
      if self.legacy then
        self.legacyPitch.vel = self.legacyPitch.vel - crouch_impulse * self.bendStrength
      else
        self.leftPitch.vel = self.leftPitch.vel - crouch_impulse * self.bendStrength
        self.rightPitch.vel = self.rightPitch.vel - crouch_impulse * self.bendStrength
      end
    end
    self.previousPose = pose
    if self.legacy then
      if self.horizontalEars then
        local movement = vertical * bend + forward * bend * 15
        self.legacyLeftYaw.vel = self.legacyLeftYaw.vel + movement
        self.legacyRightYaw.vel = self.legacyRightYaw.vel - movement
      else
        local movement = vertical * bend + forward * bend * 15
        self.legacyPitch.vel = self.legacyPitch.vel + movement
      end
    end
    if self.doEarFlick and (minecraft.world.time() + self.seed) % self.earFlickChance == 0 then
      if (math.floor(minecraft.world.time() / self.earFlickChance) + self.seed) % 2 == 0 then
        if self.legacy then self.legacyLeftYaw.vel = self.legacyLeftYaw.vel + 50 else self.leftYaw.vel = self.leftYaw.vel + 50 end
      else
        if self.legacy then self.legacyRightYaw.vel = self.legacyRightYaw.vel - 50 else self.rightYaw.vel = self.rightYaw.vel - 50 end
      end
    end
    local left_pitch, right_pitch, left_yaw, right_yaw
    if self.horizontalEars then
      if self.legacy then
        local leg_bounce = squapi.doBounce and math.abs(avatar.vanilla("LEFT_LEG"):rotation().x) / 8 * self.bendStrength or 0
        local pitch = head.x * self.rangeMultiplier - leg_bounce
        local yaw = head.y * self.rangeMultiplier - side * 150 * self.bendStrength
        left_pitch = self.legacyPitch:doBounce(pitch, self.earStiffness, self.earBounce) / 4
        right_pitch = left_pitch
        left_yaw = self.legacyLeftYaw:doBounce(yaw, self.earStiffness, self.earBounce)
        right_yaw = self.legacyRightYaw:doBounce(yaw, self.earStiffness, self.earBounce)
      else
        local rotation = 10 * bend * (vertical + forward * 10) + head.x * self.rangeMultiplier
        local yaw = head.y * self.rangeMultiplier
        left_pitch = self.leftPitch:step(0) / 4
        right_pitch = self.rightPitch:step(0) / 4
        left_yaw = self.leftYaw:step(rotation + yaw)
        right_yaw = self.rightYaw:step(-rotation + yaw)
      end
      if self.parts[1] ~= nil then rig_part(self.parts[1]):rot_add(left_pitch, left_yaw / 3, left_yaw / 4) end
      if self.parts[2] ~= nil then rig_part(self.parts[2]):rot_add(right_pitch, right_yaw / 3, right_yaw / 4) end
    else
      local leg_bounce = squapi.doBounce and math.abs(avatar.vanilla("LEFT_LEG"):rotation().x) / 8 * self.bendStrength or 0
      local pitch = self.legacy and (head.x * self.rangeMultiplier - leg_bounce) or (head.x * self.rangeMultiplier + 2 * bend * (vertical + forward * 15) - leg_bounce)
      local yaw = head.y * self.rangeMultiplier - side * (self.legacy and 150 or 100) * self.bendStrength
      if self.legacy then
        left_pitch = self.legacyPitch:doBounce(pitch, self.earStiffness, self.earBounce)
        right_pitch = left_pitch
        left_yaw = self.legacyLeftYaw:doBounce(yaw, self.earStiffness, self.earBounce)
        right_yaw = self.legacyRightYaw:doBounce(yaw, self.earStiffness, self.earBounce)
      else
        left_pitch = self.leftPitch:step(pitch)
        right_pitch = self.rightPitch:step(pitch)
        left_yaw = self.leftYaw:step(yaw)
        right_yaw = self.rightYaw:step(yaw)
      end
      if self.parts[1] ~= nil then rig_part(self.parts[1]):rot_add(left_pitch, left_yaw / 4, left_yaw / 4) end
      if self.parts[2] ~= nil then rig_part(self.parts[2]):rot_add(right_pitch, right_yaw / 4, right_yaw / 4) end
    end
    return self
  end
  function self:reset()
    self.leftPitch:reset(); self.rightPitch:reset(); self.leftYaw:reset(); self.rightYaw:reset()
    squapi_reset_bounce(self.legacyPitch); squapi_reset_bounce(self.legacyLeftYaw); squapi_reset_bounce(self.legacyRightYaw)
    for _, element in ipairs(self.parts) do rig_part(element):rot_add(0, 0, 0) end
    return self
  end
  function self:enable() self.enabled = true; return self end
  function self:disable() self.enabled = false; return self:reset() end
  return squapi_track(squapi.ears, self)
end
setmetatable(squapi.ear, { __call = function(_, left, right, do_flick, flick_chance, range, horizontal, bend, stiffness, bounce)
  local legacy_range = tonumber(range) or 1
  if horizontal == true then legacy_range = legacy_range * 2 end
  local legacy = squapi.ear:new(
    left, right,
    legacy_range, horizontal == true,
    tonumber(bend) or 2, do_flick, tonumber(flick_chance) or 400,
    tonumber(stiffness) or 0.025, tonumber(bounce) or 0.1
  )
  legacy.legacy = true
  return legacy
end })

squapi.randimations = {}
squapi.randimation = {}
function squapi.randimation:new(animation, chance_range, is_blink)
  local self = squapi_controller({ animation = squapi_animation(animation), chanceRange = math.max(0, math.floor(tonumber(chance_range) or 200)), isBlink = is_blink == true, seed = #squapi.randimations * 73 + 31 })
  function self:tick()
    if not self.enabled or (self.isBlink and (minecraft.player.sleeping() or squapi.doBlink == false)) then return self end
    local interval = self.chanceRange + 1
    if (minecraft.world.time() + self.seed) % interval == 0 and not squapi_animation_playing(self.animation) then squapi_animation_play(self.animation) end
    return self
  end
  return squapi_track(squapi.randimations, self)
end
setmetatable(squapi.randimation, { __call = function(_, animation, chance, blink) return squapi.randimation:new(animation, chance, blink) end })
function squapi.blink(animation, chance_multiplier)
  return squapi.randimation:new(animation, (tonumber(chance_multiplier) or 1) * 200, true)
end

squapi.eyes = {}
squapi.eye = {}
function squapi.eye:new(element, left_distance, right_distance, up_distance, down_distance, switch_values)
  local self = squapi_controller({
    element = rig_part(element), left = tonumber(left_distance) or 0.25, right = tonumber(right_distance) or 1.25,
    up = tonumber(up_distance) or 0.5, down = tonumber(down_distance) or 0.5, switchValues = switch_values == true, eyeScale = 1
  })
  function self:setEyeScale(scale) self.eyeScale = tonumber(scale) or 1; return self end
  function self:zero() self.element:pos(0, 0, 0); return self end
  function self:tick()
    if not self.enabled then return self end
    local head = squapi_head_rotation()
    local x = -squapi.parabolagraph(-50, -self.left, 0, 0, 50, self.right, squapi_clamp(head.y, -50, 50))
    local y = squapi.parabolagraph(-90, -self.down, 0, 0, 90, self.up, head.x)
    x, y = squapi_clamp(x, -self.right, self.left), squapi_clamp(y, -self.down, self.up)
    if self.switchValues then self.element:pos(0, y, -x) else self.element:pos(x, y, 0) end
    local scale = self.eyeScale * (tonumber(squapi.eyeScale) or 1)
    self.element:scale(scale, scale, scale)
    return self
  end
  return squapi_track(squapi.eyes, self)
end
setmetatable(squapi.eye, { __call = function(_, ...) return squapi.eye:new(...) end })

-- World-relative floating companions. Figura's modern HoverPoint and legacy
-- floatPoint both depend on a WORLD model parent. Shyne keeps the renderer
-- deliberately attachment-based, so this shim projects the simulated world
-- position back into the player's local model space each tick. Put the target
-- element in a top-level Blockbench group (not under an animated limb) for a
-- predictable result; this avoids inheriting an unrelated parent-bone transform.
local function squapi_body_space_offset(offset, rotate_with_player)
  offset = rig_vector(offset, vector.zero())
  if rotate_with_player == false then return offset end
  local yaw = math.rad(minecraft.player.body_yaw() + 180)
  local sine, cosine = math.sin(yaw), math.cos(yaw)
  return vector.new(
    cosine * offset.x - sine * offset.z,
    offset.y,
    sine * offset.x + cosine * offset.z
  )
end

local function squapi_model_space_offset(world_offset)
  world_offset = rig_vector(world_offset, vector.zero())
  local yaw = math.rad(minecraft.player.body_yaw() + 180)
  local sine, cosine = math.sin(yaw), math.cos(yaw)
  -- Inverse of squapi_body_space_offset: the player model itself already
  -- rotates with body yaw, so this preserves a point in world space.
  return vector.new(
    cosine * world_offset.x + sine * world_offset.z,
    world_offset.y,
    -sine * world_offset.x + cosine * world_offset.z
  )
end

local function squapi_apply_world_point(element, world_position, rotate_with_player)
  local relative = squapi_model_space_offset(vector.sub(world_position, minecraft.player.position()))
  element:pos(vector.mul(relative, 16))
  -- A point that does not rotate with the player needs its local yaw cancelled.
  -- `rot_add` is absolute for the current tick and therefore still composes
  -- with imported Blockbench animation rather than accumulating every frame.
  element:rot_add(0, rotate_with_player == false and -minecraft.player.body_yaw() or 0, 0)
  return relative
end

local function squapi_collide_point(controller, proposed)
  local delta = vector.sub(proposed, controller.pos)
  local distance = vector.length(delta)
  if distance <= 0.00001 or controller.doCollisions ~= true then return proposed, false end
  local direction = vector.div(delta, distance)
  local hit = minecraft.world.probe(controller.pos, direction, distance, controller.collisionRadius or 0.125)
  if not hit.hit then return proposed, false end

  -- `world.probe` returns the travelled distance for both blocks and entities.
  -- Using it rather than entity position prevents a visual companion from
  -- snapping to the centre of a large mob's hitbox.
  local padding = math.max(0.001, tonumber(controller.collisionPadding) or 0.02)
  local travelled = math.max(0, math.min(distance, (tonumber(hit.distance) or distance) - padding))
  local position = vector.add(controller.pos, vector.mul(direction, travelled))
  local normal = rig_vector(hit.normal, vector.mul(direction, -1))
  if vector.length(normal) <= 0.00001 then normal = vector.mul(direction, -1) else normal = vector.normalize(normal) end
  local normal_velocity = vector.dot(controller.vel, normal)
  if normal_velocity < 0 then
    local restitution = squapi_clamp(tonumber(controller.collisionBounce) or 0.35, 0, 1)
    controller.vel = vector.sub(controller.vel, vector.mul(normal, normal_velocity * (1 + restitution)))
  end
  return position, true
end

-- Modern SquAPI hoverPoint. It is entirely native: integration runs in the
-- Shyne Lua host and collision is the bounded visual block/entity sweep behind
-- minecraft.world.probe(). It never changes server physics or player movement.
squapi.hoverPoints, squapi.hoverPoint = {}, {}
function squapi.hoverPoint:new(element, element_offset, spring_strength, mass, resistance, rotation_speed, rotate_with_player, do_collisions)
  local self = squapi_controller({
    element = rig_part(element), elementOffset = rig_vector(element_offset, vector.zero()),
    springStrength = tonumber(spring_strength) or 0.2, mass = math.max(0.001, tonumber(mass) or 5),
    resistance = math.max(0, tonumber(resistance) or 1), rotationSpeed = tonumber(rotation_speed) or 0.05,
    rotateWithPlayer = rotate_with_player ~= false, doCollisions = do_collisions == true,
    collisionRadius = 0.125, collisionPadding = 0.02, collisionBounce = 0.35,
    pos = vector.zero(), vel = vector.zero(), velocity = vector.zero(), init = true, delay = 0
  })
  if self.element == nil then error("squapi.hoverPoint requires a model part", 2) end

  function self:target()
    return vector.add(minecraft.player.position(), squapi_body_space_offset(self.elementOffset, self.rotateWithPlayer))
  end
  function self:setOffset(value) self.elementOffset = rig_vector(value, vector.zero()); return self end
  function self:setCollisions(value) self.doCollisions = value == true; return self end
  function self:setCollisionRadius(value) self.collisionRadius = squapi_clamp(tonumber(value) or 0.125, 0, 2); return self end
  function self:reset()
    self.pos, self.vel, self.velocity, self.init = self:target(), vector.zero(), vector.zero(), false
    squapi_apply_world_point(self.element, self.pos, self.rotateWithPlayer)
    return self
  end
  function self:tick()
    if not self.enabled then return self end
    local target = self:target()
    if self.init then self.pos, self.vel, self.velocity, self.init = target, vector.zero(), vector.zero(), false end
    local force = vector.sub(vector.mul(vector.sub(target, self.pos), self.springStrength), vector.mul(self.vel, self.resistance))
    self.vel = vector.add(self.vel, vector.div(force, self.mass))
    local position, collided = squapi_collide_point(self, vector.add(self.pos, self.vel))
    self.pos, self.delay = position, collided and 2 or 0
    self.velocity = self.vel
    squapi_apply_world_point(self.element, self.pos, self.rotateWithPlayer)
    return self
  end
  return squapi_track(squapi.hoverPoints, self)
end
setmetatable(squapi.hoverPoint, { __call = function(_, ...) return squapi.hoverPoint:new(...) end })

-- Legacy SquAPI floatPoint uses pixel offsets and its older scalar
-- bounceObject integrator. Keep that contract separate from hoverPoint's
-- world-unit spring parameters so older aquatic scripts retain their tuning.
squapi.floatPoints = {}
function squapi.floatPoint(element, x_offset, y_offset, z_offset, stiffness, bouncy, y_minimum, max_radius)
  local self = squapi_controller({
    element = rig_part(element), xOffset = tonumber(x_offset) or 0, yOffset = tonumber(y_offset) or 0, zOffset = tonumber(z_offset) or 0,
    stiffness = tonumber(stiffness) or 0.02, bouncy = tonumber(bouncy) or 0.0005,
    yMinimum = tonumber(y_minimum) or 30, maxRadius = max_radius == nil and nil or tonumber(max_radius),
    points = { squapi.bounceObject:new(), squapi.bounceObject:new(), squapi.bounceObject:new(), squapi.bounceObject:new() }, init = true
  })
  if self.element == nil then error("squapi.floatPoint requires a model part", 2) end

  function self:reset()
    local player = minecraft.player.position()
    self.points[1].pos, self.points[1].position = player.x * 16 + self.xOffset, player.x * 16 + self.xOffset
    self.points[2].pos, self.points[2].position = player.y * 16 + self.yOffset, player.y * 16 + self.yOffset
    self.points[3].pos, self.points[3].position = player.z * 16 + self.zOffset, player.z * 16 + self.zOffset
    self.points[4].pos, self.points[4].position = -minecraft.player.body_yaw() - 180, -minecraft.player.body_yaw() - 180
    for _, point in ipairs(self.points) do point.vel, point.velocity = 0, 0 end
    self.init = false
    return self
  end
  function self:tick()
    if not self.enabled or squapi.floatPointEnabled == false then return self end
    if self.init then self:reset() end
    local player = minecraft.player.position()
    local target_x, target_y, target_z = player.x * 16, player.y * 16, player.z * 16
    local stiff, bounce = self.stiffness, self.bouncy
    if self.points[2].pos - target_y < -self.yMinimum then
      stiff, bounce = 0.035, 0.01
    elseif self.maxRadius ~= nil and (
      math.abs(self.points[1].pos - target_x) > self.maxRadius or math.abs(self.points[2].pos - target_y) > self.maxRadius or math.abs(self.points[3].pos - target_z) > self.maxRadius
    ) then
      stiff, bounce = stiff * 0.57, bounce * 400
    end
    -- Legacy FloatPoint passed the two scalar controls in this order. Preserve
    -- it exactly instead of reusing hoverPoint's modern spring constants.
    local x = self.points[1]:doBounce(target_x, bounce, stiff) + self.xOffset
    local y = self.points[2]:doBounce(target_y, bounce, stiff) + self.yOffset
    local z = self.points[3]:doBounce(target_z, bounce, stiff) + self.zOffset
    self.points[4]:doBounce(-minecraft.player.body_yaw() - 180, 0.0005, 0.03)
    squapi_apply_world_point(self.element, vector.new(x / 16, y / 16, z / 16), false)
    return self
  end
  return squapi_track(squapi.floatPoints, self)
end

local function squapi_limb(collection, element, strength, is_right, keep_position, vanilla_part)
  local self = squapi_controller({ element = rig_part(element), strength = tonumber(strength) or 1, isRight = is_right == true, keepPosition = keep_position ~= false, vanillaPart = vanilla_part, frozen = false, rot = vector.zero(), pos = vector.zero() })
  -- Parent position and scaled local rotation are separate so a 0.5 strength
  -- limb actually moves half as far instead of receiving a full vanilla pose.
  if self.keepPosition then self.element:vanilla_parent(vanilla_part, "position") else self.element:vanilla_parent("", "full") end
  function self:getVanilla()
    local vanilla = avatar.vanilla(self.vanillaPart)
    self.rot, self.pos = vanilla:rotation(), vanilla:position()
    return self.rot, self.pos
  end
  function self:getRot() return self.rot end
  function self:getPos() return self.pos end
  function self:freeze() self.frozen = true; return self end
  function self:unfreeze() self.frozen = false; return self end
  function self:tick()
    if self.enabled and not self.frozen then
      local rotation = self:getVanilla()
      self.element:rot_add(vector.mul(rotation, self.strength))
    end
    return self
  end
  function self:reset() self.element:rot_add(0, 0, 0); return self end
  return squapi_track(collection, self)
end

squapi.legs, squapi.leg = {}, {}
function squapi.leg:new(element, strength, is_right, keep_position)
  return squapi_limb(squapi.legs, element, strength, is_right, keep_position, is_right and "RIGHT_LEG" or "LEFT_LEG")
end
setmetatable(squapi.leg, { __call = function(_, ...) return squapi.leg:new(...) end })

squapi.arms, squapi.arm = {}, {}
function squapi.arm:new(element, strength, is_right, keep_position)
  return squapi_limb(squapi.arms, element, strength, is_right, keep_position, is_right and "RIGHT_ARM" or "LEFT_ARM")
end
setmetatable(squapi.arm, { __call = function(_, ...) return squapi.arm:new(...) end })

squapi.smoothHeads = {}
squapi.smoothHead = {}
function squapi.smoothHead:new(elements, strength, tilt, speed, keep_original_position, _fix_portrait)
  local parts = squapi_parts(elements)
  local self = squapi_controller({ elements = parts, tilt = tonumber(tilt) or 0.1, speed = tonumber(speed) or 1, keepOriginalHeadPos = keep_original_position ~= false, offset = vector.zero(), rotations = {}, ignoreCancelHeadMovement = false, publishTorsoOffset = false })
  self.strength = type(strength) == "table" and strength or {}
  if type(strength) == "number" then
    for index = 1, #parts do self.strength[index] = strength / math.max(1, #parts) end
  else
    for index = 1, #parts do self.strength[index] = tonumber(self.strength[index]) or (1 / math.max(1, #parts)) end
  end
  self.positionIndex = type(keep_original_position) == "number" and math.max(1, math.min(#parts, math.floor(keep_original_position))) or #parts
  for index, element in ipairs(parts) do
    local part = rig_part(element)
    -- SquAPI moves only the final (or explicitly indexed) head segment when
    -- keepOriginalHeadPos is enabled.  Positioning every nested segment would
    -- apply the crouch/head offset twice to body->head rigs such as Mothi.
    if self.keepOriginalHeadPos and index == self.positionIndex then part:vanilla_parent("HEAD", "position") else part:vanilla_parent("", "full") end
    self.rotations[index] = vector.zero()
  end
  function self:setOffset(x, y, z)
    self.offset = y == nil and rig_vector(x, vector.zero()) or vector.new(x, y, z)
    return self
  end
  function self:zero()
    for index, element in ipairs(self.elements) do
      self.rotations[index] = vector.zero()
      rig_part(element):rot_add(0, 0, 0):pos(0, 0, 0)
    end
    return self
  end
  function self:tick()
    if not self.enabled then return self end
    local head = squapi_head_rotation()
    head = vector.add(head, rig_vector(squapi.smoothHeadOffset, vector.zero()))
    local offset = self.offset
    local torso_offset = (not self.ignoreCancelHeadMovement and squapi.cancelHeadMovement) and rig_vector(squapi.torsoOffset, vector.zero()) or vector.zero()
    local rate = squapi_clamp(self.speed / 2, 0, 1)
    for index, element in ipairs(self.elements) do
      local target = vector.mul(head, self.strength[index])
      target.z = target.y * self.tilt - (offset.z or 0) / math.max(1, #self.elements)
      target.x = target.x - (offset.x or 0) / math.max(1, #self.elements)
      target.y = target.y - (offset.y or 0) / math.max(1, #self.elements)
      target = vector.sub(target, vector.mul(torso_offset, self.strength[index]))
      self.rotations[index] = vector.add(self.rotations[index], vector.mul(vector.sub(target, self.rotations[index]), rate))
      rig_part(element):visible(not minecraft.client.first_person()):rot_add(self.rotations[index])
    end
    if self.publishTorsoOffset then squapi.torsoOffset = vector.new(self.rotations[1] or vector.zero()) end
    return self
  end
  return squapi_track(squapi.smoothHeads, self)
end
setmetatable(squapi.smoothHead, { __call = function(_, element, tilt, strength, keep_position)
  return squapi.smoothHead:new(element, strength, tilt, 1, keep_position)
end })

function squapi.smoothTorso(element, strength, tilt)
  local torso = squapi.smoothHead:new(element, tonumber(strength) or 0.5, tonumber(tilt) or 0.4, 0.5, false)
  torso.ignoreCancelHeadMovement = true
  torso.publishTorsoOffset = true
  squapi.cancelHeadMovement = true
  local reset = torso.zero
  function torso:zero()
    reset(self)
    squapi.torsoOffset = vector.zero()
    return self
  end
  return torso
end

function squapi.smoothHeadNeck(head, neck, tilt, strength, keep_position)
  local multiplier = tonumber(strength) or 1
  local controller = squapi.smoothHead:new({ head, neck }, { multiplier * 0.6, multiplier * 0.4 }, (tonumber(tilt) or 2.5) / 5, 1, keep_position)
  controller.ignoreCancelHeadMovement = true
  return controller
end

squapi.bewbs, squapi.bewb = {}, {}
function squapi.bewb:new(element, bendability, stiffness, bounce, do_idle, idle_strength, idle_speed, down_limit, up_limit)
  local self = squapi_controller({ element = rig_part(element), bendability = tonumber(bendability) or 2, doIdle = do_idle ~= false, idleStrength = tonumber(idle_strength) or 4, idleSpeed = tonumber(idle_speed) or 1, previousPose = minecraft.player.pose(), target = 0, stiffness = tonumber(stiffness) or 0.05, bounce = tonumber(bounce) or 0.9 })
  self.berp = squapi_berp(self.stiffness, self.bounce, tonumber(down_limit) or -10, tonumber(up_limit) or 25)
  self.legacyBounce = squapi.bounceObject:new()
  function self:tick()
    if self.enabled then
      local velocity = squapi_relative_velocity()
      self.target = self.doIdle and math.sin(minecraft.world.time() / 8 * self.idleSpeed) * (self.legacy and self.bendability * 2 or self.idleStrength) or 0
      local pose = minecraft.player.pose()
      if pose == "CROUCHING" and self.previousPose == "STANDING" then
        if self.legacy then self.legacyBounce.vel = self.legacyBounce.vel + self.bendability else self.berp.vel = self.berp.vel + self.bendability end
      elseif pose == "STANDING" and self.previousPose == "CROUCHING" then
        if self.legacy then self.legacyBounce.vel = self.legacyBounce.vel - self.bendability else self.berp.vel = self.berp.vel - self.bendability end
      end
      self.previousPose = pose
      if self.legacy then
        if self.legacyBounce.pos < 25 and self.legacyBounce.pos > -30 then
          self.legacyBounce.vel = self.legacyBounce.vel - velocity.vertical / 2 * self.bendability - velocity.forward / 3 * self.bendability
        end
      else
        self.berp.vel = self.berp.vel - velocity.vertical * self.bendability - velocity.forward * self.bendability
      end
    else
      self.target = 0
    end
    local rotation = self.legacy and self.legacyBounce:doBounce(self.target, self.stiffness, self.bounce) or self.berp:step(self.target)
    self.element:rot_add(rotation, 0, 0)
    return self
  end
  function self:reset() self.berp:reset(); squapi_reset_bounce(self.legacyBounce); self.element:rot_add(0, 0, 0); return self end
  return squapi_track(squapi.bewbs, self)
end
-- The pre-1.0 helper was a callable `bewb(element, doIdle, bendability,
-- stiff, bounce)`, while current SquAPI uses `bewb:new(element, bendability,
-- stiff, bounce, doIdle, ...)`.  Preserve both instead of silently swapping
-- an old pack's idle flag and stiffness.
setmetatable(squapi.bewb, { __call = function(_, element, do_idle, bendability, stiffness, bounce)
  local legacy = squapi.bewb:new(element, tonumber(bendability) or 2, tonumber(stiffness) or 0.025, tonumber(bounce) or 0.06, do_idle)
  legacy.legacy = true
  return legacy
end })

squapi.bounceWalks, squapi.bounceWalk = {}, {}
function squapi.bounceWalk:new(element, multiplier)
  local self = squapi_controller({ element = rig_part(element), bounceMultiplier = tonumber(multiplier) or 1 })
  function self:tick()
    if not self.enabled then return self end
    local rotation = avatar.vanilla("LEFT_LEG"):rotation()
    local amount = minecraft.player.on_ground() and math.abs(rotation.x) / 40 * self.bounceMultiplier or 0
    if minecraft.player.crouching() then amount = amount / 2 end
    self.element:pos(0, amount, 0)
    return self
  end
  return squapi_track(squapi.bounceWalks, self)
end
setmetatable(squapi.bounceWalk, { __call = function(_, ...) return squapi.bounceWalk:new(...) end })
function squapi.bouncewalk(element, multiplier) return squapi.bounceWalk:new(element, multiplier) end

squapi.taurs, squapi.taur = {}, {}
function squapi.taur:new(body, front_legs, back_legs)
  local self = squapi_controller({ body = rig_part(body), frontLegs = front_legs and rig_part(front_legs) or nil, backLegs = back_legs and rig_part(back_legs) or nil, bounce = squapi.bounceObject:new() })
  function self:tick()
    if not self.enabled then return self end
    local flying = minecraft.player.fall_flying() or minecraft.player.swimming()
    local angle = flying and 80 or self.bounce:doBounce(squapi_clamp(minecraft.player.velocity().y * 40, -30, 45), 0.01, 0.2)
    self.body:rot_add(angle, 0, 0)
    if self.backLegs ~= nil then self.backLegs:rot_add(flying and -50 or angle * 1.5, 0, 0) end
    if self.frontLegs ~= nil then self.frontLegs:rot_add(flying and -50 or -angle * 3.5, 0, 0) end
    return self
  end
  return squapi_track(squapi.taurs, self)
end
setmetatable(squapi.taur, { __call = function(_, ...) return squapi.taur:new(...) end })
function squapi.taurPhysics(body, front_legs, back_legs) return squapi.taur:new(body, front_legs, back_legs) end

squapi.FPHands, squapi.FPHand = {}, {}
function squapi.FPHand:new(element, x, y, z, scale, only_visible_in_first_person)
  local self = squapi_controller({ element = rig_part(element), x = tonumber(x) or 0, y = tonumber(y) or 0, z = tonumber(z) or 0, scale = tonumber(scale) or 1, onlyVisibleInFP = only_visible_in_first_person == true })
  self.element:vanilla_parent("RIGHT_ARM", "full")
  function self:updatePos(next_x, next_y, next_z) self.x, self.y, self.z = tonumber(next_x) or 0, tonumber(next_y) or 0, tonumber(next_z) or 0; return self end
  function self:tick()
    local first_person = minecraft.client.first_person()
    if self.onlyVisibleInFP then self.element:visible(first_person) end
    if first_person then self.element:pos(self.x, self.y, self.z); self.element:scale(self.scale, self.scale, self.scale) else self.element:pos(0, 0, 0); self.element:scale(1, 1, 1) end
    return self
  end
  return squapi_track(squapi.FPHands, self)
end
setmetatable(squapi.FPHand, { __call = function(_, ...) return squapi.FPHand:new(...) end })
function squapi.setFirstPersonHandPos(...) return squapi.FPHand:new(...) end

-- Basic locomotion and pose animation helpers use Shyne's real animation
-- controller.  They deliberately transition only on state changes so they do
-- not restart an imported animation every tick.
function squapi.walk(walk_animation, run_animation)
  local self = squapi_controller({ walk = squapi_animation(walk_animation), run = squapi_animation(run_animation), active = "" })
  function self:tick()
    if not self.enabled then return self end
    local moving = minecraft.player.velocity():length() > 0.03
    local next_state = moving and minecraft.player.sprinting() and self.run ~= nil and "run" or (moving and "walk" or "")
    if next_state == self.active then return self end
    if self.active == "walk" then squapi_animation_stop(self.walk) elseif self.active == "run" then squapi_animation_stop(self.run) end
    self.active = next_state
    if next_state == "walk" then squapi_animation_play(self.walk) elseif next_state == "run" then squapi_animation_play(self.run) end
    return self
  end
  return squapi_track(nil, self)
end

function squapi.crouch(crouch_animation, uncrouch_animation, crawl_animation, uncrawl_animation)
  local self = squapi_controller({ crouch = squapi_animation(crouch_animation), uncrouch = squapi_animation(uncrouch_animation), crawl = squapi_animation(crawl_animation), uncrawl = squapi_animation(uncrawl_animation), previous = minecraft.player.pose() })
  function self:tick()
    if not self.enabled then return self end
    local pose = minecraft.player.pose()
    local crouching = pose == "CROUCHING"
    local crawling = pose == "SWIMMING" and not minecraft.player.in_water()
    if crouching then squapi_animation_stop(self.uncrouch); squapi_animation_play(self.crouch)
    elseif self.previous == "CROUCHING" then squapi_animation_stop(self.crouch); squapi_animation_play(self.uncrouch) end
    if crawling then squapi_animation_stop(self.uncrawl); squapi_animation_play(self.crawl)
    elseif self.previous == "CRAWLING" then squapi_animation_stop(self.crawl); squapi_animation_play(self.uncrawl) end
    self.previous = crawling and "CRAWLING" or pose
    return self
  end
  return squapi_track(nil, self)
end

-- Both names appear in released packs. `SquAPI` is what require("SquAPI") returns.
SquAPI = squapi

-- Compatibility controllers are manual rig controllers.  This single native
-- tick honours SquAPI.autoFunctionUpdates and lets advanced packs call their
-- own controller:tick() when they want full timing control.
table.insert(events._internal.tick, function()
  if not squapi.autoFunctionUpdates then return end
  for _, controller in ipairs(squapi._controllers) do
    local ok, message = pcall(function() controller:tick() end)
    if not ok then _shyne_report_error("squapi", "controller", tostring(message)) end
  end
end)
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() squapi._controllers = {} end)

-- Cosmetic attachment; the parent transform is applied before local animation.
function rig.attach(parts, parent, options)
  options = options or {}
  for _, part in ipairs(parts or {}) do rig_part(part):vanilla_parent(parent, options.mode or "full") end
  return parts
end

-- Cosmetic armor visibility driven by actual equipped armor slots. The arm/leg
-- aliases deliberately read chest/legs while attaching the visual to that limb.
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
    -- `feet` remains a concise compatibility alias.  Use left_foot/right_foot
    -- when each boot model needs the correct animated vanilla leg.
    { key = "feet", slot = "feet", parent = "LEFT_LEG" },
    { key = "left_foot", slot = "feet", parent = "LEFT_LEG" },
    { key = "right_foot", slot = "feet", parent = "RIGHT_LEG" }
  }
  local controller = { bindings = {}, enabled = true }
  for _, default in ipairs(defaults) do
    local spec = options[default.key]
    if spec ~= nil then
      -- A plain array is shorthand for { parts = array }.  A configuration
      -- table may intentionally contain only variants, so do not discard its
      -- metadata merely because `parts` is absent.
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
      -- `default` is a fallback, not an extra material layer.  Explicit
      -- matches can still stack (for example a diamond base plus trim overlay).
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

-- Custom Elytra layer. The model remains an avatar cosmetic, but visibility is
-- tied to the real chest slot and flight state instead of a manual toggle.
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

-- Internal handlers run before the avatar's public tick callbacks, giving its
-- script a stable physics result to inspect or override during the same tick.
events._internal.tick = events._internal.tick or {}
table.insert(events._internal.tick, function()
  for _, controller in ipairs(rig._controllers) do
    local ok, message = pcall(function() controller:update() end)
    if not ok then _shyne_report_error("rig", "controller", tostring(message)) end
  end
end)
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() rig._controllers = {} end)
