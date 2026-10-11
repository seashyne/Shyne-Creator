# ⚡ บทเรียนที่ 6: ฟิสิกส์และการตกแต่งขั้นสูง (Physics, HUD & Profiler)

บทเรียนสุดท้ายนี้จะพาไปดูฟีเจอร์ระดับสูงของ **Shyne Creator**: การสร้าง **Spring Physics** ให้หางและหูเด้งดึ๋งตามการขยับตัว, การเรนเดอร์แขนมุมมองบุคคลที่หนึ่ง (**First-Person Arm**), การวาด **Custom HUD** บนหน้าจอ, และการตรวจวัดประสิทธิภาพด้วย **Avatar Profiler**

---

## 📌 สารบัญบทเรียน
1. [การใส่ Spring Physics ให้หูและหาง (Native Rig API 1.3)](#1-การใส่-spring-physics-ให้หูและหาง)
2. [การปรับแต่งแขนมุมมองบุคคลที่หนึ่ง (First-Person Arm Rendering)](#2-การปรับแต่งแขนมุมมองบุคคลที่หนึ่ง)
3. [การวาด Custom HUD (หลอดเลือด, แถบมานา, ไอคอนสกิล)](#3-การวาด-custom-hud)
4. [การตรวจงบ Draw Calls และ Memory ด้วย Avatar Profiler](#4-การตรวจงบ-draw-calls-และ-memory)
5. [การบรรจุและแพ็กไฟล์เป็น .zip เตรียมเผยแพร่](#5-การบรรจุและแพ็กไฟล์เป็น-zip)

---

## 1. การใส่ Spring Physics ให้หูและหาง

ไม่ต้องคอยทำ Keyframe แอนิเมชันหางตลอดเวลา! Shyne Creator รองรับฟิสิกส์สปริงในตัว:

```lua
-- กำหนด Spring Physics ให้กระดูกหาง
local tail = models.Body.tail_root

-- เปิดใช้งาน physics พร้อมตั้งค่าความตึง (Stiffness) และความหนืด (Damping)
if tail.setPhysics then
    tail:setPhysics({
        enabled = true,
        stiffness = 0.15,  -- ค่าความเด้งกลับ (0.01 - 1.0)
        damping = 0.85,    -- ค่าแรงต้านลดการสั่น (0.5 - 0.99)
        gravity = -0.05    -- แรงโน้มถ่วงถ่วงลงล่าง
    })
end
```
เมื่อผู้เล่นวิ่ง, เลี้ยวตัวกะทันหัน หรือกระโดด หางจะแกว่งตามแรงเฉื่อย (Inertia) และแรงเหวี่ยงอย่างสมจริงเสมือนมีชีวิตจริง

---

## 2. การปรับแต่งแขนมุมมองบุคคลที่หนึ่ง

เมื่อผู้เล่นเปลี่ยนเป็นมุมมองบุคคลที่หนึ่ง (First-Person):
* Shyne Creator จะค้นหากระดูกชื่อ `RightArm` หรือ `RightArmFirstP` / `fpModel` ในโมเดลของคุณ
* นำมาเรนเดอร์แทนมือ Steve/Alex ของ Minecraft อัตโนมัติ

### วิธีตรวจสอบในสคริปต์:
```lua
events.RENDER:register(function()
    if renderer:isFirstPerson() then
        -- ซ่อนชิ้นส่วนที่อาจเกะกะสายตาในมุมมองคนแรก (เช่น หมวกปีกกว้าง หรือไหล่เสื้อ)
        models.Head.wide_hat:setVisible(false)
    else
        models.Head.wide_hat:setVisible(true)
    end
end)
```

---

## 3. การวาด Custom HUD บนหน้าจอ

คุณสามารถวาดองค์ประกอบกราฟิก เช่น หลอดพลัง, กรอบเวทมนตร์, หรือสัญลักษณ์ UI บนหน้าจอของผู้เล่นได้ผ่าน **Custom Render API**:

```lua
-- วาดกรอบสี่เหลี่ยมเรืองแสงบน HUD
local hudGroup = render:createHudGroup("my_avatar_hud")

events.RENDER:register(function()
    hudGroup:clear()
    
    -- ดึงขนาดหน้าจอปัจจุบัน
    local screenWidth = client:getScaledWidth()
    local screenHeight = client:getScaledHeight()

    -- วาดสี่เหลี่ยมผืนผ้า (X, Y, กว้าง, สูง, สี RGBA)
    -- ตำแหน่ง: มุมล่างขวาของหน้าจอ
    local boxX = screenWidth - 110
    local boxY = screenHeight - 30
    hudGroup:drawRect(boxX, boxY, 100, 20, 0, 0, 0, 0.6) -- พื้นหลังดำโปร่งใส
    
    -- วาดแถบพลังสีฟ้า
    local manaPercent = 0.75
    hudGroup:drawRect(boxX + 2, boxY + 2, 96 * manaPercent, 16, 0.2, 0.6, 1.0, 0.9)
end)
```

---

## 4. การตรวจงบ Draw Calls และ Memory

เพื่อความมั่นใจว่า Avatar ของคุณจะไม่ทำให้เครื่องของผู้เล่นอื่นกระตุก:
1. เข้าเกมแล้วกด `Esc -> อวตาร -> วิเคราะห์ประสิทธิภาพ (Profiler)`
2. คุณจะเห็นตัวชี้วัดสำคัญ:
   * **Draw Calls:** ควรอยู่ระหว่าง 1 - 4 ต่อ Avatar
   * **Render Budget (ms):** เวลาที่ใช้ในการเรนเดอร์ต่อเฟรม (ควรน้อยกว่า 0.5 ms)
   * **Memory Cached Textures:** ขนาดรูปภาพที่อยู่ในหน่วยความจำ RAM

---

## 5. การบรรจุและแพ็กไฟล์เป็น .zip เตรียมเผยแพร่

เมื่อโมเดลของคุณเสร็จสมบูรณ์และทดสอบเรียบร้อย:
1. รวมไฟล์ทั้งหมดในโฟลเดอร์ Avatar:
   * `avatar.json`
   * `model.bbmodel`
   * `texture.png`
   * โฟลเดอร์ `scripts/` (ถ้ามี)
2. บีบอัดเป็นไฟล์ `.zip` (เช่น `MyCoolAvatar.zip`)
3. ส่งให้เพื่อน หรือนำไปแชร์ใน Discord / Cloud ได้ทันที!
   * ผู้รับเพียงแค่นำไฟล์ `.zip` ไปวางในโฟลเดอร์ `shyne-mods/avatars/` ก็พร้อมเล่นได้ทันทีโดยไม่ต้องแตกไฟล์!
