# Shyne Avatar Standard 2.0

Shyne Avatar Standard 2.0 เป็นมาตรฐานแบบ **model-first**: งานทั่วไปควรสร้างให้เสร็จจาก Blockbench และ `avatar.json` โดยไม่ต้องมี `script.lua` เป้าหมายคือให้ผู้สร้างเริ่มจากโมเดล, hierarchy, `parent_type` และ animation ที่ตั้งชื่อชัดเจน แล้วให้ Shyne จัดการพฤติกรรมพื้นฐานให้

Standard 2.0 ทำงานบน Shyne runtime โดยตรง ไม่ติดตั้ง ไม่โหลด และไม่เรียก Figura runtime ไฟล์จาก Figura อาจใช้เป็นข้อมูลอ้างอิงตอนย้าย geometry, texture และ animation เท่านั้น มาตรฐานนี้ **ไม่โหลดสคริปต์ Figura หรือ compatibility layer รุ่นเก่า** และรับเฉพาะสัญญา API 2.0

## ระดับการสร้าง Avatar

Standard 2.0 แบ่งวิธีทำงานเป็นสามระดับที่ใช้ไฟล์รูปแบบเดียวกัน:

1. **Beginner — Zero-Lua:** ทำโมเดลและ animation ใน Blockbench แล้วกำหนด `profile` กับ `behavior` ใน `avatar.json`
2. **Intermediate — Declarative:** ผูก animation เข้ากับสถานะผู้เล่น เช่น เดิน วิ่ง ว่ายน้ำ และหลับ พร้อมตั้ง blend และ blink โดยไม่เขียน event loop
3. **Expert — Native Lua:** ใส่ `main` เฉพาะเมื่อต้องการ logic ที่ `behavior` อธิบายไม่ได้ เช่น procedural rig, interaction หรือเงื่อนไขเฉพาะ Avatar

Lua เป็นส่วนเสริม ไม่ใช่ไฟล์บังคับของ Standard 2.0

ผู้สร้างสามารถติดตั้ง [Shyne Standard 2.0 Blockbench Plugin](tools/blockbench/README_TH.md) เพื่อตั้ง profile, `parent_type`, role/tag และ animation state ผ่านหน้าต่าง Properties แล้ว export `avatar.json` โดยไม่แก้ JSON ด้วยมือ

## Avatar แบบสั้นที่สุด

ตัวอย่างหูกระต่ายหรือหางที่มี animation `EarWiggle` เล่นวนตลอด:

```json
{
  "standard": "2.0",
  "name": "Bunny Ears",
  "profile": "accessory",
  "model": "model.bbmodel",
  "behavior": {
    "autoplay": ["EarWiggle"]
  }
}
```

ไม่ต้องสร้าง `script.lua` และไม่ต้องใส่ `main` ค่า `accessory` เป็นค่าเริ่มต้นที่ปลอดภัย: โมเดล Shyne แสดงแบบ overlay ร่วมกับตัวผู้เล่น Minecraft เดิม

ใน Blockbench ให้ตั้ง `parent_type` ของ bone หลักเป็น `Head`, `Body`, `LeftArm`, `RightArm`, `LeftLeg` หรือ `RightLeg` เพื่อให้ส่วนเสริมตามส่วนร่างกายจริง Animation ของ bone จะซ้อนบน transform นั้นโดยอัตโนมัติ

## โครงสร้าง `avatar.json`

ฟิลด์หลักของ Standard 2.0:

| ฟิลด์ | รูปแบบ | หน้าที่ |
|---|---|---|
| `standard` | string | ใช้ค่า `"2.0"` เพื่อเลือกสัญญา Standard 2.0 |
| `name` | string | ชื่อที่แสดงใน Avatar Library |
| `id` | string | ID คงที่ของ Avatar; ถ้าไม่ใส่ Shyne สร้างจากชื่อโฟลเดอร์ |
| `model` | string | ไฟล์ Blockbench; ค่าเริ่มต้นคือ `model.bbmodel` |
| `profile` | string | `accessory`, `full_body` หรือ `merling` |
| `behavior` | object | controller พื้นฐาน, autoplay, state mapping, blend และ blink |
| `main` | string | ไฟล์ Shyne-native Lua แบบ optional; ไม่ใส่เมื่อไม่ใช้ Lua |

ฟิลด์ texture, permission, network และข้อมูลประกอบอื่นยังเพิ่มได้ตามความต้องการ แต่ Avatar พื้นฐานไม่ควรต้องประกาศค่าที่ Shyneหาได้จาก `.bbmodel` เอง

## Profile

### `accessory`

ใช้กับหู หาง ผม ปีก หมวก ของติดแขน หรือของเสริมอื่น เป็น profile เริ่มต้นและแสดงแบบ overlay โดยไม่ซ่อนตัวผู้เล่น vanilla

```json
"profile": "accessory"
```

หากไม่แน่ใจ ให้เริ่มจาก profile นี้ เพราะไม่ทำให้ตัวผู้เล่นหายทั้งตัวจากการตั้งค่าผิด

### `full_body`

ใช้เมื่อโมเดลถูกออกแบบให้แทนตัวผู้เล่นทั้งตัว การเลือก profile นี้เป็นการตัดสินใจชัดเจนว่า Avatar ต้องควบคุมรูปร่างทั้งตัว รวมส่วนหัว ลำตัว แขน และขา

```json
"profile": "full_body"
```

ควรทดสอบมุมมองบุคคลที่หนึ่ง, armor, held item, Elytra, ท่านั่ง และท่านอนก่อนแจก Avatar

### `merling`

ใช้กับ Avatar แนว aquatic ที่มีหาง ครีบ หรือ locomotion สำหรับว่ายน้ำ ค่าเริ่มต้นยังเป็น overlay เพื่อความปลอดภัย และรองรับ alias ของ animation แนว aquatic ใน preset อัตโนมัติ หากโมเดล Merling ถูกสร้างให้แทนผู้เล่นทั้งตัว ให้ประกาศ `replace_vanilla: true` อย่างชัดเจน

```json
"profile": "merling"
```

Profile ไม่ได้เดาชื่อ bone แบบสุ่ม ควรตั้ง hierarchy, `parent_type`, role และชื่อ animation ใน Blockbench ให้ชัดเจนเสมอ

ขอบเขตปัจจุบันของ `merling` คือ safe overlay และ aquatic animation aliases; profile นี้ยังไม่สร้าง special-form controller, physics chain หรือ armor system ให้เอง งานเหล่านั้นต้องกำหนดในโมเดลหรือเพิ่มด้วย Shyne-native Lua ตามความจำเป็น

## Declarative Behavior

ตัวอย่าง controller ครบสำหรับ Avatar เต็มตัว:

```json
{
  "standard": "2.0",
  "name": "Ocean Traveler",
  "profile": "full_body",
  "model": "model.bbmodel",
  "behavior": {
    "preset": "auto",
    "autoplay": ["Breathing"],
    "animations": {
      "idle": "Idle",
      "walk": "Walk",
      "sprint": "Run",
      "swim": "Swim",
      "crouch": "Crouch",
      "sleep": "Sleep",
      "fly": "Fly",
      "sit": "Sit"
    },
    "blend_ticks": 5,
    "blink": {
      "animation": "Blink",
      "min_ticks": 60,
      "max_ticks": 140
    }
  }
}
```

### `preset`

`"preset": "auto"` ให้ Shyne เลือก animation จากสถานะผู้เล่นจริง แล้วสลับ state ให้โดยไม่ต้องเขียน `events.on("tick", ...)`

### `autoplay`

เป็นรายชื่อ animation ที่เริ่มอัตโนมัติเมื่อ Avatar พร้อม เหมาะกับการหายใจ หูกระดิกเบา ๆ หางแกว่ง หรือ ambient loop ที่ไม่ขึ้นกับสถานะผู้เล่น

```json
"autoplay": ["Breathing", "TailIdle"]
```

### `animations`

ผูก semantic state ของ Shyne กับชื่อ animation ใน `.bbmodel`:

- `idle` — ยืนปกติ
- `walk` — เดิน
- `sprint` — วิ่ง
- `swim` — ว่ายน้ำ
- `crouch` — ย่อหรือแอบ
- `sleep` — นอน
- `fly` — บินหรือใช้ Elytra
- `sit` — นั่งหรือโดยสาร

ใส่เฉพาะ state ที่โมเดลมีจริง ไม่ต้องสร้าง animation เปล่าเพื่อให้ครบทุกช่อง ชื่อด้านขวาต้องตรงกับชื่อใน Blockbench

### `blend_ticks`

จำนวน tick ที่ใช้ผสมตอนเปลี่ยน state ค่ามากทำให้ท่าเปลี่ยนนุ่มขึ้น แต่ตอบสนองช้าลง จุดเริ่มต้นที่เหมาะกับ Avatar ทั่วไปคือ `4`–`6` ticks

### `blink`

เล่น animation กระพริบตาเป็นช่วงเวลาสุ่มระหว่าง `min_ticks` และ `max_ticks`:

```json
"blink": {
  "animation": "Blink",
  "min_ticks": 60,
  "max_ticks": 140
}
```

Minecraft ทำงานที่ 20 ticks ต่อวินาที จึงเท่ากับกระพริบทุกประมาณ 3–7 วินาทีในตัวอย่างนี้ `min_ticks` ต้องไม่มากกว่า `max_ticks`

## เมื่อใดจึงควรใช้ Lua

เพิ่ม `main` เมื่อ Avatar ต้องมี logic ที่ controller มาตรฐานทำไม่ได้จริง:

```json
{
  "standard": "2.0",
  "name": "Reactive Ears",
  "profile": "accessory",
  "model": "model.bbmodel",
  "behavior": {
    "autoplay": ["EarIdle"]
  },
  "main": "script.lua"
}
```

`script.lua` ต้องใช้ Shyne-native Lua/API เท่านั้น ไม่ใช้ global, module หรือ syntax ของ Figura หาก Lua ควบคุม animation เดียวกับ `behavior` ผู้สร้างต้องกำหนดหน้าที่ให้ชัดเจนเพื่อไม่ให้ controller สองชุดแย่งกันแก้ pose

แนวทางที่แนะนำ:

- ให้ `behavior` ดูแล locomotion, autoplay และ blink
- ให้ Lua ดูแล interaction, procedural motion หรือเงื่อนไขเฉพาะ
- ใช้ full path, UUID, role หรือ tag เมื่อต้องควบคุม rig ซับซ้อน
- หลีกเลี่ยง tick loop ที่มีไว้เพียงเลือก Idle/Walk/Swim เพราะ `preset: "auto"` ทำหน้าที่นี้แล้ว

## วิธีเตรียมโมเดลใน Blockbench

1. วาง hierarchy ให้สะท้อนโครงสร้างจริง ไม่ตั้งชื่อ bone ซ้ำโดยไม่จำเป็น
2. ตั้ง `parent_type` ให้ root ของหู หาง ปีก หรือส่วนร่างกายที่ต้องตาม vanilla pose
3. ตั้งชื่อ animation สั้นและสื่อความหมาย เช่น `Idle`, `Walk`, `Swim`, `Blink`
4. ตั้ง loop mode ใน Blockbench ให้ตรงกับชนิด animation
5. ใช้ visibility เริ่มต้นเพื่อซ่อน geometry เฉพาะ context หรือ variant
6. Export `model.bbmodel` แล้วสร้าง `avatar.json`; เริ่มจาก Zero-Lua ก่อน
7. เพิ่ม Lua เฉพาะเมื่อ behavior แบบ declarative ยังไม่พอ

## กติกาการย้ายจาก Figura

Standard 2.0 รับแนวคิด “ย้าย asset ไม่ย้าย runtime”:

- ย้าย geometry, hierarchy, texture และ animation ที่ผู้สร้างมีสิทธิ์ใช้
- แปลงการเล่น Idle/Walk/Swim/Blink เป็น `behavior`
- แปลง attachment เป็น `parent_type`, role หรือ tag
- เขียน logic ที่จำเป็นใหม่ด้วย Shyne-native API
- ไม่คัดลอก Figura library หรือคาดหวังว่า Figura global จะมีอยู่ในเกม

หาก Avatar ต้นทางมีสคริปต์หนึ่งบรรทัดเพื่อเปิด animation loop ผลลัพธ์ Standard 2.0 ควรเป็น `autoplay` และไม่มี `script.lua` ไม่ควรสร้าง template Lua ยาวกว่าต้นฉบับ

ใช้ตัวแปลงแบบ semantic ได้ดังนี้:

```powershell
.\tools\convert_figura_avatar.ps1 -Source "E:\Avatar\FiguraSource" -Destination "E:\Avatar\ShyneOutput"
```

`Destination` ต้องเป็น path ใหม่หรือ directory ว่าง และต้องไม่ใช่ directory เดียวกับ `Source` ตัวแปลงจะหยุดทันทีเมื่อพบไฟล์เดิม เพื่อป้องกัน `script.lua`, texture หรือ outfit จากงานเก่าค้างอยู่ในผลลัพธ์ Zero-Lua

PNG ที่ไม่ได้ถูกอ้างจากโมเดลและมีขนาดเท่า texture หลักอาจถูกนำเข้าเป็น outfit โดยอัตโนมัติ แต่ชื่อที่สื่อว่าเป็น normal, emissive, specular, mask หรือ reference map จะถูกข้าม ควรตรวจรายการ `Outfits:` หลังแปลงทุกครั้ง เพราะชื่อไฟล์ที่ไม่สื่อความหมายยังแยกชนิด asset โดยอัตโนมัติไม่ได้

## Checklist ก่อนแจก Avatar

- `standard` เป็น `"2.0"`
- เลือก `profile` ถูกประเภท; ของเสริมใช้ `accessory`
- `main` มีเฉพาะเมื่อมี Shyne-native Lua จริง
- ชื่อใน `autoplay`, `animations` และ `blink.animation` มีอยู่ใน `.bbmodel`
- `min_ticks` ไม่มากกว่า `max_ticks`
- animation เปลี่ยน state นุ่มพอและไม่แย่ง bone เดียวกันโดยไม่ตั้งใจ
- ทดสอบ world view, first person และ multiplayer ตามขอบเขตของ Avatar
- แพ็กไม่รวม Figura runtime หรือ compatibility library

คู่มือโครงสร้างโมเดลและ attachment เพิ่มเติมอยู่ใน [AVATAR_SYSTEM.md](AVATAR_SYSTEM.md) และมาตรฐาน animation อยู่ใน [BLOCKBENCH_ANIMATION_STANDARD.md](BLOCKBENCH_ANIMATION_STANDARD.md)
