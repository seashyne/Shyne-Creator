-- ==============================================================================
-- Shyne Creator: Action Wheel Showcase Avatar (script.lua)
-- Demonstrates:
--  1. 8-Slot Radial Action Wheel GUI (กดปุ่ม B เพื่อเปิดเมนูวงกลม 8 ช่อง)
--  2. Custom Sound Player (เล่นเสียง .ogg จากโฟลเดอร์ sounds/ โดยไม่ต้องพึ่ง Resource Pack)
--  3. First-Person Custom Arm (แสดงแขน 3D ของโมเดลจริงในมุมมองบุคคลที่หนึ่ง)
--  4. Procedural Bone Physics (ระบบฟิสิกส์หูและหาง ดึ๋งดั๋งตามการเคลื่อนไหว)
--  5. Multi-page Navigation (สลับหน้า 1 และหน้า 2 ของ Action Wheel ได้อย่างลื่นไหล)
-- ==============================================================================

local sparkle_active = false
local tick_counter = 0

-- ------------------------------------------------------------------------------
-- 1. MODEL & ANIMATION SETUP
-- ------------------------------------------------------------------------------
local head = models.model.root.Head
local left_ear = head.LeftEar
local right_ear = head.RightEar
local body = models.model.root.Body
local tail = body.Tail

-- Initial animation setup on entity spawn
events.ENTITY_INIT:register(function()
  if animations.idle then
    animations.idle:play()
  end
  print("§a[ActionWheel Showcase] Avatar initialized! Press §e'B'§a to open Action Wheel.")
end)

-- ------------------------------------------------------------------------------
-- 2. ACTION WHEEL SETUP (FIGURA COMPATIBLE API)
-- ------------------------------------------------------------------------------
local main_page = action_wheel:newPage("main")
local page2 = action_wheel:newPage("fx_page")
action_wheel:setPage(main_page)

-- Slot 1: Roar Emote + Custom Sound (.ogg)
local act_roar = main_page:newAction(1)
act_roar:setTitle("Roar Emote")
act_roar:setItem("minecraft:dragon_head")
act_roar:setColor(1.0, 0.25, 0.25)
act_roar:setHoverColor(1.0, 0.5, 0.5)
act_roar:onLeftClick(function()
  -- เล่นแอนิเมชันคำราม (roar)
  if animations.roar then
    animations.roar:restart()
  end
  -- เล่นเสียง my_roar.ogg จากโฟลเดอร์ avatar/sounds/my_roar.ogg
  sounds:playSound("my_roar", 1.2, 1.0)
  print("§c[Avatar] §f*ROAAAR!* (Played custom sounds/my_roar.ogg)")
end)

-- Slot 2: Wave Emote
local act_wave = main_page:newAction(2)
act_wave:setTitle("Wave Hello")
act_wave:setItem("minecraft:player_head")
act_wave:setColor(1.0, 0.8, 0.2)
act_wave:setHoverColor(1.0, 0.95, 0.4)
act_wave:onLeftClick(function()
  if animations.wave then
    animations.wave:restart()
  end
  print("§e[Avatar] §fAvatar waves cheerfully to everyone!")
end)

-- Slot 3: Dance Emote (Toggle Action)
local act_dance = main_page:newAction(3)
act_dance:setTitle("Dance Party")
act_dance:setItem("minecraft:music_disc_cat")
act_dance:setColor(0.9, 0.3, 0.9)
act_dance:toggled(false)
act_dance:onToggle(function(toggled)
  if toggled then
    if animations.dance then animations.dance:play() end
    sounds:playSound("minecraft:block.note_block.bell", 1.0, 1.2)
    print("§d[Avatar] §fDancing time! ♫")
  else
    if animations.dance then animations.dance:stop() end
    if animations.idle then animations.idle:play() end
    print("§7[Avatar] §fDance stopped.")
  end
end)

-- Slot 4: Dynamic Ears Physics (Toggle Action)
local act_ear_physics = main_page:newAction(4)
act_ear_physics:setTitle("Ear Physics")
act_ear_physics:setItem("minecraft:rabbit_foot")
act_ear_physics:setColor(0.2, 0.8, 1.0)
act_ear_physics:toggled(true)
-- เปิดฟิสิกส์หูเริ่มต้น
left_ear:setPhysics(true)
right_ear:setPhysics(true)
act_ear_physics:onToggle(function(toggled)
  left_ear:setPhysics(toggled)
  right_ear:setPhysics(toggled)
  local status = toggled and "§aEnabled" or "§cDisabled"
  print("§b[Avatar] §fEar dynamic spring physics: " .. status)
end)

-- Slot 5: Tail Wag Physics (Toggle Action)
local act_tail_physics = main_page:newAction(5)
act_tail_physics:setTitle("Tail Physics")
act_tail_physics:setItem("minecraft:feather")
act_tail_physics:setColor(0.3, 0.9, 0.4)
act_tail_physics:toggled(true)
tail:setPhysics(true)
act_tail_physics:onToggle(function(toggled)
  tail:setPhysics(toggled)
  local status = toggled and "§aEnabled" or "§cDisabled"
  print("§a[Avatar] §fTail sway physics: " .. status)
end)

-- Slot 6: First-Person Custom Arm (Toggle Action)
local act_first_person = main_page:newAction(6)
act_first_person:setTitle("First-Person Arm")
act_first_person:setItem("minecraft:golden_sword")
act_first_person:setColor(1.0, 0.6, 0.1)
act_first_person:toggled(false)
act_first_person:onToggle(function(toggled)
  -- สลับการใช้แขนโมเดล 3D แทนแขน Steve/Alex ในมุมมอง First-Person
  avatar:setFirstPersonArm(toggled)
  local status = toggled and "§aCustom 3D Model Arm" or "§7Vanilla Arm"
  print("§6[Avatar] §fFirst-Person Hand Mode: " .. status)
end)

-- Slot 7: Crystal Chime Sound (.ogg)
local act_chime = main_page:newAction(7)
act_chime:setTitle("Crystal Chime")
act_chime:setItem("minecraft:amethyst_shard")
act_chime:setColor(0.7, 0.5, 1.0)
act_chime:onLeftClick(function()
  -- เล่นเสียง chime.ogg จากโฟลเดอร์ avatar/sounds/chime.ogg
  sounds:playSound("chime", 1.0, 1.0)
  print("§d[Avatar] §f*Ding!* (Played custom sounds/chime.ogg)")
end)

-- Slot 8: Page 2 Navigation
local act_next_page = main_page:newAction(8)
act_next_page:setTitle("Next: FX & Tools")
act_next_page:setItem("minecraft:compass")
act_next_page:setColor(0.4, 0.8, 1.0)
act_next_page:onLeftClick(function()
  action_wheel:setPage(page2)
end)

-- ------------------------------------------------------------------------------
-- 3. ACTION WHEEL PAGE 2 (EFFECTS & COSMETICS)
-- ------------------------------------------------------------------------------

-- Slot 1: Back to Main Page
local act_back = page2:newAction(1)
act_back:setTitle("Back to Main")
act_back:setItem("minecraft:arrow")
act_back:setColor(0.9, 0.9, 0.9)
act_back:onLeftClick(function()
  action_wheel:setPage(main_page)
end)

-- Slot 2: Sparkle Aura (Toggle)
local act_sparkle = page2:newAction(2)
act_sparkle:setTitle("Sparkle Aura")
act_sparkle:setItem("minecraft:nether_star")
act_sparkle:setColor(1.0, 1.0, 0.3)
act_sparkle:toggled(false)
act_sparkle:onToggle(function(toggled)
  sparkle_active = toggled
  local status = toggled and "§aActive" or "§cInactive"
  print("§e[Avatar] §fSparkle Aura: " .. status)
end)

-- Slot 3: Big Ears Boost (Toggle Scale)
local act_scale = page2:newAction(3)
act_scale:setTitle("Big Ears Mode")
act_scale:setItem("minecraft:golden_carrot")
act_scale:setColor(1.0, 0.7, 0.2)
act_scale:toggled(false)
act_scale:onToggle(function(toggled)
  local ear_scale = toggled and 1.6 or 1.0
  left_ear:setScale(ear_scale, ear_scale, ear_scale)
  right_ear:setScale(ear_scale, ear_scale, ear_scale)
  print("§6[Avatar] §fEar scale set to " .. (toggled and "160%" or "100%"))
end)

-- Slot 4: Level-Up Fanfare (Vanilla Sound)
local act_levelup = page2:newAction(4)
act_levelup:setTitle("Level Up Fanfare")
act_levelup:setItem("minecraft:experience_bottle")
act_levelup:setColor(0.4, 1.0, 0.4)
act_levelup:onLeftClick(function()
  sounds:playSound("minecraft:entity.player.levelup", 1.0, 1.0)
  print("§a[Avatar] §f*Level Up sound played!*")
end)

-- ------------------------------------------------------------------------------
-- 4. TICK EVENT (PARTICLES & LOGIC)
-- ------------------------------------------------------------------------------
events.TICK:register(function()
  tick_counter = tick_counter + 1

  -- Emit sparkle particles if enabled
  if sparkle_active and tick_counter % 5 == 0 then
    local player_pos = player:getPos()
    if player_pos then
      particle.spawn("minecraft:portal", {
        player_pos.x + (math.random() - 0.5) * 0.8,
        player_pos.y + 1.2 + (math.random() - 0.5) * 0.8,
        player_pos.z + (math.random() - 0.5) * 0.8
      })
    end
  end
end)
