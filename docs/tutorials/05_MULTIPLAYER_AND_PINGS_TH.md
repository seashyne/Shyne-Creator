# 🌐 บทเรียนที่ 5: ระบบออนไลน์และการซิงก์ท่าทางให้เพื่อนเห็น (Multiplayer & Pings RPC)

เมื่อเล่นในโหมด Multiplayer (เซิร์ฟเวอร์ออนไลน์) การสั่งเปลี่ยนสเตทในสคริปต์ปกติ (เช่น การคลิก Action Wheel หรือกดปุ่มคีย์บอร์ด) จะเกิดขึ้นเฉพาะบนเครื่องของคุณ (Client-Side) เท่านั้น หากต้องการให้ **เพื่อนทุกคนในเซิร์ฟเวอร์เห็นท่าทาง เอฟเฟกต์ หรือการเปลี่ยนชุดพร้อมกัน 100%** คุณจำเป็นต้องใช้ระบบ **Pings RPC**

---

## 📌 สารบัญบทเรียน
1. [ทำความเข้าใจ Client-Side vs Multiplayer Sync](#1-ทำความเข้าใจ-client-side-vs-multiplayer-sync)
2. [Pings คืออะไร และทำงานอย่างไร](#2-pings-คืออะไร-และทำงานอย่างไร)
3. [ตัวอย่างการสร้างคำสั่ง Ping พื้นฐาน (เปิด-ปิดปีก/หมวก)](#3-ตัวอย่างการสร้างคำสั่ง-ping-พื้นฐาน)
4. [การส่งข้อมูลตัวแปร (Arguments) ผ่าน Ping](#4-การส่งข้อมูลตัวแปร-arguments-ผ่าน-ping)
5. [การสั่งเล่นท่า Emote ออนไลน์พร้อมเสียง Sound Effect](#5-การสั่งเล่นท่า-emote-ออนไลน์พร้อมเสียง-sound-effect)
6. [ข้อจำกัดด้านความปลอดภัยและ Rate Limiting](#6-ข้อจำกัดด้านความปลอดภัยและ-rate-limiting)

---

## 1. ทำความเข้าใจ Client-Side vs Multiplayer Sync

* **Client-Side Only (เห็นคนเดียว):**
  ถ้าคุณเขียนโค้ดใน Action Wheel ว่า:
  ```lua
  hatAction:setOnLeftClick(function()
      models.Head.hat:setVisible(false) -- มีผลเฉพาะบนจอของคุณ เพื่อนจะยังเห็นหมวกอยู่!
  end)
  ```
* **Multiplayer Sync (ทุกคนเห็นพร้อมกัน):**
  เมื่อใช้ `pings` คำสั่งจะถูกส่งขึ้น Server แล้วกระจาย (Broadcast) ไปยังผู้เล่นทุกคนรอบข้าง ทำให้โมเดลบนจอของทุกคนซิงก์ตรงกันทันที!

---

## 2. Pings คืออะไร และทำงานอย่างไร

ใน Shyne Creator ตาราง `pings` คือฟังก์ชันพิเศษสำหรับเรียกคำสั่งข้ามเครือข่าย:
1. คุณนิยามฟังก์ชันลงใน `pings.<ชื่อฟังก์ชัน> = function(...)`
2. เมื่อต้องการเรียกใช้ ให้สั่ง `pings.<ชื่อฟังก์ชัน>(...)`
3. เครื่องของคุณจะแพ็กข้อมูลส่งไปยัง Server และส่งต่อให้ผู้เล่นทุกคนรันฟังก์ชันนี้พร้อมกัน

---

## 3. ตัวอย่างการสร้างคำสั่ง Ping พื้นฐาน

### การสร้างฟังก์ชัน Ping:
```lua
-- นิยามฟังก์ชันซิงก์การเปิด-ปิดหมวก
function pings.setHatVisible(state)
    -- โค้ดในนี้จะถูกรันบนเครื่องของทุกคนที่มองเห็นตัวละครคุณ
    models.Head.hat:setVisible(state)
end
```

### การเรียกใช้จาก Action Wheel:
```lua
local currentHatState = true

hatAction:setOnLeftClick(function()
    currentHatState = not currentHatState
    -- เรียกใช้ฟังก์ชันผ่าน pings เพื่อให้ส่งข้อมูลขึ้นเซิร์ฟเวอร์
    pings.setHatVisible(currentHatState)
end)
```

---

## 4. การส่งข้อมูลตัวแปร (Arguments) ผ่าน Ping

คุณสามารถส่งพารามิเตอร์ประเภท `boolean`, `number`, หรือ `string` ไปกับคำสั่ง Ping ได้:

```lua
-- ตัวอย่าง: ซิงก์สีของดวงตา (RGB)
function pings.setEyeColor(r, g, b)
    models.Head.eyes:setColor(r, g, b)
end

-- เมื่อผู้เล่นเปลี่ยนสีตาใน UI หรือ Action Wheel:
colorButton:setOnLeftClick(function()
    pings.setEyeColor(1.0, 0.2, 0.5) -- ส่งรหัสสีชมพูไปให้เพื่อนทุกคน
end)
```

---

## 5. การสั่งเล่นท่า Emote ออนไลน์พร้อมเสียง Sound Effect

```lua
-- นิยาม Ping สำหรับเล่นท่าดีใจ
function pings.playCheerEmote()
    -- เล่นแอนิเมชันท่าดีใจ
    animations.model.cheer:play()

    -- เล่นเสียงพลุเอฟเฟกต์ที่ตำแหน่งตัวละคร
    local pos = player:getPos()
    sound:playSound("minecraft:entity.firework_rocket.blast", 1.0, 1.0)
    
    -- ปล่อยละอองประกายไฟ
    for i = 1, 10 do
        particle:addParticle("minecraft:spark", pos.x, pos.y + 1, pos.z, 0, 0.2, 0)
    end
end

-- ผูกเข้ากับปุ่มลัด (Keybind) หรือ Action Wheel
cheerAction:setOnLeftClick(function()
    pings.playCheerEmote()
end)
```

---

## 6. ข้อจำกัดด้านความปลอดภัยและ Rate Limiting

เพื่อป้องกันการแล็กของเซิร์ฟเวอร์และการส่งข้อมูลรัวผิดปกติ:
1. **Recursion Depth Limit:** ระบบป้องกันการเรียก Ping ซ้อนข้างในตัวเอง (`ping-in-ping loop`)
2. **Rate Limit:** ไม่ควรสั่ง Ping ทุกเฟรมใน `events.RENDER` หรือ `events.TICK` ให้สั่งเฉพาะเมื่อเกิดการกระทำจากผู้เล่นเท่านั้น (เช่น การคลิก หรือการกดปุ่ม)
3. **Payload Bounds:** ข้อมูลที่ส่งผ่าน Ping ถูกจำกัดขนาด เพื่อความเสถียรและประหยัดแบนด์วิดท์ของผู้เล่นทุกคนในห้อง
