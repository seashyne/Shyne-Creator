# Shyne Creator Asset Package 1.0

เอกสารนี้ตรวจทานกับ Shyne Creator `2.12.9` และ `shyne_asset_package.schema.json` คือ contract ที่ตรวจ manifest จริง ดู API อื่นที่เกี่ยวข้องได้ที่ [API Contracts](API_CONTRACTS_TH.md)

Creator Asset Package คือแพ็กคอนเทนต์สำหรับสกิล, ไอเท็ม และไอคอน native UI
มันไม่ใช้ WebView, React หรือ SVG runtime ภายใน Minecraft: UI ในเกมเป็น native Java และรับเฉพาะ PNG ที่ผ่านการตรวจแล้ว

## โครงสร้าง

```text
my_power_pack/
├─ shyne-package.json
├─ assets/
│  └─ icons/
│     └─ arc_bolt.png       # ไฟล์ที่เกมใช้จริง
├─ sources/
│  └─ icons/
│     └─ arc_bolt.svg       # optional: source สำหรับ Figma/React/editor เท่านั้น
├─ skills/
│  └─ arc_bolt.json
├─ items/
│  └─ arcane_focus.json
└─ bbmodels/
│  ├─ arcane_focus.bbmodel
│  └─ arcane_focus.png
```

วางโฟลเดอร์แพ็กไว้ที่:

```text
<Minecraft instance>/shyne-mods/my_power_pack/
```

ต้องติดตั้งแพ็กเดียวกันบน **server** ที่เป็นเจ้าของ skill/item registry; server จะตรวจ path, ขนาดไฟล์ และ PNG signature/IHDR ก่อนส่ง และ client จะ decode ยืนยันอีกชั้นพร้อมข้อมูลสกิลหรือไอเท็ม

## `shyne-package.json`

สคีมาอยู่ที่ [`shyne_asset_package.schema.json`](common/src/main/resources/shyne_sdk/schemas/shyne_asset_package.schema.json)

```json
{
  "format": "shyne_asset_package",
  "format_version": 1,
  "id": "arcane_power_pack",
  "name": "Arcane Power Pack",
  "version": "1.0.0",
  "assets": [
    {
      "id": "arc_bolt",
      "type": "png_icon",
      "path": "assets/icons/arc_bolt.png",
      "source_svg": "sources/icons/arc_bolt.svg"
    }
  ]
}
```

## Blockbench 3D Item Presentation

เมื่อไอเท็มต้องมีทรง 3D ในมือและ inventory ให้ใส่ไฟล์ `.bbmodel` ไว้ใต้ `bbmodels/` (พร้อม PNG texture ของมัน) แล้วระบุ `presentation` ใน item JSON:

```json
{
  "item_id": "arcane.focus",
  "display_name": "Arcane Focus",
  "icon": "arc_bolt",
  "presentation": {
    "model_id": "my_power_pack:arcane_focus",
    "scale": 0.72,
    "offset_y": 0.05,
    "rotation_y": 18,
    "replace_vanilla": true
  }
}
```

`model_id` มาจากชื่อโฟลเดอร์แพ็กและชื่อไฟล์: `my_power_pack/bbmodels/arcane_focus.bbmodel` คือ `my_power_pack:arcane_focus`. runtime ใช้ renderer native เดียวกันใน first-person, third-person, GUI/inventory, ground และ fixed display. ไม่ต้องเพิ่ม resource pack, model JSON ของ Minecraft หรือ Lua. PNG icon ยังคงเป็นภาพที่ใช้ใน Catalog/Deck โดยแยกจาก texture ของโมเดล 3D.

ถ้า client ยังไม่ได้ model หรือ texture ขณะ sync, item จะกลับไปใช้ Minecraft item model ปกติแทนจนกว่าจะพร้อม; item ไม่หายและ gameplay ไม่เปลี่ยน.

`id` ของ asset เป็นชื่อคงที่ที่สกิลอ้างถึง ไม่ใช่ path และไม่ใช่ URL. `source_svg` มีไว้เชื่อมกับต้นฉบับจาก editor เท่านั้น; Minecraft จะไม่อ่านหรือ rasterize SVG.

### Contract ของ manifest

| Field | ข้อกำหนด |
|---|---|
| `format` | ต้องเป็น `shyne_asset_package` |
| `format_version` | ต้องเป็นเลข `1` |
| `id` | required; ตัวพิมพ์เล็ก ตัวเลข `.`, `_`, `-` ความยาว 1–64 |
| `name`, `version` | optional metadata สำหรับแสดงใน registry |
| `assets` | required; ได้สูงสุด 256 entries |
| `assets[].id` | required; ID ที่ skill/item ใช้อ้าง `icon` |
| `assets[].type` | ต้องเป็น `png_icon` |
| `assets[].path` | required; PNG ใต้ `assets/icons/` เท่านั้น |
| `assets[].source_svg` | optional source สำหรับ editor; ไม่มีผลต่อ runtime |

`shyne-package.json` สามารถอยู่ร่วมกับ `mod.json` และ `main.lua` ใน Gameplay pack เดียวกันได้; runtime จะใช้ manifest เพื่อ resolve icon ของ JSON ใต้ `skills/` และ `items/`

## สกิลหรือไอเท็มที่ใช้ไอคอน

```json
{
  "skill_id": "arcane.arc_bolt",
  "display_name": "Arc Bolt",
  "cast_type": "projectile",
  "default_slot": "primary",
  "mana_cost": 12,
  "cooldown_ticks": 16,
  "icon": "arc_bolt"
}
```

`icon` ต้องตรงกับ `assets[].id` ที่มี `type: "png_icon"` ใน `shyne-package.json` เดียวกัน Schema รายละเอียดของ definition อยู่ที่ [`skill.schema.json`](common/src/main/resources/shyne_sdk/schemas/skill.schema.json) และ [`item.schema.json`](common/src/main/resources/shyne_sdk/schemas/item.schema.json)

ไอเท็มอ้าง icon แบบเดียวกัน และจะปรากฏพร้อมรายละเอียดในหน้า Creator Content. ผู้สร้างไม่ต้องเลือกประเภทหรือระดับความหายากเพื่อให้ไอเท็มใช้ได้; `rarity` เป็นข้อมูลตกแต่ง Minecraft แบบเลือกใส่ได้เท่านั้น:

```json
{
  "item_id": "arcane.focus",
  "display_name": "Arcane Focus",
  "description": ["Right-click to cast Arc Bolt."],
  "icon": "arc_bolt",
  "use_skill": "arcane.arc_bolt"
}
```

## ข้อกำหนด runtime

- ใช้ **PNG** เท่านั้น และ path ต้องอยู่ใต้ `assets/icons/`
- ลิงก์ไฟล์ที่ชี้ออกนอกโฟลเดอร์แพ็กจะถูกปฏิเสธ
- ขนาดภาพสูงสุด `256 × 256 px`
- ขนาดไฟล์สูงสุด `128 KiB` ต่อ icon
- Server เก็บ source icon ในแต่ละ registry (skills และ items) ได้รวมสูงสุด `4 MiB`
- Server รวมไอคอนที่ sync ในแต่ละ snapshot ได้สูงสุด `512 KiB` (เรียงตาม id เพื่อให้ผลสม่ำเสมอ)
- ไม่มีการโหลด URL, local path นอกแพ็ก, SVG, JavaScript หรือ HTML ในเกม
- ไอคอนที่ไม่ผ่านการตรวจหรือเกินงบจะไม่ทำให้คอนเทนต์หาย: Power Deck และ Item Catalog ใช้อักษรย่อ fallback แทน

## ตรวจแพ็กก่อนเปิดเกม

```powershell
python .\tools\creator\shyne_creator.py validate-pack .\tools\examples\shyne_power_pack
```

หรือใช้ wrapper PowerShell ของ Creator Kit หากติดตั้งไว้:

```powershell
.\tools\creator\shyne-creator.ps1 validate-pack .\tools\examples\shyne_power_pack
```

ตัวอย่างแบบเล็กอยู่ใน [`tools/examples/shyne_power_pack`](tools/examples/shyne_power_pack). หากต้องการตัวอย่างที่กดใช้ได้ครบทั้ง Power Deck, Item Catalog และ Lua effect ให้ใช้ [`tools/examples/aether-showcase-pack`](tools/examples/aether-showcase-pack). เก็บ SVG เป็นต้นฉบับเพื่อให้ React/Figma/Illustrator แก้ต่อได้ แล้ว export PNG ลง `assets/icons/` ก่อนส่งแพ็กเข้าเกม.
