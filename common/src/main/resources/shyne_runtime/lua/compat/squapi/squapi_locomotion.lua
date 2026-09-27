-- ==============================================================================
-- SquAPI Compatibility: Locomotion & Limbs (compat/squapi/squapi_locomotion.lua)
-- Implements vanilla limb tracking (legs/arms), smooth head/neck turning,
-- torso offset, bounceWalk, and taur physics.
-- ==============================================================================

squapi = squapi or {}

local function create_limb(collection, element, strength, is_right, keep_position, vanilla_part)
  local self = squapi._controller({
    element = model.part(element),
    strength = tonumber(strength) or 1,
    isRight = is_right == true,
    keepPosition = keep_position ~= false,
    vanillaPart = vanilla_part,
    frozen = false,
    rot = vector.zero(),
    pos = vector.zero()
  })

  if self.keepPosition then
    self.element:vanilla_parent(vanilla_part, "position")
  else
    self.element:vanilla_parent("", "full")
  end

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

  function self:reset()
    self.element:rot_add(0, 0, 0)
    return self
  end

  return squapi._track(collection, self)
end

-- ------------------------------------------------------------------------------
-- LEGS & ARMS
-- ------------------------------------------------------------------------------
squapi.legs, squapi.leg = {}, {}
function squapi.leg:new(element, strength, is_right, keep_position)
  return create_limb(squapi.legs, element, strength, is_right, keep_position, is_right and "RIGHT_LEG" or "LEFT_LEG")
end
setmetatable(squapi.leg, { __call = function(_, ...) return squapi.leg:new(...) end })

squapi.arms, squapi.arm = {}, {}
function squapi.arm:new(element, strength, is_right, keep_position)
  return create_limb(squapi.arms, element, strength, is_right, keep_position, is_right and "RIGHT_ARM" or "LEFT_ARM")
end
setmetatable(squapi.arm, { __call = function(_, ...) return squapi.arm:new(...) end })

-- ------------------------------------------------------------------------------
-- SMOOTH HEAD & TORSO
-- ------------------------------------------------------------------------------
squapi.smoothHeads = {}
squapi.smoothHead = {}
function squapi.smoothHead:new(elements, strength, tilt, speed, keep_original_position, _fix_portrait)
  local parts = squapi._parts and squapi._parts(elements) or { elements }
  local self = squapi._controller({
    elements = parts,
    tilt = tonumber(tilt) or 0.1,
    speed = tonumber(speed) or 1,
    keepOriginalHeadPos = keep_original_position ~= false,
    offset = vector.zero(),
    rotations = {},
    ignoreCancelHeadMovement = false,
    publishTorsoOffset = false
  })

  self.strength = type(strength) == "table" and strength or {}
  if type(strength) == "number" then
    for index = 1, #parts do self.strength[index] = strength / math.max(1, #parts) end
  else
    for index = 1, #parts do self.strength[index] = tonumber(self.strength[index]) or (1 / math.max(1, #parts)) end
  end
  self.positionIndex = type(keep_original_position) == "number" and math.max(1, math.min(#parts, math.floor(keep_original_position))) or #parts

  for index, element in ipairs(parts) do
    local part = model.part(element)
    if self.keepOriginalHeadPos and index == self.positionIndex then
      part:vanilla_parent("HEAD", "position")
    else
      part:vanilla_parent("", "full")
    end
    self.rotations[index] = vector.zero()
  end

  function self:setOffset(x, y, z)
    self.offset = y == nil and (x and vector.new(x) or vector.zero()) or vector.new(x, y, z)
    return self
  end

  function self:zero()
    for index, element in ipairs(self.elements) do
      self.rotations[index] = vector.zero()
      model.part(element):rot_add(0, 0, 0):pos(0, 0, 0)
    end
    return self
  end

  function self:tick()
    if not self.enabled then return self end
    local head = squapi._head_rotation()
    head = vector.add(head, squapi.smoothHeadOffset and vector.new(squapi.smoothHeadOffset) or vector.zero())
    local offset = self.offset
    local torso_offset = (not self.ignoreCancelHeadMovement and squapi.cancelHeadMovement) and (squapi.torsoOffset and vector.new(squapi.torsoOffset) or vector.zero()) or vector.zero()
    local rate = squapi._clamp(self.speed / 2, 0, 1)

    for index, element in ipairs(self.elements) do
      local target = vector.mul(head, self.strength[index])
      target.z = target.y * self.tilt - (offset.z or 0) / math.max(1, #self.elements)
      target.x = target.x - (offset.x or 0) / math.max(1, #self.elements)
      target.y = target.y - (offset.y or 0) / math.max(1, #self.elements)
      target = vector.sub(target, vector.mul(torso_offset, self.strength[index]))
      self.rotations[index] = vector.add(self.rotations[index], vector.mul(vector.sub(target, self.rotations[index]), rate))
      model.part(element):visible(not minecraft.client.first_person()):rot_add(self.rotations[index])
    end

    if self.publishTorsoOffset then
      squapi.torsoOffset = vector.new(self.rotations[1] or vector.zero())
    end
    return self
  end

  return squapi._track(squapi.smoothHeads, self)
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

-- ------------------------------------------------------------------------------
-- BOUNCE WALK & TAUR
-- ------------------------------------------------------------------------------
squapi.bounceWalks, squapi.bounceWalk = {}, {}
function squapi.bounceWalk:new(element, multiplier)
  local self = squapi._controller({
    element = model.part(element),
    bounceMultiplier = tonumber(multiplier) or 1
  })
  function self:tick()
    if not self.enabled then return self end
    local rotation = avatar.vanilla("LEFT_LEG"):rotation()
    local amount = minecraft.player.on_ground() and math.abs(rotation.x) / 40 * self.bounceMultiplier or 0
    if minecraft.player.crouching() then amount = amount / 2 end
    self.element:pos(0, amount, 0)
    return self
  end
  return squapi._track(squapi.bounceWalks, self)
end
setmetatable(squapi.bounceWalk, { __call = function(_, ...) return squapi.bounceWalk:new(...) end })
function squapi.bouncewalk(element, multiplier) return squapi.bounceWalk:new(element, multiplier) end

squapi.taurs, squapi.taur = {}, {}
function squapi.taur:new(body, front_legs, back_legs)
  local self = squapi._controller({
    body = model.part(body),
    frontLegs = front_legs and model.part(front_legs) or nil,
    backLegs = back_legs and model.part(back_legs) or nil,
    bounce = squapi.bounceObject:new()
  })
  function self:tick()
    if not self.enabled then return self end
    local flying = minecraft.player.fall_flying() or minecraft.player.swimming()
    local angle = flying and 80 or self.bounce:doBounce(squapi._clamp(minecraft.player.velocity().y * 40, -30, 45), 0.01, 0.2)
    self.body:rot_add(angle, 0, 0)
    if self.backLegs ~= nil then self.backLegs:rot_add(flying and -50 or angle * 1.5, 0, 0) end
    if self.frontLegs ~= nil then self.frontLegs:rot_add(flying and -50 or -angle * 3.5, 0, 0) end
    return self
  end
  return squapi._track(squapi.taurs, self)
end
setmetatable(squapi.taur, { __call = function(_, ...) return squapi.taur:new(...) end })
function squapi.taurPhysics(body, front_legs, back_legs) return squapi.taur:new(body, front_legs, back_legs) end
