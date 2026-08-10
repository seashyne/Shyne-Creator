# Shyne Creator Multi-loader

โปรเจกต์หลักสำหรับ Build Shyne Creator บน Minecraft 26.2 ทั้ง Fabric และ NeoForge จาก source ชุดเดียว

## Version

- Shyne Creator: `2.9.1-alpha-26.2`
- Minecraft: `26.2`
- Java: `25`
- Fabric Loader: `0.19.3+`
- Fabric API: `0.155.2+26.2`
- NeoForge: `26.2.0.25-beta+`
- Gradle: `9.5.1`

Fabric และ NeoForge ใช้ `mod id`, Shyne API standard และ network protocol version เดียวกัน ส่วนไฟล์ที่ผูกกับ Loader จะถูกแยกออกจากระบบหลัก

## ดาวน์โหลดแล้วเริ่มใช้อย่างไร

Shyne Creator เป็น runtime สำหรับใช้ Avatar และ Content Pack ไม่ใช่ม็อดที่เพิ่ม Avatar หรือสกิลสำเร็จรูปมาให้ทันที หลังติดตั้งแล้ว การเห็นตัวละคร Minecraft แบบปกติถือว่าถูกต้อง

1. เลือก JAR ให้ตรงกับ Fabric หรือ NeoForge; Fabric ต้องติดตั้ง Fabric API เพิ่ม
2. ใส่ JAR ในโฟลเดอร์ `mods` แล้วเปิด Minecraft 26.2
3. เข้าโลกแล้วกด `H` หรือเปิด `Esc → อวตาร`
4. ค้นหา Avatar จาก Cloud หรือใช้ปุ่มเปิดโฟลเดอร์เพื่อนำ Avatar มาไว้ใน `shyne-mods/avatars`
5. กด `โหลดใหม่` แล้วกด `เลือกใช้`
6. กด `G` เพื่อเปิดท่าทางหรือคำสั่งที่ Avatar นั้นเตรียมไว้ และกด `O` เพื่อเปิดการตั้งค่า Shyne

ปุ่ม `Z`, `X`, `C` และ `V` เป็นช่องความสามารถจาก Content Pack จึงอาจไม่ทำอะไรหากโลกหรือเซิร์ฟเวอร์ไม่ได้ติดตั้งแพ็กที่รองรับ ส่วน **Shyne Creator Kit ใช้สำหรับสร้าง Avatar และห้ามนำไปใส่ในโฟลเดอร์ `mods`**

คู่มือสำหรับผู้เล่น: [เริ่มใช้ Shyne ใน 1 นาที](PLAYER_QUICKSTART_TH.md)

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

## 2.9.1-alpha Creator Details

- เพิ่มแท็บผู้สร้างในหน้า Shyne Settings แสดงชื่อผู้สร้างและเวอร์ชันที่กำลังใช้งาน
- เพิ่มปุ่มเปิดคู่มือ, GitHub source, CurseForge และหน้า Issues ด้วย URL ทางการ
- ใช้ชื่อแท็บแบบสั้นเมื่อหน้าจอแคบ และแก้ URL ใน metadata ของ Fabric/NeoForge ให้ตรงกับ repository ปัจจุบัน

## 2.9.0-alpha Runtime, Outfit & Humanoid Standards

- Remote Avatar ใช้การสมัครรับข้อมูลรายผู้เล่น ผู้ใช้ที่ Block จะหยุดรับ model, pose, synced vars และ animation ของคนนั้นจาก Server
- จำกัดปริมาณข้อมูลตามจำนวน byte แยก full model ออกจาก pose update และไม่รวม snapshot ของผู้เล่นทั้งหมดไว้ใน packet เดียว
- ตรวจงบทรัพยากรก่อนติดตั้ง remote model/texture พร้อม Vanilla fallback เมื่อ Avatar เกินค่าที่ผู้ใช้เลือก
- เพิ่มทางเข้า Shyne Settings แบบโลโก้จากหน้า Main Menu และรักษาทางเข้าจาก Pause Menu เดิม
- Release pipeline ตรวจ version/protocol ของ Fabric และ NeoForge, ล้าง artifact เก่า และสร้าง SHA-256 ให้ JAR ทั้งสองไฟล์

## 2.8.3-alpha Avatar Cloud 2.2

- Public Share ใช้ ZIP มาตรฐานผ่าน HTTPS พร้อมตรวจ ZIP policy และ SHA-256 ทั้งฝั่ง Backend และ Client
- Permission ของ Public Avatar ผูกกับ package hash และรองรับ `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render` และ `world_render`
- ผู้สร้าง Revoke เพื่อลบ Public ZIP และหยุดการดาวน์โหลดครั้งใหม่ได้ โดย Private Backup ไม่ได้รับผลกระทบ
- ไม่ใช้ `.sc v1`, `.sc v2`, data key หรือ lease แบบกำหนดเอง และไม่อ้างว่าสามารถลบสำเนาที่ผู้เล่นติดตั้งไปแล้วจากระยะไกล
- Private chunk แยก namespace ตามเจ้าของ ป้องกันการอ้าง hash ข้ามบัญชี

คู่มือผู้ใช้และ API: [AVATAR_CLOUD.md](AVATAR_CLOUD.md), [CLOUD_API.md](CLOUD_API.md) และ [PUBLIC_SHARE.md](PUBLIC_SHARE.md)

## 2.8.0-alpha Native Fidelity Fix

- runtime ของ Shyne ยังคงเป็นระบบ native ทั้งหมดและไม่ต้องติดตั้งหรือโหลด Figura; ไฟล์/สคริปต์ Figura ต้นทางใช้เป็นข้อมูลอ้างอิงตอนแปลงเท่านั้น
- ตัวอ่าน `.bbmodel` เคารพค่าเริ่มต้น `visibility`/`export` ของ group และ element, รองรับ mesh และใช้ canonical full path เมื่อชื่อ part ซ้ำ โดย short alias ใช้ได้เฉพาะชื่อที่ไม่กำกวม
- แก้ Auto Humanoid ไม่ให้ใส่ pose ซ้ำกับ hierarchy ที่มีชื่อซ้อนกัน เช่น `body` > `Body` และรองรับ legacy keyframe wrapper แบบจำกัดผ่าน expression engine ที่ปลอดภัยโดยไม่รัน `require` ต้นทาง
- ส่ง vanilla visibility mask ตาม UUID ทั้ง local/remote ไปยัง body/skin layer, cape, Elytra, armor แต่ละ slot, held item, head item และ first-person arm โดยไม่ซ่อน Shyne model layer
- แก้ `avatar.png` สีดำจากการส่งพารามิเตอร์วาดภาพผิด พร้อม decode/downscale ไอคอนขนาดใหญ่ก่อนอัปโหลด และแสดงชื่อไฟล์ outfit ตัวเลขยาวเป็น `Outfit N` โดยไม่เปลี่ยน id จริง
- ตัวแปลงใช้ `-VanillaMode Auto` เป็นค่าเริ่มต้น ตรวจ full-body replacement จาก manifest/สคริปต์ต้นทาง ค้นหา `.bbmodel` ในโฟลเดอร์ย่อย และถอด Base64 texture เป็นไฟล์ภายในแพ็กได้
- ปรับ network protocol เป็น `12`; snapshot ใช้ revision ACK/retry, ตรวจ nested model/PNG ก่อน decode และ rebase เวลา animation แบบไม่อิงนาฬิกาของเครื่อง
- แยก Lua Standard bootstrap เดิม 952 บรรทัดเป็น 7 โมดูลภายในขนาด 51–227 บรรทัด โดย host compile เป็น chunk เดียว ผู้สร้าง Avatar จึงไม่ต้อง `require` เพิ่ม
- outfit `.png` ธรรมดาเป็น alpha overlay ตามค่าเริ่มต้น; ใช้ `*.replace.png`, `*_replace.png` หรือ `*-replace.png` เมื่อต้องการแทน texture ทั้งภาพ โดยการสลับชุดไม่รีเซ็ต Lua หรือ animation state

การแก้ชุดนี้เพิ่มความตรงกับโมเดลต้นทาง แต่ไม่รับรองว่า Avatar Figura ทุกแพ็กจะทำงานเหมือนเดิม 100%; API หรือ render context เฉพาะทางที่ยังไม่รองรับต้องย้ายเป็น Shyne-native เพิ่มเติม

## 2.7.44 Native Hover Physics and First Person

- อ่าน `parent_type` จาก Blockbench/Figura `.bbmodel` และผูก custom bone กับหัว ลำตัว แขน หรือขา Minecraft โดยอัตโนมัติ
- `parent_type` เป็น parent transform จริง: pose หัว/แขนขาจะซ้อนก่อน local transform และ animation ของ bone จึงไม่ทำให้หูหรือของเสริมหลุดจากส่วนผู้เล่น
- เพิ่ม Shyne Rig API 1.3: `rot_add()`, `rig.spring()`, `rig.chain()`, `rig.attach()` และ `rig.armor()` สำหรับ custom/SquAPI-style avatar, armor cosmetic และ secondary motion
- เพิ่ม swept world probe สำหรับ collision block/entity, wind/impulse, cone constraint, two-bone IK, animation graph และ Elytra cosmetic
- native `require("SquAPI")` และ `require("lib.SquAPI")` ใช้ BERP/bounce-object physics ที่เข้ากับ SquAPI สำหรับ tail, หูซ้าย/ขวาอิสระ, smooth head, limb, blink, taur, `hoverPoint`/`floatPoint` และ utility โดยไม่รัน Lua ของ Figura
- `hoverPoint` ชน block/entity ผ่าน swept world probe, สะท้อนความเร็วแบบ visual-only และคำนวณกลับเข้า coordinate ของโมเดลทุก tick; ใช้ root group ระดับบนสุดสำหรับของลอย/companion เพื่อไม่รับ transform จากแขนหรือขา
- รองรับ Blockbench hierarchy `LeftArmFP` / `RightArmFP` (หรือ role/tag เทียบเท่า) สำหรับแขน first-person เฉพาะทาง; tree นี้ไม่ถูกวาดซ้ำใน world view และคืน vanilla hand เมื่อ tree ว่างหรือซ่อน
- GUI portrait, skull block และ custom held-item geometry ยังไม่มี hook ที่เที่ยงตรง จึงยังไม่ประกาศว่ารองรับ
- รองรับ Avatar แบบ Overlay สำหรับหู หาง ปีก และของเสริมด้วย `replace_vanilla: false`
- เพิ่ม target/raycast detail สำหรับ block และ entity ใน Shyne Lua API
- เพิ่ม `shyne_role` / `shyne_tags` และ API `model.role()` / `model.tag()` โดย path ของโมเดลยังเป็น API หลักสำหรับ rig ซับซ้อน
- รุ่น 2.7.44 ใช้ network protocol `9`; รุ่นปัจจุบันใช้ protocol `14` และ client/server ต้องใช้รุ่นเดียวกัน

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

เพิ่ม patch version อัตโนมัติ จากนั้นทดสอบและสร้าง release JAR พร้อม checksum:

```powershell
.\tools\release_build.ps1
```

หากเพิ่ม version แล้ว สามารถรวบรวมเฉพาะ release JAR ปัจจุบันไว้ใน `build/releases` ได้ด้วย:

```powershell
.\gradlew.bat releaseBundle
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

ติดตั้ง Build ปัจจุบันลง Instance สำหรับทดสอบด้วยสคริปต์มาตรฐาน โดยส่ง path ของ
game directory หลักที่มีโฟลเดอร์ `mods` (ไม่ใช่ path ของ `mods` เอง):

```powershell
.\tools\install_local.ps1 `
  -Loader fabric `
  -GameDir 'C:\Users\<ชื่อผู้ใช้>\curseforge\minecraft\Instances\<ชื่อโปรไฟล์>'
```

ใช้ `-Loader neoforge` สำหรับ NeoForge หรือส่ง `-Jar <path>` เมื่อต้องการทดสอบ
JAR ที่ระบุเอง ตัวติดตั้งจะตรวจ ZIP และ metadata, stage ไฟล์ก่อนสลับ JAR เดิม
และตรวจ process `java`/`javaw` ที่มี `--gameDir` ตรงกับ Instance แบบ exact
หาก Minecraft ของ Instance นั้นยังเปิดอยู่ สคริปต์จะหยุดพร้อมข้อความให้ปิดเกม
โดยไม่ปิด process ให้และไม่แก้ JAR ที่ติดตั้งอยู่

ตรวจ regression ของตัวติดตั้ง:

```powershell
.\tools\tests\install_local_regression.ps1
```

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
