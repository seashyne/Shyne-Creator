-- ==============================================================================
-- SquAPI Compatibility: Math & Integrators (compat/squapi/squapi_math.lua)
-- Implements BERP integrators, scalar bounceObject, and interpolation graphs.
-- ==============================================================================

squapi = squapi or {}

local function squapi_clamp(value, minimum, maximum)
  return math.max(minimum, math.min(maximum, value))
end
squapi._clamp = squapi_clamp

local function squapi_angle_delta(current, previous)
  return ((current - previous + 180) % 360) - 180
end
squapi._angle_delta = squapi_angle_delta

local function squapi_parts(first, second)
  local result = {}
  if type(first) == "table" and first.path == nil and first[1] ~= nil then
    for _, value in ipairs(first) do table.insert(result, value) end
  elseif first ~= nil then
    table.insert(result, first)
  end
  if second ~= nil then table.insert(result, second) end
  return result
end
squapi._parts = squapi_parts

local function squapi_head_rotation()
  local rotation = minecraft.player.rotation()
  return vector.new(rotation.x or 0, squapi_angle_delta(rotation.y or 0, minecraft.player.body_yaw()), 0)
end
squapi._head_rotation = squapi_head_rotation

local function squapi_relative_velocity()
  local velocity = minecraft.player.velocity()
  local yaw = math.rad(minecraft.player.body_yaw())
  return {
    forward = -velocity.x * math.sin(yaw) + velocity.z * math.cos(yaw),
    side = velocity.x * math.cos(yaw) + velocity.z * math.sin(yaw),
    vertical = velocity.y
  }
end
squapi._relative_velocity = squapi_relative_velocity

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
squapi._berp = squapi_berp

local function squapi_reset_bounce(object)
  object.pos, object.position, object.vel, object.velocity = 0, 0, 0, 0
  return object
end
squapi._reset_bounce = squapi_reset_bounce

local function squapi_animation(value)
  return type(value) == "string" and model.animation.get(value) or value
end
squapi._animation = squapi_animation

local function squapi_animation_play(animation)
  animation = squapi_animation(animation)
  if animation ~= nil and animation.play ~= nil then animation:play() end
end
squapi._animation_play = squapi_animation_play

local function squapi_animation_stop(animation)
  animation = squapi_animation(animation)
  if animation ~= nil and animation.stop ~= nil then animation:stop() end
end
squapi._animation_stop = squapi_animation_stop

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
squapi._animation_playing = squapi_animation_playing

-- Scalar Bounce Object
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

function squapi.getForwardVel() return squapi_relative_velocity().forward end
function squapi.getSideVelocity() return squapi_relative_velocity().side end
function squapi.yvel() return minecraft.player.velocity().y end
