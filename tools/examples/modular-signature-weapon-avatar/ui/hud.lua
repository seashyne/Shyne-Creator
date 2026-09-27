-- ==============================================================================
-- Modular Showcase: HUD & Metrics Module (ui/hud.lua)
-- Demonstrates:
--  - Real-time player speed calculation (m/s)
--  - Mana, Health, and Signature Weapon status
--  - Responsive layout positioning
-- ==============================================================================

local HUD = {}

function HUD.update(state, config, jump_mod, flight_mod, weapon_mod)
  local screen = render.screen()
  if not screen or not screen.ready then return end

  local bar_w = 130
  local bar_h = 7
  local bar_x = math.floor((screen.width - bar_w) / 2)
  local bar_y = screen.height - 48

  -- คำนวณความเร็วปัจจุบันของผู้เล่น (m/s)
  local vel = player:getVelocity()
  local h_speed = math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0 -- บล็อกต่อวินาที
  local total_speed = math.sqrt(vel.x * vel.x + vel.y * vel.y + vel.z * vel.z) * 20.0

  -- 1) พื้นหลัง Mana Bar
  render.rect("mod_hud_bg", {
    x = bar_x - 1,
    y = bar_y - 1,
    width = bar_w + 2,
    height = bar_h + 2,
    color = 0xCC10141E,
    z_index = 40
  })

  -- 2) หลอด Mana Fill
  local mana_ratio = math.max(0, math.min(1, state.mana / config.mana.max))
  render.rect("mod_hud_fill", {
    x = bar_x,
    y = bar_y,
    width = math.floor(bar_w * mana_ratio),
    height = bar_h,
    color = config.debug.infinite_mana and 0xFFFFD700 or config.colors.mana_bar,
    z_index = 41
  })

  -- 3) ข้อความตัวเลข Mana + สถานะ Debug
  local mana_txt = config.debug.infinite_mana and "MP: [INF] (GODMODE)" or string.format("MP: %d / %d", math.floor(state.mana), config.mana.max)
  render.text("mod_hud_text", {
    text = mana_txt,
    x = bar_x + (bar_w / 2) - (#mana_txt * 2.5),
    y = bar_y - 9,
    color = 0xFFEEFFFF,
    shadow = true,
    scale = 0.85,
    z_index = 42
  })

  -- 4) มาตรวัดความเร็วแบบเรียลไทม์ (Speed Metrics)
  local speed_txt = string.format("SPD: %.1f m/s", h_speed)
  if player:isSprinting() then speed_txt = speed_txt .. " (SPRINT)" end
  if jump_mod.hover_active then speed_txt = speed_txt .. " [HOVER]" end

  render.text("mod_hud_speed", {
    text = speed_txt,
    x = bar_x + (bar_w / 2) - (#speed_txt * 2.3),
    y = bar_y + 10,
    color = config.colors.speed_text,
    shadow = true,
    scale = 0.8,
    z_index = 42
  })

  -- 5) สถานะอาวุธและสกิลลัด
  local wp_status = weapon_mod.is_drawn and "§b[G] Blade: DRAWN" or "§7[G] Blade: SHEATHED"
  local jmp_status = jump_mod.jump_cooldown > 0 and string.format("[X] %.1fs", jump_mod.jump_cooldown / 20) or "[X] Jump"
  local flt_status = flight_mod.cooldown > 0 and string.format("[R] %.1fs", flight_mod.cooldown / 20) or "[R] Dash"

  local info_row = string.format("%s  |  %s  |  %s", wp_status, jmp_status, flt_status)
  render.text("mod_hud_keys", {
    text = info_row,
    x = bar_x + (bar_w / 2) - (#info_row * 2.1),
    y = bar_y + 20,
    color = 0xFFCCCCCC,
    shadow = true,
    scale = 0.75,
    z_index = 42
  })
end

return HUD
