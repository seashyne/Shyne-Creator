# Shyne Blockbench Animation Standard

เอกสารนี้ใช้กับ Shyne Creator `2.8.0-alpha-26.2` ซึ่งอ่าน `.bbmodel` ตามรูปแบบโปรเจกต์ Blockbench 5.1.5 โดยตรง และใช้ animation runtime เดียวกันบน Fabric กับ NeoForge

## รองรับแล้ว

- Blockbench project format 5.0 และ migration แกน animation ของไฟล์ก่อน 5.0
- Bone hierarchy และ pivot ของ parent/child
- Cube และ mesh element พร้อม face/UV ภายใต้ hierarchy เดียวกัน
- ค่าเริ่มต้น `visibility` และ `export` ของ group/element; Lua visibility ที่สั่งภายหลังใช้ override ได้
- canonical full path และ UUID สำหรับ part ที่ชื่อซ้ำ; short path ใช้เฉพาะชื่อที่ไม่กำกวม
- `parent_type` ของ Blockbench/Figura สำหรับผูก bone กับ `Head`, `Body`, แขน และขา Minecraft อัตโนมัติ
- Position, rotation และ scale channels
- Keyframe แบบ pre/post data points
- Linear, step, Catmull-Rom และ cubic Bezier พร้อม handle แยกแต่ละแกน
- Euler rotation เต็มรอบ เช่น 0 → 360 และ quaternion interpolation เมื่อ Animator เปิดใช้
- Expression ที่ปลอดภัยสำหรับเวลา animation, ความเร็ว, pitch, yaw, สถานะเปียก/ว่ายน้ำ และ parameter จาก Lua
- Animation layers, weight, mask, additive, priority, fade และ transition
- ส่ง animation parameter ให้ผู้เล่นอื่นผ่าน Avatar snapshot โดยจำกัดอัตราส่ง
- Validator แจ้ง expression ที่ไม่รองรับ แทนการเปลี่ยนค่าเป็นศูนย์โดยไม่แจ้ง

## Lua API

```lua
model.animation.parameter("tail_strength", 1.25)
model.animation.parameter("pitch", -5)
model.animation.parameter("yaw", 10)
model.animation.parameter("wet", 1)

model.animation.get("swim")
  :loop(true)
  :priority(10)
  :transition(7)
  :play()
```

อ่านค่าด้วย `model.animation.parameter("name")` และล้างด้วย `model.animation.clear_parameter("name")`

## ตัวแปร Expression

- `q.anim_time`, `time`, `anim_time`
- `speed`, `yaw`, `pitch`, `wet`, `swimming`
- `v.<name>` สำหรับค่าจาก `model.animation.parameter`
- `this`, `base` สำหรับค่าพื้นฐานของแกนนั้น
- `math.sin`, `math.cos`, `math.tan`, `math.abs`, `math.pow`, `min`, `max`, `clamp` และ `lerp`

ฟังก์ชันตรีโกณมิติของ animation ใช้องศาให้ตรงกับการ preview ของ Blockbench/Molang

Expression ที่แปลงมาจากแพ็กเก่าบางชุดอาจมี wrapper รูป `local t = require("...") return <expression>` Shyne รองรับเฉพาะรูปแบบนี้แบบจำกัด โดยตัด wrapper แล้วอ่าน `t.<name>` เป็น animation parameter `v.<name>` ใน expression engine ที่ปลอดภัย ระบบจะไม่เรียก `require`, ไม่โหลด Lua module ต้นทาง และยังปฏิเสธคำสั่ง Lua อื่นที่ไม่ใช่ expression คณิตศาสตร์ที่รองรับ

Auto Humanoid จะไม่ใส่ vanilla pose ซ้ำเมื่อพบ bone ชื่อเทียบกันได้ซ้อนอยู่ใน hierarchy เช่น `body` ที่ครอบ `Body` หากโมเดลมีชื่อซ้ำหรือโครงเฉพาะทาง ให้ใช้ canonical full path, `parent_type` หรือ metadata role/tag แทนการพึ่งชื่อสั้น

## ขอบเขต

เอกสารนี้กำหนดมาตรฐาน model/transform animation ของ Shyne ไม่ได้หมายความว่า Shyne ฝัง Blockbench, Figura หรือใช้ runtime ของเครื่องมือเหล่านั้นขณะเล่นเกม ซอร์สและไฟล์ต้นทางใช้เป็น reference ตอนพัฒนา/แปลงเท่านั้นและไม่ถูกรวมเป็น dependency ของ mod การรองรับฟอร์แมตและ compatibility expression ไม่ใช่คำรับรองว่าแพ็กจาก Figura ทุกชุดจะแสดงผลเหมือนเดิม 100%
