# Shyne Custom Render API 1.1

Custom Render API 1.1 เป็นส่วนเสริมของ Shyne Lua API Standard 1.0 จึงไม่บังคับเปลี่ยน `avatar.json.api_version` และไม่ทำให้ Avatar เดิมพัง อ่านรุ่นได้จาก `render.api_version` หรือ `diagnostics.snapshot().custom_render_api_version` และตรวจฟีเจอร์ก่อนใช้ได้จาก `diagnostics.snapshot().features`

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

ทุก task รองรับ `visible`, `z_index` หรือ `layer`, `opacity`, `world`, `max_distance` และ `group` ค่า `opacity` มีผลกับ text, line, rect และ outline ส่วน item, block และ sprite ใช้ alpha/tint ตาม renderer ของ Minecraft

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

`render.screen()` คืน `width`, `height`, `ready` ตามขนาด GUI ล่าสุด และ `render.stats()` คืน `tasks`, `rendered`, `culled`, `task_limit`, `frame_limit`, `line_point_limit`

## World task

```lua
render.world("target", {
  type = "outline",
  position = vector.new(100, 70, 100),
  width = 24, height = 12, color = 0xFFFFFF55, max_distance = 128
})
```

World task เป็นการ project ตำแหน่งโลกเข้าสู่ HUD ไม่ได้สร้าง entity หรือแก้ world และจะถูก cull เมื่ออยู่นอกจอหรือไกลเกิน `max_distance`

## Permission และงบประสิทธิภาพ

- งาน HUD ต้องมี `hud_render`
- งาน world-anchored ต้องมี `world_render`
- สูงสุด 256 tasks ต่อ Avatar
- วาดสูงสุด 128 tasks และ 4096 จุดเส้นต่อเฟรม
- Public Avatar ต้องได้รับการอนุมัติ permission จากผู้ใช้
- ใช้ `render.stats()` และ Avatar Profiler ตรวจ task ที่ถูก cull และเวลาวาด

ตัวอย่างพร้อมใช้:

- `tools/examples/advanced-render-avatar`
- `tools/examples/responsive-hud-avatar`
- `tools/examples/render-profiler-avatar`
