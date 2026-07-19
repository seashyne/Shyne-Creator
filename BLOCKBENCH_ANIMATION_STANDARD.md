# Shyne Blockbench Animation Standard

Shyne Creator `2.7.29` อ่าน `.bbmodel` ตามรูปแบบโปรเจกต์ Blockbench 5.1.5 โดยตรง และใช้ animation runtime เดียวกันบน Fabric กับ NeoForge

## รองรับแล้ว

- Blockbench project format 5.0 และ migration แกน animation ของไฟล์ก่อน 5.0
- Bone hierarchy และ pivot ของ parent/child
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

## ขอบเขต

เอกสารนี้กำหนดมาตรฐาน transform animation ของ Shyne ไม่ได้หมายความว่า Shyne ฝัง Blockbench หรือใช้โค้ด Blockbench ขณะเล่นเกม ซอร์ส Blockbench 5.1.5 ใช้เป็น reference ตอนพัฒนาเท่านั้นและไม่ถูกรวมในไฟล์ mod
