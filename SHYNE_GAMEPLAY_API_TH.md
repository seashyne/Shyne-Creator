# Shyne Gameplay API

เอกสารนี้ตรวจทานกับ Shyne Creator `2.13.0` Gameplay API ทำงานฝั่ง server และ server เป็น authority ของ mana, cooldown, requirement, damage, projectile, summon และการใช้ไอเท็ม ดูคู่มือและ schema ที่เกี่ยวข้องทั้งหมดได้ที่ [API Contracts](API_CONTRACTS_TH.md)

## ขอบเขตและโครงสร้างแพ็ก

Gameplay pack เป็นโฟลเดอร์ใต้ `shyne-mods/` ที่มี `mod.json` และ entry Lua; registry จะสแกน JSON ใต้ `skills/`, `items/` และ `weapons/`:

```text
shyne-mods/<pack-id>/
├─ mod.json
├─ main.lua
├─ skills/
│  └─ arc_bolt.json
├─ items/
│  └─ arcane_focus.json
├─ weapons/
│  └─ aether_focus.json
└─ bbmodels/
   └─ effect.bbmodel
```

`mod.json` ใช้ระบุ `id`, `name`, `version`, `author`, `description`, `entry` และ `script_engine: "lua"` (ค่าเริ่มต้นของ entry คือ `main.lua`) ส่วน Native Asset Package แบบไม่มี Lua ใช้ `shyne-package.json` แยกต่างหากตาม [Power & Asset Package 1.0](POWER_ASSET_PACKAGE_TH.md)

## Skill definition

Schema: [`skill.schema.json`](common/src/main/resources/shyne_sdk/schemas/skill.schema.json)

```json
{
  "skill_id": "arcane.arc_bolt",
  "display_name": "Arc Bolt",
  "description": "Launch a focused bolt.",
  "cast_type": "projectile",
  "default_slot": "primary",
  "mana_cost": 12,
  "cooldown_ticks": 16,
  "combo_window_ticks": 20,
  "model_id": "arcane:bolt",
  "animation": "cast",
  "icon": "arc_bolt",
  "tags": ["arcane", "projectile"],
  "requirement": {
    "min_level": 2,
    "required_skills": [],
    "required_weapon_tag": "focus"
  },
  "payload": { "school": "arcane" }
}
```

| Field | Contract |
|---|---|
| `skill_id`, `display_name`, `cast_type` | required |
| `cast_type` | `instant`, `projectile`, `channel`, `summon`, `aura`, `utility` |
| `default_slot` | `primary`, `secondary`, `utility`, `ultimate`, `passive_1`, `passive_2` |
| `mana_cost`, `cooldown_ticks`, `combo_window_ticks` | non-negative; runtime clamps invalid negative values |
| `icon` | optional `png_icon` asset id from the containing Asset Package |
| `requirement` | optional level, prerequisite skill IDs and weapon tag |
| `payload` | optional creator-defined object, delivered to server-side hooks |

## Custom item definition

Schema: [`item.schema.json`](common/src/main/resources/shyne_sdk/schemas/item.schema.json)

```json
{
  "item_id": "arcane.focus",
  "display_name": "Arcane Focus",
  "description": ["Right-click to cast Arc Bolt."],
  "icon": "arc_bolt",
  "model": "shyne_creator:artifact",
  "presentation": {
    "model_id": "arcane_pack:arcane_focus",
    "scale": 0.72,
    "rotation_y": 18,
    "replace_vanilla": true
  },
  "rarity": "rare",
  "max_stack": 1,
  "glint": true,
  "use_skill": "arcane.arc_bolt",
  "weapon_id": "arcane.focus_weapon",
  "cooldown_ticks": 30,
  "consume_on_use": false,
  "payload": { "school": "arcane" }
}
```

- `item_id` และ `display_name` เป็น required; item ID ใช้อักษรพิมพ์เล็ก ตัวเลข `.`, `_` หรือ `-` ได้สูงสุด 64 ตัว
- `description` รับ string หรือรายการข้อความสูงสุด 16 บรรทัด
- `rarity` เป็น styling ของ Minecraft เท่านั้น ไม่ใช่สิทธิ์หรือเงื่อนไขการใช้
- `presentation` เป็น optional: `model_id` คือ `<ชื่อโฟลเดอร์แพ็ก>:<ชื่อไฟล์ .bbmodel>` เช่น `arcane_pack:arcane_focus`; Minecraft วาด model นี้ในมือ, GUI/inventory, ground และ item frame. `scale`, `offset_x/y/z`, `rotation_x/y/z` ปรับ presentation เฉพาะ item ได้
- `presentation` ไม่ต้องใช้ resource pack และไม่ใช่ Lua API. หาก model ยังไม่ sync, parse ไม่ผ่าน หรือไม่ระบุ `presentation` ระบบจะวาด item model ปกติแทน
- `use_skill` ให้ server เรียก SkillExecutor; mana, requirement และ cooldown ของ skill ยังคงตรวจฝั่ง server
- `weapon_id` equip weapon ที่อ้างก่อนเรียก skill; หาก weapon ไม่พบ การใช้ไอเท็มจะไม่สำเร็จ
- หลังใช้สำเร็จ runtime เรียก Lua hook `on_item_use(ctx)` ใน pack เจ้าของ item และส่ง `player`, `uuid`, `item_id`, `skill_id`, `weapon_id`, `hand`, `payload`
- `cooldown_ticks` ใช้ cooldown ของ Minecraft item และ `consume_on_use` ลด stack เฉพาะผู้เล่นที่ไม่ใช่ Creative

แจก item สำหรับทดสอบด้วย:

```text
/shyne giveitem <player> arcane.focus 1
```

## Weapon definition

Schema: [`weapon.schema.json`](common/src/main/resources/shyne_sdk/schemas/weapon.schema.json)

```json
{
  "weapon_id": "arcane.focus_weapon",
  "display_name": "Arcane Focus",
  "item_id": "arcane.focus",
  "model_id": "arcane:focus",
  "class_tag": "focus",
  "granted_skills": ["arcane.arc_bolt"],
  "stat_modifiers": { "mana": 20.0 },
  "payload": { "school": "arcane" }
}
```

Weapon ผูก item, class tag และ skill ที่มอบให้เข้าด้วยกัน Runtime ไม่เชื่อ weapon ID ที่ client อ้างเอง

## Lua hooks ของ Gameplay pack

`main.lua` ใช้ hook ที่ runtime เรียกเมื่อเกิดเหตุการณ์ของ server:

```lua
function on_item_use(ctx)
  if ctx.item_id == "arcane.focus" then
    -- ctx มาจาก server; อย่าเชื่อข้อมูลที่ client ส่งเอง
    minecraft.message("Arcane Focus used", ctx.player)
  end
end

function on_reload(ctx)
  print("Reloaded packs: " .. tostring(ctx.mods))
end
```

Hook ไม่ใช่ client Avatar Lua และไม่ควรใช้เพื่อข้าม policy ของ server

## Reload และการตรวจ registry

คำสั่งต่อไปนี้ต้องเป็นผู้ดูแล server:

```text
/shyne status
/shyne reload
/shyne skills
/shyne items
/shyne weapons
/shyne giveitem <player> <item_id> [count]
/shyne castskill <player> <skill_id>
```

`/shyne reload` โหลด pack, skill, weapon และ item ใหม่ แล้ว sync registry ไปยัง client ผู้เล่น จึงเป็นวิธีทดสอบปกติโดยไม่ต้อง restart server

เริ่มจากแพ็กที่ใช้ได้จริงได้ที่ [`tools/examples/aether-showcase-pack`](tools/examples/aether-showcase-pack): มี skill ที่ใช้ mana, item ที่มี `use_skill`, PNG icon, Blockbench 3D item presentation และ `on_skill_key` Lua effect อยู่ครบในโฟลเดอร์เดียว

## Asset icon และ UI

Skill กับ item อ้าง `icon` ด้วย asset ID ไม่ใช่ path หรือ URL. PNG ที่ผ่าน validation จะ sync ไปยัง client; ถ้า icon ไม่ผ่านหรือเกินงบ Power Deck และ Creator Content จะแสดงอักษรย่อแทน โดย registry ยังคงใช้งานได้ ดูโครงสร้างและขนาดไฟล์ที่ [Power & Asset Package 1.0](POWER_ASSET_PACKAGE_TH.md)

## กติกาความปลอดภัย

1. ตรวจ JSON กับ schema ก่อนเปิด server
2. ให้ server ตัดสิน mana, cooldown, requirement, item use และผลของ skill ทุกครั้ง
3. ใช้ ID เฉพาะ pack เพื่อหลีกเลี่ยงการแทนที่ content จาก pack อื่นโดยไม่ตั้งใจ
4. ตรวจ Content Diagnostics และ `/shyne errors` เมื่อ registry ไม่โหลด
5. ทดสอบ Fabric และ NeoForge รวมถึง multiplayer ก่อนเผยแพร่
