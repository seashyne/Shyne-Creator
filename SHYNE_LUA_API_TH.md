# Shyne Lua API Standard 1.0

มาตรฐานนี้เป็น API ที่ออกแบบสำหรับ Shyne Creator โดยตรง ไม่ใช่สำเนา API ของ Figura ชื่อหลักมีเพียง 7 กลุ่มและใช้ตัวพิมพ์เล็กเหมือนกันทุกแพ็ก:

| กลุ่ม | ใช้ทำอะไร |
|---|---|
| `minecraft` | อ่านผู้เล่น/โลก และเรียกคำสั่งเกม |
| `model` | เลือกชิ้นส่วน ขยับ ซ่อน และเล่น animation |
| `avatar` | ตั้งค่ากล้อง texture vanilla model และการซิงก์ |
| `state` | เก็บค่าชั่วคราวหรือค่าที่ซิงก์ |
| `events` | รับเหตุการณ์ด้วย `events.on(...)` |
| `ui` | เพิ่ม Action ใน Palette |
| `vector` | สร้างค่า x/y/z |

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
```

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
```

ค่า `mic` มี `level`, `speaking`, `muted` และ `whispering` สามารถยกเลิก callback ด้วย `events.off("ชื่อ", callback)`

ทุก event ส่ง table รูปแบบเดียวกัน โดยมี `type`, `time`, `tick`, `context` และ `delta` เป็นค่าพื้นฐาน

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
  on_press = function() model.animation.get("twirl"):restart() end
})
```

Input ของ Avatar เป็น local-only และสร้างได้สูงสุด 32 bindings ต่อแพ็ก Particle จำกัด 256 ครั้งต่อ tick เพื่อป้องกันแพ็กทำให้ client ค้าง Texture ที่ลงท้าย `_e` หรือ `_emissive` จะเรนเดอร์ full-bright อัตโนมัติ

## Animation blending และ Render ต่อชิ้นส่วน

```lua
local swim = model.animation.get("swim"):speed(1.2):weight(1):priority(10):loop(true):fade_in(6):fade_out(6)
local ears = model.animation.get("ears"):weight(0.7):priority(20):loop(true):mask({ "Head", "Ears" }):additive(true)
swim:play()
ears:play()

model.root.Tail:color(0.4, 0.8, 1.0):opacity(0.85):emissive(true)
```

ค่า `priority` สูงกว่าจะ blend ทีหลัง `weight` อยู่ระหว่าง 0–1 และ `speed` อยู่ระหว่าง 0.01–8 ส่วน `fade_in`/`fade_out` ใช้หน่วย tick (20 tick ต่อวินาที) `mask` จำกัด layer ให้กระทบเฉพาะกระดูกที่ระบุ และ `additive(true)` ผสมค่าต่างจาก pose พื้นฐาน การ interpolate มุมจะเลือกเส้นทางสั้นผ่าน ±180° อัตโนมัติ ข้อมูล layer ทั้งหมดซิงก์ให้ผู้เล่นอื่น

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

## Gameplay/server script

Gameplay pack เป็นโค้ดที่เจ้าของเซิร์ฟเวอร์ติดตั้งและ Server เป็นผู้ตัดสิน จึงใช้คำสั่งเต็มได้:

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

สิทธิ์และผลลัพธ์จริงยังผ่านตัวตรวจของ Shyne/Minecraft Server ไม่ควรเชื่อค่า damage, mana หรือ cooldown จาก Client

## Version ของมาตรฐาน

`avatar.json` แบบขั้นต่ำใส่เพียง `{"name":"ชื่อ Avatar"}` ได้ หากไม่ระบุ `api_version` ระบบจะล็อกให้เป็น Standard 1 เสมอ ไม่ได้เลื่อนไปตาม API รุ่นใหม่ ค่า `id`, `version`, `main`, `model`, texture, multiplayer, first-person และกล้องใช้ค่าแนะนำอัตโนมัติ แต่ถ้าระบุ `api_version` เองแล้วเป็นรุ่นที่ไม่รองรับ ระบบจะหยุดพร้อมแจ้ง error
