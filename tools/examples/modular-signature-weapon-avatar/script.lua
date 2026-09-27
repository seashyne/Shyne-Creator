-- ==============================================================================
-- Modular Showcase: Main Entrypoint (script.lua)
-- Demonstrates:
--  1. Clean Modular Scripting using require(...)
--  2. 3D Signature Weapon & Item Integration
--  3. Flight, Super Jump, and Hover Mechanics via player:setVelocity()
--  4. Real-time Speed & Death Detection
--  5. In-game Debugging & Mana Manipulation
-- ==============================================================================

-- 1. LOAD MODULAR SUB-SYSTEMS
local Config      = require("config")
local JumpSkill   = require("skills.jump")
local FlightSkill = require("skills.flight")
local Meditate    = require("skills.meditation")
local Weapon      = require("weapons.signature_blade")
local HUD         = require("ui.hud")
local Debug       = require("debug.debug_tools")

-- 2. RUNTIME STATE
local state = {
  mana = Config.mana.max
}

-- Initialize modules
Weapon.init()

-- 3. REGISTER AVATAR KEYBINDS (MINECRAFT CONTROLS INTEGRATION)

-- [X] Super Jump
input.bind("skill_super_jump", {
  title = "Skill: Super Jump",
  key = input.key.x,
  on_press = function()
    JumpSkill.super_jump(state, Config)
  end
})

-- [Z] Toggle Hover / Anti-Gravity
input.bind("skill_toggle_hover", {
  title = "Skill: Toggle Hover",
  key = input.key.z,
  on_press = function()
    JumpSkill.toggle_hover(state)
  end
})

-- [R] Sky Flight Dash
input.bind("skill_flight_dash", {
  title = "Skill: Flight Dash",
  key = input.key.r,
  on_press = function()
    FlightSkill.flight_dash(state, Config)
  end
})

-- [G] Draw / Sheath Signature Weapon
input.bind("weapon_toggle", {
  title = "Weapon: Draw/Sheath Blade",
  key = input.key.g,
  on_press = function()
    Weapon.toggle_drawn()
  end
})

-- [F] Blade Wave Attack
input.bind("weapon_slash", {
  title = "Weapon: Blade Wave",
  key = input.key.f,
  on_press = function()
    Weapon.slash_wave(state, Config)
  end
})

-- [M] Hold to Meditate (Mana Restore)
input.bind("skill_meditate", {
  title = "Skill: Meditate (Hold)",
  key = input.key.m,
  on_press = function()
    Meditate.start()
  end,
  on_release = function()
    Meditate.stop()
  end
})

-- [H] Debug: Instant Mana Refill
input.bind("debug_refill", {
  title = "Debug: Refill Mana",
  key = input.key.h,
  on_press = function()
    Debug.refill_mana(state, Config)
  end
})

-- 4. ACTION WHEEL INTEGRATION (KEY 'B')
if action_wheel then
  local page = action_wheel:newPage("main_page")
  action_wheel:setPage(page)

  page:newAction(1)
    :setTitle("Signature Blade [G]")
    :setItem("minecraft:iron_sword")
    :setColor(0.3, 0.7, 1.0)
    :onLeftClick(Weapon.toggle_drawn)

  page:newAction(2)
    :setTitle("Blade Wave [F]")
    :setItem("minecraft:nether_star")
    :setColor(0.5, 0.9, 1.0)
    :onLeftClick(function() Weapon.slash_wave(state, Config) end)

  page:newAction(3)
    :setTitle("Super Jump [X]")
    :setItem("minecraft:feather")
    :setColor(0.8, 1.0, 0.4)
    :onLeftClick(function() JumpSkill.super_jump(state, Config) end)

  page:newAction(4)
    :setTitle("Toggle Hover [Z]")
    :setItem("minecraft:phantom_membrane")
    :setColor(0.4, 0.9, 0.8)
    :onLeftClick(function() JumpSkill.toggle_hover(state) end)

  page:newAction(5)
    :setTitle("Flight Dash [R]")
    :setItem("minecraft:elytra")
    :setColor(0.9, 0.5, 1.0)
    :onLeftClick(function() FlightSkill.flight_dash(state, Config) end)

  page:newAction(8)
    :setTitle("Debug Refill [H]")
    :setItem("minecraft:potion")
    :setColor(1.0, 0.8, 0.2)
    :onLeftClick(function() Debug.refill_mana(state, Config) end)
end

-- 5. LIFECYCLE & TICK RUNTIME
events.ENTITY_INIT:register(function()
  if animations and animations.idle then
    animations.idle:play()
  end
  print("§b[Modular Avatar] §aLoaded! Controls: §e[X] Jump§a, §e[Z] Hover§a, §e[R] Flight§a, §e[G] Blade§a, §e[F] Wave§a, §e[M] Meditate§a, §e[H] Debug")
end)

events.TICK:register(function()
  -- 1) บริหารการฟื้นฟูมานาตามสภาวะ
  if not Config.debug.infinite_mana and state.mana < Config.mana.max then
    if player:isSprinting() then
      -- ขณะวิ่งเร็ว ไม่ฟื้นฟู
    else
      state.mana = math.min(Config.mana.max, state.mana + Config.mana.regen_base)
    end
  end

  -- 2) รันโมดูลย่อย
  JumpSkill.tick(state, Config)
  FlightSkill.tick()
  Meditate.tick(state, Config)
  Weapon.tick(state, Config)

  -- 3) ตรวจจับความเร็ว & เอฟเฟกต์ลมที่เท้าเมื่อวิ่งเร็ว
  if player:isSprinting() then
    local pos = player:getPos()
    particle.spawn("minecraft:cloud", {
      x = pos.x + (math.random() - 0.5) * 0.4,
      y = pos.y + 0.1,
      z = pos.z + (math.random() - 0.5) * 0.4
    }, {
      velocity = { 0, 0.05, 0 }
    })
  end

  -- 4) ตรวจจับการตายและการเกิดใหม่
  Debug.check_death(state, Config)

  -- 5) อัปเดตหน้าต่าง UI
  HUD.update(state, Config, JumpSkill, FlightSkill, Weapon)
end)
