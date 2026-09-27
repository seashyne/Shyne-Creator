-- ==============================================================================
-- Modular Showcase: Sky Flight & Dash Module (skills/flight.lua)
-- Demonstrates:
--  - Propelling the player into the air along their camera gaze
--  - Dynamic air streaks & sonic wave particles
-- ==============================================================================

local FlightModule = {
  cooldown = 0
}

function FlightModule.flight_dash(state, config)
  if FlightModule.cooldown > 0 then return end
  if not config.debug.infinite_mana and state.mana < config.skills.flight_dash.cost then
    sounds:playSound("minecraft:block.fire.extinguish", player:getPos(), 0.7, 1.6)
    return
  end

  if not config.debug.infinite_mana then
    state.mana = state.mana - config.skills.flight_dash.cost
  end
  FlightModule.cooldown = config.skills.flight_dash.cooldown

  local look = player:getLookDir()
  local spd = config.skills.flight_dash.speed

  -- พุ่งทะยานไปตามแนวสายตาใน 3 มิติ (X, Y, Z)
  player:setVelocity(look.x * spd, look.y * spd + 0.35, look.z * spd)

  local pos = player:getPos()
  sounds:playSound("minecraft:entity.warden.sonic_boom", pos, 1.0, 1.3)
  sounds:playSound("minecraft:item.elytra.flying", pos, 1.2, 1.0)

  -- ปล่อยลำแสงลมและไอพ่นข้างหลัง
  for i = 1, 16 do
    particle.spawn("minecraft:cloud", pos, {
      velocity = { -look.x * 0.4 + (math.random() - 0.5) * 0.2, -look.y * 0.4, -look.z * 0.4 + (math.random() - 0.5) * 0.2 }
    })
  end

  if animations and animations.flight_pose then
    animations.flight_pose:restart()
  end
end

function FlightModule.tick()
  if FlightModule.cooldown > 0 then
    FlightModule.cooldown = FlightModule.cooldown - 1
  end
end

return FlightModule
