# Shyne Native Lua API — Standard 2.0

เอกสารนี้ตรงกับ Shyne Creator `2.8.0-alpha-26.2`

Lua เป็นส่วนเสริมสำหรับงานขั้นสูงของ Shyne Avatar Standard 2.0 เท่านั้น Avatar แบบ model-first ไม่ต้องมี `script.lua` หากต้องใช้ procedural rig หรือ logic เฉพาะ ให้ระบุ `main` และใช้ API ของ Shyne โดยตรง:

| กลุ่ม | ใช้ทำอะไร |
|---|---|
| `minecraft` | อ่านผู้เล่น/โลก และเรียกคำสั่งเกม |
| `model` | เลือกชิ้นส่วน ขยับ ซ่อน และเล่น animation |
| `avatar` | ตั้งค่ากล้อง texture vanilla model และการซิงก์ |
| `state` | เก็บค่าชั่วคราวหรือค่าที่ซิงก์ |
| `events` | รับเหตุการณ์ด้วย `events.on(...)` |
| `ui` | เพิ่ม Action ใน Palette |
| `vector` | สร้างและคำนวณค่า x/y/z |
| `render` | วาด HUD, item, block, sprite, line และ world task |
| `input` | เพิ่มปุ่ม Dynamic ระหว่างเกม |
| `sound`, `particle` | เล่นเสียงและสร้างอนุภาค |
| `task` | ตั้งงานครั้งเดียวหรืองานวนตาม tick |
| `permissions` | ตรวจสิทธิ์ของ Local/Public Avatar |
| `diagnostics`, `profiler` | ตรวจ API, error, เวลา และหน่วยความจำ |

## เลือก API อัตโนมัติและตรวจความสามารถ

`avatar.json` ที่มีเพียงชื่อเป็น Zero-Lua Avatar และใช้ declarative controller โดยอัตโนมัติ หากต้องการ Lua ให้ระบุ `main` และ API 2.0:

```json
{
  "standard": "2.0",
  "name": "My Advanced Avatar",
  "main": "script.lua",
  "api": "2.0"
}
```

กำหนดโมดูลขั้นต่ำที่สคริปต์ต้องใช้ได้ดังนี้:

```json
{
  "standard": "2.0",
  "name": "My Advanced Avatar",
  "main": "script.lua",
  "api": "2.0",
  "requires": {
    "render": ">=1.1",
    "scheduler": "^1.1"
  }
}
```

ระบบตรวจ `requires` ก่อนเริ่ม Lua จึงไม่ปล่อยให้ Avatar ทำงานครึ่งหนึ่งแล้วค่อยพัง สคริปต์ตรวจความสามารถขณะทำงานได้ดังนี้:

```lua
print(shyne.api.version, shyne.api.automatic)
if shyne.api.supports("render", ">=1.1") then
  render.rect("panel", { x = 8, y = 8, width = 80, height = 24 })
end
shyne.api.require("scheduler", ">=1.1")
```

โมดูลใน Standard 2.0 ได้แก่ `core`, `animation`, `behavior`, `diagnostics`, `input`, `minecraft`, `modules`, `network`, `permissions`, `render`, `scheduler`, `ui`, `vector` และ `rig`

## Avatar: ตัวอย่างเริ่มต้น

```lua
local mouth = model.root.Head.mouth_open
local forced_open = false

avatar.hide_vanilla(true)
avatar.camera.configure({
  local_only = true,
  first_person_masking = true,
  hide_head = true
})
avatar.texture.sync("manifest")
avatar.network.online(true)

events.on("microphone", function(mic)
  mouth:visible(forced_open or (mic.speaking and not mic.muted))
end)

ui.action({
  id = "toggle_mouth",
  title = "เปิด/ปิดปาก",
  icon = "spark",
  close = false,
  on_use = function()
    forced_open = not forced_open
    mouth:visible(forced_open)
  end
})

events.on("avatar_unload", function()
  avatar.hide_vanilla(false)
end)
```

## โมเดล

เลือกชิ้นส่วนได้สองแบบ ผลเหมือนกัน:

```lua
local head = model.root.Head
local head_again = model.part("root.Head")

head:show()
head:hide()
head:visible(true)
head:rotate(10, 0, 0)
head:move(0, 1, 0)
head:scale(1.1, 1.1, 1.1)
head:reset()

model.animation.play("wave")
model.animation.stop("wave")
model.animation.exists("wave")
model.animation.parameter("tail_strength", 1.0)
model.animation.clear_parameter("tail_strength")
```

## Rig: Vanilla pose และ Model hierarchy

Shyne ส่ง pose ที่ renderer ของ Minecraft คำนวณแล้วให้ Lua อ่านแบบ read-only จึงผูกหาง หู แขน และโมเดล first-person เข้ากับท่าผู้เล่นจริงได้:

```lua
local head = avatar.vanilla("HEAD")
local left_arm = avatar.vanilla("LEFT_ARM")

events.on("tick", function()
  model.root.Hat:rotation(head:rotation())
  model.root.LeftSleeve:rotation(left_arm:rotation())
end)

local tail = model.root.Tail
local parent = tail:parent()
local children = tail:children()
print(tail:name(), parent and parent:name(), tail:world_position())
```

`avatar.vanilla(part):position()`, `rotation()` และ `visible()` ใช้กับ `PLAYER`, `HEAD`, `BODY`, `LEFT_ARM`, `RIGHT_ARM`, `LEFT_LEG`, `RIGHT_LEG`, ชั้นผิว `HAT`/`JACKET`/`LEFT_SLEEVE`/`RIGHT_SLEEVE`/`LEFT_PANTS`/`RIGHT_PANTS`, `CAPE`, `ELYTRA`, `ARMOR`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `HELD_ITEMS`, `LEFT_ITEM`, `RIGHT_ITEM`, `MAIN_HAND`, `OFF_HAND` และ `HEAD_ITEM` ได้ ค่า visibility ถูกใช้แยกตาม UUID ทั้ง local/remote และ `PLAYER=false` จะเป็น master mask ของ vanilla layers ทั้งหมด โดย Shyne model layer ยังวาดตามปกติ

`model.part(...):parent()`, `children()`, `name()`, `world_position()` และ `world_rotation()` อ่าน hierarchy จากโมเดลที่กำลังใช้ โดย `world_position()`/`world_rotation()` สืบทอด rotation และ scale ของ parent bone รวมถึง transform ที่ Lua ตั้งไว้แล้ว

Avatar ที่เป็นส่วนเสริมและยังใช้ตัว Minecraft เดิม ให้ตั้ง `"profile": "accessory"` ใน `avatar.json` และใช้ `parent_type` ใน Blockbench เป็นวิธีหลัก หากต้องเปลี่ยน attachment ระหว่างเล่นจึงค่อยใช้ Lua:

```lua
avatar.hide_vanilla(false)
model.part("model.root"):vanilla_parent("BODY") -- HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
```

ใช้ `part:attach_to_vanilla("HEAD")` เป็นชื่อเรียกแบบเดียวกันได้ การผูกนี้ sync ให้ผู้เล่น Shyne คนอื่นด้วย

หาก `.bbmodel` ตั้ง Blockbench/Figura `parent_type` เป็น `Head`, `Body`, `LeftArm`, `RightArm`, `LeftLeg` หรือ `RightLeg` ไว้ Shyne จะอ่านและผูก bone นั้นอัตโนมัติ จึงไม่ต้องมีบรรทัด `vanilla_parent(...)` ใน script. Transform จาก vanilla part จะเป็น parent ของ local transform และ animation ของ bone เสมอ จึงใช้กับหูที่ตามหัวพร้อม animation กระดิกได้

สำหรับ geometry first-person ให้ใช้ tree `LeftArmFP`/`RightArmFP` หรือ role/tag `first_person_left_arm`/`first_person_right_arm`; renderer จะเลือก tree ตาม pivot ที่ตรงกับแขน Minecraft, ซ่อนจาก world render และ fallback กลับไปใช้มือ vanilla หากไม่มี geometry ที่วาดได้. GUI portrait, skull และ held-item transform แบบ custom ยังไม่ใช่ API ที่พร้อมใช้งาน.

### Floating companion และ collision ภาพ

Avatar ที่ใช้ `"rig": ">=1.3"` สามารถใช้ native rig API สำหรับ companion ที่ลอยตามผู้เล่นได้ โดยไม่ต้องฝังหรือนำ Lua ของ Figura มาใช้:

```lua
local squapi = require("SquAPI")
local orb = squapi.hoverPoint:new(
  model.part("model.Companion"), vector.new(0.8, 1.2, 0),
  0.2, 5, 1, 0.05, true, true
)
orb:setCollisionRadius(0.16)
```

`hoverPoint` ใช้ world-unit, spring, mass และ resistance; เมื่อเปิด argument สุดท้าย มัน sweep ชน block/entity และสะท้อนความเร็วในภาพเท่านั้น. วาง `model.Companion` เป็น top-level group ใน Blockbench เพื่อไม่ให้มันสืบทอด animation จากแขน/ขา. สำหรับ Avatar รุ่นเก่า `squapi.floatPoint(...)` ใช้ได้แล้วเช่นกัน แต่ `x/y/z` เป็น pixel offset ตาม SquAPI เก่า. รายละเอียดพารามิเตอร์และข้อจำกัดอยู่ใน `RIG_API_TH.md`.

### Model-first: role และ tag (ไม่บังคับ)

งานละเอียดควรควบคุมผ่าน path ของโมเดลโดยตรง เช่น `model.part("model.Character.Head.EarLeft.Tip")` เพราะไม่ซ่อนความต่างของ rig แต่ผู้สร้างสามารถเพิ่ม metadata ใน bone ของ `.bbmodel` เพื่อให้ library ใช้ซ้ำได้:

```json
{
  "name": "BunnyEars",
  "parent_type": "Head",
  "shyne_role": "ears",
  "shyne_tags": ["accessory", "cosmetic"]
}
```

```lua
local ears = model.role("ears")       -- bone แรกที่มี shyne_role นี้ หรือ nil
for _, part in ipairs(model.tag("accessory")) do
  part:visible(true)
end
-- alias: model.roles("ears"), model.parts_with_tag("accessory")
```

role/tag เป็น optional และไม่แทน `model.part(path)`; metadata จะถูก sync พร้อม model ให้ผู้เล่น Shyne คนอื่น

## Animation state, storage และ bone attachment

```lua
local blink = model.animation.get("blink"):loop(false):priority(20)
blink:on_keyframe(0.08, function() sound.play("minecraft:entity.cat.ambient") end)
blink:on_complete(function() print("blink complete") end)
blink:play()

local settings_enabled = storage.get("ears_enabled", true)
storage.set("ears_enabled", settings_enabled)

ui.toggle({ id = "ears", title = "Ears", default = settings_enabled,
  on_toggle = function(value) model.root.Ears:visible(value) end })

ui.action({ id = "mode", title = "Mode", on_use = function() end,
  on_right_click = function() print("secondary action") end })

render.sprite("halo", {
  texture = "shyne_creator:textures/gui/shyne_creator_logo.png",
  attach = "root.Head", width = 16, height = 16
})
```

`storage.get(key, fallback)` และ `storage.set(key, value)` เก็บค่าเฉพาะเครื่อง แยกจาก `state.sync` และแยกตาม Avatar ID ส่วน `render.*` ที่ใส่ `attach` หรือ `bone` จะใช้ตำแหน่งโลกของ bone นั้นโดยอัตโนมัติ

`script.lua` มีลำดับสูงกว่า animation ใน `.bbmodel` และ Auto Humanoid เฉพาะ channel ที่สคริปต์สั่ง ใช้ `reset()` เพื่อคืนการควบคุมให้ animation/Auto Humanoid

## Minecraft API ใน Avatar

```lua
local name = minecraft.player.name()
local uuid = minecraft.player.uuid()
local pos = minecraft.player.position()
local hp = minecraft.player.health()
local max_hp = minecraft.player.max_health()
local crouching = minecraft.player.crouching()
local sprinting = minecraft.player.sprinting()
local in_water = minecraft.player.in_water()
local time = minecraft.world.time()
local vehicle = minecraft.player.vehicle() -- nil หรือ { name, uuid, position }
local effects = minecraft.player.effects()
local target = minecraft.player.target(8) -- block: { type, block, block_position, face, distance } / entity: { type, entity_id, uuid, name, position, distance }
local biome = minecraft.world.biome(pos)

-- Avatar สั่งได้เฉพาะ namespace ของ Shyne
minecraft.command("shyne help")
```

Avatar ที่ผู้เล่นดาวน์โหลดเป็นโค้ดจากภายนอก จึงอ่านข้อมูลเกมที่ปลอดภัยได้ แต่ `minecraft.command(...)` จำกัดเฉพาะ `/shyne` และ `/sjyne` จำกัดความยาว 256 ตัวอักษร และเว้นอย่างน้อย 250 ms ต่อครั้ง ไม่มี filesystem, network, OS, Java reflection หรือคำสั่ง Minecraft อื่น

## State และ Network

```lua
state.set("page", 2)
local page = state.get("page", 1)

state.sync("mood", "happy")
avatar.network.allow("mood")
local other_mood = state.remote(player_uuid, "mood")

avatar.network.online(true)
avatar.network.local_part("model.root.secret", true)
avatar.network.local_vanilla("PLAYER", true)
```

## Events

```lua
events.on("entity_init", function(event) end)
events.on("tick", function(event) end)
events.on("render", function(event) end)
events.on("microphone", function(mic) end)
events.on("avatar_unload", function(event) end)
events.once("entity_init", function(event) end)
```

ค่า `mic` มี `level`, `speaking`, `muted` และ `whispering` สามารถยกเลิก callback ด้วย `events.off("ชื่อ", callback)` หรือทั้งหมดด้วย `events.clear("ชื่อ")`

ทุก event ส่ง table รูปแบบเดียวกัน โดยมี `type`, `time`, `tick`, `context`, `delta`, `sequence` และ `api` เป็นค่าพื้นฐาน Callback เรียงตามลำดับที่ลงทะเบียน และ error ของ callback หนึ่งจะถูกบันทึกใน `diagnostics.snapshot().runtime_errors` โดยไม่หยุด callback ตัวอื่น

## Vector, Result และ Task

```lua
local velocity = vector.new(1, 2, 3)
local direction = velocity:normalize()
local next_pos = minecraft.player.position() + direction * 2
local distance = next_pos:distance(minecraft.player.position())
local blend = vector.lerp(vector.zero(), next_pos, 0.5)

local safe = result.try(function() return model.root.Head:rotation() end)
if not safe.ok then print(safe.error.code, safe.error.message) end

task.after(20, function() print("ผ่านไป 1 วินาที") end)
local timer = task.every(10, function(event, id)
  return minecraft.player.loaded() -- คืน false เพื่อหยุดงานวน
end)
task.cancel(timer)
```

Vector รองรับ `add`, `sub`, `mul`, `div`, `dot`, `cross`, `length`, `normalize`, `distance`, `lerp`, `clamp` และ operator `+ - * /` งานตั้งเวลาใช้ tick และจำกัด 128 งานต่อ Avatar งานทั้งหมดถูกล้างเมื่อ unload

## Permission

```lua
if permissions.has("world_render") then
  render.world("marker", { type = "text", text = "Here", position = minecraft.player.position() })
end

permissions.require("camera")
local all_permissions = permissions.list()
```

`permissions.has`, `requested`, `require` และ `list` ช่วยให้สคริปต์เลือก fallback ได้โดยไม่ต้องลองเรียก API อันตรายแล้วรอ error

## Animation object และสถานะ ModelPart

```lua
local swim = model.animation.get("swim")
swim:play()
swim:restart()
if swim:playing() then swim:stop() end

local head = model.root.Head
local position = head:position()
local rotation = head:rotation()
local scale = head:scale()
local visible = head:visible()
```

## Sound, Particle และ Input

```lua
sound.play("minecraft:entity.axolotl.splash", { volume = 0.8, pitch = 1.1 })
particle.spawn("minecraft:bubble", minecraft.player.position(), {
  velocity = vector.new(0, 0.05, 0)
})

input.bind("twirl", {
  title = "Twirl",
  key = input.key.r,
  type = "keyboard", -- ใช้ "mouse" คู่กับ input.mouse.left ได้
  modifiers = { "shift" },
  repeat = false,
  on_press = function() model.animation.get("twirl"):restart() end,
  on_hold = function(id) avatar.state.set("holding_" .. id, true) end,
  on_release = function(id) avatar.state.set("holding_" .. id, false) end
})

-- จัดการ binding ระหว่างเกม
local down = input.is_down("twirl")
local saved_key = input.get_key("twirl") -- เช่น "key.keyboard.r"
input.set_key("twirl", "key.keyboard.t")
local conflicts = input.conflicts("twirl")
input.unbind("twirl")
```

Input ของ Avatar เป็น local-only และสร้างได้สูงสุด 32 bindings ต่อแพ็ก รองรับ keyboard, mouse, modifier, on_press, on_release, on_hold และ repeat ระบบใช้ stable id รูปแบบ `avatar_id.binding_id` จำปุ่มใน `config/shyne-creator/avatar-keybinds.json` และปล่อย binding อัตโนมัติเมื่อสลับ/รีโหลดอวตาร ปุ่มจะไม่ทำงานขณะเปิด Chat/เมนูหรือเมื่อหน้าต่างเกมเสีย focus ผู้เล่นเปลี่ยนปุ่ม ตรวจปุ่มซ้ำ ยกเลิกปุ่ม และคืนค่าเดิมได้ที่ Shyne Settings → Avatar Controls

ถ้า binding ใดลงทะเบียนไม่ได้ ระบบจะปิดเฉพาะ binding นั้นและบันทึก diagnostics โดยไม่ทำให้ทั้งอวตารโหลดล้มเหลว การ activate ใช้สองขั้นตอน: runtime ใหม่ถือ lease ชั่วคราวก่อน หาก script พังจะ rollback lease ใหม่และเก็บอวตารเดิมไว้

Particle จำกัด 256 ครั้งต่อ tick เพื่อป้องกันแพ็กทำให้ client ค้าง Texture ที่ลงท้าย `_e` หรือ `_emissive` จะเรนเดอร์ full-bright อัตโนมัติ

## Animation blending และ Render ต่อชิ้นส่วน

```lua
local swim = model.animation.get("swim"):speed(1.2):weight(1):priority(10):loop(true):fade_in(6):fade_out(6):transition(7)
local ears = model.animation.get("ears"):weight(0.7):priority(20):loop(true):mask({ "Head", "Ears" }):additive(true)
model.animation.parameter("tail_strength", 1.25)
model.animation.parameter("wet", minecraft.player.in_water() and 1 or 0)
swim:play()
ears:play()

model.root.Tail:color(0.4, 0.8, 1.0):opacity(0.85):emissive(true)
```

ค่า `priority` สูงกว่าจะ blend ทีหลัง `weight` อยู่ระหว่าง 0–1 และ `speed` อยู่ระหว่าง 0.01–8 ส่วน `fade_in`/`fade_out`/`transition` ใช้หน่วย tick (20 tick ต่อวินาที) `transition` จะ crossfade animation non-additive ที่มี priority เดียวกัน `mask` จำกัด layer ให้กระทบเฉพาะกระดูกที่ระบุ และ `additive(true)` ผสมค่าต่างจาก pose พื้นฐาน

Keyframe Euler ใน Blockbench รักษาการหมุนเต็มรอบ เช่น 0 → 360 ส่วนการ blend ระหว่าง layer จะเลือกเส้นทางสั้นผ่าน ±180° โดยอัตโนมัติ ค่า animation parameter อ่านได้จาก expression ด้วย `v.<ชื่อ>` และซิงก์ให้ผู้เล่นอื่นโดยจำกัดอัตราส่ง

ค่า opacity ต่ำกว่า 1 จะถูกแยกเข้า translucent render pass รายชิ้นโดยอัตโนมัติ จึง blend จริงโดยไม่ทำให้ชิ้นส่วนทึบส่วนอื่นเปลี่ยนโหมด render สี opacity และ emissive ซิงก์ให้ผู้เล่นอื่น

## Camera, Nameplate และ Diagnostics

```lua
avatar.camera.configure({
  offset = vector.new(0, 0.25, -0.4),
  rotation = vector.new(5, 0, 0),
  local_only = true
})

avatar.nameplate.configure({ text = "Merling", visible = true })
local report = diagnostics.snapshot()
```

Camera เป็น local-only เสมอ ส่วน nameplate ซิงก์ผ่าน Avatar snapshot และจำกัดข้อความ 128 ตัวอักษร Diagnostics รายงาน bones, cubes, textures, animation layers, input bindings และ feature flags

## Render Task และ Profiler

```lua
render.text("status", { text = "READY", x = 12, y = 12, color = 0xFF55FFFF, shadow = true })
render.item("icon", { item = "minecraft:diamond", x = 12, y = 28 })
render.block("block", { block = "minecraft:amethyst_block", x = 32, y = 28 })
render.sprite("logo", { texture = "shyne_creator:textures/gui/shyne_creator_logo.png", x = 52, y = 12, width = 32, height = 32 })
render.line("line", { from = vector.new(12, 52, 0), to = vector.new(112, 52, 0), color = 0xFFFFFFFF, width = 2 })
render.rect("panel", { x = 8, y = 8, width = 128, height = 48, color = 0xC0101728, z_index = -1 })
render.outline("edge", { x = 8, y = 8, width = 128, height = 48, thickness = 2, color = 0xFF55FFFF })
render.world("marker", { type = "text", text = "Target", position = vector.new(100, 70, 100), color = 0xFFFFFF55, max_distance = 128 })
render.update("status", { text = "SWIMMING", opacity = 0.8 })
render.remove("icon")
render.clear()
```

รองรับ `text`, `item`, `block`, `sprite`, `line`, `rect`, `outline`, `polyline` และ world-anchored task ทุกชนิด Custom Render API 1.1 เพิ่ม task handle, group ซ้อนชั้น, `z_index`, `opacity`, responsive HUD ผ่าน `render.screen()` และข้อมูลงบผ่าน `render.stats()` ดูรายละเอียดและตัวอย่างเต็มใน `CUSTOM_RENDER_API_TH.md`

เรียก ID เดิมหรือ `render.update` เพื่ออัปเดต task เดิม เก็บได้ 256 tasks แต่เรนเดอร์ไม่เกิน 128 tasks และ 4096 จุดของเส้นต่อเฟรม World task ถูก cull นอกจอและเกิน `max_distance` (เริ่มต้น 128 blocks) แล้วล้างอัตโนมัติเมื่อ unload การวาดใช้ตำแหน่งโลก project เข้าหน้าจอ จึงไม่เขียนข้อมูลลง world หรือส่ง network ต้องประกาศ permission `hud_render` หรือ `world_render` ตามชนิดงาน

เปิด `Shyne Settings → Advanced → Avatar Profiler` เพื่อดู Lua load/tick/render/event, model render, task render, FPS, frame time, heap, ขนาดอวตาร และการประเมิน FPS loss แบบ rolling 240 samples ปุ่ม Export JSON บันทึกรายงานไว้ใน `.minecraft/shyne-logs/profiler/` ดูขั้นตอนสร้างโปรเจกต์และเครื่องมือ validate เพิ่มเติมที่ `CREATOR_QUICKSTART_TH.md`

Lua อ่านค่าเดียวกันได้ด้วย `local profile = profiler.snapshot()` โดยมี `fps`, `frame_ms`, `avatar_frame_ms`, `estimated_fps_loss`, `heap_bytes`, `avatar_bytes`, `task_count` และ `metrics`

## Gameplay/server script

Gameplay pack ทำงานคนละ trust boundary กับ Avatar Lua และให้ Server เป็นผู้ตัดสิน จึงแยกมาตรฐานไว้ใน `SHYNE_GAMEPLAY_API_TH.md`

## Version ของมาตรฐาน

`avatar.json` แบบขั้นต่ำใส่เพียง `{"standard":"2.0","name":"ชื่อ Avatar"}` ได้ ค่า `id`, `model`, texture, multiplayer และ profile overlay ใช้ค่าเริ่มต้นอัตโนมัติ ไม่มีค่า `main` โดยปริยาย หากระบุ Lua ให้ใช้ `"api": "2.0"` และประกาศ `requires`; ระบบจะหยุดพร้อมข้อความชัดเจนเมื่อ API หรือโมดูลไม่รองรับ
