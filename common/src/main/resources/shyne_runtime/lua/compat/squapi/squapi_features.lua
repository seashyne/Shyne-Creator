-- ==============================================================================
-- SquAPI Compatibility: Features & Expressions (compat/squapi/squapi_features.lua)
-- Implements random blinking, eye pupil tracking, floating companion points,
-- first person custom hand, and locomotion states.
-- ==============================================================================

squapi = squapi or {}

local function body_space_offset(offset, rotate_with_player)
  offset = offset and vector.new(offset) or vector.zero()
  if rotate_with_player == false then return offset end
  local yaw = math.rad(minecraft.player.body_yaw() + 180)
  local sine, cosine = math.sin(yaw), math.cos(yaw)
  return vector.new(
    cosine * offset.x - sine * offset.z,
    offset.y,
    sine * offset.x + cosine * offset.z
  )
end

local function model_space_offset(world_offset)
  world_offset = world_offset and vector.new(world_offset) or vector.zero()
  local yaw = math.rad(minecraft.player.body_yaw() + 180)
  local sine, cosine = math.sin(yaw), math.cos(yaw)
  return vector.new(
    cosine * world_offset.x + sine * world_offset.z,
    world_offset.y,
    -sine * world_offset.x + cosine * world_offset.z
  )
end

local function apply_world_point(element, world_position, rotate_with_player)
  local relative = model_space_offset(vector.sub(world_position, minecraft.player.position()))
  element:pos(vector.mul(relative, 16))
  element:rot_add(0, rotate_with_player == false and -minecraft.player.body_yaw() or 0, 0)
  return relative
end

local function collide_point(controller, proposed)
  local delta = vector.sub(proposed, controller.pos)
  local distance = vector.length(delta)
  if distance <= 0.00001 or controller.doCollisions ~= true then return proposed, false end
  local direction = vector.div(delta, distance)
  local hit = minecraft.world.probe(controller.pos, direction, distance, controller.collisionRadius or 0.125)
  if not hit.hit then return proposed, false end

  local padding = math.max(0.001, tonumber(controller.collisionPadding) or 0.02)
  local travelled = math.max(0, math.min(distance, (tonumber(hit.distance) or distance) - padding))
  local position = vector.add(controller.pos, vector.mul(direction, travelled))
  local normal = hit.normal and vector.new(hit.normal) or vector.mul(direction, -1)
  if vector.length(normal) <= 0.00001 then normal = vector.mul(direction, -1) else normal = vector.normalize(normal) end
  local normal_velocity = vector.dot(controller.vel, normal)
  if normal_velocity < 0 then
    local restitution = squapi._clamp(tonumber(controller.collisionBounce) or 0.35, 0, 1)
    controller.vel = vector.sub(controller.vel, vector.mul(normal, normal_velocity * (1 + restitution)))
  end
  return position, true
end

-- ------------------------------------------------------------------------------
-- BLINK & RANDIMATION
-- ------------------------------------------------------------------------------
squapi.randimations = {}
squapi.randimation = {}
function squapi.randimation:new(animation, chance_range, is_blink)
  local self = squapi._controller({
    animation = squapi._animation and squapi._animation(animation) or animation,
    chanceRange = math.max(0, math.floor(tonumber(chance_range) or 200)),
    isBlink = is_blink == true,
    seed = #squapi.randimations * 73 + 31
  })
  function self:tick()
    if not self.enabled or (self.isBlink and (minecraft.player.sleeping() or squapi.doBlink == false)) then return self end
    local interval = self.chanceRange + 1
    if (minecraft.world.time() + self.seed) % interval == 0 and not squapi._animation_playing(self.animation) then
      squapi._animation_play(self.animation)
    end
    return self
  end
  return squapi._track(squapi.randimations, self)
end
setmetatable(squapi.randimation, { __call = function(_, animation, chance, blink) return squapi.randimation:new(animation, chance, blink) end })

function squapi.blink(animation, chance_multiplier)
  return squapi.randimation:new(animation, (tonumber(chance_multiplier) or 1) * 200, true)
end

-- ------------------------------------------------------------------------------
-- EYE TRACKING
-- ------------------------------------------------------------------------------
squapi.eyes = {}
squapi.eye = {}
function squapi.eye:new(element, left_distance, right_distance, up_distance, down_distance, switch_values)
  local self = squapi._controller({
    element = model.part(element),
    left = tonumber(left_distance) or 0.25,
    right = tonumber(right_distance) or 1.25,
    up = tonumber(up_distance) or 0.5,
    down = tonumber(down_distance) or 0.5,
    switchValues = switch_values == true,
    eyeScale = 1
  })
  function self:setEyeScale(scale) self.eyeScale = tonumber(scale) or 1; return self end
  function self:zero() self.element:pos(0, 0, 0); return self end

  function self:tick()
    if not self.enabled then return self end
    local head = squapi._head_rotation()
    local x = -squapi.parabolagraph(-50, -self.left, 0, 0, 50, self.right, squapi._clamp(head.y, -50, 50))
    local y = squapi.parabolagraph(-90, -self.down, 0, 0, 90, self.up, head.x)
    x, y = squapi._clamp(x, -self.right, self.left), squapi._clamp(y, -self.down, self.up)
    if self.switchValues then self.element:pos(0, y, -x) else self.element:pos(x, y, 0) end
    local scale = self.eyeScale * (tonumber(squapi.eyeScale) or 1)
    self.element:scale(scale, scale, scale)
    return self
  end
  return squapi._track(squapi.eyes, self)
end
setmetatable(squapi.eye, { __call = function(_, ...) return squapi.eye:new(...) end })

-- ------------------------------------------------------------------------------
-- HOVER POINT & FLOAT POINT
-- ------------------------------------------------------------------------------
squapi.hoverPoints, squapi.hoverPoint = {}, {}
function squapi.hoverPoint:new(element, element_offset, spring_strength, mass, resistance, rotation_speed, rotate_with_player, do_collisions)
  local self = squapi._controller({
    element = model.part(element),
    elementOffset = element_offset and vector.new(element_offset) or vector.zero(),
    springStrength = tonumber(spring_strength) or 0.2,
    mass = math.max(0.001, tonumber(mass) or 5),
    resistance = math.max(0, tonumber(resistance) or 1),
    rotationSpeed = tonumber(rotation_speed) or 0.05,
    rotateWithPlayer = rotate_with_player ~= false,
    doCollisions = do_collisions == true,
    collisionRadius = 0.125,
    collisionPadding = 0.02,
    collisionBounce = 0.35,
    pos = vector.zero(),
    vel = vector.zero(),
    velocity = vector.zero(),
    init = true,
    delay = 0
  })
  if self.element == nil then error("squapi.hoverPoint requires a model part", 2) end

  function self:target()
    return vector.add(minecraft.player.position(), body_space_offset(self.elementOffset, self.rotateWithPlayer))
  end
  function self:setOffset(value) self.elementOffset = value and vector.new(value) or vector.zero(); return self end
  function self:setCollisions(value) self.doCollisions = value == true; return self end
  function self:setCollisionRadius(value) self.collisionRadius = squapi._clamp(tonumber(value) or 0.125, 0, 2); return self end

  function self:reset()
    self.pos, self.vel, self.velocity, self.init = self:target(), vector.zero(), vector.zero(), false
    apply_world_point(self.element, self.pos, self.rotateWithPlayer)
    return self
  end

  function self:tick()
    if not self.enabled then return self end
    local target = self:target()
    if self.init then self.pos, self.vel, self.velocity, self.init = target, vector.zero(), vector.zero(), false end
    local force = vector.sub(vector.mul(vector.sub(target, self.pos), self.springStrength), vector.mul(self.vel, self.resistance))
    self.vel = vector.add(self.vel, vector.div(force, self.mass))
    local position, collided = collide_point(self, vector.add(self.pos, self.vel))
    self.pos, self.delay = position, collided and 2 or 0
    self.velocity = self.vel
    apply_world_point(self.element, self.pos, self.rotateWithPlayer)
    return self
  end

  return squapi._track(squapi.hoverPoints, self)
end
setmetatable(squapi.hoverPoint, { __call = function(_, ...) return squapi.hoverPoint:new(...) end })

squapi.floatPoints = {}
function squapi.floatPoint(element, x_offset, y_offset, z_offset, stiffness, bouncy, y_minimum, max_radius)
  local self = squapi._controller({
    element = model.part(element),
    xOffset = tonumber(x_offset) or 0,
    yOffset = tonumber(y_offset) or 0,
    zOffset = tonumber(z_offset) or 0,
    stiffness = tonumber(stiffness) or 0.02,
    bouncy = tonumber(bouncy) or 0.0005,
    yMinimum = tonumber(y_minimum) or 30,
    maxRadius = max_radius == nil and nil or tonumber(max_radius),
    points = { squapi.bounceObject:new(), squapi.bounceObject:new(), squapi.bounceObject:new(), squapi.bounceObject:new() },
    init = true
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

    local x = self.points[1]:doBounce(target_x, bounce, stiff) + self.xOffset
    local y = self.points[2]:doBounce(target_y, bounce, stiff) + self.yOffset
    local z = self.points[3]:doBounce(target_z, bounce, stiff) + self.zOffset
    self.points[4]:doBounce(-minecraft.player.body_yaw() - 180, 0.0005, 0.03)
    apply_world_point(self.element, vector.new(x / 16, y / 16, z / 16), false)
    return self
  end

  return squapi._track(squapi.floatPoints, self)
end

-- ------------------------------------------------------------------------------
-- FIRST PERSON HAND
-- ------------------------------------------------------------------------------
squapi.FPHands, squapi.FPHand = {}, {}
function squapi.FPHand:new(element, x, y, z, scale, only_visible_in_first_person)
  local self = squapi._controller({
    element = model.part(element),
    x = tonumber(x) or 0,
    y = tonumber(y) or 0,
    z = tonumber(z) or 0,
    scale = tonumber(scale) or 1,
    onlyVisibleInFP = only_visible_in_first_person == true
  })
  self.element:vanilla_parent("RIGHT_ARM", "full")
  function self:updatePos(next_x, next_y, next_z) self.x, self.y, self.z = tonumber(next_x) or 0, tonumber(next_y) or 0, tonumber(next_z) or 0; return self end
  function self:tick()
    local first_person = minecraft.client.first_person()
    if self.onlyVisibleInFP then self.element:visible(first_person) end
    if first_person then
      self.element:pos(self.x, self.y, self.z)
      self.element:scale(self.scale, self.scale, self.scale)
    else
      self.element:pos(0, 0, 0)
      self.element:scale(1, 1, 1)
    end
    return self
  end
  return squapi._track(squapi.FPHands, self)
end
setmetatable(squapi.FPHand, { __call = function(_, ...) return squapi.FPHand:new(...) end })
function squapi.setFirstPersonHandPos(...) return squapi.FPHand:new(...) end

-- ------------------------------------------------------------------------------
-- WALK & CROUCH ANIMATION HELPERS
-- ------------------------------------------------------------------------------
function squapi.walk(walk_animation, run_animation)
  local self = squapi._controller({
    walk = squapi._animation and squapi._animation(walk_animation) or walk_animation,
    run = squapi._animation and squapi._animation(run_animation) or run_animation,
    active = ""
  })
  function self:tick()
    if not self.enabled then return self end
    local moving = minecraft.player.velocity():length() > 0.03
    local next_state = moving and minecraft.player.sprinting() and self.run ~= nil and "run" or (moving and "walk" or "")
    if next_state == self.active then return self end
    if self.active == "walk" then squapi._animation_stop(self.walk) elseif self.active == "run" then squapi._animation_stop(self.run) end
    self.active = next_state
    if next_state == "walk" then squapi._animation_play(self.walk) elseif next_state == "run" then squapi._animation_play(self.run) end
    return self
  end
  return squapi._track(nil, self)
end

function squapi.crouch(crouch_animation, uncrouch_animation, crawl_animation, uncrawl_animation)
  local self = squapi._controller({
    crouch = squapi._animation and squapi._animation(crouch_animation) or crouch_animation,
    uncrouch = squapi._animation and squapi._animation(uncrouch_animation) or uncrouch_animation,
    crawl = squapi._animation and squapi._animation(crawl_animation) or crawl_animation,
    uncrawl = squapi._animation and squapi._animation(uncrawl_animation) or uncrawl_animation,
    previous = minecraft.player.pose()
  })
  function self:tick()
    if not self.enabled then return self end
    local pose = minecraft.player.pose()
    local crouching = pose == "CROUCHING"
    local crawling = pose == "SWIMMING" and not minecraft.player.in_water()
    if crouching then
      squapi._animation_stop(self.uncrouch)
      squapi._animation_play(self.crouch)
    elseif self.previous == "CROUCHING" then
      squapi._animation_stop(self.crouch)
      squapi._animation_play(self.uncrouch)
    end
    if crawling then
      squapi._animation_stop(self.uncrawl)
      squapi._animation_play(self.crawl)
    elseif self.previous == "CRAWLING" then
      squapi._animation_stop(self.crawl)
      squapi._animation_play(self.uncrawl)
    end
    self.previous = crawling and "CRAWLING" or pose
    return self
  end
  return squapi._track(nil, self)
end
