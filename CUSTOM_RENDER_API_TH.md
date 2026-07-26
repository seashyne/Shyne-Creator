# Shyne Custom Render API 1.3

เอกสารนี้ตรงกับ Shyne Creator `2.9.1-alpha-26.2`

Custom Render API 1.3 เป็นโมดูล render ภายใต้ Shyne Avatar Standard 2.0 Avatar ใหม่ใช้ผ่าน `api: "2.0"` หรือ `api: "latest"` และตรวจได้จาก `shyne.api.supports("render", ">=1.3")`, `render.api_version` หรือ `diagnostics.snapshot().custom_render_api_version`

## Primitive

```lua
render.text("title", { text = "Hello", x = 12, y = 12, color = 0xFFFFFFFF, shadow = true })
render.item("icon", { item = "minecraft:diamond", x = 12, y = 28 })
render.block("block", { block = "minecraft:amethyst_block", x = 32, y = 28 })
render.sprite("logo", { texture = "namespace:textures/logo.png", x = 52, y = 12, width = 32, height = 32 })
render.line("line", { from = vector.new(12, 52, 0), to = vector.new(112, 52, 0), color = 0xFFFFFFFF, width = 2 })
render.rect("background", { x = 8, y = 8, width = 128, height = 48, color = 0xC0101728 })
render.outline("border", { x = 8, y = 8, width = 128, height = 48, thickness = 2, color = 0xFF55FFFF })
render.polyline("graph", {
  points = { vector.new(8, 40, 0), vector.new(32, 20, 0), vector.new(64, 34, 0) },
  color = 0xFF55FFFF, width = 2
})
```

ทุก task รองรับ `visible`, `z_index` หรือ `layer`, `opacity`, `world`, `max_distance` และ `group` ค่า `opacity` มีผลกับ text, sprite, line, rect และ outline ส่วน item/block ใช้ material และ alpha ตาม item renderer ของ Minecraft

## อัปเดต task โดยไม่สร้างใหม่

```lua
render.text("status", { text = "Ready", x = 12, y = 12 })
render.update("status", { text = "Swimming", color = 0xFF55FFFF })

local status = render.task("status")
status:update({ y = 20 })
status:hide()
status:show()
status:remove()
```

การเรียก primitive ด้วย ID เดิมยังคงอัปเดต task เหมือน API รุ่นเดิม ส่วน `render.update` จะรวมเฉพาะค่าที่ส่งมาเข้ากับ options เดิม

## Group และ responsive HUD

```lua
local panel = render.group("panel", { x = 12, y = 12, opacity = 0.9, z_index = 20 })
render.rect("panel.bg", { group = "panel", x = 0, y = 0, width = 120, height = 40, color = 0xE0000000 })
render.text("panel.text", { group = "panel", x = 8, y = 8, text = "Shyne" })

render.on_frame(function()
  local screen = render.screen()
  if screen.ready then panel:update({ x = screen.width - 132 }) end
end)
```

Group รองรับ `x`, `y`, `z`, `scale`, `scale_x`, `scale_y`, `scale_z`, `opacity`, `visible`, `z_index` และ group ซ้อนกันได้สูงสุด 16 ชั้น ระบบตัดวงจร group อัตโนมัติ ควรอัปเดต group เฉพาะเมื่อค่ามีการเปลี่ยนเพื่อลดงานต่อเฟรม

`render.screen()` คืน `width`, `height`, `ready` ตามขนาด GUI ล่าสุด และ `render.stats()` คืน `tasks`, `rendered`, `culled`, `task_limit`, `frame_limit`, `line_point_limit`, `glyph_limit`

## World task

```lua
render.world("target", {
  type = "outline",
  position = vector.new(100, 70, 100),
  width = 24, height = 12, color = 0xFFFFFF55, max_distance = 128
})
```

World task เป็น geometry 3D ที่ส่งเข้า world renderer จริง มี depth test จึงถูกบังด้วย block/entity ได้ ไม่ได้สร้าง entity และไม่แก้ข้อมูล world ระบบ cull ด้วยระยะและ camera frustum ก่อนส่ง GPU ค่าเริ่มต้นรับแสง block/sky ณ ตำแหน่ง task; ใช้ `fullbright = true` หรือ `light = "fullbright"` เฉพาะงานที่ต้องเรืองแสง

## Live bone attachment

```lua
render.sprite("ear_marker", {
  texture = "namespace:textures/marker.png",
  attach = "model.Head.EarLeft",
  local_offset = vector.new(0, 8, 0), -- Blockbench pixels; ตาม rotation และ scale
  width = 12, height = 12
})

render.item("held_charm", {
  item = "minecraft:amethyst_shard",
  attach = "model.Body.RightArm.RightHand",
  local_offset = vector.new(0, 2, 0),
  billboard = false, -- รับ rotation/scale ของ bone เต็มรูปแบบ
  scale = 0.35
})

render.text("name", {
  bone = "model.Head",
  offset = vector.new(0, 0.35, 0), -- world units
  text = "Shyne"
})
```

Task ที่มี `attach` หรือ `bone` เก็บ native binding ไว้ใน Java แล้ว resolve จาก matrix ของ renderer ในเฟรมเดียวกัน จึงไม่ต้องเรียก Lua เพื่อคำนวณตำแหน่งทุกเฟรมและไม่ตาม bone ช้าหนึ่งเฟรม `offset` เป็น world-unit ส่วน `local_offset` เป็นพิกัด Blockbench pixel ที่รับ hierarchy และ animation ทั้งหมด สำหรับ line ใช้ `local_to` เป็นปลายใน coordinate space เดียวกัน

`billboard = true` ให้หน้าของ task หันเข้ากล้อง; text/sprite ใช้ค่านี้โดยปริยาย ส่วน item/block ที่ผูก bone ใช้ `false` โดยปริยายเพื่อหมุนตามมือหรือกระดูก ตั้งค่าเองได้ทุกชนิด ถ้า renderer ยังไม่มี matrix ในเฟรมแรก task จะถูกข้ามอย่างปลอดภัยและเริ่มแสดงทันทีเมื่อ matrix พร้อม หาก avatar/bone หยุดถูกวาด snapshot จะหมดอายุภายใน 250 ms เพื่อไม่ให้ task ค้างอยู่ที่ pose เก่า

## Permission และงบประสิทธิภาพ

- งาน HUD ต้องมี `hud_render`
- งาน world-anchored ต้องมี `world_render`
- สูงสุด 256 tasks ต่อ Avatar
- วาดสูงสุด 128 tasks ต่อ render pass (HUD และ world แยกกัน), 4096 จุดเส้น HUD และ 4096 glyph ต่อ pass
- world line ยาวได้สูงสุด 1024 blocks และพิกัดผิดปกติจะถูก clamp ก่อนส่ง GPU
- Public Avatar ต้องได้รับการอนุมัติ permission จากผู้ใช้
- ใช้ `render.stats()` และ Avatar Profiler ตรวจ task ที่ถูก cull และเวลาวาด

ตัวอย่างพร้อมใช้:

- `tools/examples/advanced-render-avatar`
- `tools/examples/responsive-hud-avatar`
- `tools/examples/render-profiler-avatar`
