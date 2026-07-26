# Shyne Standard 2.0 สำหรับ Blockbench

ไฟล์ `shyne_standard_2.js` เป็น plugin ของ Shyne เอง ไม่ต้องติดตั้ง Figura หรือ plugin ของ Figura

## ติดตั้ง

1. เปิด Blockbench
2. ไปที่ `File → Plugins… → Load Plugin from File`
3. เลือก `tools/blockbench/shyne_standard_2.js`
4. เปิด `.bbmodel` แล้วตั้งค่า Shyne ใน Project, Group และ Animation properties

## วิธีใช้แบบสั้น

1. ตั้ง Project profile เป็น `Accessory`, `Full Body` หรือ `Merling`
2. เลือก Group หลัก แล้วตั้ง `Shyne Vanilla Attachment` เช่น Head หรือ Body
3. ตั้ง Animation State ให้ animation เช่น Idle, Walk, Sprint หรือ Swim
4. Animation ที่เล่นซ้อนตลอดให้เปิด `Shyne Ambient Autoplay`; animation กระพริบตาให้เปิด `Shyne Blink`
5. เลือก texture แล้วตั้ง `Shyne Package Role` เป็น Model Texture, Avatar Icon หรือ Wardrobe Outfit ตามหน้าที่
6. ใช้ `Tools → Validate Shyne Standard 2.0`
7. ใช้ `File → Export → Export Shyne Avatar Package (.zip)` เพื่อรับแพ็กที่พร้อมแตกลงโฟลเดอร์ `shyne-mods/avatars`

ZIP จะมีโฟลเดอร์ชื่อ Avatar ID และรวม `avatar.json`, `model.bbmodel`, `textures/`, `avatar.png` และ `outfit/` ให้อัตโนมัติ Texture ทุกภาพยังคงอยู่ใน `textures/` เพื่อรักษา index และ UV ของ Blockbench ส่วน Avatar Icon และ Wardrobe Outfit จะถูกทำสำเนาไปยังตำแหน่งที่ Shyne runtime ใช้

หากต้องการเฉพาะ manifest ยังใช้ `File → Export → Export Shyne avatar.json` ได้

## avatar.json แบบสั้น

ค่า `Shyne Manifest Detail` เริ่มต้นเป็น `Compact` ถ้าโปรเจกต์ใช้ profile `Accessory`, model ชื่อ `model.bbmodel`, Auto Animation และไม่มี Lua ไฟล์ที่ export จะเหลือเพียง:

```json
{
  "name": "Bunny Ears"
}
```

Shyne เติม Standard 2.0, ID จากชื่อโฟลเดอร์, model, profile และ animation controller ค่าเริ่มต้นให้เอง เมื่อเปลี่ยนเป็น Full Body/Merling ตั้ง state animation เอง หรือเปิด Lua plugin จะเพิ่มเฉพาะ field ที่จำเป็น หากต้องการเห็นทุกค่าให้เลือก `Explicit defaults`

Avatar ทั่วไปไม่ต้องเปิด `Use Advanced Shyne Lua` และไม่ต้องมี `script.lua` แต่ Lua ยังเป็นความสามารถหลักสำหรับงานอิสระและ rig ซับซ้อน หากเปิดตัวเลือกนี้ plugin จะเพิ่ม `main` และ `api: "2.0"` แล้วถามให้เลือกไฟล์ `.lua` ตอน export ZIP ถ้าเลือกไฟล์เดียว plugin จะใช้ไฟล์นั้นเป็น main ตามชื่อ `Advanced Lua Main`; ถ้าเลือกหลายไฟล์ต้องมี main ตรงชื่อนี้ โมดูลใน exporter รุ่นนี้ใช้ชื่อไฟล์ระดับบนสุดที่ไม่ซ้ำกัน

ค่า `Shyne Physics Preset` บน Group ทำงานใน Standard 2.0 runtime แล้ว เลือก `Bunny Ears`, `Tail`, `Hair`, `Cloth` หรือ `Wings` ที่ root ของสายกระดูก ระบบจะสร้าง secondary-motion chain ให้ root และลูกที่ไม่ได้ตั้ง preset ใหม่โดยอัตโนมัติ พร้อม spring, damping, angle cone, การรับแรงเร่ง/เลี้ยว/ลงพื้น, แรงลม และการหน่วงในน้ำ การเคลื่อนไหวอยู่ใน additive layer แยกจึงซ้อนกับ Blockbench animation และ Lua ได้ งาน collision/IK หรือ tuning ราย bone ขั้นสูงยังใช้ Native Rig API

Plugin รุ่นนี้ยังไม่ส่ง live reload เข้าเกมโดยตรง และยังไม่สร้าง preview render เป็น `avatar.png` อัตโนมัติ ผู้สร้างเลือก texture ที่เตรียมไว้เป็น `Avatar Icon` ได้โดยตรง
