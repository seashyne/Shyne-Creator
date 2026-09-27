-- ==============================================================================
-- Shyne Creator Showcase: Custom Skill & Mana HUD Avatar (script.lua)
-- Demonstrates:
--  1. Avatar Custom Keybinds (input.bind) appearing in Minecraft Controls menu
--  2. Responsive 2D Mana & Energy HUD (render.screen, render.rect, render.text)
--  3. Visual & Audio Skill Execution (particles, sounds, and animations)
--  4. 3D Rotating Magic Shield & Bone Transforms (models.model.root.Body.MagicShield)
--  5. Dual-Input: Action Wheel (B) + Keyboard Hotkeys ([R], [V], [C])
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. STATE & DATA STRUCTURES
-- ------------------------------------------------------------------------------
local mana = {
  max = 100,
  current = 100,
  regen_per_tick = 0.5, -- ฟื้นฟู 10 MP ต่อวินาที (20 ticks)
}

local skills = {
  dash = {
    name = "Gale Dash",
    key_name = "R",
    cost = 20,
    cooldown = 30, -- 1.5 วินาที
    timer = 0
  },
  ultimate = {
    name = "Dragon Burst",
    key_name = "V",
    cost = 50,
    cooldown = 120, -- 6 วินาที
    timer = 0
  },
  shield = {
    name = "Magic Barrier",
    key_name = "C",
    cost = 35,
    cooldown = 100, -- 5 วินาที
    duration = 60,  -- กางโล่ 3 วินาที
    active_timer = 0,
    timer = 0
  }
}

local hud_visible = true
local warning_timer = 0
local shield_angle = 0

-- Model references
local magic_shield = models.model and models.model.root and models.model.root.Body and models.model.root.Body.MagicShield

-- Initialize model state
if magic_shield then
  magic_shield:setVisible(false)
end

-- ------------------------------------------------------------------------------
-- 2. HUD RENDERING SYSTEM (RESPONSIVE 2D SCREEN)
-- ------------------------------------------------------------------------------
local function update_mana_hud()
  if not hud_visible then
    render.clear()
    return
  end

  local screen = render.screen()
  if not screen or not screen.ready then return end

  local bar_w = 126
  local bar_h = 7
  local bar_x = math.floor((screen.width - bar_w) / 2)
  local bar_y = screen.height - 49

  local mana_ratio = math.max(0, math.min(1, mana.current / mana.max))
  local fill_w = math.floor(bar_w * mana_ratio)

  -- 1) Background Frame (สีดำโปร่งแสง)
  render.rect("hud_mana_bg", {
    x = bar_x - 1,
    y = bar_y - 1,
    width = bar_w + 2,
    height = bar_h + 2,
    color = 0xCC111622,
    z_index = 40
  })

  -- 2) Mana Fill Bar (สีฟ้าคราม Cyan เรืองแสง)
  render.rect("hud_mana_fill", {
    x = bar_x,
    y = bar_y,
    width = fill_w,
    height = bar_h,
    color = 0xFF00C8FF,
    z_index = 41
  })

  -- 3) Mana Value Text ("MP: 85 / 100")
  local val_str = string.format("MP: %d / %d", math.floor(mana.current), mana.max)
  render.text("hud_mana_text", {
    text = val_str,
    x = bar_x + (bar_w / 2) - (#val_str * 2.6),
    y = bar_y - 9,
    color = 0xFFDDFFFF,
    shadow = true,
    scale = 0.9,
    z_index = 42
  })

  -- 4) Skill Badges & Cooldown Status
  local dash_st = skills.dash.timer > 0 and string.format("[R] %.1fs", skills.dash.timer / 20) or "[R] Dash"
  local ult_st = skills.ultimate.timer > 0 and string.format("[V] %.1fs", skills.ultimate.timer / 20) or "[V] Ult"
  local shd_st
  if skills.shield.active_timer > 0 then
    shd_st = string.format("§b[C] Barrier (%.1fs)", skills.shield.active_timer / 20)
  elseif skills.shield.timer > 0 then
    shd_st = string.format("[C] %.1fs", skills.shield.timer / 20)
  else
    shd_st = "[C] Barrier"
  end

  local status_str = string.format("%s  |  %s  |  %s", dash_st, ult_st, shd_st)
  render.text("hud_skill_status", {
    text = status_str,
    x = bar_x + (bar_w / 2) - (#status_str * 2.3),
    y = bar_y + 10,
    color = 0xFFE0E0E0,
    shadow = true,
    scale = 0.8,
    z_index = 42
  })

  -- 5) Warning message if not enough mana
  if warning_timer > 0 then
    local warn_str = "§c! NOT ENOUGH MANA !"
    render.text("hud_mana_warn", {
      text = warn_str,
      x = bar_x + (bar_w / 2) - (#warn_str * 2.2),
      y = bar_y - 20,
      color = 0xFFFF4444,
      shadow = true,
      scale = 0.9,
      z_index = 43
    })
  else
    render.remove("hud_mana_warn")
  end
end

-- ------------------------------------------------------------------------------
-- 3. SKILL EXECUTION LOGIC (VFX, SFX & ANIMATIONS)
-- ------------------------------------------------------------------------------

-- แจ้งเตือนเมื่อมานาไม่พอ
local function trigger_out_of_mana()
  warning_timer = 20 -- กะพริบเตือน 1 วินาที
  sounds:playSound("minecraft:block.fire.extinguish", player:getPos(), 0.8, 1.6)
end

-- Skill 1: Gale Dash
local function cast_dash()
  if skills.dash.timer > 0 then return end
  if mana.current < skills.dash.cost then
    trigger_out_of_mana()
    return
  end

  mana.current = mana.current - skills.dash.cost
  skills.dash.timer = skills.dash.cooldown

  local pos = player:getPos()
  local look = player:getLookDir()

  -- SFX & VFX
  sounds:playSound("minecraft:entity.enderman.teleport", pos, 1.0, 1.4)
  for i = 1, 14 do
    particle.spawn("minecraft:cloud", {
      x = pos.x + (math.random() - 0.5) * 0.8,
      y = pos.y + math.random() * 1.6,
      z = pos.z + (math.random() - 0.5) * 0.8
    }, {
      vel = { -look.x * 0.25, 0.05, -look.z * 0.25 }
    })
  end

  if animations and animations.cast_dash then
    animations.cast_dash:restart()
  end
end

-- Skill 2: Dragon Burst (Ultimate)
local function cast_ultimate()
  if skills.ultimate.timer > 0 then return end
  if mana.current < skills.ultimate.cost then
    trigger_out_of_mana()
    return
  end

  mana.current = mana.current - skills.ultimate.cost
  skills.ultimate.timer = skills.ultimate.cooldown

  local pos = player:getPos()

  -- SFX: Sonic boom
  sounds:playSound("minecraft:entity.warden.sonic_boom", pos, 1.0, 1.1)

  -- VFX: 360-degree golden wave
  for deg = 0, 360, 18 do
    local rad = math.rad(deg)
    local vx = math.cos(rad) * 0.7
    local vz = math.sin(rad) * 0.7
    particle.spawn("minecraft:dust", {
      x = pos.x,
      y = pos.y + 0.8,
      z = pos.z
    }, {
      velocity = { vx, 0.08, vz },
      color = { r = 1.0, g = 0.85, b = 0.1 }, -- สีทอง
      scale = 1.8
    })
  end

  -- ตรวจจับและระเบิดพลังใส่มอนสเตอร์ในระยะ 6 บล็อก (Entity Detection)
  if world and world.getEntities then
    local nearby_entities = world.getEntities(6)
    for _, ent in ipairs(nearby_entities) do
      if ent.is_monster then
        local epos = ent:getPos()
        particle.spawn("minecraft:crit", epos, { velocity = { 0, 0.4, 0 } })
      end
    end
  end

  if animations and animations.cast_ultimate then
    animations.cast_ultimate:restart()
  end
end

-- Skill 3: Magic Barrier
local function cast_shield()
  if skills.shield.timer > 0 or skills.shield.active_timer > 0 then return end
  if mana.current < skills.shield.cost then
    trigger_out_of_mana()
    return
  end

  mana.current = mana.current - skills.shield.cost
  skills.shield.active_timer = skills.shield.duration
  skills.shield.timer = skills.shield.cooldown

  local pos = player:getPos()
  sounds:playSound("minecraft:block.beacon.activate", pos, 1.0, 1.3)

  -- ตรวจจับเพื่อนรอบตัวในระยะ 8 บล็อกเพื่อส่งละอองเกราะคุ้มกันให้ (Co-op Team Shield)
  if world and world.getPlayers then
    local nearby_friends = world.getPlayers(8)
    for _, friend in ipairs(nearby_friends) do
      if not friend.is_self then
        local fpos = friend:getPos()
        particle.spawn("minecraft:totem_of_undying", {
          x = fpos.x,
          y = fpos.y + 1.0,
          z = fpos.z
        }, { velocity = { 0, 0.2, 0 } })
        sounds:playSound("minecraft:block.amethyst_block.chime", fpos, 0.8, 1.4)
        print("§b[Magic Barrier] §aGranted protective aura to teammate: §e" .. friend:getName())
      end
    end
  end

  if magic_shield then
    magic_shield:setVisible(true)
  end

  if animations and animations.cast_shield then
    animations.cast_shield:restart()
  end
end

-- ------------------------------------------------------------------------------
-- 4. DYNAMIC KEYBIND REGISTRATION (MINECRAFT CONTROLS INTEGRATION)
-- ------------------------------------------------------------------------------
input.bind("skill_gale_dash", {
  title = "Skill: Gale Dash",
  key = input.key.r,
  on_press = cast_dash
})

input.bind("skill_dragon_burst", {
  title = "Skill: Dragon Burst",
  key = input.key.v,
  on_press = cast_ultimate
})

input.bind("skill_magic_barrier", {
  title = "Skill: Magic Barrier",
  key = input.key.c,
  on_press = cast_shield
})

-- ------------------------------------------------------------------------------
-- 5. ACTION WHEEL INTEGRATION (KEY 'B')
-- ------------------------------------------------------------------------------
if action_wheel then
  local wheel_page = action_wheel:newPage("skills_page")
  action_wheel:setPage(wheel_page)

  -- Action 1: Gale Dash
  wheel_page:newAction(1)
    :setTitle("Gale Dash [R]")
    :setItem("minecraft:feather")
    :setColor(0.3, 0.8, 1.0)
    :onLeftClick(cast_dash)

  -- Action 2: Dragon Burst
  wheel_page:newAction(2)
    :setTitle("Dragon Burst [V]")
    :setItem("minecraft:dragon_breath")
    :setColor(1.0, 0.8, 0.2)
    :onLeftClick(cast_ultimate)

  -- Action 3: Magic Barrier
  wheel_page:newAction(3)
    :setTitle("Magic Barrier [C]")
    :setItem("minecraft:shield")
    :setColor(0.2, 1.0, 0.6)
    :onLeftClick(cast_shield)

  -- Action 8: Toggle HUD
  wheel_page:newAction(8)
    :setTitle("Toggle Mana HUD")
    :setItem("minecraft:compass")
    :setColor(0.8, 0.8, 0.8)
    :onLeftClick(function()
      hud_visible = not hud_visible
      print("§a[Skill Avatar] Mana HUD: " .. (hud_visible and "§2ENABLED" or "§cDISABLED"))
    end)
end

-- ------------------------------------------------------------------------------
-- 6. LIFECYCLE & TICK RUNTIME
-- ------------------------------------------------------------------------------
events.ENTITY_INIT:register(function()
  if animations and animations.idle then
    animations.idle:play()
  end
  print("§b[Skill Avatar] §aAvatar loaded! Keys: §e[R] Dash§a, §e[V] Ultimate§a, §e[C] Barrier§a (Or press §e'B'§a for Action Wheel)")
end)

events.TICK:register(function()
  -- 1) ฟื้นฟูมานา
  if mana.current < mana.max then
    mana.current = math.min(mana.max, mana.current + mana.regen_per_tick)
  end

  -- 2) ลดคูลดาวน์
  if skills.dash.timer > 0 then skills.dash.timer = skills.dash.timer - 1 end
  if skills.ultimate.timer > 0 then skills.ultimate.timer = skills.ultimate.timer - 1 end
  if skills.shield.timer > 0 then skills.shield.timer = skills.shield.timer - 1 end
  if warning_timer > 0 then warning_timer = warning_timer - 1 end

  -- 3) ตรวจสอบสถานะ Magic Barrier (กางโล่ 3D และหมุนรอบตัว)
  if skills.shield.active_timer > 0 then
    skills.shield.active_timer = skills.shield.active_timer - 1
    shield_angle = (shield_angle + 12) % 360

    if magic_shield then
      magic_shield:setVisible(true)
      magic_shield:setRot(0, shield_angle, 0)
    end

    -- ปล่อยละอองเกราะเวทสีฟ้าอ่อนรอบตัว
    local ppos = player:getPos()
    particle.spawn("minecraft:dust", {
      x = ppos.x + (math.random() - 0.5) * 1.5,
      y = ppos.y + 0.3 + math.random() * 1.2,
      z = ppos.z + (math.random() - 0.5) * 1.5
    }, {
      color = { r = 0.2, g = 0.8, b = 1.0 },
      scale = 0.9
    })

    if skills.shield.active_timer == 0 and magic_shield then
      magic_shield:setVisible(false)
    end
  end

  -- 4) อัปเดตหน้าต่าง HUD
  update_mana_hud()
end)
