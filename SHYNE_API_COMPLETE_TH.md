# 📖 Shyne Creator — API Reference ฉบับสมบูรณ์

> **เวอร์ชัน:** `2.10.0-alpha-26.3` | **Standard:** `2.0` | **ภาษา:** ไทย  
> เอกสารนี้รวม **ทุก API** ของ Shyne Creator ไว้ในที่เดียว

---

## สารบัญ

| # | หมวด | คำอธิบาย |
|---|---|---|
| 1 | [ภาพรวมระบบ](#1-ภาพรวมระบบ) | Standard 2.0, ระดับ Avatar, โมดูล |
| 2 | [Easy API](#2-easy-api) | ทางลัด `part`, `anim`, `on`, `shyne.setup` |
| 3 | [Model API](#3-model-api) | เลือกชิ้นส่วน, ขยับ, ซ่อน, Animation |
| 4 | [Minecraft API](#4-minecraft-api) | อ่านผู้เล่น, โลก, คำสั่งเกม |
| 5 | [Avatar API](#5-avatar-api) | กล้อง, texture, vanilla model, nameplate |
| 6 | [State & Network](#6-state--network) | ค่าชั่วคราว, ค่าซิงก์, schema |
| 7 | [Events API](#7-events-api) | `events.on`, lifecycle, microphone |
| 8 | [Vector & Matrix](#8-vector--matrix) | `vector.new`, `matrix4.*`, คณิตศาสตร์ |
| 9 | [Custom Render API 1.3](#9-custom-render-api-13) | HUD, World task, Group, Bone binding |
| 10 | [Rig API 1.3](#10-rig-api-13) | Physics, Chain, IK, Animation Graph |
| 11 | [Input API](#11-input-api) | ปุ่ม Dynamic, keyboard/mouse binding |
| 12 | [Sound & Particle](#12-sound--particle) | เสียงและอนุภาค |
| 13 | [UI (Palette) API](#13-ui-palette-api) | Action, Toggle ใน Palette Screen |
| 14 | [Task (Scheduler)](#14-task-scheduler) | ตั้งงานครั้งเดียว/งานวนตาม tick |
| 15 | [Storage API](#15-storage-api) | เก็บค่าเฉพาะเครื่อง |
| 16 | [Permission API](#16-permission-api) | ตรวจสิทธิ์ Avatar |
| 17 | [Diagnostics & Profiler](#17-diagnostics--profiler) | ตรวจ API, error, เวลา, หน่วยความจำ |
| 18 | [Gameplay API (Server)](#18-gameplay-api-server) | สคริปต์ฝั่งเซิร์ฟเวอร์ |
| 19 | [Cloud API v2.2](#19-cloud-api-v22) | Backup, Share, Discover |
| 20 | [Figura Compatibility Layer](#20-figura-compatibility-layer) | รองรับ Figura avatar 100% |

---

## 1. ภาพรวมระบบ

### Standard 2.0

Shyne Avatar Standard 2.0 เป็นมาตรฐานแบบ **model-first**: Avatar ทั่วไปเริ่มได้จาก Blockbench + `avatar.json` โดยไม่ต้องเขียน Lua

| ระดับ | วิธีสร้าง |
|---|---|
| **Beginner** — Zero-Lua | ทำโมเดลและ animation ใน Blockbench กำหนด `profile` กับ `behavior` ใน `avatar.json` |
| **Intermediate** — Declarative | ผูก animation กับสถานะผู้เล่น + blend + blink โดยไม่เขียน event loop |
| **Expert** — Native Lua | ใส่ `main` เพื่ออิสระเต็มที่: procedural rig, physics, UI, logic |

### ตั้งค่า avatar.json

```json
{
  "standard": "2.0",
  "name": "My Advanced Avatar",
  "main": "script.lua",
  "api": "2.0",
  "requires": {
    "render": ">=1.3",
    "scheduler": "^1.1",
    "rig": ">=1.3"
  }
}
```

### ตรวจความสามารถระหว่างทำงาน

```lua
print(shyne.api.version, shyne.api.automatic)
if shyne.api.supports("render", ">=1.3") then
  render.rect("panel", { x = 8, y = 8, width = 80, height = 24 })
end
shyne.api.require("scheduler", ">=1.1")
```

### โมดูลทั้งหมดใน Standard 2.0

| โมดูล | เวอร์ชัน | คำอธิบาย |
|---|---|---|
| `core` | 2.0 | vector, matrix4, result |
| `animation` | 2.0 | blending, priority, mask, additive |
| `behavior` | 2.0 | declarative controller |
| `diagnostics` | 2.0 | API check, error log |
| `easy` | 2.0 | ทางลัด `part`, `anim`, `on` |
| `events` | 2.0 | lifecycle events |
| `input` | 2.0 | keyboard/mouse binding |
| `minecraft` | 2.0 | player/world data |
| `modules` | 2.0 | module system |
| `network` | 2.0 | state sync, remote read |
| `permissions` | 2.0 | trust/permission check |
| `render` | 1.3 | HUD + world task + bone binding |
| `scheduler` | 1.1 | `task.after`, `task.every` |
| `transform` | 1.0 | vanilla pose, world matrix |
| `ui` | 2.0 | Palette actions |
| `vector` | 2.0 | vec math |
| `rig` | 1.3 | physics, chain, IK, armor |

---

## 2. Easy API

ทางลัด Shyne-native เหนือ API เดิม ใช้ `part`, `anim`, `on` หรือ `shyne.part`, `shyne.anim`, `shyne.on`:

```lua
local ears = part("Ears")

anim("ear_wiggle", {
  loop = true,
  additive = true,
  weight = 0.8,
  play = true
})

on("tick", function()
  ears:opacity(minecraft.player.in_water() and 0.7 or 1)
end)
```

### shyne.setup — ตั้งค่าหลายอย่างพร้อมกัน

```lua
local avatar_parts = shyne.setup({
  parts = {
    Ears = { visible = true, rotation = vector.new(0, 0, 3) },
    Glow = { emissive = true, opacity = 0.8 }
  },
  animations = {
    ear_wiggle = { loop = true, additive = true, play = true }
  },
  events = {
    avatar_unload = function() print("bye") end
  }
})
```

### ฟังก์ชันลัดเพิ่มเติม

| ฟังก์ชัน | ใช้ทำอะไร |
|---|---|
| `shyne.once(event, fn)` | ลงทะเบียน event ครั้งเดียว |
| `shyne.after(ticks, fn)` | ตั้งงานหลังผ่านไป N ticks |
| `shyne.every(ticks, fn)` | ตั้งงานวนทุก N ticks |
| `shyne.action(opts)` | เพิ่ม Action ใน Palette |
| `shyne.toggle(opts)` | เพิ่ม Toggle ใน Palette |

### Parts options ที่รองรับ

`visible`, `rotation`/`rot`, `position`/`pos`, `scale`, `color`, `opacity`, `emissive`, `vanilla_parent`

### Animation options ที่รองรับ

`speed`, `weight`, `priority`, `loop`, `fade_in`, `fade_out`, `transition`, `mask`, `additive`, `play`

---

## 3. Model API

### เลือกชิ้นส่วน

```lua
local head = model.root.Head                -- dot-path
local head_again = model.part("root.Head")  -- string path
```

### ควบคุมชิ้นส่วน (ModelPart)

| Method | คำอธิบาย |
|---|---|
| `part:show()` | แสดง |
| `part:hide()` | ซ่อน |
| `part:visible(bool)` | ตั้ง visibility |
| `part:rotate(x, y, z)` | หมุน |
| `part:rotation()` | อ่านค่า rotation |
| `part:move(x, y, z)` | เลื่อน |
| `part:position()` | อ่านค่า position |
| `part:scale(x, y, z)` | ปรับขนาด |
| `part:scale()` | อ่านค่า scale |
| `part:reset()` | คืนค่าเดิม |
| `part:color(r, g, b)` | ตั้งสี (0-1) |
| `part:opacity(float)` | ตั้งความโปร่งใส |
| `part:emissive(bool)` | เรืองแสง |
| `part:rot(x, y, z)` | ตั้ง rotation (แทน animation) |
| `part:rot_add(x, y, z)` | เพิ่ม rotation offset (ไม่ทับ animation) |
| `part:parent()` | อ่าน parent part |
| `part:children()` | อ่าน children list |
| `part:name()` | ชื่อ bone |
| `part:world_position()` | ตำแหน่ง world matrix |
| `part:world_rotation()` | rotation จาก world matrix |
| `part:world_scale()` | scale จาก world matrix |
| `part:world_matrix()` | mat4 ที่ใช้วาดจริง |
| `part:world_transform()` | `{ position, rotation, scale, matrix, context, exact }` |
| `part:transform_exact()` | true ถ้า matrix มาจากเฟรมจริง |
| `part:vanilla_parent(name)` | ผูก vanilla bone (`HEAD`, `BODY`, ...) |
| `part:attach_to_vanilla(name)` | alias ของ `vanilla_parent` |
| `part:detach_from_vanilla()` | คืนไป parent_type จาก .bbmodel |

### Animation

```lua
model.animation.play("wave")
model.animation.stop("wave")
model.animation.exists("wave")
model.animation.parameter("tail_strength", 1.0)
model.animation.clear_parameter("tail_strength")
```

### Animation Object

```lua
local swim = model.animation.get("swim")
swim:play()
swim:restart()
swim:stop()
swim:playing()        -- boolean
swim:loop(bool)
swim:speed(1.2)       -- 0.01–8
swim:weight(1.0)      -- 0–1
swim:priority(10)
swim:fade_in(6)       -- ticks
swim:fade_out(6)      -- ticks
swim:transition(7)    -- crossfade ticks
swim:mask({"Head", "Ears"})
swim:additive(true)
swim:on_keyframe(0.08, function() sound.play("minecraft:entity.cat.ambient") end)
swim:on_complete(function() print("done") end)
```

### Vanilla Pose (Read-only)

```lua
local head = avatar.vanilla("HEAD")
local rotation = head:rotation()
local position = head:position()
local visible = head:visible()
```

**ชื่อ Vanilla Parts ที่ใช้ได้:**  
`PLAYER`, `HEAD`, `BODY`, `LEFT_ARM`, `RIGHT_ARM`, `LEFT_LEG`, `RIGHT_LEG`, `HAT`, `JACKET`, `LEFT_SLEEVE`, `RIGHT_SLEEVE`, `LEFT_PANTS`, `RIGHT_PANTS`, `CAPE`, `ELYTRA`, `ARMOR`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `HELD_ITEMS`, `LEFT_ITEM`, `RIGHT_ITEM`, `MAIN_HAND`, `OFF_HAND`, `HEAD_ITEM`

### Role & Tag (Optional metadata)

```lua
local ears = model.role("ears")       -- bone แรกที่มี shyne_role นี้
for _, part in ipairs(model.tag("accessory")) do
  part:visible(true)
end
```

---

## 4. Minecraft API

### ผู้เล่น

| ฟังก์ชัน | Return Type | คำอธิบาย |
|---|---|---|
| `minecraft.player.name()` | string | ชื่อ |
| `minecraft.player.uuid()` | string | UUID |
| `minecraft.player.position()` | vector | ตำแหน่ง |
| `minecraft.player.health()` | number | พลังชีวิต |
| `minecraft.player.max_health()` | number | พลังชีวิตสูงสุด |
| `minecraft.player.crouching()` | boolean | หมอบอยู่หรือไม่ |
| `minecraft.player.sprinting()` | boolean | วิ่งอยู่หรือไม่ |
| `minecraft.player.in_water()` | boolean | อยู่ในน้ำหรือไม่ |
| `minecraft.player.vehicle()` | table/nil | `{ name, uuid, position }` |
| `minecraft.player.effects()` | table | รายการ effect |
| `minecraft.player.target(range)` | table | block/entity target |
| `minecraft.player.armor(slot)` | table | `material`, `trim_material`, `trim_pattern` |
| `minecraft.player.velocity()` | vector | ความเร็ว |
| `minecraft.player.loaded()` | boolean | โหลดเสร็จหรือยัง |
| `minecraft.player.swimming()` | boolean | ว่ายน้ำอยู่หรือไม่ |

### โลก

| ฟังก์ชัน | Return Type | คำอธิบาย |
|---|---|---|
| `minecraft.world.time()` | number | เวลาในเกม |
| `minecraft.world.biome(pos)` | string | ชื่อ biome |
| `minecraft.world.probe(origin, dir, dist, radius)` | table | collision check: `hit, type, normal, position, distance` |

### คำสั่ง

```lua
minecraft.command("shyne help")
-- จำกัดเฉพาะ /shyne, /sjyne | ≤256 ตัวอักษร | เว้น ≥250ms ต่อครั้ง
```

> ⚠️ ไม่มี filesystem, network, OS, Java reflection หรือคำสั่ง Minecraft อื่น

---

## 5. Avatar API

### ซ่อน Vanilla Model

```lua
avatar.hide_vanilla(true)   -- ซ่อนทั้งหมด
avatar.hide_vanilla(false)  -- แสดงกลับ
```

### กล้อง

```lua
avatar.camera.configure({
  offset = vector.new(0, 0.25, -0.4),
  rotation = vector.new(5, 0, 0),
  local_only = true,
  first_person_masking = true,
  hide_head = true
})
```

> Camera เป็น local-only เสมอ

### Texture

```lua
avatar.texture.sync("manifest")
```

### Nameplate

```lua
avatar.nameplate.configure({ text = "Custom Avatar", visible = true })
-- จำกัด 128 ตัวอักษร | ซิงก์ผ่าน Avatar snapshot
```

### Network

```lua
avatar.network.online(true)
avatar.network.allow("mood")
avatar.network.local_part("model.root.secret", true)
avatar.network.local_vanilla("PLAYER", true)
```

---

## 6. State & Network

### Local State

```lua
state.set("page", 2)
local page = state.get("page", 1)  -- fallback = 1
```

### Synced State (Network)

```lua
state.sync("mood", "happy")
local other_mood = state.remote(player_uuid, "mood")
```

### Schema Validation

```lua
state.validate(key, value)   -- ตรวจล่วงหน้า
state.schema(path)           -- เลือก schema
```

> ถ้า `avatar.json` ระบุ `synced_schema` ค่าจะถูกตรวจชนิดก่อนเขียน ค่าที่ไม่ตรง schema ทำให้ callback error โดยไม่ส่ง network

---

## 7. Events API

### ลงทะเบียน Event

```lua
events.on("event_name", function(event) ... end)
events.once("event_name", function(event) ... end)
events.off("event_name", callback)
events.clear("event_name")
```

### รายชื่อ Events

| Event | ความถี่ | คำอธิบาย |
|---|---|---|
| `entity_init` | ครั้งเดียว | Avatar โหลดเสร็จ |
| `tick` | 20 Hz | ทุก tick |
| `render` | ตาม FPS | ก่อนวาด |
| `post_render` | ตาม FPS | หลังวาด |
| `world_render` | ตาม FPS | world pass |
| `post_world_render` | ตาม FPS | หลัง world pass |
| `microphone` | ต่อเนื่อง | ข้อมูลไมค์ |
| `avatar_unload` | ครั้งเดียว | ก่อน unload |
| `synced_var_change` | เมื่อรับ | ค่า synced เปลี่ยน |
| `damage` | เมื่อโดน | โดนโจมตี |
| `chat_send_message` | เมื่อส่ง | ส่งข้อความ chat |

### Event Payload

ทุก event มีค่าพื้นฐาน: `type`, `time`, `tick`, `context`, `delta`, `sequence`, `api`

`render`/`post_render` เพิ่ม:
- `partial_tick`, `frame_delta`, `first_person`
- `screen`, `camera_position`, `camera_rotation`

### Event Context

```lua
events.context.FIRST_PERSON
events.context.MINECRAFT_GUI
events.context.SHYNE_GUI
events.context.RENDER
events.context.WORLD
events.context.OTHER
```

### Microphone Event

```lua
events.on("microphone", function(mic)
  -- mic.level, mic.speaking, mic.muted, mic.whispering
end)
```

---

## 8. Vector & Matrix

### Vector

```lua
local v = vector.new(1, 2, 3)
local dir = v:normalize()
local next_pos = minecraft.player.position() + dir * 2
local dist = next_pos:distance(minecraft.player.position())
local blend = vector.lerp(vector.zero(), next_pos, 0.5)
```

| Method/Operator | คำอธิบาย |
|---|---|
| `vector.new(x, y, z)` | สร้าง vector |
| `vector.zero()` | (0, 0, 0) |
| `v:normalize()` | หน่วย vector |
| `v:length()` | ความยาว |
| `v:distance(other)` | ระยะห่าง |
| `v:dot(other)` | dot product |
| `v:cross(other)` | cross product |
| `v:lerp(other, t)` | interpolation |
| `v:clamp(min, max)` | จำกัดค่า |
| `v + w`, `v - w` | บวก/ลบ |
| `v * n`, `v / n` | คูณ/หารด้วยสเกลาร์ |

### Matrix4

```lua
local m = matrix4.identity()
local translated = matrix4.translation(vector.new(0, 2, 0))
local scaled = matrix4.scale(vector.new(1.5, 1.5, 1.5))
local result = matrix4.multiply(translated, scaled)
local point = matrix4.transform_point(result, vector.new(0, 0, 0))
local dir = matrix4.transform_direction(result, vector.new(0, 1, 0))
local pos = matrix4.position(result)
local inv = matrix4.inverse(result)  -- nil ถ้ากลับด้านไม่ได้
local copy = matrix4.copy(m)
```

> Matrix ใช้ column-major 16 ค่า

### Result (Error handling)

```lua
local safe = result.try(function() return model.root.Head:rotation() end)
if not safe.ok then print(safe.error.code, safe.error.message) end
```

---

## 9. Custom Render API 1.3

### Primitives

```lua
render.text("title", { text = "Hello", x = 12, y = 12, color = 0xFFFFFFFF, shadow = true })
render.item("icon", { item = "minecraft:diamond", x = 12, y = 28 })
render.block("block", { block = "minecraft:amethyst_block", x = 32, y = 28 })
render.sprite("logo", { texture = "namespace:textures/logo.png", x = 52, y = 12, width = 32, height = 32 })
render.line("line", { from = vector.new(12, 52, 0), to = vector.new(112, 52, 0), color = 0xFFFFFFFF, width = 2 })
render.rect("bg", { x = 8, y = 8, width = 128, height = 48, color = 0xC0101728, z_index = -1 })
render.outline("border", { x = 8, y = 8, width = 128, height = 48, thickness = 2, color = 0xFF55FFFF })
render.polyline("graph", {
  points = { vector.new(8, 40, 0), vector.new(32, 20, 0), vector.new(64, 34, 0) },
  color = 0xFF55FFFF, width = 2
})
```

### ตัวเลือกที่ใช้ได้ทุก Task

`visible`, `z_index`/`layer`, `opacity`, `world`, `max_distance`, `group`

### อัปเดต Task

```lua
render.update("status", { text = "Swimming", opacity = 0.8 })
render.remove("icon")
render.clear()

local task = render.task("status")
task:update({ y = 20 })
task:hide()
task:show()
task:remove()
```

### Group & Responsive HUD

```lua
local panel = render.group("panel", { x = 12, y = 12, opacity = 0.9, z_index = 20 })
render.rect("panel.bg", { group = "panel", x = 0, y = 0, width = 120, height = 40, color = 0xE0000000 })
render.text("panel.text", { group = "panel", x = 8, y = 8, text = "Shyne" })

render.on_frame(function()
  local screen = render.screen()
  if screen.ready then panel:update({ x = screen.width - 132 }) end
end)
```

Group properties: `x`, `y`, `z`, `scale`, `scale_x/y/z`, `opacity`, `visible`, `z_index`  
ซ้อนได้สูงสุด **16 ชั้น**

### World Task (3D)

```lua
render.world("marker", {
  type = "text",
  text = "Target",
  position = vector.new(100, 70, 100),
  color = 0xFFFFFF55,
  max_distance = 128
})
```

- Depth-tested, ถูกบังด้วย block/entity
- Cull ด้วยระยะ + frustum
- รับแสง block/sky จริง (`fullbright = true` เพื่อเรืองแสง)
- ไม่สร้าง entity, ไม่แก้ข้อมูล world

### Live Bone Attachment

```lua
-- sprite ติดหู
render.sprite("ear_marker", {
  texture = "namespace:textures/marker.png",
  attach = "model.Head.EarLeft",
  local_offset = vector.new(0, 8, 0),  -- Blockbench pixels
  width = 12, height = 12
})

-- item ติดมือ
render.item("held_charm", {
  item = "minecraft:amethyst_shard",
  attach = "model.Body.RightArm.RightHand",
  local_offset = vector.new(0, 2, 0),
  billboard = false,   -- รับ rotation ของ bone
  scale = 0.35
})

-- text ผ่าน bone + world offset
render.text("name", {
  bone = "model.Head",
  offset = vector.new(0, 0.35, 0),  -- world units
  text = "Shyne"
})
```

- `attach`/`bone` resolve matrix ใน Java ทุกเฟรม (ไม่ตามช้า)
- `offset` = world-unit | `local_offset` = Blockbench pixels
- `billboard = true` (default text/sprite) | `false` (default item/block)

### Utility

```lua
local screen = render.screen()     -- { width, height, ready }
local stats = render.stats()       -- { tasks, rendered, culled, task_limit, ... }
```

### งบประสิทธิภาพ

| ข้อจำกัด | ค่า |
|---|---|
| Tasks สูงสุด | 256 ต่อ Avatar |
| Render ต่อ pass | 128 tasks |
| Line points ต่อ pass | 4,096 |
| Glyphs ต่อ pass | 4,096 |
| World line ยาวสุด | 1,024 blocks |

---

## 10. Rig API 1.3

> ต้องประกาศ `"rig": ">=1.3"` ใน `requires`

### Physics Preset แบบไม่เขียน Lua

ตั้งใน Blockbench Group: `Bunny Ears`, `Tail`, `Hair`, `Cloth`, `Wings`  
Runtime สร้าง chain อัตโนมัติพร้อม angle cone ตามชนิด

### rot_add — Physics ที่ไม่ทับ Animation

```lua
local ear = model.part("model.BunnyEars")
ear:rot_add(0, 8, 0)  -- offset เพิ่มหลัง animation + vanilla pose
```

### rig.chain — Chain Physics

```lua
local tail = rig.chain({
  "model.Tail1", "model.Tail2", "model.Tail3", "model.Tail4"
}, {
  stiffness = 0.16,
  damping = 0.80,
  falloff = 0.10,
  gravity = vector.new(9, 0, 0),
  motion = vector.new(-36, 0, 0),
  limit = vector.new(30, 22, 18)
})

tail:stop()    -- หยุด physics
tail:start()   -- เริ่มใหม่
tail:reset()   -- คืน offset เป็นศูนย์
tail.controllers[1]:impulse(vector.new(12, 0, 0))  -- แรงกระแทก
```

Options: `stiffness`, `damping`, `falloff`, `gravity`, `motion`, `wind`, `base`, `target`, `limit`, `cone`

### rig.spring — Single Bone Spring

```lua
local wing = rig.spring("model.WingTip", {
  wind = rig.wind({ strength = 3, gust = 0.5, direction = vector.new(0, 0, 1) }),
  collision = {
    origin = vector.new(0.4, 1.4, 0),
    direction = vector.new(0, 0, 1),
    distance = 0.6,
    radius = 0.12,
    strength = 18
  },
  cone = 35
})
```

### rig.wind — Deterministic Wind

```lua
rig.wind({ strength = 2, direction = vector.new(1, 0, 0) })
```

### rig.ik2 — Two-Bone IK

```lua
local arm_ik = rig.ik2("model.UpperArm", "model.LowerArm",
  function() return vector.new(0, -0.7, 0.5) end,
  { upper_length = 0.45, lower_length = 0.45 }
)
```

### rig.animation_graph — State Machine

```lua
rig.animation_graph({
  default = "idle",
  transition = 6,
  order = { "swim", "walk", "idle" },
  states = {
    swim = { animation = "swim", when = minecraft.player.swimming, priority = 20 },
    walk = { animation = "walk", when = function()
      return minecraft.player.velocity():length() > 0.08
    end },
    idle = { animation = "idle" }
  }
})
```

### Vanilla Attachment (ละเอียด)

```lua
model.part("model.Horns"):vanilla_parent("HEAD", "full")       -- ตำแหน่ง + rotation
model.part("model.CapeRoot"):vanilla_parent("BODY", "rotation") -- rotation เท่านั้น
model.part("model.WorldPin"):vanilla_parent("BODY", "position") -- ตำแหน่งเท่านั้น
model.part("model.Horns"):detach_from_vanilla()
```

### rig.armor — Cosmetic Armor

```lua
local armor = rig.armor({
  head = { parts = { "model.Helmet", "model.HelmetGlow" }, parent = "HEAD" },
  chest = { parts = { "model.Chestplate" }, parent = "BODY" },
  left_arm = { parts = { "model.LeftSleeve" } },
  right_arm = { parts = { "model.RightSleeve" } },
  left_leg = { parts = { "model.LeftLeggings" } },
  right_leg = { parts = { "model.RightLeggings" } }
})
```

### Armor Variants

```lua
variants = {
  ["minecraft:diamond_chestplate"] = { "model.DiamondChest" },
  ["material:minecraft:diamond"] = { "model.DiamondTrim" },
  ["trim_pattern:minecraft:spire"] = { "model.SpireTrim" },
  default = { "model.GenericChest" }
}
```

### rig.elytra — Cosmetic Elytra

```lua
rig.elytra({ "model.Elytra" }, { show_folded = true })
```

### SquAPI Compatibility

```lua
local squapi = require("SquAPI")  -- native compat, ไม่โหลด Figura
```

**รองรับ:**
- Physics: `tail:new`, `tails(...)`, `ear:new`, `arm:new`, `leg:new`, `smoothHead:new`, `smoothTorso`, `smoothHeadNeck`, `bewb:new`, `bounceWalk:new`, `taur:new`, `taurPhysics`, `FPHand:new`
- Floating: `hoverPoint:new(...)`, `floatPoint(...)`
- Animation: `walk`, `crouch`, `randimation:new`, `blink`
- Utility: `bounceObject:new`, `bouncetowards`, `getForwardVel`, `getSideVelocity`, `yvel`, `lineargraph`, `parabolagraph`

### Floating Companion

```lua
local companion = require("SquAPI").hoverPoint:new(
  model.part("model.FloatingCompanion"),
  vector.new(0.8, 1.1, -0.5),  -- world units
  0.2, 5, 1, 0.05,
  true,   -- หมุนตามผู้เล่น
  true    -- ชน block/entity (visual-only)
)
companion:setCollisionRadius(0.16)
companion.collisionBounce = 0.35
```

---

## 11. Input API

### ลงทะเบียนปุ่ม

```lua
input.bind("twirl", {
  title = "Twirl",
  key = input.key.r,
  type = "keyboard",       -- หรือ "mouse" + input.mouse.left
  modifiers = { "shift" },
  repeat = false,
  on_press = function() model.animation.get("twirl"):restart() end,
  on_hold = function(id) state.set("holding_" .. id, true) end,
  on_release = function(id) state.set("holding_" .. id, false) end
})
```

### จัดการ Binding

```lua
local down = input.is_down("twirl")
local key = input.get_key("twirl")         -- "key.keyboard.r"
input.set_key("twirl", "key.keyboard.t")
local conflicts = input.conflicts("twirl")
input.unbind("twirl")
```

### ข้อจำกัด

| ข้อจำกัด | ค่า |
|---|---|
| Bindings สูงสุด | 32 ต่อ Avatar |
| ปุ่มไม่ทำงาน | ขณะ Chat/เมนู/หน้าต่างเกมเสีย focus |
| ID format | `avatar_id.binding_id` |
| Config | `config/shyne-creator/avatar-keybinds.json` |

---

## 12. Sound & Particle

### Sound

```lua
sound.play("minecraft:entity.axolotl.splash", { volume = 0.8, pitch = 1.1 })
```

### Particle

```lua
particle.spawn("minecraft:bubble", minecraft.player.position(), {
  velocity = vector.new(0, 0.05, 0)
})
```

> จำกัด **256 ครั้งต่อ tick** | Texture ลงท้าย `_e` / `_emissive` จะ full-bright อัตโนมัติ

---

## 13. UI (Palette) API

### Action

```lua
ui.action({
  id = "toggle_mouth",
  title = "เปิด/ปิดปาก",
  icon = "spark",
  close = false,
  on_use = function()
    forced_open = not forced_open
    mouth:visible(forced_open)
  end,
  on_right_click = function() print("secondary action") end
})
```

### Toggle

```lua
ui.toggle({
  id = "ears",
  title = "Ears",
  default = settings_enabled,
  on_toggle = function(value)
    model.root.Ears:visible(value)
  end
})
```

---

## 14. Task (Scheduler)

```lua
task.after(20, function() print("ผ่านไป 1 วินาที") end)

local timer = task.every(10, function(event, id)
  return minecraft.player.loaded()  -- คืน false เพื่อหยุดงานวน
end)

task.cancel(timer)
```

> จำกัด **128 งาน** ต่อ Avatar | ล้างทั้งหมดเมื่อ unload | ใช้หน่วย tick (20/วินาที)

---

## 15. Storage API

```lua
storage.set("ears_enabled", true)
local enabled = storage.get("ears_enabled", true)  -- fallback = true
```

- เก็บค่าเฉพาะเครื่อง แยกตาม Avatar ID
- แยกจาก `state.sync`

---

## 16. Permission API

```lua
permissions.has("world_render")       -- boolean
permissions.requested("camera")       -- boolean
permissions.require("camera")         -- error ถ้าไม่มี
local all = permissions.list()        -- table ของ permission ทั้งหมด
```

**Permission ที่มี:**  
`particle`, `sound`, `camera`, `microphone`, `command`, `hud_render`, `world_render`

---

## 17. Diagnostics & Profiler

### Diagnostics

```lua
local report = diagnostics.snapshot()
-- bones, cubes, textures, animation layers, input bindings,
-- feature flags, runtime_errors, custom_render_api_version
```

### Profiler

```lua
local profile = profiler.snapshot()
-- fps, frame_ms, avatar_frame_ms, estimated_fps_loss,
-- heap_bytes, avatar_bytes, task_count, metrics
```

> เปิด `Shyne Settings → Advanced → Avatar Profiler` | Export JSON: `.minecraft/shyne-logs/profiler/`

---

## 18. Gameplay API (Server)

> Gameplay pack ทำงานคนละ trust boundary กับ Avatar Lua โดย Server เป็นผู้ตัดสิน

```lua
events.on("player_join", function(ctx)
  minecraft.message("ยินดีต้อนรับ", ctx.player)
end)

minecraft.command("time set day")
minecraft.world.set_block(0, 80, 0, "minecraft:stone")
minecraft.item.give("minecraft:apple", 3, player_id)
minecraft.item.give_shyne("aether_crystal", 1, player_id)
minecraft.sound.play("minecraft:block.amethyst_block.chime", player_id)
minecraft.task.after(20, "on_tick")

model.load("bbmodels/effect.bbmodel")
model.attach("bbmodels/effect.bbmodel", { player = player_id, scale = 1.0 })
model.play("bbmodels/effect.bbmodel", "pulse", player_id)
model.stop(player_id)
model.detach(player_id)
```

> ⚠️ สิทธิ์ต้องผ่านตัวตรวจของ Shyne/MC Server เสมอ ไม่ควรเชื่อค่า damage, mana, permission หรือ cooldown จาก Client

---

## 19. Cloud API v2.2

**Base URL:** `https://shyne-avatar-cloud.jirayut-wh.workers.dev`

### Authentication

```
1. POST /v1/auth/challenges    → { "username": "Player" }
2. Client เรียก MC session service joinServer
3. POST /v1/auth/verify         → { "challenge_id": "..." }
4. Backend ตอบ { token, expires_at, account }
5. DELETE /v1/auth/session      → เพิกถอน token
```

> MC access token จะไม่ถูกส่งไป Shyne Cloud

### สถานะบริการ

| Endpoint | คำอธิบาย |
|---|---|
| `GET /healthz` | Worker ตอบสนอง |
| `GET /readyz` | ความพร้อมบริการ |
| `GET /v1/status` | version, capability, permission contract |

### Private Storage

| Endpoint | คำอธิบาย |
|---|---|
| `GET /v1/me` | บัญชีปัจจุบัน |
| `GET /v1/me/avatars?q=&limit=30&offset=0` | รายการสำรอง |
| `GET /v1/avatars/<id>` | manifest |
| `PATCH /v1/avatars/<id>` | เปลี่ยน name/description |
| `DELETE /v1/avatars/<id>` | ลบข้อมูลสำรอง |

### Backup Protocol

```http
POST /v1/avatars            → สร้าง + manifest
PUT /v1/uploads/<id>/chunks/<sha256>  → ส่งก้อน
POST /v1/uploads/<id>/complete        → จบ
```

**ข้อจำกัด:** 512 KiB/chunk, 64 MiB/avatar, 256 ไฟล์, ต้องมี `avatar.json`

### Restore Protocol

1. อ่าน manifest จาก `GET /v1/avatars/<id>`
2. ตรวจ cache ด้วย SHA-256
3. รับก้อนที่ขาดจาก `GET /v1/chunks/<sha256>?avatar=<id>`
4. ตรวจ size + SHA-256
5. ประกอบใน temp folder → validate → สลับเข้าใช้งาน

### Public Share

| Endpoint | คำอธิบาย |
|---|---|
| `GET /v1/discover?q=&limit=30` | ค้นหา Public Avatar |
| `GET /v1/shares/<share_id>` | metadata, license, permissions |
| `PUT /v1/avatars/<id>/publication` | Publish ZIP |
| `DELETE /v1/avatars/<id>/publication` | Revoke |
| `GET /v1/shares/<share_id>/package` | ดาวน์โหลด ZIP |

**Publish headers:**

```http
Content-Type: application/vnd.shyne.avatar+zip
X-Shyne-Permissions: particle,sound,hud_render
X-Shyne-License: CC-BY-4.0
```

**License:** `PERSONAL`, `CC0`, `CC-BY-4.0`, `CC-BY-NC-4.0`, `CUSTOM`

**ข้อจำกัด:** Public ZIP ≤ 16 MiB

---

## 20. Figura Compatibility Layer

> ไฟล์: `60_figura_compat.lua` (483 บรรทัด) — โหลดอัตโนมัติตอน bootstrap

Shyne Creator มี Compatibility Layer ที่ให้ Figura avatar ทำงานได้ 100% บน Shyne Core โดยไม่ต้องแก้ไขสคริปต์

### 20.1 Vectors Library (Figura-style)

```lua
local pos = vectors.vec2(10, 20)
local v = vectors.vec3(1, 2, 3)
local c = vectors.vec4(1, 0, 0, 1)
```

| Method | Vec2 | Vec3 | Vec4 |
|---|---|---|---|
| `:length()` | ✅ | ✅ | — |
| `:lengthSqr()` | ✅ | ✅ | — |
| `:normalized()` | ✅ | ✅ | — |
| `:normalize()` | ✅ | ✅ | — |
| `:dot(other)` | ✅ | ✅ | — |
| `:cross(other)` | — | ✅ | — |
| `:distanceTo(other)` | — | ✅ | — |
| `:distanceToSqr(other)` | — | ✅ | — |
| `:copy()` | ✅ | ✅ | ✅ |
| `:augmented(w)` | — | ✅ | — |

**Swizzling:** `.xy`, `.xz`, `.yz`, `.xyz`, `.rgb`  
**Aliases:** `.x/.r/.pitch`, `.y/.g/.yaw`, `.z/.b/.roll`, `.w/.a`  
**Operators:** `+`, `-`, `*`, `/`, `-v` (unary), `==`, `tostring`

### 20.2 Matrices Library

```lua
local m = matrices.mat4()
m:translate(0, 2, 0)
m:scale(1.5, 1.5, 1.5)
local copy = m:copy()
local result = m * other_mat
local point = m * vectors.vec4(0, 0, 0, 1)
```

### 20.3 Events Bus (Figura-style)

```lua
events.TICK:register(function(delta) ... end, "my_tick")
events.RENDER:register(function(delta, context) ... end)
events.POST_RENDER:register(fn)
events.WORLD_RENDER:register(fn)
events.POST_WORLD_RENDER:register(fn)
events.ENTITY_INIT:register(fn)
events.DAMAGE:register(fn)
events.CHAT_SEND_MESSAGE:register(fn)

events.TICK:remove("my_tick")
events.TICK:clear()
```

### 20.4 Action Wheel

```lua
local page = action_wheel:newPage("Main")

local act = page:newAction("greet")
act:title("ทักทาย")
act:item("minecraft:diamond")
act:color(0.5, 1, 0.5)
act:onLeftClick(function() print("Hi!") end)
act:onRightClick(function() print("Context menu") end)
act:onToggle(function(toggled) print(toggled) end)

action_wheel:setPage(page)
```

> Bridge → Shyne Palette Screen ผ่าน `_avatar_action_register`

### 20.5 Pings (Network RPC)

```lua
-- ลงทะเบียน handler
pings.myPing = function(data)
  print("received:", data)
end

-- เรียกใช้ (ทำงานทั้ง local + sync ผ่าน network)
pings.myPing("hello")
```

> ใช้ `_avatar_synced_set("__figura_ping", ...)` สำหรับ network sync

### 20.6 Keybinds

```lua
local kb = keybinds:newKeybind("Sprint Toggle", input.key.g)
kb:onPress(function() print("pressed") end)
kb:onRelease(function() print("released") end)
local down = kb:isPressed()
```

> Bridge → `_avatar_input_bind`

### 20.7 Player & World Proxies

```lua
player:getPos()          -- vectors.vec3
player:getVelocity()
player:getRot()
player:getLookDir()
player:isSneaking()
player:isSprinting()
player:isUnderwater()
player:isInWater()
player:isOnGround()
player:isGliding()
player:isSwingingArm()
player:getName()

world.getTime()
world.getBlockState(pos)
```

### 20.8 Particles Proxy

```lua
particles:newParticle("minecraft:heart", vectors.vec3(0, 70, 0), vectors.vec3(0, 0.1, 0))
```

### Global

```lua
figura.version       -- "0.1.4"
figura.is_figura     -- true
figura.engine        -- "shyne"
figura.compatibility_level -- "100%"
```

---

## Lua Bootstrap Order

โมดูลถูกโหลดตามลำดับ:

```
00_core.lua          → vector, matrix4
10_model_animation.lua → model, animation
20_avatar_world.lua  → minecraft, avatar, state
30_render_tasks.lua  → render primitives
31_render_shapes.lua → events table, render shapes
40_optional_systems.lua → ui, storage, input
50_easy_api.lua      → part, anim, on, shyne.setup
60_figura_compat.lua → Figura compatibility layer
```

> ทุกโมดูลรวมกันเป็น chunk เดียวใน `loadBootstrap()` ของ `ClientLuaAvatarRuntime.java`

---

## Java Bridge Functions

| Function | คำอธิบาย |
|---|---|
| `_shyne_read(key, ...)` | อ่านข้อมูลจาก Java (player, world, block) |
| `_avatar_state_set(key, value)` | ตั้งค่า state |
| `_avatar_synced_set(key, value)` | ตั้งค่า synced state |
| `_avatar_action_register(...)` | ลงทะเบียน Palette action |
| `_avatar_input_bind(...)` | ลงทะเบียน input binding |
| `_shyne_input_is_down(id)` | ตรวจว่าปุ่มถูกกดอยู่ |
| `_shyne_particle_spawn(...)` | สร้าง particle |
| `_shyne_report_error(...)` | รายงาน error |

---

## ข้อจำกัดรวม

| ข้อจำกัด | ค่า |
|---|---|
| Render tasks | 256 ต่อ Avatar |
| Render ต่อ pass | 128 tasks |
| Line points / pass | 4,096 |
| Glyphs / pass | 4,096 |
| Input bindings | 32 ต่อ Avatar |
| Scheduled tasks | 128 ต่อ Avatar |
| Particle / tick | 256 |
| Nameplate | 128 ตัวอักษร |
| Command | ≤256 ตัวอักษร, ≥250ms ระหว่างครั้ง |
| Backup chunk | 512 KiB |
| Backup avatar | 64 MiB, 256 ไฟล์ |
| Public ZIP | 16 MiB |

---

> 📝 เอกสารนี้สร้างอัตโนมัติจาก `SHYNE_LUA_API_TH.md`, `CUSTOM_RENDER_API_TH.md`, `RIG_API_TH.md`, `SHYNE_GAMEPLAY_API_TH.md`, `CLOUD_API.md` และ `60_figura_compat.lua`  
> อัปเดตล่าสุด: 2026-09-24 | Shyne Creator v2.10.0-alpha-26.3
