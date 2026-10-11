# 🌐 Shyne Standard — คู่มืออ้างอิง Globals ทั้งหมด (Shyne Globals Reference)

เอกสารนี้รวบรวม **ตัวแปร ออบเจ็กต์ และโมดูลส่วนกลาง (Global Scope / `_G`)** ทั้งหมดใน **Shyne Creator Runtime** 

ใน Shyne Standard คุณสามารถเรียกใช้งานออบเจ็กต์เหล่านี้ได้ทันทีในสคริปต์ `main.lua` หรือไฟล์สคริปต์ของอวตารโดยไม่ต้องใช้ `require()` โดยระบบแบ่งออกเป็นหมวดหมู่อย่างเป็นระเบียบ ชัดเจน และมีประสิทธิภาพสูงสุด

---

## 📌 สารบัญหมวดหมู่ Globals

| หมวดหมู่ | ตัวแปรและออบเจ็กต์ส่วนกลาง | คำอธิบายหน้าที่ |
|---|---|---|
| **1. เอนจิน & สิทธิ์** | [`shyne`](#1-shyne-core-engine), [`result`](#2-result-safe-execution), [`permissions`](#3-permissions-sandbox-security) | แกนกลางเอนจิน, ตรวจเวอร์ชัน API, ดักจับข้อผิดพลาด, และระบบสิทธิ์ |
| **2. ชิ้นส่วนโมเดล & แอนิเมชัน** | [`part`](#4-part-easy-model-api), [`models`](#5-models-model-hierarchy), [`anim`](#6-anim-easy-animation-api), [`animations`](#7-animations-playback-controller) | ควบคุมกระดูก 3D, ปรับตำแหน่ง/องศา/ขนาด, และเล่นแอนิเมชัน |
| **3. อีเวนต์ & ตัวตั้งเวลา** | [`on`](#8-on-easy-events-api), [`events`](#9-events-event-bus), [`task`](#10-task-scheduler) | ดักจับเหตุการณ์ในเกม (Tick, Render, Input, Damage) และตั้งเวลา |
| **4. ตัวละคร, โลก & ผู้เล่น** | [`minecraft`](#11-minecraft-bridge-api), [`player`](#12-player-state), [`world`](#13-world-environment), [`host`](#14-host-player-context) | ข้อมูลตำแหน่ง, ความเร็ว, เลือด, ท่าทาง, บล็อก, และแสงในโลก |
| **5. กล้อง, ป้ายชื่อ & หน้าจอ** | [`avatar`](#15-avatar-configuration), [`nameplate`](#16-nameplate-system), [`action_wheel`](#17-action_wheel-radial-menu), [`ui`](#18-ui-palette--canvas) | ตั้งค่ากล้อง, First-Person, ป้ายชื่อลอย, วงล้อคำสั่ง และ UI |
| **6. เวกเตอร์ & เมทริกซ์** | [`vector` / `vec`](#19-vector--vec-3d-math), [`matrix4` / `matrices`](#20-matrix4--matrices-linear-algebra) | เวกเตอร์ 2D, 3D, 4D, การแปลงตำแหน่ง, หมุน, สเกล และสี Hex |
| **7. ฟิสิกส์, เสียง & ไมโครโฟน** | [`rig`](#21-rig-native-spring-physics), [`voice` / `microphone`](#22-voice--microphone-lip-sync), [`sounds`](#23-sounds-audio-system), [`particles`](#24-particles-effect-system) | ฟิสิกส์สปริงหู/หาง, ตรวจจับเสียงไมค์ขยับปาก, เล่นเสียง, และอนุภาค |
| **8. เรนเดอร์ 2D/3D & เท็กซ์เจอร์** | [`render`](#25-render-custom-render-api), [`textures`](#26-textures-dynamic-canvas), [`vanilla_model`](#27-vanilla_model-masking) | วาด HUD, บาร์เลือด, เท็กซ์เจอร์สด และซ่อนตัวละครเดิม |
| **9. ข้อมูล, เน็ตเวิร์ก & ประสิทธิภาพ** | [`state` / `synced`](#28-state--synced-variables), [`pings` / `network`](#29-pings--network-multiplayer-rpc), [`data`](#30-data-persistent-storage), [`profiler`](#31-profiler-performance-metrics) | ซิงก์ออนไลน์ข้ามผู้เล่น, เซฟข้อมูลถาวร, และตรวจวัด FPS/Memory |
| **10. ฟังก์ชันยูทิลิตี** | [Global Utility Functions](#32-global-utility-functions) | `print`, `log`, `logTable`, `toJson`, `parseJson`, `math.lerp` ฯลฯ |

---

## 1. เอนจิน & สิทธิ์ความปลอดภัย

### 1. `shyne` (Core Engine)
ออบเจ็กต์แกนกลางสำหรับตรวจเช็กความสามารถของเอนจิน และการตั้งค่าเริ่มต้น
```lua
-- ตรวจสอบเวอร์ชันเอนจิน
print("Shyne Engine Version:", shyne.api.version)

-- ตรวจสอบว่ารองรับโมดูลที่ต้องการหรือไม่
if shyne.api.supports("render", ">=1.4") then
  -- รันโค้ดเรนเดอร์ขั้นสูง
end

-- บังคับว่าต้องมีโมดูล (ถ้าไม่มีจะแจ้งเตือนอย่างปลอดภัย)
shyne.api.require("rig", ">=1.3")

-- ตั้งค่าอวตารแบบรวดเร็วใน 1 บรรทัด (Quick Setup)
shyne.setup({
  walk_anim = true,       -- เปิดแอนิเมชันเดินอัตโนมัติ
  blink = true,           -- เปิดการกะพริบตาอัตโนมัติ
  hide_vanilla = true,    -- ซ่อนโมเดลตัวละครเดิมของ Minecraft
  first_person_arm = true -- แสดงแขนโมเดลในมุมมองบุคคลที่หนึ่ง
})
```

### 2. `result` (Safe Execution)
ห่อหุ้มผลลัพธ์และจัดการ Error อย่างเป็นระบบ
```lua
-- รันฟังก์ชันอย่างปลอดภัย คล้าย pcall แต่ส่งคืน Result Object
local res = result.try(function()
  return 10 / 2
end)

if res.ok then
  print("ผลลัพธ์:", res.value)
else
  print("เกิดข้อผิดพลาด:", res.error.message)
end
```

### 3. `permissions` (Sandbox Security)
ตรวจสอบสิทธิ์การเข้าถึงฟีเจอร์ที่ต้องได้รับอนุญาตจากผู้เล่น
```lua
if permissions.has("custom_sounds") then
  -- เล่นเสียงเฉพาะ
end

-- ดูรายการสิทธิ์ทั้งหมดที่อวตารนี้ได้รับ
local granted_list = permissions.list()
```

---

## 2. ชิ้นส่วนโมเดล & แอนิเมชัน

### 4. `part` (Easy Model API)
ทางลัดสำหรับเข้าถึงชิ้นส่วนกระดูกโมเดลอย่างรวดเร็ว (ไม่ต้องพิมพ์ path ยาว)
```lua
-- ควบคุมการหมุน (องศา)
part("Head"):rot(0, 45, 0)

-- ควบคุมตำแหน่ง (Units)
part("Tail"):pos(0, 2, -1)

-- ควบคุมขนาด (Scale)
part("Wings"):scale(1.2, 1.2, 1.2)

-- ซ่อน / แสดงผล
part("Hat"):hide()
part("ArmorGlow"):show()
part("Cape"):visible(false)

-- ปรับความโปร่งใส (Opacity 0.0 - 1.0)
part("GhostBody"):opacity(0.6)
```

### 5. `models` (Model Hierarchy)
เข้าถึงโครงสร้างกระดูกโมเดลแบบเต็มรูปแบบตามลำดับชั้นของ Blockbench
```lua
-- เข้าถึงชิ้นส่วนตามลำดับ Tree
models.model.Head:setRot(0, 20, 0)
models.model.Head.LeftEar:setRot(15, 0, 0)

-- ย้อมสีชิ้นส่วน (RGB ค่า 0.0 - 1.0)
models.model.Body:setColor(1, 0.8, 0.8)

-- สลับเท็กซ์เจอร์สด
models.model.Body:setPrimaryTexture(textures["alternate_skin"])
```

### 6. `anim` (Easy Animation API)
สั่งเล่นแอนิเมชันแบบสั้นกระชับ
```lua
-- เล่นแอนิเมชันทันที
anim("Wave"):play()

-- เล่นแบบวนลูปพร้อมปรับความเร็ว
anim("Fly", {
  loop = true,
  speed = 1.2,
  play = true
})
```

### 7. `animations` (Playback Controller)
เข้าถึงและควบคุมตัวควบคุมแอนิเมชันเต็มรูปแบบ
```lua
local dance = animations.model.dance

dance:play()          -- เริ่มเล่น
dance:pause()         -- พักชั่วคราว
dance:stop()          -- หยุดและรีเซ็ต
dance:speed(1.5)      -- เพิ่มความเร็ว 1.5 เท่า

local is_playing = dance:isPlaying()
local current_time = dance:getTime()
```

---

## 3. อีเวนต์ & ตัวตั้งเวลา

### 8. `on` (Easy Events API)
ดักจับเหตุการณ์ในเกมแบบกระชับ
```lua
-- ทำงานทุก Game Tick (20 ครั้งต่อวินาที)
on("tick", function()
  -- ตรรกะเกม
end)

-- ทำงานทุกเฟรมการเรนเดอร์ (ตาม FPS จอ)
on("render", function(delta, context)
  -- delta: ค่าระหว่างเฟรม, context: สภาพแวดล้อมการเรนเดอร์
end)
```

### 9. `events` (Event Bus)
ระบบกระจายอีเวนต์เต็มรูปแบบ รองรับทั้งการลงทะเบียน ถอดถอน และอีเวนต์พิเศษ
```lua
-- รับอีเวนต์กดปุ่มคีย์บอร์ด
events.KEY_PRESS:register(function(key, action, mods)
  if key == 71 and action == 1 then -- ปุ่ม G ถูกกดลง
    print("กดปุ่ม G!")
  end
end)

-- รับอีเวนต์เมื่อผู้เล่นโดนดาเมจ
events.DAMAGE:register(function(amount, source)
  print("โดนโจมตี " .. amount .. " ดาเมจ จาก " .. source)
end)

-- รับอีเวนต์เมื่อ Totem of Undying ทำงาน
events.TOTEM:register(function()
  print("Totem ป้องกันชีวิตทำงาน!")
end)
```

### 10. `task` (Scheduler)
ตั้งเวลาการทำงานล่วงหน้าตามจำนวน Game Ticks (20 Ticks = 1 วินาที)
```lua
-- ทำงานครั้งเดียวหลังผ่านไป 40 Ticks (2 วินาที)
task.after(40, function()
  print("ผ่านไป 2 วินาทีแล้ว!")
end)

-- ทำงานซ้ำทุกๆ 10 Ticks (0.5 วินาที)
local task_id = task.every(10, function()
  -- ทำงานวนลูป
end)

-- ยกเลิก Task
task.cancel(task_id)
```

---

## 4. ตัวละคร, โลก & ผู้เล่น

### 11. `minecraft` (Bridge API)
บริดจ์หลักเชื่อมโยงกับเอนจินของ Minecraft โดยตรง
```lua
-- ข้อมูลผู้เล่น
local in_water = minecraft.player.in_water()
local is_sneaking = minecraft.player.sneaking()

-- ข้อมูลโลก
local dimension = minecraft.world.dimension() -- "minecraft:overworld"
local time = minecraft.world.time()

-- สั่งเล่นเสียงและสร้างอนุภาค
minecraft.sound.play("minecraft:entity.experience_orb.pickup", 1.0, 1.2)
minecraft.particle.spawn("minecraft:end_rod", { x = 0, y = 2, z = 0 })
```

### 12. `player` (Player State)
ข้อมูลตำแหน่ง เลือด ค่าพลัง และไอเท็มของผู้เล่น
```lua
local pos = player:getPos()          -- Vector3 ตำแหน่งพิกัดในโลก
local rot = player:getRot()          -- Vector3 มุมก้ม-เงย และหันหน้า
local vel = player:getVelocity()     -- Vector3 ความเร็วการเคลื่อนที่
local hp = player:getHealth()        -- เลือดปัจจุบัน
local max_hp = player:getMaxHealth() -- เลือดสูงสุด

-- ตรวจสอบไอเท็มในมือ
local main_hand = player:getHeldItem()
if main_hand and not main_hand:isEmpty() then
  print("ถือไอเท็ม:", main_hand:getName(), main_hand:getId())
end
```

### 13. `world` (World Environment)
ดึงข้อมูลสิ่งแวดล้อม บล็อก และผู้เล่นรอบตัว
```lua
local time = world.getTime()
local light = world.getLightLevel(player:getPos())

-- ค้นหาผู้เล่นรอบตัวในระยะ 32 บล็อก
local nearby_players = world.getPlayers(32)
for _, p in ipairs(nearby_players) do
  print("พบผู้เล่น:", p:getName())
end
```

### 14. `host` (Host Player Context)
คำสั่งและข้อมูลที่เรียกใช้ได้เฉพาะฝั่งผู้เล่นที่เป็นเจ้าของอวตาร
```lua
if host:isHost() then
  local chat_open = host:isChatOpen()     -- แชตเปิดอยู่หรือไม่
  local mic_active = host:isVoiceActive() -- กำลังพูดไมค์หรือไม่
  host:sendChat("สวัสดีทุกคนในเซิร์ฟเวอร์!")
end
```

---

## 5. กล้อง, ป้ายชื่อ & หน้าจอ

### 15. `avatar` (Avatar Configuration)
ตั้งค่าพฤติกรรมกล้อง การเรนเดอร์ และการแสดงผลเฉพาะตัว
```lua
-- ตั้งค่ากล้อง
avatar.camera.configure({
  first_person_arm = true,       -- แสดงแขนโมเดลในมุมมองบุคคลที่หนึ่ง
  hide_head = true,              -- ซ่อนหัวในมุมมองบุคคลที่หนึ่ง
  offset = vector.new(0, 0, 0)
})

-- ดึงสีประจำตัวอวตาร
local color = avatar:getColor()
```

### 16. `nameplate` (Nameplate System)
ปรับแต่งป้ายชื่อลอยบนหัว ในหน้าต่างแชต และในแท็บรายชื่อผู้เล่น
```lua
-- ป้ายชื่อลอยบนหัว (In-World Overhead Nameplate)
nameplate.ENTITY
  :setText("จอมเวทพฤกษา")
  :setColor(0x55FF55)
  :setBadge("★")
  :setOutline(true)
  :setShadow(true)
  :setVisible(true)

-- ป้ายชื่อในห้องแชต
nameplate.CHAT:setBadge("[VIP]"):setColor("#FFAA00")

-- ป้ายชื่อในแท็บรายชื่อ (Tab List)
nameplate.LIST:setText("Online Pro"):setBadge("⚡")
```

### 17. `action_wheel` (Radial Menu)
เมนูวงล้อคำสั่ง 8 ช่อง ควบคุมผ่านปุ่ม `G`
```lua
local wheel = action_wheel.newPage()

wheel:newAction(1)
  :title("โบกมือ (Wave)")
  :item("minecraft:feather")
  :setOnLeftClick(function()
    anim("Wave"):play()
  end)

wheel:newAction(2)
  :title("สลับปีก (Toggle Wings)")
  :item("minecraft:elytra")
  :setOnToggle(function(state)
    part("Wings"):visible(state)
  end)

action_wheel.setPage(wheel)
```

### 18. `ui` (Palette & Script Canvas)
สร้างหน้าจอ Palette ปรับแต่งชุด หรือหน้าต่าง UI เต็มจอที่เขียนด้วยสคริปต์
```lua
-- ลงทะเบียน Action เข้าสู่เมนู Palette (กด Esc > อวตาร)
ui.palette.action("สลับชุดคอสตูม", function()
  part("Costume"):visible(not part("Costume"):visible())
end)
```

---

## 6. เวกเตอร์ & เมทริกซ์

### 19. `vector` / `vec` (3D Math)
การคำนวณเวกเตอร์ 2D, 3D, 4D
```lua
local v1 = vector.new(1, 2, 3)
local v2 = vec(4, 5, 6)

-- บวก ลบ คูณ หารเวกเตอร์
local v3 = v1 + v2
local length = v3:length()
local normalized = v3:normalized()

-- Swizzling สลับแกนอย่างอิสระ
local zyx = v3.zyx -- ได้ vec3(z, y, x)

-- แปลงสี Hex <-> RGB
local rgb = vectors.hexToRGB("#55FFFF")
local hex = vectors.rgbToHex(rgb)
```

### 20. `matrix4` / `matrices` (Linear Algebra)
คำนวณเมทริกซ์การแปลงรูปทรงเรขาคณิต 4x4
```lua
local mat = matrices.mat4()
  :translate(0, 10, 0)
  :rotateY(45)
  :scale(2, 2, 2)

local result_pos = mat * vec(1, 0, 0)
local inv = mat:inverted()
```

---

## 7. ฟิสิกส์, เสียง & ไมโครโฟน

### 21. `rig` (Native Spring Physics)
ระบบฟิสิกส์สปริงระดับ Native Engine สำหรับหู หาง ปีก และเส้นผม ขยับตามแรงเหวี่ยงตัวละคร
```lua
-- เปิดฟิสิกส์ที่กระดูกหาง ขยับนุ่มนวลอัตโนมัติ
models.model.Tail:setPhysics({
  damping = 0.85,    -- แรงต้านการเคลื่อนที่ (0.0 - 1.0)
  stiffness = 0.25,  -- ความแข็งสปริงดีดกลับ
  gravity = 0.1      -- แรงโน้มถ่วงจำลอง
})
```

### 22. `voice` / `microphone` (Lip-Sync)
ตรวจจับเสียงพูดไมโครโฟนเพื่อให้อวตารขยับปากพูดแบบเรียลไทม์
```lua
on("tick", function()
  if voice.isSpeaking() then
    local vol = voice.getVolume()
    part("Mouth"):scale(1, 1 + vol * 2.5, 1)
  else
    part("Mouth"):scale(1, 1, 1)
  end
end)
```

### 23. `sounds` (Audio System)
เล่นเสียงเอฟเฟกต์ตามพิกัด 3D (พร้อมโควตา Rate-Limit ป้องกันแล็ก)
```lua
sounds:playSound("minecraft:entity.allay.item_given", player:getPos(), 1.0, 1.0)
```

### 24. `particles` (Effect System)
สปอว์นอนุภาคในโลก
```lua
particles:newParticle("minecraft:flame", player:getPos() + vec(0, 1, 0), vec(0, 0.1, 0))
```

---

## 8. เรนเดอร์ 2D/3D & เท็กซ์เจอร์

### 25. `render` (Custom Render API)
วาดองค์ประกอบกราฟิก 2D บนหน้าจอผู้เล่น (HUD) หรือวัตถุ 3D ในโลก
```lua
-- วาดกรอบสี่เหลี่ยมบนหน้าจอ (HUD)
render.rect("mana_bar", {
  x = 20, y = 40, width = 120, height = 10,
  color = { 0.2, 0.6, 1.0, 0.9 }
})

-- วาดข้อความเรืองแสง
render.text("mana_label", {
  x = 25, y = 42,
  text = "MANA: 100/100",
  shadow = true
})
```

### 26. `textures` (Dynamic Canvas)
วาดและแก้ไขพิกเซลบนภาพ Texture ได้แบบ Real-time
```lua
local canvas = textures:newTexture("meter", 16, 16)
canvas:fill(0, 0, 0, 1)        -- เทสีพื้นหลังดำ
canvas:setPixel(4, 4, 1, 0, 0) -- ระบายพิกเซลสีแดง
canvas:apply()                 -- อัปโหลดขึ้น GPU
```

### 27. `vanilla_model` (Masking)
ซ่อนชิ้นส่วนตัวละครเดิมของ Minecraft ให้โมเดลอวตารแสดงผลอย่างสะอาดตา
```lua
vanilla_model.PLAYER:setVisible(false)  -- ซ่อนตัวละครเดิมทั้งหมด
vanilla_model.CAPE:setVisible(false)    -- ซ่อนผ้าคลุม
vanilla_model.ELYTRA:setVisible(false)  -- ซ่อนปีก Elytra
vanilla_model.ARMOR:setVisible(false)   -- ซ่อนชุดเกราะ
```

---

## 9. ข้อมูล, เน็ตเวิร์ก & ประสิทธิภาพ

### 28. `state` & `synced`
ตัวแปรสถานะภายในเครื่อง และตัวแปรที่ซิงก์ข้ามออนไลน์
```lua
-- เก็บตัวแปรเฉพาะเครื่อง
state.set("menu_opened", true)
local is_opened = state.get("menu_opened", false)

-- ซิงก์ตัวแปรให้เพื่อนในเซิร์ฟเวอร์ทุกคนเห็นค่าตรงกัน
synced.set("current_form", "super_mode")
local mode = synced.get("current_form", "normal")
```

### 29. `pings` & `network` (Multiplayer RPC)
ส่งคำสั่งผ่านเครือข่ายออนไลน์แบบ Type-Safe
```lua
-- ประกาศฟังก์ชัน Ping ซิงก์ข้ามเครื่อง
function pings.cast_spell(spell_name, power)
  anim(spell_name):play()
  print("ร่ายเวทมนตร์:", spell_name, "ระดับ:", power)
end

-- สั่งเรียกใช้งาน
pings.cast_spell("Fireball", 100)
```

### 30. `data` (Persistent Storage)
บันทึกและโหลดข้อมูลถาวรเฉพาะเครื่องของผู้เล่น (เก็บเป็น JSON ปลอดภัย)
```lua
data:save("total_coins", 2500)
local coins = data:load("total_coins") or 0
```

### 31. `profiler` (Performance Metrics)
ตรวจสอบการทำงานของสคริปต์และงบการเรนเดอร์ เพื่อไม่ให้อวตารทำเครื่องหน่วง
```lua
local stats = profiler:getMetrics()
print("เวลาประมวลผลสคริปต์ (ms):", stats.lua_time_ms)
print("จำนวนงานเรนเดอร์:", stats.render_tasks_count)
```

---

## 10. ฟังก์ชันยูทิลิตี (Global Utility Functions)

```lua
-- แสดงข้อความลง Console / Log
print("ข้อความทั่วไป")
log("บันทึกข้อมูล")
logTable({ name = "Shyne Avatar", level = 50 })

-- แปลงตารางเป็น JSON และแปลงกลับ
local json_string = toJson({ id = "shyne", count = 5 })
local table_data = parseJson(json_string)

-- คณิตศาสตร์และแอนิเมชัน
local lerped = math.lerp(0, 100, 0.5)   -- ค่าเฉลี่ยการเคลื่อนไหว (ได้ 50)
local clamped = math.clamp(120, 0, 100) -- ล็อกค่าให้อยู่ในช่วง (ได้ 100)
local sign = math.sign(-25)             -- เครื่องหมายบวก/ลบ (ได้ -1)
local rounded = math.round(4.6)         -- ปัดเศษตัวเลข (ได้ 5)
```

---

> 📖 **เอกสารที่เกี่ยวข้อง:**
> - [คู่มือการเขียนสคริปต์ Shyne Native Lua API Standard](SHYNE_LUA_API_TH.md)
> - [คู่มือ Custom Render API 1.4](CUSTOM_RENDER_API_TH.md)
> - [คู่มือ Native Rig & Physics API 1.3](RIG_API_TH.md)
> - [รวมตัวอย่างสคริปต์สำเร็จรูป (Script Recipes)](../guides/SCRIPT_RECIPES_TH.md)
