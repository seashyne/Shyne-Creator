# Shyne Avatar System

เอกสารนี้ตรงกับ Shyne Creator `2.8.0-alpha-26.2`

ระบบนี้เป็นสถาปัตยกรรมของ Shyne Creator เอง ใช้ Blockbench model เป็นข้อมูลภาพและใช้ Lua sandbox ของ Shyne เป็นพฤติกรรม ไม่มีการนำ source, API หรือ asset ของม็อดอวตารอื่นมารวมไว้ ขณะเล่นเกม Shyne ไม่โหลดหรือต้องติดตั้ง Figura; ไฟล์ Figura ต้นทางมีบทบาทเป็นข้อมูลอ้างอิงให้ตัวแปลงเท่านั้น

Shyne Creator ไม่แถมและไม่แตกไฟล์ Avatar อัตโนมัติ ผู้สร้างต้องแจก Avatar เป็นแพ็กแยกและผู้เล่นเป็นผู้ติดตั้งเอง

## ตำแหน่งอวตาร

วางอวตารหนึ่งตัวต่อหนึ่งโฟลเดอร์ที่:

```text
.minecraft/shyne-mods/avatars/my_avatar/
├─ avatar.json
├─ model.bbmodel
├─ script.lua
├─ synced.schema.json
├─ outfit/
│  ├─ school_uniform.png
│  └─ casual.png
└─ textures/
   └─ skin.png
```

ระบบค้นหาเฉพาะ `.minecraft/shyne-mods/avatars/<avatar-id>/` เพื่อให้ติดตั้ง ย้าย สำรอง และตรวจไฟล์ได้ตรงกันทุกเครื่อง แต่ละ Avatar เป็นโฟลเดอร์ธรรมดา ไม่ใช่ไฟล์แพ็กนามสกุลพิเศษ

หน้า Avatar Manager มีปุ่ม `ตรวจไฟล์` สำหรับตรวจ manifest, Lua syntax, model limits, texture path/PNG/dimensions, ชื่อ bone/animation ซ้ำ, ขนาด multiplayer และเงื่อนไข Cloud โดยรายงานข้อผิดพลาดแยกตามไฟล์ Cloud จะเรียก Validator เดียวกันก่อน Publish และหลัง Download ก่อนติดตั้งจริง

การควบคุมโมเดลเรียงลำดับเป็น `Lua override (ถ้ามี) > animation ที่ behavior เลือก > Auto Humanoid` ค่าที่ Lua สั่งด้วย `part:pos`, `part:rot` หรือ `part:scale` จะเป็นค่าหลักของ channel นั้น แม้กำหนดเป็นศูนย์ ส่วน animation ที่กำลังเล่นจะควบคุมเฉพาะ channel ที่มี keyframe และ Auto Humanoid จะทำงานเฉพาะส่วนที่ทั้ง Lua และ animation ไม่ได้ควบคุม กลุ่มกระดูกชื่อ `Head`, `Body`/`Torso`, `LeftArm`, `RightArm`, `LeftLeg` และ `RightLeg` จึงยังเดิน วิ่ง มอง หมอบ และแกว่งแขนขาได้อัตโนมัติเมื่อผู้สร้างไม่ได้เขียนท่าของส่วนนั้นเอง ใช้ `part:reset()` เมื่อต้องการคืนส่วนนั้นให้ animation หรือ Auto Humanoid ควบคุมต่อ

## Avatar แบบ Overlay และ Auto Parent

Standard 2.0 ใช้ profile เป็นค่าหลัก:

- `profile: "full_body"` สำหรับโมเดลที่แทนทั้งตัวผู้เล่น
- `profile: "accessory"` สำหรับของเสริมที่แสดงทับบน player model เดิม เช่น หูกระต่าย หาง ปีก หมวก หรือของติดแขน และเป็นค่าเริ่มต้นเมื่อไม่ระบุ
- `profile: "merling"` สำหรับส่วนเสริม aquatic แบบ overlay

`replace_vanilla` ยังใช้เป็น override เฉพาะโมเดลที่ต้องผสมพฤติกรรมต่างจาก profile ได้

สำหรับ Overlay ให้ตั้ง `parent_type` บน bone ใน Blockbench model ก่อน export Shyne 2.8.0 จะเก็บ metadata นี้ผ่าน parser, sync ให้ผู้เล่นอื่น และผูก pose ของ bone เข้ากับ Minecraft rig อัตโนมัติ:

| `parent_type` | การใช้งาน |
|---|---|
| `Head` | หู หมวก แว่น |
| `Body` | หาง ปีก กระเป๋า |
| `LeftArm`, `RightArm` | ปลอกแขน อาวุธติดแขน |
| `LeftLeg`, `RightLeg` | รองเท้า/เกราะขา |

หากต้องสลับ attachment ระหว่างเล่น ใช้ Shyne-native Lua โดยระบุ `main`:

```lua
avatar.hide_vanilla(false)
model.part("model.BunnyEars"):vanilla_parent("HEAD")
-- alias: :attach_to_vanilla("HEAD")
```

เมื่อ bone มี `parent_type: "Head"` ถูกต้อง ไม่มีบรรทัด Lua ข้างต้นก็ได้ Shyne จะใช้ pose ของหัวเป็น coordinate-space parent ก่อนวาง local transform ของ bone ดังนั้น animation เช่นหูกระติกหรือหูพับจะซ้อนกับการก้ม เงย และหันหัวของผู้เล่น ไม่ได้แทนที่กัน ลูกหลานของ bone จะรับ transform นี้ต่อโดยอัตโนมัติ

สำหรับแขนมุมมองบุคคลที่หนึ่ง ให้สร้าง root แยกชื่อ `LeftArmFP` และ `RightArmFP` (หรือกำหนด `shyne_role`/`shyne_tags` เป็น `first_person_left_arm` และ `first_person_right_arm`) พร้อม cube จริงใต้ root นั้น Shyne จะเลือกแขนตามตำแหน่ง pivot ของ Blockbench เพื่อไม่สลับ texture/geometry ซ้ายขวา, วาด tree นี้เฉพาะตอน first-person และไม่วาดซ้ำใน world view. หากไม่มี tree ที่วาดได้หรือ script ซ่อนไว้ ระบบจะคืนแขน vanilla แทน ไม่ทำให้มือหาย

`Portrait`, skull block และ custom held-item geometry ยังไม่ใช่ render context ที่รองรับ: อย่าตั้ง `parent_type: "Portrait"` โดยคาดหวังการจัดกล้องแบบ Figura ในรุ่นนี้ เพราะ Shyne จะไม่เปิด feature ครึ่งเดียวที่ตำแหน่งไม่เที่ยงตรง

งาน rig ซับซ้อนควรใช้ `model.part(path)` เป็นหลักเพื่อควบคุมตาม hierarchy จริงของโมเดล Shyne รองรับ metadata ทางเลือก `shyne_role` (หนึ่ง role ต่อ bone) และ `shyne_tags` (หลาย tag ต่อ bone) สำหรับ library ที่ต้องการหา bone ข้ามหลายโมเดล ใช้ `model.role("ears")` หรือ `model.tag("accessory")`; metadata ไม่บังคับชื่อ bone และถูก sync พร้อม model

## ความตรงของโมเดล Blockbench

Shyne 2.8.0 เก็บ hierarchy และใช้ canonical path เต็ม เช่น `model.Character.Head.EarLeft` เป็นตัวระบุหลัก ชื่อสั้นอย่าง `model.EarLeft` ยังใช้ได้เมื่อชื่อนั้นมีเพียงตำแหน่งเดียวในโมเดล แต่จะไม่เลือกให้เองเมื่อชื่อซ้ำ ให้ใช้ full path หรือ UUID ในงานที่ซับซ้อนเพื่อไม่ควบคุมผิดชิ้น

ตัวอ่านและ renderer เคารพค่าเริ่มต้น `visibility` และ `export` ของ group/element จึงไม่แสดง alternate body, portrait, skull, first-person หรือ armor geometry ที่ถูกซ่อนไว้ใน Blockbench โดยไม่ตั้งใจ รองรับทั้ง cube และ mesh element ภายใต้ hierarchy เดียวกัน ค่า `part:visible(...)` จาก Lua ยังคงมีลำดับสูงกว่า visibility เริ่มต้น

Auto Humanoid เลือก bone ตัวแทนเพียงชั้นเดียวเมื่อ hierarchy มีชื่อที่เทียบกันได้ซ้อนกัน เช่น `body` ที่ครอบ `Body` เพื่อไม่บวก vanilla pose สองรอบ การเลือกอัตโนมัติเป็น fallback; rig ที่ต้องการควบคุมละเอียดควรกำหนด `parent_type`, role/tag หรือ full path ให้ชัดเจน

Vanilla visibility ใช้จริงกับผู้เล่นแต่ละ UUID ทั้ง local และ remote: `PLAYER`, ส่วนลำตัวและชั้นผิว, `CAPE`, `ELYTRA`, armor รวม/แยก slot, ของถือซ้าย-ขวา/main-off hand, head item และแขน first-person การซ่อน `PLAYER` เป็น master mask ของ vanilla layers แต่ไม่ปิด render layer ของโมเดล Shyne จึงใช้ full-body replacement ได้โดยไม่ทำให้ avatar หาย

ตัวอย่าง `avatar.json`:

```json
{
  "standard": "2.0",
  "id": "my_avatar",
  "name": "My Avatar",
  "profile": "full_body",
  "model": "model.bbmodel",
  "behavior": {
    "preset": "auto",
    "animations": {
      "idle": "Idle",
      "walk": "Walk",
      "sprint": "Run",
      "swim": "Swim"
    },
    "blend_ticks": 5,
    "blink": "Blink"
  }
}
```

`id` ใช้ตัวพิมพ์เล็ก ตัวเลข `_` `-` หรือ `.` และต้องไม่ซ้ำกับอวตารอื่น

`permissions` จำเป็นเฉพาะเมื่อระบุ `main` และ Lua เรียกความสามารถพิเศษ รองรับ `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render` และ `world_render` หากไม่ประกาศ runtime จะปฏิเสธ API นั้น Avatar ในเครื่องอนุมัติความสามารถที่ประกาศให้อัตโนมัติโดยไม่แสดงหน้าขอสิทธิ์ ส่วน Public Avatar ต้องผ่านหน้าตรวจสิทธิ์ก่อนเปิดแพ็กเกจ โดย `camera`, `microphone`, `command` และ `hud_render` ปิดไว้เป็นค่าเริ่มต้น การอนุมัติผูกกับ `share_id + package_hash` จึงต้องอนุมัติใหม่เมื่อผู้สร้างอัปเดตแพ็กเกจ

`online_sync: true` ส่ง model, texture, outfit และสถานะ animation ผ่านเซิร์ฟเวอร์ Shyne ไปยังผู้เล่นที่ติดตั้ง Shyne Creator ด้วยกัน Permission ข้างต้นควบคุมเฉพาะ API ที่ทำงานบนเครื่องของผู้ใช้ ไม่ได้ปิดการมองเห็นโมเดลออนไลน์

## การสลับตัวละคร

Avatar Library ทำงานแบบ transactional:

1. ตรวจ JSON, path, ขนาดไฟล์ และ ID
2. parse โมเดลและตรวจจำนวน bone, cube, animation และ texture
3. สร้าง Lua sandbox ใหม่และโหลด script ให้สำเร็จ
4. จึงถอดตัวเดิมและติดตั้งตัวใหม่

ถ้าขั้นใดล้มเหลว ตัวเดิมจะไม่ถูกถอด รายการจะแสดง `ERROR` พร้อมสาเหตุ และผู้เล่นสามารถแก้ไฟล์แล้วกด Reload ได้ ตัวที่เลือกสำเร็จจะถูกจำใน `config/shyne-creator-client.json`

ปุ่ม `Vanilla` ถอดอวตารปัจจุบัน ล้าง animation/attachment/part state และแจ้งผู้เล่นอื่นให้กลับไปเห็นโมเดล Minecraft ปกติ

วาง `avatar.png` ไว้ข้าง `avatar.json` เพื่อใช้เป็นไอคอนในรายการ ไฟล์ภาพขนาดใหญ่ที่ผ่านข้อจำกัดความปลอดภัยจะถูก decode แล้วลดขนาดให้ไม่เกิน `128×128` ก่อนอัปโหลดขึ้น GPU โดยรักษาอัตราส่วน จึงไม่จำเป็นต้องใช้ภาพความละเอียดสูงเพื่อให้ไอคอนคมชัด

## ตู้เสื้อผ้า (Outfit)

สร้างโฟลเดอร์ `outfit` ไว้ข้าง `avatar.json` แล้วนำไฟล์ชุด `.png` ใส่ลงไปได้ทันที ชื่อไฟล์จะกลายเป็นชื่อชุดในเกม รองรับภาพสี่เหลี่ยมทุกขนาดตั้งแต่ `32×32` ถึง `1024×1024` รวมถึง skin layout เก่าอัตราส่วน 2:1 เช่น `64×32`, `128×64`, `256×128`, `512×256` และ `1024×512` สูงสุด 64 ชุดต่ออวตาร

หากชื่อไฟล์เป็นตัวเลขยาวที่ใช้เป็น id ของ texture เช่น `1000012674.png` หน้าตู้เสื้อผ้าจะแสดงชื่ออ่านง่ายเป็น `Outfit 1`, `Outfit 2` ตามลำดับ แต่ยังเก็บชื่อไฟล์/id เดิมไว้สำหรับการเลือกและซิงก์

เปิด `Esc > อวตาร` เลือกอวตารให้เป็นตัวที่ใช้อยู่ แล้วกด `ชุด` เพื่อเปิดหน้าตู้เสื้อผ้า ปุ่ม `ชุดเริ่มต้น` จะกลับไปใช้ texture เดิมจาก `model.bbmodel` หากไม่มีไฟล์ในโฟลเดอร์ ระบบจะแสดงชุดเริ่มต้นเพียงรายการเดียว

ชุดเสริมจะถูกซ้อนทับ texture ลำดับแรกของโมเดลตามค่า alpha พิกเซลโปร่งใสจึงคงหัว ผิว และรายละเอียดเดิมไว้ ส่วน texture ลำดับอื่น เช่น ปาก ตา หรือเอฟเฟกต์ ไม่ถูกเปลี่ยน ภาพที่มีความละเอียดต่างจาก texture หลักจะถูกปรับด้วย nearest-neighbor เพื่อรักษาพิกเซลอาร์ต สำหรับ skin แบบเก่า 2:1 ระบบจะสร้างแขนและขาซ้ายใน layout ใหม่ให้อัตโนมัติก่อนปรับเข้ากับ UV ของโมเดล หาก PNG ที่รวมแล้วใหญ่เกินเพดาน Multiplayer ระบบจะลดความละเอียดภาพที่ส่งโดยยังคง UV เดิม ตัวเลือกถูกจำแยกตามอวตาร และเมื่อเปิด `online_sync` ผู้เล่นอื่นที่ใช้ Shyne Creator รุ่นเดียวกันจะเห็นชุดที่เลือกด้วย

```text
.minecraft/shyne-mods/avatars/my_avatar/outfit/
├─ school_uniform.png   # 64×64
├─ classic.png          # 64×32 แบบเก่า
└─ formal_hd.png        # 1024×1024
```

## Client API สำหรับม็อดเสริม

คลาสหลักคือ `seashyne.shynecore.client.avatar.AvatarRuntime`:

```java
AvatarActivationResult result = AvatarRuntime.switchAvatar("my_avatar", Minecraft.getInstance());
if (!result.success()) {
    LOGGER.warn("Avatar switch failed: {}", result.message());
}

AvatarRuntime.reloadActive(Minecraft.getInstance());
AvatarRuntime.deactivate(Minecraft.getInstance());
AvatarState active = AvatarRuntime.active();
```

API นี้เป็น client-side เท่านั้น ม็อดเสริมสามารถสร้างหน้าจอเลือกตัวละครของตัวเองได้โดยอ่าน `AvatarRuntime.catalog()` และเรียก `switchAvatar(...)` โดยไม่จำเป็นต้องใช้หน้าจอ Avatar Library ของ Shyne

## Shyne Lua API Standard

Avatar ใหม่ใช้ API ของ Shyne โดยตรง: `minecraft`, `model`, `avatar`, `state`, `events`, `ui` และ `vector` ตัวอย่างเต็มและขอบเขตความปลอดภัยอยู่ใน `SHYNE_LUA_API_TH.md`

```lua
local head = model.root.Head

events.on("tick", function()
  if minecraft.player.crouching() then head:move(0, -1, 0) else head:reset() end
end)

ui.action({
  id = "wave",
  title = "โบกมือ",
  icon = "spark",
  on_use = function() model.animation.play("wave") end
})
```

Standard 2.0 ไม่บังคับ Lua; ตัวอย่างส่วนนี้ใช้เฉพาะ Avatar ขั้นสูงที่ระบุ `main` เท่านั้น หากใช้ Lua ให้ล็อก `api: "2.0"` และประกาศ `requires` ตาม module ที่เรียก ระบบจะตรวจสัญญาก่อนเริ่มสคริปต์

## สคริปต์รุ่นเดิม

Standard 2.0 ไม่มี `compatibility: "legacy"` และไม่ตรวจหรือเปิด global แบบ Figura อัตโนมัติ ให้นำเข้า geometry, texture และ animation แล้วกำหนด `profile`/`behavior` ใหม่ สคริปต์ขั้นสูงต้องใช้ Shyne-native Lua เท่านั้น

## Palette และไอคอนพลัง

Action ของ Avatar จะแสดงเป็นการ์ดพลังที่ปรับจำนวนคอลัมน์และแถวตามขนาดหน้าจอ สามารถกำหนดไอคอนด้วย `icon(...)`:

```lua
local powers = ui.page("powers")
powers:action({
  id = "aether_dash",
  title = "Aether Dash",
  description = "พุ่งไปข้างหน้าอย่างรวดเร็ว",
  icon = "bolt",
  on_use = function() emote.play("dash") end
})
```

ชื่อไอคอนที่รองรับใน UI ได้แก่ `spark`, `bolt`, `shield`, `heart`, `music`, `fire`, `wave` และ `star` หากไม่กำหนด ระบบจะเลือกจาก `id` ของ Action หรือใช้ `spark` เป็นค่าเริ่มต้น

## แปลง Avatar จาก Figura

ใช้ตัวแปลงกับ Avatar ที่คุณมีสิทธิ์แก้ไขและแจกจ่าย:

```powershell
.\tools\convert_figura_avatar.ps1 `
  -Source "E:\path\to\figura-avatar" `
  -Destination ".\src\main\resources\shyne_runtime\avatars\my_avatar" `
  -Id "my_avatar" `
  -Name "My Avatar"
```

ตัวแปลงจะค้นหา `.bbmodel` ในโฟลเดอร์ต้นทางและโฟลเดอร์ย่อย โดยต้องพบไฟล์ที่เลือกได้แน่นอนเพียงไฟล์เดียว จากนั้นย้าย texture path มาไว้ภายในแพ็ก หาก texture ไม่มีไฟล์ภายในต้นทางแต่มี `data:image/...;base64,...` จะ decode เป็นไฟล์ PNG อย่างปลอดภัย แล้วสร้าง manifest Standard 2.0 จาก animation ที่มีจริงโดยไม่สร้าง `script.lua` หรือ event loop ทั่วไป

`-VanillaMode Auto` เป็นค่าเริ่มต้น ตัวแปลงจะเลือก `Replace` เมื่อ manifest ระบุ `replace_vanilla: true` หรือพบว่าสคริปต์ต้นทางซ่อน player model ทั้งตัว มิฉะนั้นจะเลือก `Overlay` สามารถบังคับด้วย `-VanillaMode Overlay` สำหรับหู/หาง/ปีก หรือ `-VanillaMode Replace` สำหรับ Avatar เต็มตัวได้ ตัวแปลงเก็บ `parent_type` และ hierarchy ใน `.bbmodel` ไว้ จึงใช้ Auto Parent ได้ทันทีเมื่อโมเดลต้นทางตั้งค่าไว้

Lua/module ต้นทางจะไม่ถูกรันและไม่ถูกนำมาเป็น dependency ของแพ็กใหม่ runtime ใช้ parser, renderer และ declarative controller ของ Shyne เอง การแปลงช่วยย้ายข้อมูลภาพและ animation แต่ logic เฉพาะแพ็กต้องกำหนดใหม่ด้วย Standard 2.0 หรือ Shyne-native Lua

## Microphone event

เมื่อติดตั้ง Simple Voice Chat 2.6.20 ขึ้นไป Shyne จะคำนวณเฉพาะระดับ RMS จาก PCM ในหน่วย `0.0` ถึง `1.0` แล้วทิ้ง PCM ทันที Lua จะไม่ได้รับหรือเก็บเสียงดิบ:

```lua
events.on("microphone", function(mic)
  model.root.Head:scale(1, mic.speaking and (1 + mic.level * 0.03) or 1, 1)
end)

events.on("tick", function()
  if not microphone.available() then
    -- Simple Voice Chat ไม่ได้เชื่อมต่อ
  end
end)
```

อ่านสถานะล่าสุดได้ด้วย `microphone.available()`, `microphone.level()`, `microphone.speaking()` และ `microphone.muted()` ระบบจะส่ง callback เข้า Lua บน Minecraft client thread ไม่ใช่ audio capture thread

## ขอบเขตความปลอดภัย

- Lua ไม่มี filesystem, OS, Java reflection หรือ process API
- path ใน manifest ต้องอยู่ภายในโฟลเดอร์อวตาร รวมถึง symlink
- manifest, Lua และ model มีขนาดสูงสุด
- เพดานโมเดลกำหนดได้ใน `shyne-creator-client.json`: `avatarMaxBones`, `avatarMaxCubes`, `avatarMaxAnimations`, `avatarMaxTextures` และ `avatarMaxTextureSize`
- ค่าเริ่มต้นคือ 4096 bones, 16384 cubes, 512 animations, 256 textures และ texture canvas สูงสุด 8192×8192; ยังคงต้องมีเพดานเพื่อป้องกันแพ็กใช้ RAM/GPU/เครือข่ายจนเกมล่ม
- ตัวอ่าน animation รองรับ `vector`, shared animator `keyframes`, pre/post `data_points`, expression และ interpolation ของ Blockbench 5.1
- Lua `require("folder.module")` โหลดไฟล์ `.lua` ภายในโฟลเดอร์อวตารได้ โดยป้องกัน path traversal และ cache module ตามพฤติกรรมมาตรฐาน
- Texture ไม่บังคับชื่อหรือโฟลเดอร์: Shyne จะลอง path ใน `.bbmodel` ก่อน แล้วค้นหาชื่อไฟล์แบบไม่สนตัวพิมพ์เล็กใหญ่ทั่วทั้งแพ็ก หากโมเดลไม่มีรายการ texture จะค้นพบไฟล์ PNG ทั้งหมดอัตโนมัติ กรณีพบชื่อเดียวกันหลายไฟล์จะหยุดพร้อมแจ้งว่าไฟล์กำกวมเพื่อไม่เลือกภาพผิด
- outfit จำกัด 64 ไฟล์ต่ออวตาร ไฟล์ต้นฉบับละไม่เกิน 8 MB และต้องเป็น PNG ขนาดที่รองรับ ส่วนภาพที่ส่ง Multiplayer ไม่เกิน 512 KB
- ข้อมูล Multiplayer ถูกจำกัดขนาดและตรวจซ้ำโดย Server
