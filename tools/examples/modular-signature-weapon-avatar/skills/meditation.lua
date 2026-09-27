-- ==============================================================================
-- Modular Showcase: Mana Meditation Module (skills/meditation.lua)
-- Demonstrates:
--  - Hold-to-charge mechanic for mana restoration
--  - Concentrated aura particles
-- ==============================================================================

local MeditationModule = {
  is_meditating = false
}

function MeditationModule.start()
  MeditationModule.is_meditating = true
  sounds:playSound("minecraft:block.enchantment_table.use", player:getPos(), 0.9, 1.2)
end

function MeditationModule.stop()
  MeditationModule.is_meditating = false
end

function MeditationModule.tick(state, config)
  if MeditationModule.is_meditating then
    if state.mana < config.mana.max then
      state.mana = math.min(config.mana.max, state.mana + config.mana.regen_meditate)
    end

    local pos = player:getPos()
    -- ปล่อยประกายดาวและแสงสีทองดึงดูดเข้าสู่แกนกลางตัวละคร
    particle.spawn("minecraft:enchant", {
      x = pos.x + (math.random() - 0.5) * 1.5,
      y = pos.y + math.random() * 2.0,
      z = pos.z + (math.random() - 0.5) * 1.5
    }, {
      velocity = { 0, -0.2, 0 }
    })
  end
end

return MeditationModule
