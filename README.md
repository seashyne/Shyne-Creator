# Shyne Creator Multi-loader

โปรเจกต์หลักสำหรับ Build Shyne Creator บน Minecraft 26.2 ทั้ง Fabric และ NeoForge จาก source ชุดเดียว

## Version

- Shyne Creator: `2.8.0-alpha-26.2`
- Minecraft: `26.2`
- Java: `25`
- Fabric Loader: `0.19.3+`
- Fabric API: `0.155.2+26.2`
- NeoForge: `26.2.0.25-beta+`
- Gradle: `9.5.1`

Fabric และ NeoForge ใช้ `mod id`, Shyne API standard และ network protocol version เดียวกัน ส่วนไฟล์ที่ผูกกับ Loader จะถูกแยกออกจากระบบหลัก

## Shyne Avatar Standard 2.0 — Model-first

Standard 2.0 เปลี่ยน workflow ให้เริ่มจาก Blockbench และ `avatar.json`: Avatar ทั่วไปเล่น autoplay, locomotion และ blink ได้โดยไม่ต้องมี `script.lua` ส่วน Lua เป็นทางเลือกสำหรับ procedural rig หรือ logic เฉพาะเท่านั้น

```json
{
  "standard": "2.0",
  "name": "Bunny Ears",
  "profile": "accessory",
  "behavior": {
    "autoplay": ["EarWiggle"]
  }
}
```

`accessory` เป็นค่าเริ่มต้นแบบ overlay ที่ปลอดภัยและไม่ซ่อนตัวผู้เล่น vanilla มาตรฐานนี้ใช้ Shyne runtime โดยตรง ไม่พึ่ง Figura และไม่รับรอง compatibility กับสคริปต์ Figura/สคริปต์ legacy อ่านสัญญาและตัวอย่างทั้งหมดที่ [SHYNE_STANDARD_2_TH.md](SHYNE_STANDARD_2_TH.md)

เครื่องมือตั้ง profile, attachment, role, animation state และ export manifest จาก Blockbench อยู่ที่ [Shyne Blockbench Plugin](tools/blockbench/README_TH.md)

## 2.7.45 Native Fidelity Fix

- runtime ของ Shyne ยังคงเป็นระบบ native ทั้งหมดและไม่ต้องติดตั้งหรือโหลด Figura; ไฟล์/สคริปต์ Figura ต้นทางใช้เป็นข้อมูลอ้างอิงตอนแปลงเท่านั้น
- ตัวอ่าน `.bbmodel` เคารพค่าเริ่มต้น `visibility`/`export` ของ group และ element, รองรับ mesh และใช้ canonical full path เมื่อชื่อ part ซ้ำ โดย short alias ใช้ได้เฉพาะชื่อที่ไม่กำกวม
- แก้ Auto Humanoid ไม่ให้ใส่ pose ซ้ำกับ hierarchy ที่มีชื่อซ้อนกัน เช่น `body` > `Body` และรองรับ legacy keyframe wrapper แบบจำกัดผ่าน expression engine ที่ปลอดภัยโดยไม่รัน `require` ต้นทาง
- ส่ง vanilla visibility mask ตาม UUID ทั้ง local/remote ไปยัง body/skin layer, cape, Elytra, armor แต่ละ slot, held item, head item และ first-person arm โดยไม่ซ่อน Shyne model layer
- แก้ `avatar.png` สีดำจากการส่งพารามิเตอร์วาดภาพผิด พร้อม decode/downscale ไอคอนขนาดใหญ่ก่อนอัปโหลด และแสดงชื่อไฟล์ outfit ตัวเลขยาวเป็น `Outfit N` โดยไม่เปลี่ยน id จริง
- ตัวแปลงใช้ `-VanillaMode Auto` เป็นค่าเริ่มต้น ตรวจ full-body replacement จาก manifest/สคริปต์ต้นทาง ค้นหา `.bbmodel` ในโฟลเดอร์ย่อย และถอด Base64 texture เป็นไฟล์ภายในแพ็กได้
- ปรับ network protocol เป็น `10`; client/server ต้องใช้รุ่นเดียวกันเพราะ snapshot เพิ่ม mesh และ visibility metadata

การแก้ชุดนี้เพิ่มความตรงกับโมเดลต้นทาง แต่ไม่รับรองว่า Avatar Figura ทุกแพ็กจะทำงานเหมือนเดิม 100%; API หรือ render context เฉพาะทางที่ยังไม่รองรับต้องย้ายเป็น Shyne-native เพิ่มเติม

## 2.7.44 Native Hover Physics and First Person

- อ่าน `parent_type` จาก Blockbench/Figura `.bbmodel` และผูก custom bone กับหัว ลำตัว แขน หรือขา Minecraft โดยอัตโนมัติ
- `parent_type` เป็น parent transform จริง: pose หัว/แขนขาจะซ้อนก่อน local transform และ animation ของ bone จึงไม่ทำให้หูหรือของเสริมหลุดจากส่วนผู้เล่น
- เพิ่ม Shyne Rig API 1.3: `rot_add()`, `rig.spring()`, `rig.chain()`, `rig.attach()` และ `rig.armor()` สำหรับ Merling/SquAPI-style avatar, armor cosmetic และ secondary motion
- เพิ่ม swept world probe สำหรับ collision block/entity, wind/impulse, cone constraint, two-bone IK, animation graph และ Elytra cosmetic
- native `require("SquAPI")` และ `require("lib.SquAPI")` ใช้ BERP/bounce-object physics ที่เข้ากับ SquAPI สำหรับ tail, หูซ้าย/ขวาอิสระ, smooth head, limb, blink, taur, `hoverPoint`/`floatPoint` และ utility โดยไม่รัน Lua ของ Figura
- `hoverPoint` ชน block/entity ผ่าน swept world probe, สะท้อนความเร็วแบบ visual-only และคำนวณกลับเข้า coordinate ของโมเดลทุก tick; ใช้ root group ระดับบนสุดสำหรับของลอย/companion เพื่อไม่รับ transform จากแขนหรือขา
- รองรับ Blockbench hierarchy `LeftArmFP` / `RightArmFP` (หรือ role/tag เทียบเท่า) สำหรับแขน first-person เฉพาะทาง; tree นี้ไม่ถูกวาดซ้ำใน world view และคืน vanilla hand เมื่อ tree ว่างหรือซ่อน
- GUI portrait, skull block และ custom held-item geometry ยังไม่มี hook ที่เที่ยงตรง จึงยังไม่ประกาศว่ารองรับ
- รองรับ Avatar แบบ Overlay สำหรับหู หาง ปีก และของเสริมด้วย `replace_vanilla: false`
- เพิ่ม target/raycast detail สำหรับ block และ entity ใน Shyne Lua API
- เพิ่ม `shyne_role` / `shyne_tags` และ API `model.role()` / `model.tag()` โดย path ของโมเดลยังเป็น API หลักสำหรับ rig ซับซ้อน
- ปรับ network protocol เป็น `9`; ฝั่ง client และ server ต้องใช้ Shyne รุ่นนี้ให้ตรงกัน

Creator quick start, Render Task API และ Profiler: [CREATOR_QUICKSTART_TH.md](CREATOR_QUICKSTART_TH.md)

มาตรฐานสร้าง Avatar แบบ model-first และ Zero-Lua: [SHYNE_STANDARD_2_TH.md](SHYNE_STANDARD_2_TH.md)

มาตรฐาน Blockbench animation และ Shyne Expression: [BLOCKBENCH_ANIMATION_STANDARD.md](BLOCKBENCH_ANIMATION_STANDARD.md)

คู่มือ Native Rig, armor cosmetic และ secondary physics: [RIG_API_TH.md](RIG_API_TH.md)

แผนผังโค้ดและกติกาการแยกไฟล์: [ARCHITECTURE_TH.md](ARCHITECTURE_TH.md)

## Structure

```text
common/
└─ src/
   ├─ main/java/       # Avatar, Lua API, model, Cloud, security และ gameplay
   ├─ main/resources/  # assets, schemas, Lua libraries และ mixin configs
   └─ test/java/       # unit tests ที่ใช้ร่วมกัน

fabric/
└─ src/main/
   ├─ java/            # Fabric entrypoints/events/network/render adapters
   └─ resources/       # fabric.mod.json

neoforge/
└─ src/main/
   ├─ java/            # NeoForge entrypoints/events/network/registry/render adapters
   └─ templates/       # META-INF/neoforge.mods.toml
```

โค้ดใน `common` ต้องไม่ import Fabric หรือ NeoForge โดยตรง หากต้องเชื่อม Loader ให้สร้าง adapter ชื่อเดียวกันใน `fabric` และ `neoforge`

## Build

Build และตรวจทั้งสอง Loader:

```powershell
.\gradlew.bat buildAll
```

รวบรวมเฉพาะ release JAR ไว้ใน `build/releases`:

```powershell
.\gradlew.bat collectReleaseJars
```

Build แยก Loader:

```powershell
.\gradlew.bat :fabric:build
.\gradlew.bat :neoforge:build
```

ไฟล์ที่ได้:

```text
fabric/build/libs/shyne-creator-fabric-<version>.jar
neoforge/build/libs/shyne-creator-neoforge-<version>.jar
```

อย่าใส่ JAR ทั้งสองตัวใน Minecraft instance เดียว ให้เลือกไฟล์ที่ตรงกับ Loader

## Development runtime

```powershell
.\gradlew.bat :fabric:runClient
.\gradlew.bat :fabric:runServer
.\gradlew.bat :neoforge:runClient
.\gradlew.bat :neoforge:runServer
```

Avatar และ gameplay packs ยังใช้โฟลเดอร์มาตรฐานเดียวกัน:

```text
.minecraft/shyne-mods/
├─ avatars/
└─ <content-pack>/
```

Core JAR ไม่บรรจุ Avatar, model หรือ gameplay pack ตัวอย่าง ดูรูปแบบไฟล์ใน `AVATAR_SYSTEM.md`, Avatar Lua API ใน `SHYNE_LUA_API_TH.md`, Gameplay API ใน `SHYNE_GAMEPLAY_API_TH.md`, Custom Render API ใน `CUSTOM_RENDER_API_TH.md` และการทดสอบหลายผู้เล่นใน `MULTIPLAYER_TESTING.md`

## License

Copyright (C) 2026 seashyne

ซอร์สโค้ด Shyne Creator เผยแพร่ภายใต้ `Mozilla Public License 2.0` (`MPL-2.0`) ดูข้อความฉบับเต็มใน `LICENSE` ชื่อ Shyne, Shyne Creator และโลโก้ของโครงการไม่รวมอยู่ในสิทธิ์การใช้เครื่องหมายการค้าของ MPL โปรดดู `TRADEMARKS.md`
