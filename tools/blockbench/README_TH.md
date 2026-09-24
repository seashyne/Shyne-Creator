# เริ่มใช้ Blockbench กับ Shyne Standard 2.0

คู่มือนี้สำหรับคนที่ยังไม่เคยใช้ Blockbench มาก่อน เป้าหมายคือสร้างของตกแต่งง่าย ๆ เช่น หู หมวก หรือปีก ตรวจไฟล์ แล้ว Export เป็น Avatar ที่เปิดใช้ใน Shyne Creator ได้โดยไม่ต้องเขียน Lua

> วิธีเริ่มที่ง่ายที่สุดคือสร้าง **Accessory** ก่อน เพราะโมเดลจะวาดเพิ่มบนตัวละคร Minecraft เดิมและใช้ Group ยึดกับหัว ลำตัว แขน หรือขาได้ทันที

## สิ่งที่ต้องเตรียม

1. [Blockbench](https://www.blockbench.net/) รุ่น 4.10 ขึ้นไป
2. ไฟล์ Plugin `shyne_standard_2.js` จาก `tools/blockbench/`
3. Minecraft 26.3 ที่ติดตั้ง Shyne Creator แล้ว
4. โฟลเดอร์ตัวอย่าง `examples/zero-lua-avatar`

Plugin ของ Shyne ใช้ได้ทั้ง Blockbench Desktop และ Web แต่แนะนำ Desktop สำหรับมือใหม่ เพราะการเปิดไฟล์ Plugin, Texture และตำแหน่ง Export ทำได้ตรงกว่า ไม่ต้องติดตั้ง Figura หรือ Plugin ของ Figura

## รู้จักหน้าจอ Blockbench ก่อน

| ส่วนของหน้าจอ | ใช้ทำอะไรกับ Avatar |
| --- | --- |
| Viewport ตรงกลาง | ดู หมุน ย้าย และปรับขนาดโมเดลสามมิติ |
| Outliner ด้านขวา | จัด Group, Cube, Mesh และลำดับพ่อ–ลูกที่ Shyne ใช้เป็นโครงกระดูก |
| Texture Panel | เพิ่มหรือสร้างไฟล์ PNG และกำหนดบทบาท Model Texture, Icon หรือ Outfit |
| UV Editor | กำหนดว่าผิวแต่ละด้านของ Cube ใช้ส่วนใดของ Texture |
| Edit Mode | สร้าง Group, Cube, Mesh และจัดตำแหน่งโมเดล |
| Paint Mode | วาด Texture บนโมเดลหรือภาพสองมิติ |
| Animate Mode | สร้าง Animation และวาง Keyframe บน Timeline |

Outliner สำคัญที่สุดสำหรับ Shyne เพราะ Group เป็นทั้งโครงสร้าง จุดหมุน จุดยึดกับตัวผู้เล่น และสาย Physics ส่วน Cube/Mesh เป็นรูปทรงที่อยู่ใต้ Group นั้น

## 1. ติดตั้ง Shyne Plugin

1. เปิด Blockbench
2. ไปที่ `File → Plugins…`
3. กดปุ่มโหลด Plugin จากไฟล์ แล้วเลือก `tools/blockbench/shyne_standard_2.js`
4. เปิดไฟล์ `.bbmodel`
5. ตรวจว่าเมนู `Tools → Validate Shyne Standard 2.0` ปรากฏขึ้น

Blockbench รองรับการลากไฟล์ Plugin มาวางในโปรแกรมด้วย หากติดตั้งแล้วแต่เมนูไม่ปรากฏ ให้เปิดหน้า Plugins และ Reload Plugin หรือปิดแล้วเปิด Blockbench ใหม่

เมื่อติดตั้งสำเร็จ Plugin จะเพิ่มค่าของ Shyne ลงในหน้าต่าง Project, Group, Texture และ Animation Properties โดยตรง

## 2. เริ่มจากไฟล์ตัวอย่าง

อย่าแก้ไฟล์ต้นฉบับใน Creator Kit โดยตรง ให้ทำสำเนาก่อน:

1. คัดลอกโฟลเดอร์ `examples/zero-lua-avatar`
2. เปลี่ยนชื่อสำเนา เช่น `my-first-avatar`
3. เปิด `model.bbmodel` ใน Blockbench
4. ใช้ `File → Save Project As…` หากต้องการย้ายไฟล์ไปโฟลเดอร์งานอื่น

ตัวอย่างมี Group `HeadAccessory` ที่ตั้ง `Shyne Vanilla Attachment` เป็น `Head` ไว้แล้ว สิ่งที่สร้างใต้ Group นี้จะหมุนตามหัว Minecraft

โครงสร้างเริ่มต้น:

```text
HeadAccessory  [Attachment: Head]
└─ สร้าง Cube หรือ Group ของคุณที่นี่
```

หากต้องการสร้างโปรเจกต์เปล่า ให้เลือก Generic Model แล้วบันทึกเป็น `model.bbmodel` จากนั้นสร้าง Group หลักและตั้งค่าตามหัวข้อถัดไป

## 3. ตั้งค่า Project

เปิด `File → Project…` แล้วตรวจค่าของ Shyne:

| ค่า | แนะนำสำหรับงานแรก |
| --- | --- |
| Shyne Avatar Name | ชื่อที่ต้องการให้แสดงในเกม |
| Shyne Avatar ID | ใช้อักษรอังกฤษตัวเล็ก ตัวเลข จุด ขีดกลาง หรือขีดล่าง เช่น `my.bunny_ears` |
| Shyne Manifest Detail | `Compact (recommended)` |
| Shyne Profile | `Accessory` |
| Shyne Auto Animation | เปิด |
| Use Advanced Shyne Lua | ปิด |

ค่า Compact จะ Export เฉพาะข้อมูลที่จำเป็น Shyne เติม Standard, model, profile และ Animation Controller ค่าเริ่มต้นให้เอง งานทั่วไปจึงไม่ต้องเขียน `avatar.json` ยาว ๆ

## 4. สร้าง Group และรูปทรง

ตัวอย่างสร้างหูกระต่าย:

1. เข้า Edit Mode
2. เลือก Group `HeadAccessory` ใน Outliner
3. เพิ่ม Group ลูกชื่อ `LeftEar`
4. ตั้ง Pivot ของ `LeftEar` ไว้บริเวณโคนหู เพราะ Pivot คือจุดที่ Group ใช้หมุน
5. เพิ่ม Cube เป็นลูกของ `LeftEar` แล้วปรับขนาดให้เป็นหู
6. Duplicate เป็น `RightEar` และย้ายไปอีกข้าง
7. ตรวจใน Outliner ว่า Cube อยู่ใต้ Group ที่ถูกต้อง

โครงสร้างที่แนะนำ:

```text
HeadAccessory  [Attachment: Head]
├─ LeftEar
│  └─ LeftEarCube
└─ RightEar
   └─ RightEarCube
```

ตั้งชื่อ Group ไม่ให้ซ้ำกันเมื่อทำ rig ซับซ้อน จะช่วยให้ Animation, Lua และข้อความตรวจไฟล์ชี้ชิ้นส่วนได้ตรง

### จุดยึดกับตัว Minecraft

เลือก Group หลัก เปิด Group Properties แล้วตั้ง `Shyne Vanilla Attachment`:

| Attachment | เหมาะกับ |
| --- | --- |
| `Head` | หมวก หู แว่น ผม |
| `Body` | เสื้อคลุม กระเป๋า หาง ปีก |
| `LeftArm` / `RightArm` | ถุงมือ กำไล อาวุธตกแต่ง |
| `LeftLeg` / `RightLeg` | รองเท้า ของติดขา |
| `None` | Group อิสระหรือ Group ลูกที่รับตำแหน่งจาก Parent อยู่แล้ว |

ตั้ง Attachment ที่ Group รากของของตกแต่ง ไม่จำเป็นต้องตั้งซ้ำทุก Cube

## 5. ใส่ Texture และจัด UV

1. เพิ่ม PNG จาก Texture Panel หรือสร้าง Texture ใหม่
2. เลือก Cube แล้วตรวจว่าทุกด้านใช้ Texture ที่ต้องการ
3. จัดตำแหน่งหน้า Cube ใน UV Editor
4. เข้า Paint Mode หากต้องการวาดสีบนโมเดลโดยตรง
5. Save Texture และ Save Project

Texture Panel สามารถกำหนด `Shyne Package Role` ได้:

| Role | หน้าที่ |
| --- | --- |
| Model Texture | Texture ที่โมเดลใช้ตามปกติ |
| Avatar Icon | ภาพปกที่แสดงในคลังอวตาร เลือกได้หนึ่งภาพ |
| Wardrobe Outfit | ชุดเสริมที่สลับจากหน้าตู้เสื้อผ้าได้ |

Outfit ชื่อ `.png` ธรรมดาจะซ้อนทับ Texture เดิมแบบ alpha overlay หากต้องการแทน Texture เดิมทั้งภาพ ให้ตั้งชื่อเป็น `name.replace.png`, `name_replace.png` หรือ `name-replace.png`

## 6. ทำ Animation แบบง่าย

หาก Avatar แรกยังไม่ต้องขยับ สามารถข้ามหัวข้อนี้แล้ว Export ได้เลย

1. เข้า Animate Mode
2. สร้าง Animation เช่น `EarWiggle`
3. เลือก Group `LeftEar` หรือ `RightEar`
4. เพิ่ม Rotation Keyframe ที่เวลาเริ่ม กลาง และจบ
5. เลื่อน Playhead แล้วกด Play เพื่อตรวจการเคลื่อนไหว
6. เปิด Animation Properties และตั้งค่าของ Shyne

ค่าที่ใช้บ่อย:

| ค่าของ Shyne | ใช้เมื่อ |
| --- | --- |
| Shyne Animation State: `Idle` | เล่นตอนผู้เล่นยืน |
| `Walk` / `Sprint` | เล่นตามการเดินหรือวิ่ง |
| `Swim` / `Crouch` / `Fly` | เล่นตามสถานะนั้น |
| Shyne Ambient Autoplay | Animation ที่เล่นวนตลอดโดยไม่ผูกกับการเคลื่อนที่ |
| Shyne Blink | Animation กระพริบตาที่ระบบเรียกเป็นช่วง ๆ |

Animation State เหมาะกับท่าที่แทนกันตามสถานะ ส่วน Ambient Autoplay เหมาะกับหูกระดิก แสงหมุน หรือของตกแต่งที่ขยับตลอด

## 7. เพิ่ม Physics โดยไม่เขียน Lua

เลือก Group รากของสายกระดูก แล้วตั้ง `Shyne Physics Preset`:

- Bunny Ears
- Tail
- Hair
- Cloth
- Wings

Preset จะทำงานกับ Group รากและลูกที่ไม่ได้ตั้ง Preset ใหม่ ควรวาง Pivot ของแต่ละ Group ไว้ตรงข้อต่อจริง เช่น โคนหูหรือข้อหาง การชน, IK และการปรับค่าราย Bone แบบละเอียดจึงค่อยใช้ Native Rig API ภายหลัง

## 8. ตรวจโปรเจกต์ก่อน Export

เลือก `Tools → Validate Shyne Standard 2.0`

- **Error/สีแดง:** ต้องแก้ก่อน Export เช่น ไม่มี Texture, ID ผิดรูปแบบ หรืออ้าง Animation ที่ไม่มี
- **Warning/คำเตือน:** Export ได้แต่ควรตรวจ เช่น ยังไม่มี Avatar Icon หรือไม่มี Animation

แก้ Error รายการแรกก่อนแล้ว Validate ซ้ำ เพราะปัญหาหนึ่งอาจทำให้เกิดข้อความต่อเนื่องหลายรายการ

## 9. Export เป็น Avatar Package

1. Save Project ให้เรียบร้อย
2. เลือก `File → Export → Export Shyne Avatar Package (.zip)`
3. เลือกตำแหน่งบันทึก
4. แตก ZIP ลงใน `shyne-mods/avatars/`
5. กลับเข้า Minecraft เปิดคลังอวตารแล้วกด `โหลดใหม่`
6. เลือก Avatar และกด `เลือกใช้`

ZIP จะรวมไฟล์ที่จำเป็นให้อัตโนมัติ:

```text
my-first-avatar/
├─ avatar.json
├─ model.bbmodel
├─ textures/
├─ avatar.png       # ถ้ากำหนด Avatar Icon
└─ outfit/          # ถ้ามี Wardrobe Outfit
```

หากต้องการเฉพาะ Manifest ใช้ `File → Export → Export Shyne avatar.json` แต่สำหรับมือใหม่แนะนำให้ Export Package เพื่อไม่ตกหล่น Texture

## 10. ทดสอบในเกม

1. กด `H` หรือเปิด `Esc → อวตาร`
2. เลือก Avatar ที่ Export
3. กด `เลือกใช้`
4. กด `F5` เพื่อดูมุมมองบุคคลที่สาม
5. หันหัว เดิน วิ่ง และลอง Animation
6. หลังแก้ใน Blockbench ให้ Save/Export ซ้ำ แล้วกด `โหลดใหม่` ในคลังอวตาร

Plugin ยังไม่ส่ง Live Reload เข้าเกมโดยตรง และยังไม่สร้างภาพ Preview เป็น `avatar.png` อัตโนมัติ ต้องเลือก Texture ที่เตรียมไว้เป็น Avatar Icon

## แก้ปัญหาที่พบบ่อย

| อาการ | วิธีแก้ |
| --- | --- |
| ไม่พบเมนู Validate หรือ Export Shyne | ตรวจว่า Plugin ถูกโหลดและเปิดไฟล์ `.bbmodel`; Reload Plugin หรือเปิด Blockbench ใหม่ |
| ของตกแต่งไม่ตามหัว/แขน | ตั้ง Shyne Vanilla Attachment ที่ Group รากให้ถูก |
| ของหมุนรอบจุดแปลก | ย้าย Pivot ของ Group ไปยังจุดข้อต่อหรือฐานของชิ้นส่วนนั้น |
| Texture เป็นม่วงดำหรือหาย | ตรวจ Texture Path, การ Assign หน้า Cube และให้ Texture ถูกบันทึกก่อน Export |
| Animation ไม่เล่น | ตั้ง Shyne Animation State หรือเปิด Ambient Autoplay แล้ว Validate |
| Physics สะบัดแรง | ตรวจ Pivot และลดจำนวน/ระยะของ Group ในสายก่อนใช้ Rig API ขั้นสูง |
| Avatar ไม่ปรากฏในเกม | แตก ZIP ก่อนและตรวจว่า `avatar.json` อยู่ที่ `avatars/<avatar-id>/avatar.json` |
| แก้โมเดลแล้วเกมไม่เปลี่ยน | Export ทับแพ็กเดิม จากนั้นกด `โหลดใหม่` ในคลังอวตาร |

## เช็กลิสต์ก่อนแจก Avatar

- Project ใช้ Profile ถูกประเภท
- Group รากตั้ง Attachment ถูกส่วน
- Pivot อยู่ตรงจุดหมุนจริง
- ไม่มีชื่อ Group สำคัญซ้ำกัน
- Texture ทุกภาพเป็นไฟล์ที่คุณมีสิทธิ์แจก
- Validate ไม่มี Error
- ทดสอบ Idle, Walk, Sprint และมุมมอง F5 แล้ว
- แตก ZIP ทดสอบในโฟลเดอร์ใหม่หนึ่งครั้งก่อนเผยแพร่

## ไปต่อ

- [สร้าง Avatar แรกแบบละเอียด](../../CREATOR_QUICKSTART_TH.md)
- [Shyne Avatar Standard 2.0](../../SHYNE_STANDARD_2_TH.md)
- [Blockbench Animation Standard](../../BLOCKBENCH_ANIMATION_STANDARD.md)
- [คู่มือ Blockbench อย่างเป็นทางการ](https://www.blockbench.net/wiki/guides/blockbench-overview-tips/)
- [พื้นฐาน Modeling, Texture และ Animation](https://www.blockbench.net/wiki/guides/bedrock-modeling/)
