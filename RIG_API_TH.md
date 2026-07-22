# Shyne Native Rig API 1.3

เอกสารนี้ตรงกับ Shyne Creator `2.8.0-alpha-26.2` และใช้กับ Avatar ที่ระบุ `main` เพื่อเปิด native Lua พร้อมประกาศ `api: "2.0"` และ `"rig": ">=1.3"` ใน `requires`

Rig API เป็นระบบ native ของ Shyne สำหรับ avatar แบบ Merling/SquAPI-style โดยไม่ต้องใช้ Figura: มันทำ secondary motion, chain, cosmetic armor และ vanilla attachment ผ่าน Lua ปกติ

## Physics ที่ไม่ทับ animation

`part:rot(...)` เป็นการควบคุม rotation หลัก จึงแทน animation channel เดิม ส่วน `part:rot_add(...)` เป็น offset ที่บวกหลัง Blockbench animation และหลังท่า vanilla เหมาะกับ physics, หู, หาง, ผม และผ้า

```lua
local ear = model.part("model.BunnyEars")
ear:rot_add(0, 8, 0) -- ยังคงเล่น animation ใน .bbmodel ต่อได้
```

## Spring และ chain

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
```

`gravity`, `motion`, `wind`, `base` และ `target` เป็น vector องศา; `target` เป็น function ที่คืน vector ได้ `motion` คูณกับความเร็วผู้เล่นในแต่ละแกน และ `limit` จำกัด rotation ของทุก segment. `tail:stop()` หยุด physics, `tail:start()` เริ่มใหม่, `tail:reset()` คืน offset เป็นศูนย์

`cone` จำกัดมุมรวมของ spring, `tail.controllers[1]:impulse(vector.new(12, 0, 0))` เพิ่มแรงกระแทก และ `rig.wind({ strength = 2, direction = vector.new(1,0,0) })` สร้างแรงลม deterministic ได้

## Collision, IK และ animation graph

```lua
local wing = rig.spring("model.WingTip", {
  wind = rig.wind({ strength = 3, gust = 0.5, direction = vector.new(0, 0, 1) }),
  collision = { origin = vector.new(0.4, 1.4, 0), direction = vector.new(0, 0, 1), distance = 0.6, radius = 0.12, strength = 18 },
  cone = 35
})

local arm_ik = rig.ik2("model.UpperArm", "model.LowerArm", function() return vector.new(0, -0.7, 0.5) end, {
  upper_length = 0.45, lower_length = 0.45
})

rig.animation_graph({
  default = "idle",
  transition = 6,
  order = { "swim", "walk", "idle" },
  states = {
    swim = { animation = "swim", when = minecraft.player.swimming, priority = 20 },
    walk = { animation = "walk", when = function() return minecraft.player.velocity():length() > 0.08 end },
    idle = { animation = "idle" }
  }
})
```

`minecraft.world.probe(origin, direction, distance, radius)` ตรวจ collision แบบ sweep กับ block/entity และคืน `hit`, `type`, `normal`, `position`, `distance`. เมื่อให้ `radius > 0` Shyne จะ sample รอบแนว probe เพื่อให้ขอบ block และ entity ตอบสนองกับความกว้างของหู/ปีก ไม่ใช่ ray ตรงเส้นเดียว; ระบบนี้ใช้เพื่อภาพของ Avatar เท่านั้น ไม่แก้ hitbox หรือโลกเกม

Animation graph เลือก state ตาม `order = { "swim", "walk" }` ก่อนเสมอเมื่อกำหนดไว้; ถ้าไม่กำหนด จะเลือก `priority` ที่มากกว่า และหาก priority เท่ากันจะเลือกอย่างคงที่แทนการพึ่งลำดับ table ของ Lua. `transition`, `fade_in` และ `fade_out` ของแต่ละ state ถูกบันทึกกับ animation layer ตั้งแต่เริ่มเล่น เพื่อให้การเปลี่ยนท่าค่อย ๆ blend ได้จริง

## Vanilla attachment ที่ละเอียด

```lua
model.part("model.Horns"):vanilla_parent("HEAD", "full")
model.part("model.CapeRoot"):vanilla_parent("BODY", "rotation")
model.part("model.WorldPin"):vanilla_parent("BODY", "position")
model.part("model.Horns"):detach_from_vanilla() -- คืนไปใช้ parent_type จาก .bbmodel
```

mode `full` รับทั้งตำแหน่งและ rotation, `rotation` รับเฉพาะ rotation และ `position` รับเฉพาะตำแหน่ง. ทุก mode เป็น parent transform จึงซ้อนกับ animation ได้

## Cosmetic armor

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

ชิ้นส่วนจะแสดงเมื่อผู้เล่นใส่ armor slot นั้น และซ่อนเมื่อ slot ว่าง. key `left_arm`/`right_arm` อ่าน chest slot, `left_leg`/`right_leg` อ่าน legs slot, และ `left_foot`/`right_foot` อ่าน feet slot พร้อมผูกกับ limb ให้โดยอัตโนมัติ; `feet` ยังเป็น alias แบบสั้นที่ผูกกับขาซ้ายเพื่อความเข้ากันได้. ใช้ `slot` หรือ `parent` ใน spec เมื่อต้องการเปลี่ยนพฤติกรรม

ใช้ `variants = { ["minecraft:diamond_chestplate"] = { "model.DiamondChest" }, ["material:minecraft:diamond"] = { "model.DiamondTrim" }, ["trim_pattern:minecraft:spire"] = { "model.SpireTrim" }, default = { "model.GenericChest" } }` เพื่อเลือก material/trim model ตาม item จริง และใช้ `rig.elytra({ "model.Elytra" }, { show_folded = true })` สำหรับ cosmetic Elytra ที่ตาม chest slot/flight state. `default` เป็น fallback จะแสดงเฉพาะเมื่อไม่มี variant อื่นตรงกัน; material และ trim ที่ตรงกันหลายรายการสามารถแสดงซ้อนกันได้. `minecraft.player.armor(slot)` คืน `material`, `trim_material` และ `trim_pattern` สำหรับ armor vanilla ที่มีข้อมูลนั้น

## SquAPI compatibility

`require("SquAPI")` และ `require("lib.SquAPI")` คืน native compatibility table ของ Shyne โดยไม่โหลดไฟล์ Figura หรือ Lua ของ Figura เลย รองรับรูปแบบ `:new(...)` ของแพ็ก SquAPI ที่ใช้จริง และยังใช้ชื่อเก่าแบบเรียกตรงได้ในหลายจุด เช่น `squapi.tails(...)`, `squapi.ear(...)`, `squapi.eye(...)` และ `squapi.bouncewalk(...)`.

Tail, ear และ bewb แบบ `:new(...)` ใน compatibility layer ใช้ BERP integrator เดียวกับ SquAPI ปัจจุบัน (`stiffness`/`bounce` จึงมีความหมายเดิม) ไม่ได้แปลงค่า `bounce` ไปเป็น damping ของ `rig.spring`. รูปแบบ callable legacy (`tails(...)`, `ear(...)`, `bewb(...)`) ใช้สมการ `bounceObject` รุ่นเดิมของมันเอง เพื่อให้ค่า bounce เล็ก เช่น `0.025` ยังหน่วงอย่างถูกต้อง. หูซ้าย/ขวาเป็น physics คนละตัวและ mirror กันตาม API ต้นทาง ไม่ใช่ chain ต่อกัน. `squapi.tail:new(...)` ใช้รูปแบบ SquAPI ปัจจุบัน; `squapi.tails(...)` ใช้พารามิเตอร์ legacy เดิม โดย `intensity` เป็นแรงเลี้ยว/แนวตั้ง และ `tailVelBend` เป็นแรงไปหน้า/หลังแยกกัน

- Physics/attachment: `tail:new`, `tails(...)`, `ear:new`, `arm:new`, `leg:new`, `smoothHead:new`, `smoothTorso`, `smoothHeadNeck`, `bewb:new`, `bounceWalk:new`, `taur:new`, `taurPhysics`, `FPHand:new`, `setFirstPersonHandPos`
- Floating physics: `hoverPoint:new(...)` และ `floatPoint(...)`
- Animation: `walk`, `crouch`, `randimation:new`, `blink`
- Utility ที่มีพฤติกรรมจริง: `bounceObject:new`, `bouncetowards`, `getForwardVel`, `getSideVelocity`, `yvel`, `lineargraph`, `parabolagraph`

ค่าที่แก้ระหว่างเล่น เช่น `tail.bendStrength`, `tail.velocityPush`, `tail.flyingOffset`, `arm.strength` และ `controller.enabled` อ่านทุก tick เช่นเดียวกับรูปแบบ SquAPI. Controller รองรับ `enable()`, `disable()`, `toggle()`, `setEnabled(boolean)`, `zero()` และ limb รองรับ `freeze()`/`unfreeze()` พร้อม `rot`/`pos`. ค่า global ที่แพ็กเดิมใช้บ่อยคือ `squapi.doBlink`, `wagStrength`, `doBounce`, `smoothHeadOffset`, `cancelHeadMovement` และ `torsoOffset`. `smoothHead:new({ body, head }, ..., true)` จะรับตำแหน่งหัวเฉพาะ segment สุดท้าย (หรือ index ที่ระบุ) เหมือนต้นทาง จึงไม่ซ้อนตำแหน่งตอนหมอบ.

ค่าเริ่มต้น `squapi.autoFunctionUpdates = true`; หากแพ็กตั้งเป็น `false` ให้เรียก `controller:tick()` เอง และ Shyne จะไม่อัปเดต controller นั้นซ้ำ.

### Floating companion: HoverPoint และ FloatPoint

```lua
local companion = require("SquAPI").hoverPoint:new(
  model.part("model.FloatingCompanion"),
  vector.new(0.8, 1.1, -0.5), -- ระยะ world unit จากผู้เล่น
  0.2, 5, 1, 0.05,
  true,  -- หมุน offset ตามตัวผู้เล่น
  true   -- ชน block และ entity แบบ visual-only
)

companion:setCollisionRadius(0.16)
companion.collisionBounce = 0.35
```

`hoverPoint:new(element, elementOffset, springStrength, mass, resistance, rotationSpeed, rotateWithPlayer, doCollisions)` ใช้หน่วย world สำหรับ `elementOffset`; `pos`, `vel`/`velocity`, `enabled`, `reset()`, `setEnabled()`, `setCollisions()` และ `setCollisionRadius()` เปลี่ยนได้ระหว่างเล่น. เมื่อเปิด collision จะใช้ `minecraft.world.probe()` จึงชนได้ทั้ง block/entity แต่เป็นภาพของ Avatar เท่านั้น ไม่กระทบ hitbox, server หรือการเดินของผู้เล่น. วาง element ไว้ใน top-level Blockbench group เพื่อไม่ให้รับ transform ของ bone ที่เคลื่อนไหวอยู่.

`squapi.floatPoint(element, x, y, z, stiffness, bouncy, ymin, maxradius)` รองรับสคริปต์ Merling/SquAPI รุ่นเก่าแล้ว โดย `x/y/z` ยังเป็น pixel offset ตาม API เดิม และใช้ `bounceObject` รุ่นเก่า ไม่ควรสลับไปใช้ค่าของ `hoverPoint` ตรง ๆ. `squapi.floatPointEnabled = false` หยุด update ของ FloatPoint ตามพฤติกรรมเดิม.

`animateTexture` ยังไม่มี เพราะต้องใช้ UV mutation ที่ Shyne renderer ยังไม่มี equivalent จริง. รวมถึง `fixPortrait` ของ `smoothHead` จะรับ argument ได้แต่ไม่สร้าง portrait copy เพราะ GUI portrait transform ยังไม่พร้อม.

## Merling

ตัวแปลง Figura profile `Merling` สร้าง controller native ที่มี form/locomotion อยู่แล้วใน `tools/templates/merling_native.lua`; รุ่นนี้เพิ่ม tail chain physics ให้ template นั้นด้วย. พฤติกรรมที่ต้องอาศัย module Figura เฉพาะทางยังต้องเขียนด้วย Rig API หรือย้าย logic มาเป็น Shyne Lua ก่อน
