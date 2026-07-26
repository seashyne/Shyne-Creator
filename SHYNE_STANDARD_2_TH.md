# Shyne Avatar Standard 2.0

Shyne Avatar Standard 2.0 เป็นมาตรฐานแบบ **model-first**: งานทั่วไปควรสร้างให้เสร็จจาก Blockbench และ `avatar.json` โดยไม่ต้องมี `script.lua` เป้าหมายคือให้ผู้สร้างเริ่มจากโมเดล, hierarchy, `parent_type` และ animation ที่ตั้งชื่อชัดเจน แล้วให้ Shyne จัดการพฤติกรรมพื้นฐานให้

Standard 2.0 ทำงานบน Shyne runtime โดยตรง ไม่ติดตั้ง ไม่โหลด และไม่เรียก Figura runtime ไฟล์จาก Figura อาจใช้เป็นข้อมูลอ้างอิงตอนย้าย geometry, texture และ animation เท่านั้น มาตรฐานนี้ **ไม่โหลดสคริปต์ Figura หรือ compatibility layer รุ่นเก่า** และรับเฉพาะสัญญา API 2.0

## ระดับการสร้าง Avatar

Standard 2.0 แบ่งวิธีทำงานเป็นสามระดับที่ใช้ไฟล์รูปแบบเดียวกัน:

1. **Beginner — Zero-Lua:** ทำโมเดลและ animation ใน Blockbench แล้วกำหนด `profile` กับ `behavior` ใน `avatar.json`
2. **Intermediate — Declarative:** ผูก animation เข้ากับสถานะผู้เล่น เช่น เดิน วิ่ง ว่ายน้ำ และหลับ พร้อมตั้ง blend และ blink โดยไม่เขียน event loop
3. **Expert — Native Lua:** ใส่ `main` เมื่อต้องการอิสระเต็ม เช่น procedural rig, interaction, physics หรือเงื่อนไขเฉพาะ Avatar ใช้ Easy API เพื่อเริ่มสั้นแล้วผสม API รายละเอียดได้

Lua เป็นความสามารถระดับหนึ่งของ Standard 2.0 แต่ไม่ใช่ไฟล์บังคับ งานง่ายไม่ต้องเขียน ส่วนงานซับซ้อนยังใช้ Shyne-native Lua ได้เต็มที่

ผู้สร้างสามารถติดตั้ง [Shyne Standard 2.0 Blockbench Plugin](tools/blockbench/README_TH.md) เพื่อตั้ง profile, `parent_type`, role/tag และ animation state ผ่านหน้าต่าง Properties แล้ว export ZIP ที่รวม manifest, model, texture, icon, outfit และ Lua โดยไม่ต้องจัดโฟลเดอร์เอง

มาตรฐานตู้เสื้อผ้ากำหนดให้ไฟล์ `.png` ธรรมดาใน `outfit/` เป็น alpha overlay เสมอ หากต้องการแทน texture หลักทั้งภาพ ต้องระบุด้วยชื่อ `name.replace.png`, `name_replace.png` หรือ `name-replace.png` การสลับชุดเปลี่ยนเฉพาะ texture โดยไม่เริ่ม Lua หรือ animation controller ใหม่ รายละเอียดและตัวอย่างอยู่ใน [AVATAR_SYSTEM.md](AVATAR_SYSTEM.md#ตู้เสื้อผ้า-outfit)

## Avatar แบบสั้นที่สุด

ถ้า model ใช้ profile accessory, ไฟล์ชื่อ `model.bbmodel`, ตั้ง `parent_type` ถูก และใช้ชื่อ animation มาตรฐาน เช่น Idle/Walk/Swim/Blink เขียนเองเพียงชื่อก็พอ:

```json
{
  "name": "Bunny Ears"
}
```

Shyne เติม `standard: 2.0`, ID จากชื่อโฟลเดอร์, `model.bbmodel`, profile `accessory`, blend และ Auto Animation ให้เอง ถ้า animation ชื่อเฉพาะอย่าง `EarWiggle` ต้องเล่นตลอด ให้ตั้ง `Shyne Ambient Autoplay` ใน Blockbench plugin หรือเพิ่ม `behavior.autoplay` ไม่ต้องสร้าง `script.lua` สำหรับกรณีนี้

ใน Blockbench ให้ตั้ง `parent_type` ของ bone หลักเป็น `Head`, `Body`, `LeftArm`, `RightArm`, `LeftLeg` หรือ `RightLeg` เพื่อให้ส่วนเสริมตามส่วนร่างกายจริง Animation ของ bone จะซ้อนบน transform นั้นโดยอัตโนมัติ

## โครงสร้าง `avatar.json`

ฟิลด์หลักของ Standard 2.0:

| ฟิลด์ | รูปแบบ | หน้าที่ |
|---|---|---|
| `standard` | string | ไม่ใส่ได้ โดยค่าเริ่มต้นคือ `"2.0"` |
| `name` | string | ชื่อที่แสดงใน Avatar Library |
| `id` | string | ID คงที่ของ Avatar; ถ้าไม่ใส่ Shyne สร้างจากชื่อโฟลเดอร์ |
| `model` | string | ไฟล์ Blockbench; ค่าเริ่มต้นคือ `model.bbmodel` |
| `profile` | string | `accessory`, `full_body` หรือ `custom` |
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

หากไม่มี animation ที่สร้างเอง โมเดลจะใช้ Minecraft pose อัตโนมัติ โดยต้องมี root bone พี่น้องกัน 6 ตัวชื่อ `Head`, `Body` (หรือ `Torso`), `LeftArm`, `RightArm`, `LeftLeg`, `RightLeg` และต้องไม่ตั้ง `parent_type` บน root เหล่านี้ จุดหมุนมาตรฐานคือหัว/ลำตัว `[0,24,0]`, แขน `[±5,22,0]` และขา `[±1.9,12,0]` ค่า `animations: []` จึงเป็นรูปแบบที่ถูกต้องสำหรับโมเดล skin ที่ต้องการใช้ท่าเดิน วิ่ง ก้ม นั่ง ว่ายน้ำ และโจมตีจาก Minecraft โดยตรง Validator จะเตือนเมื่อสัญญานี้ไม่ครบ และ Figura converter จะสร้าง rig ให้เฉพาะโมเดล skin ที่ตรวจองค์ประกอบได้ครบและไม่มี hierarchy/animation ที่ผู้สร้างทำไว้แล้ว

ควรทดสอบมุมมองบุคคลที่หนึ่ง, armor, held item, Elytra, ท่านั่ง และท่านอนก่อนแจก Avatar

### `custom`

ใช้กับ Avatar ที่มีรูปร่างหรือระบบเฉพาะ เช่น aquatic, creature, หางที่สลับรูปแบบ หรือ rig ที่ไม่เข้ากลุ่ม Accessory และ Full Body ค่าเริ่มต้นยังเป็น overlay เพื่อความปลอดภัย และ preset อัตโนมัติยังรู้จักชื่อ animation แนว aquatic หากโมเดลต้องแทนผู้เล่นทั้งตัว ให้ประกาศ `replace_vanilla: true` อย่างชัดเจน

```json
"profile": "custom"
```

Profile ไม่ได้เดาชื่อ bone แบบสุ่ม ควรตั้ง hierarchy, `parent_type`, role และชื่อ animation ใน Blockbench ให้ชัดเจนเสมอ

ขอบเขตปัจจุบันของ `custom` คือ safe overlay สำหรับโมเดลเฉพาะทาง; profile จะไม่เดาว่ากระดูกใดควรเป็น physics หรือ armor เอง แต่ Group ที่ตั้ง `Shyne Physics Preset` จะสร้าง native secondary-motion chain ได้ทุก profile งาน special-form, collision/IK และ armor mapping เฉพาะโมเดลยังต้องกำหนดใน Blockbench หรือเพิ่มด้วย Shyne-native Lua

ชื่อ `merling` เคยใช้กับ profile aquatic รุ่นเก่า Runtime และ Creator CLI ยังอ่านชื่อนี้เป็น alias ของ `custom` เพื่อไม่ให้ Avatar เดิมพัง แต่ manifest, schema, exporter และโปรเจกต์ใหม่ควรใช้ `custom` เท่านั้น

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

PNG ที่ไม่ได้ถูกอ้างจากโมเดลและมีขนาดเท่า texture หลักอาจถูกนำเข้าเป็น alternate texture อัตโนมัติ ตัวแปลงจะเติม `.replace.png` เพื่อรักษาความหมายว่าเป็นภาพทดแทนทั้งชุดตามต้นฉบับ แต่ชื่อที่สื่อว่าเป็น normal, emissive, specular, mask หรือ reference map จะถูกข้าม ไฟล์ที่ระบุ `.overlay.png` ไว้ชัดเจนจะคงเป็น overlay ควรตรวจรายการ `Outfits:` หลังแปลงทุกครั้ง เพราะชื่อไฟล์ที่ไม่สื่อความหมายยังแยกชนิด asset โดยอัตโนมัติไม่ได้

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
