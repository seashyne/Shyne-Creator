# Shyne Creator SDK

เอกสารนี้ตรวจทานกับ Shyne Creator `2.12.6` schema ใน `shyne_sdk/schemas/` คือ contract สำหรับ JSON ของ Creator ดูภาพรวมได้ที่ [API Contracts](API_CONTRACTS_TH.md)

เอกสารนี้เป็นจุดเริ่มต้นสำหรับมอดเสริมที่สร้าง Power, Skill และ Avatar โดยไม่ฝัง content ตัวอย่างไว้ใน Shyne Creator

## โครงมอด Gameplay

```text
shyne-mods/<pack-id>/
├─ mod.json
├─ main.lua
├─ shyne-package.json        # optional; required when skill/item uses native PNG icon
├─ assets/icons/
│  └─ arc_bolt.png
├─ skills/
│  └─ dash.json
├─ weapons/
│  └─ focus.json
├─ items/
│  └─ aether_crystal.json
├─ bbmodels/
│  └─ effect.bbmodel
└─ sources/icons/
   └─ arc_bolt.svg           # optional editor source; never read at runtime
```

Server เป็นเจ้าของ profile, mana, cooldown, damage, projectile และ summon Client ส่งเพียง input intent เช่นช่อง Primary/Secondary/Utility/Ultimate ห้ามเขียน Power โดยเชื่อค่าจาก client

## Custom Item ที่มีพลัง

สร้างไฟล์ `items/aether_crystal.json`:

```json
{
  "item_id": "aether_crystal",
  "display_name": "Aether Crystal",
  "description": [
    "ผลึกที่กักเก็บพลังแห่งท้องฟ้า",
    "คลิกขวาเพื่อปล่อย Arc Bolt"
  ],
  "model": "shyne_creator:artifact",
  "rarity": "rare",
  "max_stack": 1,
  "glint": true,
  "use_skill": "arc_bolt",
  "weapon_id": "aether_focus",
  "cooldown_ticks": 30,
  "consume_on_use": false,
  "payload": {
    "school": "aether"
  }
}
```

- `use_skill` เรียก Skill ที่ลงทะเบียนไว้ โดย Server ตรวจ mana, requirement และ cooldown ตามปกติ
- `weapon_id` equip อาวุธเชิงระบบเข้ามือก่อนตรวจ requirement ของ Skill
- ทุกครั้งที่ใช้จะเรียก Lua hook `on_item_use(ctx)` ใน pack เจ้าของ Item
- `ctx` มี `player`, `uuid`, `item_id`, `skill_id`, `weapon_id`, `hand` และ `payload`
- `model` อ้าง item model definition จาก resource pack ได้ หากไม่กำหนดจะใช้รูปลักษณ์ Shyne Artifact

แจกเพื่อทดสอบ:

```text
/shyne reload
/shyne items
/shyne giveitem <player> aether_crystal 1
```

หรือแจกจาก Lua:

```lua
item.give("aether_crystal", 1, ctx)

function on_item_use(ctx)
  if ctx.item_id == "aether_crystal" then
    player.say("Aether awakened!", ctx)
  end
end
```

Schema อยู่ที่ `src/main/resources/shyne_sdk/schemas` และคู่มือ API อยู่ที่ `SHYNE_LUA_API_TH.md` ส่วน bootstrap ภายในถูกแบ่งไว้ที่ `src/main/resources/shyne_runtime/lua/avatar/` โดย `shyne_avatar.lua` เป็นเพียง index ผู้สร้าง Avatar ไม่ต้องโหลดโมดูลเหล่านี้เอง

## Native PNG icon และ Asset Package

เมื่อกำหนด `icon` ใน skill หรือ item ต้องวาง `shyne-package.json` ที่ root เดียวกับ `skills/` หรือ `items/` และประกาศ asset ID ให้ตรงกัน:

```json
{
  "format": "shyne_asset_package",
  "format_version": 1,
  "id": "arcane_pack",
  "assets": [{
    "id": "arc_bolt",
    "type": "png_icon",
    "path": "assets/icons/arc_bolt.png"
  }]
}
```

เกมอ่าน PNG package-local เท่านั้น ไม่โหลด SVG, React, JavaScript, URL หรือ path ภายนอกแพ็ก ข้อกำหนดขนาดและตัวอย่างเต็มอยู่ที่ [POWER_ASSET_PACKAGE_TH.md](POWER_ASSET_PACKAGE_TH.md)

## Blockbench 3D item presentation

ไอเท็ม Creator ไม่ต้องมี resource pack เพื่อเป็น 3D: วาง `bbmodels/arcane_focus.bbmodel` และ texture PNG ไว้ในแพ็ก แล้วเพิ่ม `presentation` ใน `items/arcane_focus.json`:

```json
"presentation": {
  "model_id": "my_power_pack:arcane_focus",
  "scale": 0.72,
  "rotation_y": 18,
  "replace_vanilla": true
}
```

โมเดลจะแสดงในมือ, GUI/inventory, ground และ fixed display; `icon` PNG ยังใช้กับหน้า Deck/Catalog. ละ `presentation` หรือให้ `replace_vanilla` เป็น `false` เพื่อคงโมเดล Minecraft ปกติไว้. ดู field ครบและตัวอย่างได้ที่ [Gameplay API](SHYNE_GAMEPLAY_API_TH.md) และ [Asset Package](POWER_ASSET_PACKAGE_TH.md)

## โครง Avatar

```text
shyne-mods/avatars/<avatar-id>/
├─ avatar.json
├─ model.bbmodel
├─ script.lua
├─ synced.schema.json
└─ textures/
   └─ body.png
```

ทั้งการพัฒนาและใช้งานจริงใช้ตำแหน่งเดียวคือ `shyne-mods/avatars/<avatar-id>/` ตัว Avatar เป็นโฟลเดอร์ธรรมดา ไม่ต้องเปลี่ยนนามสกุลหรือบีบอัดไฟล์

`avatar.json` ขั้นต่ำแบบ Zero-Lua:

```json
{
  "standard": "2.0",
  "id": "author.avatar",
  "name": "Avatar Name",
  "profile": "accessory",
  "behavior": {
    "preset": "auto"
  }
}
```

### Avatar แบบ Overlay

สำหรับหู หาง ปีก หรือ armor cosmetic ที่ยังต้องการให้เห็น skin/armor ของ Minecraft ให้ใช้ `"profile": "accessory"` และกำหนด `parent_type` ของ bone ใน Blockbench เป็น `Head`, `Body`, `LeftArm`, `RightArm`, `LeftLeg` หรือ `RightLeg` Shyne Creator 2.12.6 จะผูก bone ตามส่วนผู้เล่นอัตโนมัติ แม้ไม่มี Lua script โดย pose ของ vanilla part จะซ้อนกับ animation ของ bone อย่างเป็น parent transform

หาก asset จำเป็นต้องสลับ attachment ระหว่างเล่น จึงค่อยระบุ `main` และใช้ Shyne-native Lua:

```lua
avatar.hide_vanilla(false)
model.part("model.Wings"):vanilla_parent("BODY")
```

การผูกด้วย `parent_type` และ `vanilla_parent()` จะถูกส่งไปยังผู้เล่น Shyne คนอื่นพร้อม avatar snapshot

ใช้ id ที่มี namespace ของผู้สร้าง ห้ามใช้ชื่อ Shyne Creator/Figura หรือ asset ของบุคคลอื่นให้ผู้เล่นเข้าใจว่าเป็นของทางการ

## ข้อมูลที่ซิงก์

- Gameplay state: Minecraft server เป็นผู้กำหนด
- Avatar snapshot: model/texture/animation และ state ที่ schema อนุญาต
- Microphone event: ข้อมูลระดับเสียงภายในเครื่อง ไม่ส่งเสียงดิบขึ้น Cloud
- Cloud: ไฟล์ Avatar, metadata, owner UUID และ permissions เท่านั้น

เมื่อกำหนด `"synced_schema": "synced.schema.json"` runtime จะอ่านและ compile schema ก่อนเปิด Avatar แล้วตรวจทุก `state.sync(key, value)` จริง รองรับชนิด `object`, `array`, `string`, `number`, `integer`, `boolean`, `null`, `enum`/`const`, ช่วงตัวเลข, ความยาว string/array, `properties`, `items` และ `additionalProperties` ไม่รองรับ `$ref` หรือ regex จากภายนอกเพื่อให้ตรวจได้แน่นอน หากตั้ง `additionalProperties: false` เฉพาะ key ใน `properties` เท่านั้นที่จะอยู่ใน network snapshot

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "ears_enabled": { "type": "boolean" },
    "tail_strength": { "type": "number", "minimum": 0, "maximum": 1 }
  }
}
```

## ก่อนเผยแพร่

1. ตรวจว่า asset ทุกไฟล์เป็นของตนเองหรือมี license
2. เพิ่ม `description`, version และ permissions ที่ตรงความตั้งใจ
3. เปิด Avatar ในเกมและดู Content Diagnostics
4. ทดสอบ Client A/B ตาม `MULTIPLAYER_TESTING.md`
5. เผยแพร่ผ่าน Cloud Library; อย่าแจก Shyne Creator JAR ที่ฝังตัวอย่างของ pack

รายละเอียด Avatar runtime เพิ่มเติมอยู่ใน `AVATAR_SYSTEM.md` และ Cloud protocol อยู่ใน `CLOUD_API.md`

Avatar Lua สามารถแยกไฟล์เป็น module แล้วเรียก `require("lib.my_module")` ได้ ระบบจะค้นหา `lib/my_module.lua` ภายในแพ็กเท่านั้นและ cache ผลลัพธ์ให้หนึ่งครั้งต่อการเปิด Avatar

Avatar ใหม่ควรใช้ declarative Standard 2.0 ก่อน Lua เสมอ งานขั้นสูงที่ระบุ `main` ให้ล็อก `api: "2.0"` และประกาศโมดูลขั้นต่ำใน `requires`; Rig/physics ผ่าน Lua ต้องประกาศ `"rig": ">=1.3"`
