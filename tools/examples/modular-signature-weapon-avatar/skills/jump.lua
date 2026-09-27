-- ==============================================================================
-- Modular Showcase: Super Jump & Hover Module (skills/jump.lua)
-- Demonstrates:
--  - Physics launch: player:setVelocity(x, y, z)
--  - Anti-gravity floating/hovering
-- ==============================================================================

local JumpModule = {
  hover_active = false,
  jump_cooldown = 0
}

function JumpModule.super_jump(state, config)
  if JumpModule.jump_cooldown > 0 then return end
  if not config.debug.infinite_mana and state.mana < config.skills.super_jump.cost then
    sounds:playSound("minecraft:block.fire.extinguish", player:getPos(), 0.7, 1.6)
    return
  end

  if not config.debug.infinite_mana then
    state.mana = state.mana - config.skills.super_jump.cost
  end
  JumpModule.jump_cooldown = config.skills.super_jump.cooldown

  local cur = player:getVelocity()
  -- ดีดตัวลอยขึ้นฟ้าสูงด้วย setVelocity
  player:setVelocity(cur.x, config.skills.super_jump.power, cur.z)

  local pos = player:getPos()
  sounds:playSound("minecraft:entity.firework_rocket.launch", pos, 1.0, 1.2)

  -- ปล่อยวงแหวนลมที่เท้า
  for deg = 0, 360, 30 do
    local rad = math.rad(deg)
    particle.spawn("minecraft:cloud", pos, {
      velocity = { math.cos(rad) * 0.4, 0.05, math.sin(rad) * 0.4 }
    })
  end
end

function JumpModule.toggle_hover(state)
  JumpModule.hover_active = not JumpModule.hover_active
  local pos = player:getPos()
  if JumpModule.hover_active then
    sounds:playSound("minecraft:block.beacon.activate", pos, 1.0, 1.5)
    if animations and animations.hover_pose then
      animations.hover_pose:play()
    end
  else
    sounds:playSound("minecraft:block.beacon.deactivate", pos, 0.8, 1.2)
    if animations and animations.hover_pose then
      animations.hover_pose:stop()
    end
  end
end

function JumpModule.tick(state, config)
  if JumpModule.jump_cooldown > 0 then
    JumpModule.jump_cooldown = JumpModule.jump_cooldown - 1
  end

  -- ตรวจสอบและรักษาสถานะลอยตัวเคว้งกลางอากาศ (Anti-Gravity)
  if JumpModule.hover_active then
    if not config.debug.infinite_mana and state.mana < config.skills.hover.cost_per_tick then
      JumpModule.hover_active = false
      if animations and animations.hover_pose then animations.hover_pose:stop() end
      return
    end

    if not config.debug.infinite_mana then
      state.mana = state.mana - config.skills.hover.cost_per_tick
    end

    local vel = player:getVelocity()
    -- รักษาระดับความสูงและต้านแรงโน้มถ่วง (Levitate/Hover)
    player:setVelocity(vel.x * 0.92, 0.04, vel.z * 0.92)

    -- ปล่อยละอองเวทต้านแรงโน้มถ่วง
    local pos = player:getPos()
    particle.spawn("minecraft:dust", {
      x = pos.x + (math.random() - 0.5) * 0.8,
      y = pos.y + 0.1,
      z = pos.z + (math.random() - 0.5) * 0.8
    }, {
      color = { r = 0.1, g = 0.9, b = 1.0 },
      velocity = { 0, -0.1, 0 }
    })
  end
end

return JumpModule
