# เริ่มสร้าง Avatar บน Shyne

Shyne Creator 2.7.32 มีเครื่องมือสร้างโครงโปรเจกต์ ตรวจไฟล์ ดูประสิทธิภาพ และ Custom Render API ในเกม โดย Avatar ยังคงเป็นไฟล์ภายนอกและไม่ถูกฝังในตัว Mod

## 1. สร้างโปรเจกต์

```powershell
.\tools\creator\shyne-creator.ps1 new E:\Minecraft\avatars\my-avatar --id my.avatar --name "My Avatar"
```

เปิด `model.bbmodel` ด้วย Blockbench แล้วแก้ `script.lua` ได้ทันที Texture จะถูกค้นหาอัตโนมัติ ไม่บังคับชื่อไฟล์ ส่วน `avatar.json` ต้องมีเพียง `api_version`, `id`, `main` และ `model` สำหรับโปรเจกต์ปกติ

## 2. ตรวจไฟล์ก่อนเข้าเกม

```powershell
.\tools\creator\shyne-creator.ps1 validate E:\Minecraft\avatars\my-avatar
.\tools\creator\shyne-creator.ps1 inspect E:\Minecraft\avatars\my-avatar
```

ตัวตรวจจะเช็ก JSON, entry script, model, path traversal, path ชื่อซ้ำ, จำนวน cube/animation, ขนาดรวม, texture ขนาดใหญ่ และ permission ที่ Lua เรียกใช้แต่ยังไม่ได้ประกาศ

## 3. Render Task

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

Custom Render API 1.1 เพิ่ม `rect`, `outline`, `polyline`, `render.update`, task handle, group, layer, opacity, `render.screen()` และ `render.stats()` โดยไม่ต้องเปลี่ยน `api_version` ดูคู่มือเต็มที่ `CUSTOM_RENDER_API_TH.md`

## 4. Profiler

เปิด `Shyne Settings → Advanced → Avatar Profiler` เพื่อดู FPS, frame time, Lua load/tick/render/event, model render, render tasks, จำนวน task ที่ถูก cull, heap, ขนาดอวตาร และรายการสิ่งที่อาจทำให้ FPS ลด กด `ส่งออก JSON` เพื่อบันทึกรายงานไว้ที่ `.minecraft/shyne-logs/profiler/` ควรพยายามให้ Avatar/frame ต่ำกว่า 4 ms และหลีกเลี่ยง task จำนวนมากที่อัปเดตทุก tick โดยไม่จำเป็น

ตัวอย่างพร้อมใช้: `tools/examples/render-profiler-avatar`, `tools/examples/advanced-render-avatar` และ `tools/examples/responsive-hud-avatar`
