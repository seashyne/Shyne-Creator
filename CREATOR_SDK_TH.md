# Shyne Creator SDK

เอกสารนี้เป็นจุดเริ่มต้นสำหรับมอดเสริมที่สร้าง Power, Skill และ Avatar โดยไม่ฝัง content ตัวอย่างไว้ใน Shyne Creator

## โครงมอด Gameplay

```text
shyne-mods/<pack-id>/
├─ mod.json
├─ main.lua
├─ skills/
│  └─ dash.json
├─ weapons/
│  └─ focus.json
├─ items/
│  └─ aether_crystal.json
└─ bbmodels/
   └─ effect.bbmodel
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

Schema อยู่ที่ `src/main/resources/shyne_sdk/schemas` และคู่มือ API อยู่ที่ `SHYNE_LUA_API_TH.md` ส่วน bootstrap จริงอยู่ใน `src/main/resources/shyne_runtime/lua/shyne_avatar.lua`

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

`avatar.json` ขั้นต่ำ:

```json
{
  "api": "latest",
  "requires": {
    "core": ">=1.1"
  },
  "id": "author.avatar",
  "name": "Avatar Name",
  "version": "1.0.0",
  "main": "script.lua",
  "model": "model.bbmodel",
  "online_sync": true
}
```

ใช้ id ที่มี namespace ของผู้สร้าง ห้ามใช้ชื่อ Shyne Creator/Figura หรือ asset ของบุคคลอื่นให้ผู้เล่นเข้าใจว่าเป็นของทางการ

## ข้อมูลที่ซิงก์

- Gameplay state: Minecraft server เป็นผู้กำหนด
- Avatar snapshot: model/texture/animation และ state ที่ schema อนุญาต
- Microphone event: ข้อมูลระดับเสียงภายในเครื่อง ไม่ส่งเสียงดิบขึ้น Cloud
- Cloud: ไฟล์ Avatar, metadata, owner UUID และ permissions เท่านั้น

## ก่อนเผยแพร่

1. ตรวจว่า asset ทุกไฟล์เป็นของตนเองหรือมี license
2. เพิ่ม `description`, version และ permissions ที่ตรงความตั้งใจ
3. เปิด Avatar ในเกมและดู Content Diagnostics
4. ทดสอบ Client A/B ตาม `MULTIPLAYER_TESTING.md`
5. เผยแพร่ผ่าน Cloud Library; อย่าแจก Shyne Creator JAR ที่ฝังตัวอย่างของ pack

รายละเอียด Avatar runtime เพิ่มเติมอยู่ใน `AVATAR_SYSTEM.md` และ Cloud protocol อยู่ใน `CLOUD_API.md`

Avatar Lua สามารถแยกไฟล์เป็น module แล้วเรียก `require("lib.my_module")` ได้ ระบบจะค้นหา `lib/my_module.lua` ภายในแพ็กเท่านั้นและ cache ผลลัพธ์ให้หนึ่งครั้งต่อการเปิด Avatar

Avatar ใหม่ควรใช้ Shyne Lua API Standard 1.1 ผ่าน `api: "latest"` หรือเวอร์ชันล็อก `api: "1.1"` และประกาศโมดูลขั้นต่ำใน `requires` API มีทั้งชื่อ global แบบสั้นและ `shyne.*` โดยให้ผลเหมือนกัน
