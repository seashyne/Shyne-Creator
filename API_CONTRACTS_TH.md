# Shyne Creator API Contracts

เอกสารนี้เป็นดัชนีสัญญา API ของ Shyne Creator `2.12.5` สำหรับ Creator, ผู้ทำ content pack และผู้พัฒนาม็อดเสริม ใช้ร่วมกับ schema ใน JAR เพื่อไม่ต้องเดาว่า field หรือ API ใดเป็นสัญญาสาธารณะ

## เลือกคู่มือให้ตรงงาน

| งาน | คู่มือหลัก | สัญญาที่ตรวจได้ |
|---|---|---|
| Avatar แบบไม่เขียน Lua | [Shyne Avatar Standard 2.0](SHYNE_STANDARD_2_TH.md) | `avatar.schema.json` |
| Avatar Lua, event, input, state และ Figura compatibility | [Shyne Native Lua API](SHYNE_LUA_API_TH.md) | `api: "2.0"`, `requires` ใน `avatar.json` |
| HUD, world task, bone attachment และ Script Canvas UI | [Custom Render API 1.4](CUSTOM_RENDER_API_TH.md) | permission `hud_render` / `world_render` |
| Physics, IK, armor และ SquAPI | [Native Rig API 1.3](RIG_API_TH.md) | `requires.rig: ">=1.3"` |
| Skill, Item, Weapon และคำสั่งฝั่ง server | [Gameplay API](SHYNE_GAMEPLAY_API_TH.md) | `skill.schema.json`, `item.schema.json`, `weapon.schema.json` |
| ไอคอน PNG และ Creator Asset Package | [Power & Asset Package 1.0](POWER_ASSET_PACKAGE_TH.md) | `shyne_asset_package.schema.json` |
| Cloud backup และ Public ZIP | [Cloud API 2.2](CLOUD_API.md), [Public Share](PUBLIC_SHARE.md) | HTTP response / package hash |
| โครงโปรเจกต์และการทดสอบ | [Creator SDK](CREATOR_SDK_TH.md) | `shyne_sdk/schemas/` ใน JAR |

## Source of truth

ไฟล์ schema ที่มากับม็อดเป็นตัวตรวจรูปแบบจริงของ content JSON:

```text
common/src/main/resources/shyne_sdk/schemas/
├─ avatar.schema.json
├─ avatar_synced.schema.json
├─ item.schema.json
├─ profile.schema.json
├─ shyne_asset_package.schema.json
├─ shyne_mod.schema.json
├─ skill.schema.json
└─ weapon.schema.json
```

คู่มืออธิบายเหตุผลและ workflow; schema กำหนดชื่อ field, type, required field และค่าที่รับได้ หากต่างกัน ให้ยึด schema และแจ้ง issue พร้อมตัวอย่างไฟล์ที่ตรวจไม่ผ่าน

## ขอบเขตความปลอดภัย

- **Avatar Lua** ทำงานบน client sandbox; permission ที่ไม่ประกาศหรือไม่อนุญาตจะใช้ไม่ได้
- **Gameplay pack** อยู่ฝั่ง server และ server เป็นผู้ตรวจ mana, cooldown, requirement, damage และ item use เสมอ
- **Asset Package** รับ PNG ที่อยู่ภายในแพ็กเท่านั้น; SVG เป็น source สำหรับ editor และไม่ถูก parse ในเกม
- **Cloud API** เป็น HTTP contract แยกจาก Lua API; อย่าส่ง Minecraft access token ไปยัง Cloud

## ตรวจ content ก่อนเปิดเกม

```powershell
python .\tools\creator\shyne_creator.py validate-pack .\my_power_pack
```

สำหรับ Avatar ให้เปิด Content Diagnostics ในเกมและทดสอบ multiplayer ตาม [MULTIPLAYER_TESTING.md](MULTIPLAYER_TESTING.md) ก่อนเผยแพร่

## Compatibility policy

- Avatar รุ่นใหม่ใช้ `standard: "2.0"`; `api: "2.0"` ใช้เฉพาะเมื่อมี `main`/Lua
- ระบุ `requires` เฉพาะโมดูลที่ Avatar ต้องใช้จริง เพื่อให้ runtime ปฏิเสธอย่างชัดเจนเมื่อ client เก่าเกินไป
- `format: "shyne_asset_package"` กับ `format_version: 1` คือสัญญาปัจจุบันของ asset package
- การเปลี่ยน schema หรือ API ที่ทำให้ content เดิมพังต้องออก format/API ใหม่ ไม่เปลี่ยนความหมายเงียบ ๆ
