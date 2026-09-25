# Shyne Creator Kit 2.9.1-alpha-26.2

ชุดนี้ใช้สำหรับ **ผู้สร้าง Avatar** บน Shyne Avatar Standard 2.0 ไม่ใช่ตัวม็อดสำหรับใส่ในโฟลเดอร์ `mods`

## สิ่งที่ต้องเตรียม

- Blockbench สำหรับสร้างและแก้ `model.bbmodel`
- Shyne Creator สำหรับ Fabric หรือ NeoForge ติดตั้งใน Minecraft 26.2 เพื่อทดสอบ Avatar
- Python 3 เฉพาะเมื่อต้องการใช้ Creator CLI
- PowerShell เฉพาะเมื่อต้องการใช้เครื่องมือย้าย geometry/texture ที่ตนเองมีสิทธิ์

ตัวม็อด Fabric และ NeoForge แจกแยกจาก Kit นี้ ห้ามนำ JAR ทั้งสอง Loader ไปติดตั้งพร้อมกัน

## เริ่มต้นแบบไม่เขียน Lua

1. ติดตั้ง [Shyne Standard 2.0 Blockbench Plugin](tools/blockbench/README_TH.md)
2. คัดลอก `examples/zero-lua-avatar` เป็นโฟลเดอร์งานใหม่
3. เปิด `model.bbmodel` ด้วย Blockbench
4. สร้าง geometry ใต้กลุ่ม `HeadAccessory` หรือตั้ง `parent_type` ให้ตรงส่วนร่างกาย
5. ใช้เมนูตรวจ Standard 2.0 แล้วเลือก `Export Shyne Avatar Package (.zip)`
6. แตก ZIP ลง `shyne-mods/avatars` หรือใช้ Creator CLI ตรวจโฟลเดอร์ก่อนนำเข้าเกม:

```powershell
.\tools\creator\shyne-creator.ps1 validate .\examples\zero-lua-avatar
```

อ่านรูปแบบ manifest และ animation ที่ [SHYNE_STANDARD_2_TH.md](SHYNE_STANDARD_2_TH.md) และ [CREATOR_QUICKSTART_TH.md](CREATOR_QUICKSTART_TH.md)

## สร้างโปรเจกต์ใหม่ด้วย Creator CLI

```powershell
.\tools\creator\shyne-creator.ps1 new .\MyAvatar
```

คำสั่งนี้ใช้ชื่อโฟลเดอร์เป็นชื่อ Avatar และสร้าง `avatar.json`, `model.bbmodel` และ README แบบ Zero-Lua ให้อัตโนมัติ ชื่อโฟลเดอร์ภาษาไทยใช้ได้ หากต้องการชื่อในเกมต่างออกไป ใช้ `--name "My Avatar"`; หากต้องการ ID คงที่ ใช้ `--id my.avatar`

เพิ่ม `--lua` เฉพาะเมื่ออยากเขียนพฤติกรรมพิเศษ เครื่องมือจะสร้าง `script.lua` เริ่มต้นและเชื่อมกับ `avatar.json` ให้เอง

## เครื่องมือย้าย Avatar

`tools/convert_figura_avatar.ps1` ใช้ย้ายเฉพาะ geometry, hierarchy, texture และ animation ที่ผู้ใช้มีสิทธิ์แก้ไขหรือแจกจ่าย เครื่องมือนี้ไม่คัดลอกหรือรัน Figura script/runtime และปลายทางต้องเป็นโฟลเดอร์ใหม่หรือโฟลเดอร์ว่าง

ตัวอย่าง:

```powershell
.\tools\convert_figura_avatar.ps1 `
  -Source "D:\MyOwnedAvatar" `
  -Destination "D:\MyShyneAvatar" `
  -Profile Accessory
```

## นำ Avatar เข้าเกม

คัดลอกโฟลเดอร์ Avatar ไปไว้ที่:

```text
<Minecraft instance>/shyne-mods/avatars/<avatar-id>/
```

โครงสร้างขั้นต่ำ:

```text
<avatar-id>/
├─ avatar.json
└─ model.bbmodel
```

`avatar.png`, `textures/` และ `outfit/` เพิ่มได้ตามที่โมเดลใช้ ส่วน `script.lua` เป็น Shyne-native Lua สำหรับงานอิสระและงานขั้นสูง ตัว Blockbench exporter รวมไฟล์เหล่านี้ใน ZIP ได้

## เผยแพร่ผ่าน Avatar Cloud

Shyne Creator 2.9.1 ส่ง Public Avatar เป็น ZIP มาตรฐานที่ Backend ตรวจโครงสร้างและ SHA-256 ไม่ใช้ `.sc v1`, `.sc v2` หรือ lease ผู้สร้างควรใส่เฉพาะไฟล์ที่ตนมีสิทธิ์แจก เพราะผู้รับจะดาวน์โหลด ZIP ได้จริง

1. ตรวจโปรเจกต์ด้วย Plugin หรือ Creator CLI
2. Export เป็น Shyne Avatar Package (`.zip`)
3. ทดสอบ Avatar ในเกม แล้วใช้หน้า Avatar Cloud เพื่อ Publish รายการที่ตรงกับ Avatar ที่กำลังใช้
4. ตรวจ license และ permission manifest ก่อนยืนยัน

การ Revoke จะหยุดการดาวน์โหลดครั้งใหม่และไม่ลบ Private Backup แต่ไม่สามารถลบสำเนาที่ผู้เล่นดาวน์โหลดหรือติดตั้งไปแล้วจากระยะไกล

## ความสามารถและข้อจำกัดของ Plugin รุ่นนี้

- Physics Preset บน Group สร้าง native secondary-motion chain อัตโนมัติแล้ว รองรับ Bunny Ears, Tail, Hair, Cloth และ Wings
- collision/IK หรือการปรับ spring ราย bone แบบละเอียดใช้ Shyne Native Rig API เพิ่มเติม
- ยังไม่สร้างภาพ preview เป็น `avatar.png` อัตโนมัติ ต้องเลือก texture ที่เตรียมไว้เป็น Avatar Icon
- Lua module ใน ZIP ใช้ชื่อไฟล์ระดับบนสุดที่ไม่ซ้ำกัน
- ไม่รองรับ Figura script หรือ compatibility layer รุ่นเก่า
