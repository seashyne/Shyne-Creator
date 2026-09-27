-- ==============================================================================
-- Modular Showcase: Debug & Diagnostics Module (debug/debug_tools.lua)
-- Demonstrates:
--  - Death detection & respawn handling
--  - Instant mana refill and Godmode toggles
-- ==============================================================================

local DebugTools = {
  was_dead = false
}

function DebugTools.refill_mana(state, config)
  state.mana = config.mana.max
  sounds:playSound("minecraft:entity.player.levelup", player:getPos(), 1.0, 1.5)
  print("§a[Debug] Refilled Mana to MAX (100)!")
end

function DebugTools.toggle_godmode(config)
  config.debug.infinite_mana = not config.debug.infinite_mana
  local msg = config.debug.infinite_mana and "§6[Debug] Infinite Mana: ENABLED" or "§7[Debug] Infinite Mana: DISABLED"
  print(msg)
end

function DebugTools.check_death(state, config)
  -- ตรวจจับสถานะการมีชีวิต/การตาย
  local is_alive = player:isAlive() and (player:getHealth() > 0)
  if not is_alive and not DebugTools.was_dead then
    DebugTools.was_dead = true
    print("§c[Avatar System] Player Death detected! Resetting skills and mana...")
    local pos = player:getPos()
    particle.spawn("minecraft:soul", pos, { velocity = { 0, 0.3, 0 } })
    sounds:playSound("minecraft:entity.wither.hurt", pos, 1.0, 0.8)
  elseif is_alive and DebugTools.was_dead then
    DebugTools.was_dead = false
    state.mana = config.mana.max
    print("§a[Avatar System] Player Respawned! Restored full Mana.")
  end
end

return DebugTools
