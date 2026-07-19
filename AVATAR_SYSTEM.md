# Shyne Avatar System

ระบบนี้เป็นสถาปัตยกรรมของ Shyne Creator เอง ใช้ Blockbench model เป็นข้อมูลภาพและใช้ Lua sandbox ของ Shyne เป็นพฤติกรรม ไม่มีการนำ source, API หรือ asset ของม็อดอวตารอื่นมารวมไว้

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

การควบคุมโมเดลเรียงลำดับเป็น `script.lua > animation ใน model.bbmodel > Auto Humanoid` ค่าที่ Lua สั่งด้วย `part:pos`, `part:rot` หรือ `part:scale` จะเป็นค่าหลักของ channel นั้น แม้กำหนดเป็นศูนย์ ส่วน animation ที่กำลังเล่นจะควบคุมเฉพาะ channel ที่มี keyframe และ Auto Humanoid จะทำงานเฉพาะส่วนที่ทั้ง Lua และ animation ไม่ได้ควบคุม กลุ่มกระดูกชื่อ `Head`, `Body`/`Torso`, `LeftArm`, `RightArm`, `LeftLeg` และ `RightLeg` จึงยังเดิน วิ่ง มอง หมอบ และแกว่งแขนขาได้อัตโนมัติเมื่อผู้สร้างไม่ได้เขียนท่าของส่วนนั้นเอง ใช้ `part:reset()` เมื่อต้องการคืนส่วนนั้นให้ animation หรือ Auto Humanoid ควบคุมต่อ

ตัวอย่าง `avatar.json`:

```json
{
  "api": "latest",
  "requires": {
    "animation": ">=1.1",
    "permissions": ">=1.1"
  },
  "id": "my_avatar",
  "name": "My Avatar",
  "version": "1.0.0",
  "main": "script.lua",
  "model": "model.bbmodel",
  "replace_vanilla": true,
  "online_sync": true,
  "first_person_masking": true,
  "local_camera": true,
  "texture_sync_mode": "manifest",
  "synced_schema": "synced.schema.json",
  "permissions": ["particle", "sound", "camera", "hud_render", "world_render"]
}
```

`id` ใช้ตัวพิมพ์เล็ก ตัวเลข `_` `-` หรือ `.` และต้องไม่ซ้ำกับอวตารอื่น

`permissions` ประกาศความสามารถพิเศษที่ Lua ต้องใช้ รองรับ `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render` และ `world_render` หากไม่ประกาศ runtime จะปฏิเสธ API นั้น Avatar ในเครื่องอนุมัติความสามารถที่ประกาศให้อัตโนมัติโดยไม่แสดงหน้าขอสิทธิ์ ส่วน Public Avatar ต้องผ่านหน้าตรวจสิทธิ์ก่อนเปิดแพ็กเกจ โดย `camera`, `microphone`, `command` และ `hud_render` ปิดไว้เป็นค่าเริ่มต้น การอนุมัติผูกกับ `share_id + package_hash` จึงต้องอนุมัติใหม่เมื่อผู้สร้างอัปเดตแพ็กเกจ

`online_sync: true` ส่ง model, texture, outfit และสถานะ animation ผ่านเซิร์ฟเวอร์ Shyne ไปยังผู้เล่นที่ติดตั้ง Shyne Creator ด้วยกัน Permission ข้างต้นควบคุมเฉพาะ API ที่ทำงานบนเครื่องของผู้ใช้ ไม่ได้ปิดการมองเห็นโมเดลออนไลน์

## การสลับตัวละคร

Avatar Library ทำงานแบบ transactional:

1. ตรวจ JSON, path, ขนาดไฟล์ และ ID
2. parse โมเดลและตรวจจำนวน bone, cube, animation และ texture
3. สร้าง Lua sandbox ใหม่และโหลด script ให้สำเร็จ
4. จึงถอดตัวเดิมและติดตั้งตัวใหม่

ถ้าขั้นใดล้มเหลว ตัวเดิมจะไม่ถูกถอด รายการจะแสดง `ERROR` พร้อมสาเหตุ และผู้เล่นสามารถแก้ไฟล์แล้วกด Reload ได้ ตัวที่เลือกสำเร็จจะถูกจำใน `config/shyne-creator-client.json`

ปุ่ม `Vanilla` ถอดอวตารปัจจุบัน ล้าง animation/attachment/part state และแจ้งผู้เล่นอื่นให้กลับไปเห็นโมเดล Minecraft ปกติ

## ตู้เสื้อผ้า (Outfit)

สร้างโฟลเดอร์ `outfit` ไว้ข้าง `avatar.json` แล้วนำไฟล์ชุด `.png` ใส่ลงไปได้ทันที ชื่อไฟล์จะกลายเป็นชื่อชุดในเกม รองรับภาพสี่เหลี่ยมทุกขนาดตั้งแต่ `32×32` ถึง `1024×1024` รวมถึง skin layout เก่าอัตราส่วน 2:1 เช่น `64×32`, `128×64`, `256×128`, `512×256` และ `1024×512` สูงสุด 64 ชุดต่ออวตาร

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

ไม่มี alias ของ API รุ่นทดลองก่อนหน้า แพ็กที่ไม่ระบุ `api` จะใช้ Standard ล่าสุดโดยอัตโนมัติ แพ็กที่ต้องการผลลัพธ์คงที่ให้ล็อก `api: "1.1"` และประกาศ `requires` ระบบจะตรวจ API ก่อนเริ่ม Lua ส่วน `api_version: 1` เก็บไว้เฉพาะแพ็กเก่าที่ต้องการสัญญา 1.0

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

ตัวแปลงจะย้าย texture path มาไว้ภายในแพ็ก ลบ embedded texture data และสร้าง manifest พร้อมสคริปต์ Shyne-native ที่มี locomotion, fade, Palette emote, input และ multiplayer เป็นจุดเริ่มต้น โมดูลต้นทางจะถูกเก็บไว้เป็นเอกสารอ้างอิงและไม่ถูกรันอัตโนมัติ

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
