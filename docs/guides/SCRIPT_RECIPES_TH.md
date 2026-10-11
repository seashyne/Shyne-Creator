# 📜 รวมตัวอย่างสคริปต์ Shyne Creator (Script Recipes)

รวมโค้ดตัวอย่าง **Lua Script** สำหรับ Shyne Creator หลากหลายรูปแบบที่ผู้สร้าง Avatar ใช้งานบ่อยที่สุด ตั้งแต่ระบบขยับปากพูด, Action Wheel, การซิงก์ท่าใน Multiplayer, จนถึงการวาด UI บนหน้าจอ

---

## 1. ระบบขยับปากพูดตามไมค์ & แชท (Mouth & Voice Tracking)

### 1.1 สลับโมเดลปากเปิด-ปิดเมื่อกำลังพูด (Voice Activity)
เหมาะสำหรับโมเดลที่มีชิ้นส่วน `mouth_open` และ `mouth_closed` แยกกัน:

```lua
-- ดึงชิ้นส่วนโมเดลปาก
local mouth_open = models.model.root.Head.mouth_open
local mouth_closed = models.model.root.Head.mouth_closed

-- ทำงานทุกเฟรม (RENDER) เพื่อความลื่นไหลระดับ 60fps
events.RENDER:register(function()
    -- ตรวจสอบว่ากำลังพูดไมค์ หรือกำลังเปิดหน้าต่างแชท
    local is_speaking = voice.isSpeaking() or host:isVoiceActive() or host:isChatOpen()

    if is_speaking then
        mouth_open:setVisible(true)
        mouth_closed:setVisible(false)
    else
        mouth_open:setVisible(false)
        mouth_closed:setVisible(true)
    end
end)
```

### 1.2 ขยับอ้าปากกว้างตามความดังของเสียง (Voice Amplitude)
เหมาะสำหรับโมเดลที่ต้องการให้อ้าปากกว้าง-แคบตามระดับเสียงพูดจริง:

```lua
local jaw = models.model.root.Head.Jaw

events.RENDER:register(function()
    if host:isVoiceActive() then
        -- ดึงระดับเสียงปัจจุบัน (0.0 ถึง 1.0)
        local volume = voice.getVolume() or 0.5
        -- อ้าขากรรไกรลงตามระดับเสียง (เช่น ก้มลง 0 ถึง 25 องศา)
        jaw:setRot(volume * 25, 0, 0)
    else
        jaw:setRot(0, 0, 0)
    end
end)
```

---

## 2. ระบบวงล้อ Action Wheel (เมนูวงกลม 8 ช่อง)

### 2.1 วงล้อท่าทางพร้อมเล่นเสียง .ogg และสลับหน้า
กดเปิดด้วยปุ่ม Action Wheel ในเกม (ค่าเริ่มต้นคือปุ่ม `G` หรือ `B`):

```lua
-- สร้างหน้าหลักของวงล้อ
local main_page = action_wheel:newPage("main")
local emote_page = action_wheel:newPage("emotes")
action_wheel:setPage(main_page)

-- ปุ่มที่ 1: เล่นท่าทางคำราม (Roar) พร้อมเสียง .ogg
local act_roar = main_page:newAction(1)
act_roar:setTitle("§cคำราม (Roar)")
act_roar:setItem("minecraft:dragon_head")
act_roar:onLeftClick(function()
    if animations.model.roar then
        animations.model.roar:restart()
    end
    -- เล่นเสียงจาก avatar/sounds/my_roar.ogg
    sounds:playSound("my_roar", 1.0, 1.0)
end)

-- ปุ่มที่ 2: สลับเปิด/ปิดปีก (Toggle Wings)
local wings_visible = false
local act_wings = main_page:newAction(2)
act_wings:setTitle("§bเปิด/ปิดปีก")
act_wings:setItem("minecraft:elytra")
act_wings:onLeftClick(function()
    wings_visible = not wings_visible
    models.model.root.Wings:setVisible(wings_visible)
    sounds:playSound("minecraft:item.armor.equip_elytra", 1.0, 1.0)
end)

-- ปุ่มที่ 8: เปลี่ยนไปหน้า Emotes
local act_next = main_page:newAction(8)
act_next:setTitle("§eหน้าถัดไป →")
act_next:setItem("minecraft:arrow")
act_next:onLeftClick(function()
    action_wheel:setPage(emote_page)
end)

-- ปุ่มย้อนกลับในหน้า Emotes
local act_back = emote_page:newAction(8)
act_back:setTitle("§e← ย้อนกลับ")
act_back:setItem("minecraft:barrier")
act_back:onLeftClick(function()
    action_wheel:setPage(main_page)
end)
```

---

## 3. การควบคุมโมเดลและซ่อนร่างกาย Vanilla (Figura Parity)

### 3.1 ซ่อนร่างกายผู้เล่นปกติเพื่อใช้โมเดลคัสตอมเต็มตัว
```lua
events.ENTITY_INIT:register(function()
    -- ซ่อนร่างกายผู้เล่น Vanilla
    vanilla_model.PLAYER:setVisible(false)
    
    -- ซ่อนผ้าคลุมและปีกเดิม
    vanilla_model.CAPE:setVisible(false)
    vanilla_model.ELYTRA:setVisible(false)
    
    -- ซ่อนเกราะ (ถ้าต้องการให้ชุดของโมเดลเราแสดงชัดเจน)
    vanilla_model.ARMOR:setVisible(false)
end)
```

### 3.2 ขยับชิ้นส่วนโมเดลแบบ Procedural (หางแกว่งตามเวลา)
```lua
local tail = models.model.root.Body.Tail

events.TICK:register(function()
    local time = world.getTime() * 0.1
    -- แกว่งหางซ้ายขวาด้วยฟังก์ชันตรีโกณมิติ
    local yaw = math.sin(time) * 15
    local pitch = math.cos(time * 0.5) * 5
    tail:setRot(pitch, yaw, 0)
end)
```

---

## 4. ระบบซิงก์ท่าทางข้ามเครื่องใน Multiplayer (Network Pings)

เมื่อเล่นบนเซิร์ฟเวอร์ คำสั่งที่กดผ่าน Action Wheel จะต้องส่งให้ผู้เล่นคนอื่นเห็นด้วยผ่าน **Pings RPC**:

```lua
-- 1. ประกาศ Ping ฟังก์ชัน (จะรันบนเครื่องของทุกคนในเซิร์ฟเวอร์)
pings:define("play_emote", { emote_name = "string" }, function(data)
    local anim = animations.model[data.emote_name]
    if anim then
        anim:restart()
    end
end)

-- 2. เมื่อผู้เล่นกดปุ่มใน Action Wheel ให้เรียก pings
local act_wave = main_page:newAction(3)
act_wave:setTitle("โบกมือ (Wave)")
act_wave:setItem("minecraft:player_head")
act_wave:onLeftClick(function()
    -- ส่งให้ทุกคนในเซิร์ฟเวอร์เห็นพร้อมกัน
    pings.play_emote({ emote_name = "wave" })
end)
```

---

## 5. การดักจับปุ่มและอินพุต (Keyboard & Mouse Events)

### 5.1 ดักจับการกดปุ่มคีย์บอร์ด (เช่น ปุ่ม R เพื่อสลับอาวุธ)
```lua
events.KEY_PRESS:register(function(key, action, mods)
    -- action == 1 คือกดลง (Press)
    -- GLFW Key Code 82 คือปุ่ม 'R'
    if key == 82 and action == 1 then
        print("§e[Avatar] กดปุ่ม R: สลับโหมดอาวุธ!")
        if animations.model.draw_weapon then
            animations.model.draw_weapon:restart()
        end
    end
end)
```

### 5.2 ดักจับการหมุนลูกกลิ้งเมาส์ (Mouse Scroll)
```lua
events.MOUSE_SCROLL:register(function(dx, dy)
    if dy > 0 then
        -- เลื่อนเมาส์ขึ้น
    elseif dy < 0 then
        -- เลื่อนเมาส์ลง
    end
end)
```

---

## 6. อีเวนต์การเล่นและการต่อสู้ (Combat & Item Events)

### 6.1 เปลี่ยนสีหน้าเจ็บปวดเมื่อโดนโจมตี (Entity Damage)
```lua
local face_hurt = models.model.root.Head.FaceHurt
local face_normal = models.model.root.Head.FaceNormal

events.ENTITY_DAMAGE:register(function(amount, source, fatal)
    -- สลับไปหน้าเจ็บปวด
    face_hurt:setVisible(true)
    face_normal:setVisible(false)
    
    -- ตั้งเวลานับถอยหลัง 15 tick (0.75 วินาที) แล้วเปลี่ยนกลับ
    tasks:schedule(15, function()
        face_hurt:setVisible(false)
        face_normal:setVisible(true)
    end)
end)
```

### 6.2 เล่นเอฟเฟกต์เมื่อใช้งานไอเทม (กินอาหาร หรือยิงธนู)
```lua
events.ITEM_USE:register(function(item_id, hand)
    -- ตรวจสอบเมื่อกิน Golden Apple
    if item_id:find("golden_apple") then
        particles:spawn("minecraft:totem_of_undying", player:getPos())
    end
end)
```

---

## 7. การวาด UI บนหน้าจอ (Custom Render API & HUD)

วาดแถบพลังหรือรูปภาพบนหน้าจอของผู้เล่น (ต้องการ permission `hud_render`):

```lua
events.RENDER:register(function(delta, context)
    -- วาดเฉพาะในมุมมองหน้าจอ HUD ปกติ
    if context ~= "HUD" then return end
    
    local screen_w = client:getScaledWidth()
    local screen_h = client:getScaledHeight()
    
    -- วาดกล่องพื้นหลังสีดำโปร่งใสบริเวณมุมซ้ายล่าง
    render:rect(20, screen_h - 40, 120, 16, 0xAA000000)
    
    -- วาดแถบพลังสีฟ้า (Cyan)
    local mana_percent = 0.75
    render:rect(22, screen_h - 38, 116 * mana_percent, 12, 0xFF55E7FF)
    
    -- วาดข้อความกำกับ
    render:text("MANA: 75/100", 25, screen_h - 36, 0xFFFFFFFF)
end)
```

---

## สรุปภาพรวม Permission ที่ต้องใส่ใน `avatar.json`

หากสคริปต์ของคุณใช้ฟังก์ชันขั้นสูง อย่าลืมเปิด permission ใน `avatar.json`:

```json
{
  "standard": "2.0",
  "name": "My Scripted Avatar",
  "main": "script.lua",
  "permissions": [
    "sound",
    "particle",
    "hud_render"
  ]
}
```
