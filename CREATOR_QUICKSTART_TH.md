# สร้าง Avatar แรกด้วย Shyne Standard 2.0

คู่มือนี้พาเริ่มจากโปรเจกต์ว่างจนเห็น Avatar ในเกม เหมาะสำหรับคนที่เพิ่งใช้ Blockbench และยังไม่เคยเขียน Lua

> **เป้าหมาย:** สร้างหมวก หู หรือของตกแต่งที่ขยับตามหัวผู้เล่น โดยใช้ `avatar.json` เพียงหนึ่งบรรทัดและไม่ต้องมี `script.lua`

## ก่อนเริ่ม เตรียม 3 อย่าง

1. Minecraft 26.2 ที่ติดตั้ง Shyne Creator แล้ว
2. [Blockbench](https://www.blockbench.net/) สำหรับทำโมเดล
3. [Shyne Standard 2.0 Blockbench Plugin](tools/blockbench/README_TH.md)

โฟลเดอร์ Avatar อยู่ใต้โฟลเดอร์เกม:

```text
shyne-mods/
└─ avatars/
   └─ my-first-avatar/
```

ถ้าใช้ CurseForge ตำแหน่งจะมีหน้าตาคล้าย:

```text
C:\Users\<ชื่อผู้ใช้>\curseforge\minecraft\Instances\<ชื่อโปรไฟล์>\shyne-mods\avatars\
```

## 1. เลือกประเภท Avatar

เริ่มจาก **Accessory** ง่ายที่สุด เพราะเป็นของเสริมที่วาดทับตัว Minecraft เดิม เหมาะกับหมวก หู หาง ปีก และของติดตัว

| ประเภท | ใช้เมื่อ | เหมาะกับมือใหม่ |
|---|---|---|
| `accessory` | เพิ่มของตกแต่งให้ตัวผู้เล่นเดิม | แนะนำ |
| `full_body` | ใช้โมเดลของเราแทนรูปร่างทั้งตัว | ทำหลังจาก Accessory สำเร็จ |
| `merling` | Avatar ใต้น้ำหรือรูปร่างเฉพาะทาง | ระดับต่อยอด |

สำหรับ Accessory ไฟล์ `avatar.json` แบบสั้นที่สุดคือ:

```json
{
  "name": "My First Avatar"
}
```

Shyne จะเติม Standard 2.0, ID, `model.bbmodel`, profile `accessory` และ Auto Animation ให้โดยอัตโนมัติ

## 2. ติดตั้ง Blockbench Plugin

1. เปิด Blockbench
2. ไปที่ `File → Plugins… → Load Plugin from File`
3. เลือก `tools/blockbench/shyne_standard_2.js`
4. เปิดหรือสร้างไฟล์ `.bbmodel`
5. ตรวจว่าเมนู `Tools → Validate Shyne Standard 2.0` ปรากฏขึ้น

เมื่อติดตั้งครั้งแรกสำเร็จ ไม่ต้องโหลด Plugin ซ้ำทุกครั้ง

> ยังไม่ต้องเปิด **Use Advanced Shyne Lua** งาน Avatar แรกใช้โมเดลและ Animation จาก Blockbench ได้โดยตรง

## 3. สร้างโมเดลและกำหนดจุดยึด

ตัวอย่างนี้สร้างของตกแต่งที่ตามหัว:

1. สร้าง Group ชื่อ `HeadAccessory`
2. เลือก Group แล้วตั้ง `Shyne Vanilla Attachment` เป็น `Head`
3. สร้าง Cube เป็นลูกของ Group เช่น `Hat` หรือ `BunnyEar`
4. ใส่ Texture และจัด UV ให้เรียบร้อย
5. กด Save เป็น `model.bbmodel`

โครงสร้างที่ถูกต้องควรเป็นแบบนี้:

```text
HeadAccessory  [Attachment: Head]
├─ Hat
├─ LeftEar
└─ RightEar
```

เมื่อเลือก `Head`, ทั้ง Group จะก้ม เงย และหมุนตามหัว Minecraft โดยไม่ต้องเขียน Lua

จุดยึดที่ใช้บ่อย:

| จุดยึด | ตัวอย่าง |
|---|---|
| `Head` | หมวก หู แว่น ผม |
| `Body` | เสื้อคลุม กระเป๋า หาง |
| `LeftArm` / `RightArm` | ถุงมือ กำไล ของติดแขน |
| `LeftLeg` / `RightLeg` | รองเท้า ของติดขา |

### เพิ่ม Animation แบบง่าย

ถ้ายังไม่ต้องการ Animation ให้ข้ามหัวข้อนี้ได้

1. สร้าง Animation ใน Blockbench เช่น `Idle`
2. ตั้ง `Shyne Animation State` เป็น `Idle`
3. ถ้าต้องการให้เล่นตลอด ให้เปิด `Shyne Ambient Autoplay`

ชื่อ Animation มาตรฐาน เช่น `Idle`, `Walk`, `Sprint`, `Swim` และ `Blink` จะถูก Auto Animation Controller เลือกตามสถานะผู้เล่น

## 4. Export เป็นแพ็ก Shyne

1. เลือก `Tools → Validate Shyne Standard 2.0`
2. แก้รายการสีแดงให้หมด
3. ไปที่ `File → Export → Export Shyne Avatar Package (.zip)`
4. ตั้งชื่อ Avatar และเลือกตำแหน่งบันทึก
5. แตก ZIP ลงใน `shyne-mods/avatars/`

หลังแตกไฟล์ควรได้โครงสร้าง:

```text
shyne-mods/
└─ avatars/
   └─ my-first-avatar/
      ├─ avatar.json
      ├─ model.bbmodel
      ├─ textures/
      └─ avatar.png       # มีหรือไม่มีก็ได้
```

ระวังอย่าให้โฟลเดอร์ซ้อนกันสองชั้น:

```text
# ผิด
avatars/my-first-avatar/my-first-avatar/avatar.json

# ถูก
avatars/my-first-avatar/avatar.json
```

ถ้าไม่ใช้ Plugin สามารถสร้างโฟลเดอร์ด้วยมือได้ โดยอย่างน้อยต้องมี `avatar.json` และ `model.bbmodel`

## 5. ใส่เกมและทดสอบ

1. เปิด Minecraft ด้วยโปรไฟล์ที่ติดตั้ง Shyne Creator
2. เปิดเมนู **Shyne Creator → Avatar Library**
3. เลือก `my-first-avatar`
4. กด **ตรวจไฟล์** หากต้องการดูคำเตือนแบบละเอียด
5. กดใช้ Avatar แล้วดูโมเดลในมุมมองบุคคลที่สาม

หลังแก้ไฟล์ ให้กลับมาที่ Avatar Library แล้วกด **Reload** ไม่จำเป็นต้องปิดเกมทุกครั้ง

### เช็กลิสต์ว่าสำเร็จแล้ว

- Avatar ปรากฏใน Library
- Texture แสดงถูกด้านและไม่เป็นสีม่วงดำ
- ของตกแต่งอยู่ตำแหน่งถูกต้อง
- เมื่อหันหัว ของตกแต่งหมุนตาม
- เข้าโลก Multiplayer แล้วไม่มีข้อความ `ERROR`

เมื่อทั้งห้าข้อผ่าน Avatar แรกของคุณพร้อมใช้งานแล้ว

## แก้ปัญหาที่พบบ่อย

| อาการ | ตรวจตรงไหน | วิธีแก้ |
|---|---|---|
| Avatar ไม่ขึ้นในรายการ | ตำแหน่ง `avatar.json` | ย้ายไฟล์ให้อยู่ที่ `avatars/<avatar-id>/avatar.json` |
| ขึ้น `ERROR` | ปุ่ม **ตรวจไฟล์** | อ่านชื่อไฟล์และสาเหตุที่รายงาน แล้วแก้รายการแรกก่อน |
| โมเดลอยู่กลางตัว | Vanilla Attachment | ตั้ง Group หลักเป็น `Head`, `Body`, แขน หรือขาให้ถูก |
| Texture หาย | Texture ใน Blockbench | ตรวจว่า Texture ถูกบันทึกหรือรวมอยู่ในแพ็ก ZIP |
| Animation ไม่เล่น | Animation State | ตั้ง State หรือเปิด Ambient Autoplay |
| ของตกแต่งบังตัวผู้เล่น | Profile | ใช้ `accessory`; อย่าเริ่มด้วย `full_body` |
| แก้ไฟล์แล้วไม่เปลี่ยน | Avatar Library | กด **Reload** หลัง Save หรือ Export |

## ตัวเลือกเสริมสำหรับคนใช้คำสั่ง

เครื่องมือนี้ไม่จำเป็นสำหรับ Avatar แรก แต่ช่วยตรวจแพ็กก่อนแจก:

```powershell
.\tools\creator\shyne-creator.ps1 validate E:\Minecraft\avatars\my-first-avatar
.\tools\creator\shyne-creator.ps1 inspect E:\Minecraft\avatars\my-first-avatar
```

## ไปต่อทางไหนดี

- อ่าน [Shyne Avatar Standard 2.0](SHYNE_STANDARD_2_TH.md) เมื่อต้องการ `full_body`, behavior หรือ outfit
- อ่าน [Blockbench Animation Standard](BLOCKBENCH_ANIMATION_STANDARD.md) เมื่อต้องการ Animation ซับซ้อน
- อ่าน [ระบบ Avatar](AVATAR_SYSTEM.md) เมื่อต้องการ first-person arms, palette และ multiplayer
- อ่าน [Lua API 2.0](SHYNE_LUA_API_TH.md) เมื่อ logic จาก Blockbench ไม่พอ
- อ่าน [Custom Render API 1.3](CUSTOM_RENDER_API_TH.md) เมื่อต้องการ HUD หรือสิ่งที่วาดในโลก

เริ่มจาก Model-first ก่อน แล้วเพิ่ม Lua เฉพาะสิ่งที่จำเป็น จะทำให้ Avatar ดูแลง่ายและทำงานลื่นกว่าครับ
