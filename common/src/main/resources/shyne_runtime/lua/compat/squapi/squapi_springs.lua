-- ==============================================================================
-- SquAPI Compatibility: Secondary Springs (compat/squapi/squapi_springs.lua)
-- Implements tails, ears, and bewb secondary-motion controllers.
-- ==============================================================================

squapi = squapi or {}

local function squapi_tail_parts(value)
  if type(value) == "table" and type(value[2]) == "number" then
    local count = math.max(1, math.floor(value[2]))
    local root = model.part(value[1])
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
  return squapi._parts and squapi._parts(value) or { value }
end

-- ------------------------------------------------------------------------------
-- TAILS CONTROLLER
-- ------------------------------------------------------------------------------
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
  local self = squapi._controller({
    parts = squapi_tail_parts(parts),
    idleXMovement = tonumber(idle_x) or 15, idleYMovement = tonumber(idle_y) or 5,
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
    self.berps[index] = {
      pitch = squapi._berp(self.stiffness, self.bounce, self.downLimit, self.upLimit),
      yaw = squapi._berp(self.stiffness, self.bounce, -180, 180)
    }
    self.legacyMotion[index] = { pitch = 0, pitchVelocity = 0, yaw = 0, yawVelocity = 0 }
  end

  function self:target(index)
    local velocity = squapi._relative_velocity()
    local time = minecraft.world.time()
    local pitch = math.sin(time * self.idleYSpeed / 10 - index * self.offsetBetweenSegments + self.initialMovementOffset) * self.idleYMovement
    local yaw = math.sin(time * self.idleXSpeed / 10 - index * self.offsetBetweenSegments) * self.idleXMovement * (tonumber(squapi.wagStrength) or 1)
    if self.legacy then
      pitch = pitch + velocity.vertical * 20 * self.bendStrength - velocity.forward * self.bendStrength * 50 * self.velocityPush
      yaw = yaw + self.bodyTurn * self.bendStrength * 0.5
    else
      pitch = pitch + velocity.vertical * 15 * self.bendStrength - velocity.forward * self.bendStrength * 15 * self.velocityPush
      yaw = yaw + self.bodyTurn * self.bendStrength + velocity.side * self.bendStrength * 40
    end
    if index == 1 and (minecraft.player.fall_flying() or minecraft.player.swimming()) then pitch = self.flyingOffset end
    return squapi._clamp(pitch, self.downLimit, self.upLimit), yaw
  end

  function self:tick()
    if not self.enabled then return self end
    local body_yaw = minecraft.player.body_yaw()
    self.bodyTurn = squapi._clamp(squapi._angle_delta(body_yaw, self.lastBodyYaw), -20, 20)
    self.lastBodyYaw = body_yaw
    if minecraft.player.pose() ~= "SLEEPING" then
      for index, element in ipairs(self.parts) do
        local pitch, yaw = self:target(index)
        if self.legacy then
          local motion = self.legacyMotion[index]
          motion.pitch, motion.pitchVelocity = squapi.bouncetowards(motion.pitch, pitch, motion.pitchVelocity, self.stiffness, self.bounce)
          motion.yaw, motion.yawVelocity = squapi.bouncetowards(motion.yaw, yaw, motion.yawVelocity, self.stiffness, self.bounce)
          model.part(element):rot_add(motion.pitch, motion.yaw, 0)
        else
          local spring = self.berps[index]
          model.part(element):rot_add(spring.pitch:step(pitch), spring.yaw:step(yaw), 0)
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
      model.part(element):rot_add(0, 0, 0)
    end
    return self
  end
  function self:enable() self.enabled = true; return self end
  function self:disable() self.enabled = false; return self:reset() end

  return squapi._track(squapi.tails, self)
end

-- ------------------------------------------------------------------------------
-- EARS CONTROLLER
-- ------------------------------------------------------------------------------
squapi.ears = {}
squapi.ear = {}
function squapi.ear:new(left, right, range, horizontal, bend_strength, do_flick, flick_chance, stiffness, bounce)
  local self = squapi._controller({
    parts = squapi._parts(left, right),
    rangeMultiplier = tonumber(range) or 1,
    horizontalEars = horizontal == true,
    bendStrength = tonumber(bend_strength) or 2,
    doEarFlick = do_flick ~= false,
    earFlickChance = math.max(1, math.floor(tonumber(flick_chance) or 400)),
    earStiffness = tonumber(stiffness) or 0.1,
    earBounce = tonumber(bounce) or 0.8,
    previousPose = minecraft.player.pose(),
    seed = #squapi.ears * 97 + 13
  })
  if self.horizontalEars then self.rangeMultiplier = self.rangeMultiplier / 2 end
  for _, element in ipairs(self.parts) do model.part(element):vanilla_parent("HEAD", "position") end

  self.leftPitch = squapi._berp(self.earStiffness, self.earBounce, -90, 90)
  self.rightPitch = squapi._berp(self.earStiffness, self.earBounce, -90, 90)
  self.leftYaw = squapi._berp(self.earStiffness, self.earBounce, -90, 90)
  self.rightYaw = squapi._berp(self.earStiffness, self.earBounce, -90, 90)
  self.legacyPitch = squapi.bounceObject:new()
  self.legacyLeftYaw = squapi.bounceObject:new()
  self.legacyRightYaw = squapi.bounceObject:new()

  function self:tick()
    if not self.enabled then return self end
    local velocity = squapi._relative_velocity()
    local head = squapi._head_rotation()
    local forward = squapi._clamp(velocity.forward, -0.75, 0.75)
    local vertical = squapi._clamp(velocity.vertical, -1.5, 1.5) * 5
    local side = squapi._clamp(velocity.side, -0.5, 0.5)
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
      local movement = vertical * bend + forward * bend * 15
      if self.horizontalEars then
        self.legacyLeftYaw.vel = self.legacyLeftYaw.vel + movement
        self.legacyRightYaw.vel = self.legacyRightYaw.vel - movement
      else
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
      if self.parts[1] ~= nil then model.part(self.parts[1]):rot_add(left_pitch, left_yaw / 3, left_yaw / 4) end
      if self.parts[2] ~= nil then model.part(self.parts[2]):rot_add(right_pitch, right_yaw / 3, right_yaw / 4) end
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
      if self.parts[1] ~= nil then model.part(self.parts[1]):rot_add(left_pitch, left_yaw / 4, left_yaw / 4) end
      if self.parts[2] ~= nil then model.part(self.parts[2]):rot_add(right_pitch, right_yaw / 4, right_yaw / 4) end
    end
    return self
  end

  function self:reset()
    self.leftPitch:reset(); self.rightPitch:reset(); self.leftYaw:reset(); self.rightYaw:reset()
    squapi._reset_bounce(self.legacyPitch); squapi._reset_bounce(self.legacyLeftYaw); squapi._reset_bounce(self.legacyRightYaw)
    for _, element in ipairs(self.parts) do model.part(element):rot_add(0, 0, 0) end
    return self
  end
  function self:enable() self.enabled = true; return self end
  function self:disable() self.enabled = false; return self:reset() end

  return squapi._track(squapi.ears, self)
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

-- ------------------------------------------------------------------------------
-- BEWB CONTROLLER
-- ------------------------------------------------------------------------------
squapi.bewbs, squapi.bewb = {}, {}
function squapi.bewb:new(element, bendability, stiffness, bounce, do_idle, idle_strength, idle_speed, down_limit, up_limit)
  local self = squapi._controller({
    element = model.part(element),
    bendability = tonumber(bendability) or 2,
    doIdle = do_idle ~= false,
    idleStrength = tonumber(idle_strength) or 4,
    idleSpeed = tonumber(idle_speed) or 1,
    previousPose = minecraft.player.pose(),
    target = 0,
    stiffness = tonumber(stiffness) or 0.05,
    bounce = tonumber(bounce) or 0.9
  })
  self.berp = squapi._berp(self.stiffness, self.bounce, tonumber(down_limit) or -10, tonumber(up_limit) or 25)
  self.legacyBounce = squapi.bounceObject:new()

  function self:tick()
    if self.enabled then
      local velocity = squapi._relative_velocity()
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

  function self:reset()
    self.berp:reset()
    squapi._reset_bounce(self.legacyBounce)
    self.element:rot_add(0, 0, 0)
    return self
  end

  return squapi._track(squapi.bewbs, self)
end

setmetatable(squapi.bewb, { __call = function(_, element, do_idle, bendability, stiffness, bounce)
  local legacy = squapi.bewb:new(element, tonumber(bendability) or 2, tonumber(stiffness) or 0.025, tonumber(bounce) or 0.06, do_idle)
  legacy.legacy = true
  return legacy
end })
