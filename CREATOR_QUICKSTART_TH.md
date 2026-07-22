# เริ่มสร้าง Avatar บน Shyne Standard 2.0

Shyne Standard 2.0 ใช้แนวทาง model-first: สร้างโมเดลและ animation ใน Blockbench แล้วกำหนดพฤติกรรมใน `avatar.json` งานทั่วไปไม่ต้องมี `script.lua` ตัวเกมใช้ Shyne runtime เอง ไม่ต้องติดตั้ง Figura และไม่รับรองสคริปต์ Figura/compatibility รุ่นเก่า

## 1. สร้างโปรเจกต์

```powershell
.\tools\creator\shyne-creator.ps1 new E:\Minecraft\avatars\my-avatar --id my.avatar --name "My Avatar"
```

เปิด `model.bbmodel` ด้วย Blockbench แล้วสร้าง `avatar.json` แบบ Standard 2.0 โดยเริ่มจาก Zero-Lua ก่อน:

```json
{
  "standard": "2.0",
  "name": "My Avatar",
  "profile": "accessory",
  "model": "model.bbmodel",
  "behavior": {
    "preset": "auto",
    "autoplay": ["Idle"]
  }
}
```

ไม่ต้องใส่ `main` และไม่ต้องสร้าง `script.lua` หาก `behavior` ครอบคลุมงานทั้งหมด Texture จะถูกค้นหาอัตโนมัติและไม่บังคับชื่อไฟล์ หากเป็นโมเดลเต็มตัวให้เปลี่ยน `profile` เป็น `full_body`; โมเดล aquatic ใช้ `merling` อ่านรายละเอียด fields และตัวอย่าง controller ที่ [SHYNE_STANDARD_2_TH.md](SHYNE_STANDARD_2_TH.md)

Shyne เคารพ `visibility`/`export` เริ่มต้นของ group และ element และวาดได้ทั้ง cube กับ mesh วาง `avatar.png` ไว้ข้าง manifest หากต้องการไอคอนรายการ; ระบบจะลดภาพขนาดใหญ่ให้เหมาะกับ GPU โดยอัตโนมัติ

## 2. ตรวจไฟล์ก่อนเข้าเกม

```powershell
.\tools\creator\shyne-creator.ps1 validate E:\Minecraft\avatars\my-avatar
.\tools\creator\shyne-creator.ps1 inspect E:\Minecraft\avatars\my-avatar
```

ตัวตรวจจะเช็ก JSON, model, path traversal, path ชื่อซ้ำ, จำนวน cube/animation, ชื่อ animation ที่ behavior อ้างถึง, ขนาดรวม และ texture ขนาดใหญ่ หากประกาศ `main` จึงตรวจ entry script และ permission ของ Lua เพิ่ม

เมื่อต้องย้ายแพ็กที่คุณมีสิทธิ์แก้ไข ให้นำเข้าเฉพาะ model, hierarchy, texture และ animation แล้วสร้าง behavior ของ Shyne ใหม่ สคริปต์ Figura ไม่ถูกรันและ Standard 2.0 ไม่รับประกัน legacy compatibility หากใช้ `tools/convert_figura_avatar.ps1` ต้องระบุปลายทางใหม่หรือ directory ว่างเสมอ ตัวแปลงจะไม่เขียนทับงานเก่า ดูคำสั่งและกติกาการย้ายใน [SHYNE_STANDARD_2_TH.md](SHYNE_STANDARD_2_TH.md)

## 3. Avatar ส่วนเสริม: หู หาง และปีก

หาก Avatar เป็นของเสริมที่ต้องแสดงร่วมกับตัว Minecraft เดิม ให้เลือก safe profile ใน `avatar.json`:

```json
{
  "standard": "2.0",
  "profile": "accessory"
}
```

ใน Blockbench ให้เลือก bone หลักของของเสริม เช่น `BunnyEars` แล้วตั้ง `parent_type` เป็น `Head` Shyne จะอ่านค่าและผูก bone เข้ากับหัวผู้เล่นอัตโนมัติ หูจึงก้ม เงย และหมุนตามหัว โดยไม่ต้องเขียน Lua เพิ่ม แม้ animation ของหูกำลังควบคุม rotation อยู่ pose หัวก็ยังเป็น parent ของ animation เสมอ

รองรับ `Head`, `Body`, `LeftArm`, `RightArm`, `LeftLeg` และ `RightLeg` เหมาะกับหู หมวก หาง ปีก ของติดแขน และรองเท้า ควรตั้ง `parent_type` ให้ถูกใน Blockbench เพื่อไม่ต้องเขียน Lua

Animation ยังเล่นปกติบน bone ที่ผูกแล้วและเริ่มได้จาก manifest:

```json
"behavior": {
  "autoplay": ["ear_wiggle"]
}
```

เมื่อชื่อ part ซ้ำ ให้ระบุ canonical full path ตาม hierarchy เช่น `model.part("model.Character.Head.BunnyEars")` ชื่อสั้นเหมาะเฉพาะ part ที่มีชื่อไม่ซ้ำ เพื่อไม่ให้ rig ซับซ้อนควบคุมผิด bone

ถ้าต้องการแขนหรือ item-pivot ใน first-person ให้สร้าง bone root แยกชื่อ `LeftArmFP` และ `RightArmFP` พร้อม cube ลูกของแต่ละข้าง Shyne จะใช้เฉพาะ tree นี้ในมุมมองบุคคลที่หนึ่งและซ่อนจากการวาดตัวผู้เล่นปกติ ถ้าไม่มี cube หรือ script ซ่อน root ไว้ จะกลับไปใช้มือ vanilla อัตโนมัติ ดูข้อกำหนดเต็มใน `AVATAR_SYSTEM.md`.

ของลอยหรือ companion ให้สร้าง top-level group แยก เช่น `Companion` แล้วใช้ native SquAPI ของ Shyne (ไม่ต้องวาง `lib/SquAPI.lua` ของ Figura):

```lua
local orb = require("SquAPI").hoverPoint:new(
  model.part("model.Companion"), vector.new(0.8, 1.2, 0),
  0.2, 5, 1, 0.05, true, true
)
orb:setCollisionRadius(0.16)
```

argument ตัวสุดท้ายเปิด collision แบบภาพกับ block/entity; มันไม่แก้ hitbox หรือ physics ของเกม โค้ดนี้เป็น Shyne-native Lua สำหรับงานขั้นสูงและไม่เกี่ยวกับ Figura compatibility

## 4. Render Task

```lua
render.text("status", { text = "Ready", x = 12, y = 12, color = 0xFF55FFFF, shadow = true })
render.item("held", { item = "minecraft:diamond", x = 12, y = 28 })
render.block("block", { block = "minecraft:amethyst_block", x = 32, y = 28 })
render.sprite("logo", { texture = "shyne_creator:textures/gui/shyne_creator_logo.png", x = 52, y = 12, width = 32, height = 32 })
render.line("meter", { from = vector.new(12, 52, 0), to = vector.new(112, 52, 0), color = 0xFF55FFFF, width = 2 })
render.rect("panel", { x = 8, y = 8, width = 128, height = 48, color = 0xC0101728, z_index = -1 })
render.outline("edge", { x = 8, y = 8, width = 128, height = 48, thickness = 2, color = 0xFF55FFFF })

-- งานที่ยึดตำแหน่งในโลกและถูก project มายังหน้าจอ
render.world("marker", { type = "text", text = "Target", position = vector.new(100, 70, 100), color = 0xFFFFFF55, shadow = true })
render.world("route", { type = "line", from = vector.new(100, 70, 100), to = vector.new(110, 70, 110), color = 0xFFFF55FF, width = 2 })

render.remove("status")
render.clear()
```

Avatar ต้องประกาศ `hud_render` สำหรับงานบน HUD และ `world_render` สำหรับงานที่ยึดตำแหน่งโลก ใน Public Share สิทธิ์ `hud_render` ปิดไว้จนกว่าผู้ใช้จะอนุมัติ เพราะสามารถวาดทับหน้าจอเกมได้

เรียก task ด้วย ID เดิมเพื่ออัปเดตโดยไม่สร้าง object ใหม่ เก็บได้สูงสุด 256 tasks ต่อ Avatar แต่เรนเดอร์ไม่เกิน 128 tasks และ 4096 จุดของเส้นต่อเฟรม World task มีระยะเริ่มต้น 128 blocks ปรับได้ด้วย `max_distance` ระหว่าง 8–1024 blocks และถูกล้างอัตโนมัติเมื่อเปลี่ยนหรือรีโหลด Avatar

Custom Render API 1.1 เพิ่ม `rect`, `outline`, `polyline`, `render.update`, task handle, group, layer, opacity, `render.screen()` และ `render.stats()` ดูคู่มือเต็มที่ `CUSTOM_RENDER_API_TH.md`

## 5. Profiler

เปิด `Shyne Settings → Advanced → Avatar Profiler` เพื่อดู FPS, frame time, Lua load/tick/render/event, model render, render tasks, จำนวน task ที่ถูก cull, heap, ขนาดอวตาร และรายการสิ่งที่อาจทำให้ FPS ลด กด `ส่งออก JSON` เพื่อบันทึกรายงานไว้ที่ `.minecraft/shyne-logs/profiler/` ควรพยายามให้ Avatar/frame ต่ำกว่า 4 ms และหลีกเลี่ยง task จำนวนมากที่อัปเดตทุก tick โดยไม่จำเป็น

ตัวอย่างพร้อมใช้: `tools/examples/lua-api-2.0-avatar`, `tools/examples/render-profiler-avatar`, `tools/examples/advanced-render-avatar` และ `tools/examples/responsive-hud-avatar`
