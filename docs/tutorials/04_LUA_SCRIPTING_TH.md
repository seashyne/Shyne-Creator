# 📜 บทเรียนที่ 4: การเขียนสคริปต์ Lua ตั้งแต่เริ่มต้น (Lua Scripting & Events)

สำหรับ Creator ที่ต้องการความสามารถขั้นสูง เช่น **การควบคุมชิ้นส่วนกระดูกแบบไดนามิก, การทำวงล้อ Action Wheel, การเล่นเสียง Sound Effect, หรือการตรวจจับเหตุการณ์ในเกม** บทเรียนนี้จะสอนการเขียนสคริปต์ภาษา Lua บน Shyne Standard ตั้งแต่ศูนย์จนใช้งานได้จริง

---

## 📌 สารบัญบทเรียน
1. [ตำแหน่งไฟล์สคริปต์และการเปิดใช้งาน](#1-ตำแหน่งไฟล์สคริปต์และการเปิดใช้งาน)
2. [วงจรการทำงาน (Lifecycle Events: TICK, RENDER, KEY_PRESS)](#2-วงจรการทำงาน-lifecycle-events)
3. [การควบคุมชิ้นส่วนโมเดล (Position, Rotation, Scale, Visibility)](#3-การควบคุมชิ้นส่วนโมเดล)
4. [การเขียนระบบ Action Wheel 8 ทิศทาง](#4-การเขียนระบบ-action-wheel-8-ทิศทาง)
5. [การสั่งเล่นเสียง Sound Effect และ Particle](#5-การสั่งเล่นเสียง-sound-effect-และ-particle)
6. [การตรวจสอบสถานะของผู้เล่น (Health, Movement, Flying)](#6-การตรวจสอบสถานะของผู้เล่น)

---

## 1. ตำแหน่งไฟล์สคริปต์และการเปิดใช้งาน

สร้างโฟลเดอร์ชื่อ `scripts/` ในโฟลเดอร์ Avatar ของคุณ:
```text
my_avatar/
├── avatar.json
├── model.bbmodel
└── scripts/
    └── main.lua      (ไฟล์สคริปต์หลัก)
```

ใน `avatar.json` ให้ระบุ path ของไฟล์สคริปต์:
```json
{
  "name": "Scripted Fox",
  "model": "model.bbmodel",
  "scripts": ["scripts/main.lua"]
}
```

---

## 2. วงจรการทำงาน (Lifecycle Events)

สคริปต์ Lua ของ Shyne Creator ทำงานผ่านระบบ Event-Driven:

```lua
-- 1. ทำงานทันทีที่ Avatar ถูกโหลดเข้าเกม (Initialization)
print("Avatar loaded successfully!")

-- 2. ทำงานทุกๆ Game Tick (20 ครั้งต่อวินาที) - เหมาะสำหรับคำนวณ Logic
events.TICK:register(function()
    -- ใส่โค้ดคำนวณสเตท หรือนับเวลาที่นี่
end)

-- 3. ทำงานทุกๆ Render Frame (ตาม FPS ของจอ) - เหมาะสำหรับขยับกระดูกให้นุ่มนวล
events.RENDER:register(function(delta)
    -- delta คือเศษส่วนเวลา (0.0 ถึง 1.0) สำหรับทำ Interpolation
end)

-- 4. ทำงานเมื่อผู้เล่นกดปุ่มบนคีย์บอร์ด
events.KEY_PRESS:register(function(key, action)
    -- key: รหัสปุ่ม, action: 1 (กดลง), 0 (ปล่อย)
end)
```

---

## 3. การควบคุมชิ้นส่วนโมเดล

คุณสามารถเข้าถึงกระดูกทุกชิ้นในโมเดลผ่านออบเจกต์ `models` (หรือ `model`):

```lua
-- เข้าถึงกระดูก Head และหูข้างซ้าย
local leftEar = models.Head.ear_left

events.RENDER:register(function(delta)
    -- ซ่อนหรือแสดงชิ้นส่วน
    leftEar:setVisible(true)

    -- หมุนกระดูก (หน่วยเป็นองศา: X, Y, Z)
    -- ตัวอย่าง: ให้หูกระดิกตามฟังก์ชันไซน์คลื่นความถี่
    local time = client:getSystemTime() / 200
    local twitch = math.sin(time) * 15
    leftEar:setRot(0, 0, twitch)

    -- ปรับตำแหน่ง (Offset: X, Y, Z)
    leftEar:setPos(0, 0.5, 0)

    -- ปรับขนาด (Scale: X, Y, Z)
    leftEar:setScale(1.1, 1.1, 1.1)
end)
```

---

## 4. การเขียนระบบ Action Wheel 8 ทิศทาง

วงล้อคำสั่ง Action Wheel ช่วยให้ผู้เล่นสามารถเปลี่ยนท่าทางหรือเปิด-ปิดอุปกรณ์ได้ง่ายๆ เมื่อกดปุ่ม `G`:

```lua
-- ดึงออบเจกต์หน้า Action Wheel
local page = action_wheel:newPage()
action_wheel:setPage(page)

-- ช่องที่ 1: ปุ่มเปิด-ปิดหมวก
local hatAction = page:newAction(1)
hatAction:setTitle("เปิด/ปิด หมวก")
hatAction:setTexture("minecraft:leather_helmet") -- หรือใส่ path รูป png

local showHat = true
hatAction:setOnLeftClick(function()
    showHat = not showHat
    models.Head.hat:setVisible(showHat)
    print("สถานะหมวก: " .. tostring(showHat))
end)

-- ช่องที่ 2: ปุ่มเล่นท่าเต้น Emote
local danceAction = page:newAction(2)
danceAction:setTitle("ท่าเต้น (Dance)")
danceAction:setTexture("minecraft:music_disc_cat")
danceAction:setOnLeftClick(function()
    animations.model.dance:play()
end)
```

---

## 5. การสั่งเล่นเสียง Sound Effect และ Particle

คุณสามารถเล่นเสียงหรือปล่อยละอองเอฟเฟกต์รอบตัวได้:

```lua
-- เล่นเสียงเอฟเฟกต์ Vanilla (ชื่อเสียง, ความดัง, ความทุ้มแหลม pitch)
sound:playSound("minecraft:entity.experience_orb.pickup", 1.0, 1.2)

-- ปล่อยอนุภาค Particle บริเวณตำแหน่งของผู้เล่น
local pos = player:getPos()
particle:addParticle("minecraft:heart", pos.x, pos.y + 2, pos.z, 0, 0.1, 0)
```

---

## 6. การตรวจสอบสถานะของผู้เล่น

คุณสามารถเขียนเงื่อนไขให้โมเดลตอบสนองต่อสถานะต่างๆ ของตัวละครในเกม:

```lua
events.TICK:register(function()
    -- ตรวจสอบว่าผู้เล่นกำลังบินอยู่หรือไม่
    if player:isGliding() or player:isFlying() then
        models.Body.wings:setVisible(true)
    else
        models.Body.wings:setVisible(false)
    end

    -- ตรวจสอบว่าเลือดเหลือน้อยกว่า 30% หรือไม่
    local health = player:getHealth()
    local maxHealth = player:getMaxHealth()
    if health / maxHealth < 0.3 then
        -- ให้หูลู่ลงเมื่อบาดเจ็บ
        models.Head.ear_left:setRot(0, 0, -45)
        models.Head.ear_right:setRot(0, 0, 45)
    end
end)
```
