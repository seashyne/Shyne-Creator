# Arcane Power Pack

ตัวอย่าง Creator asset package สำหรับ Power Deck และ Item Catalog แบบ native UI

ก่อนติดตั้ง ให้ export `sources/icons/arc_bolt.svg` เป็น `assets/icons/arc_bolt.png`:

- PNG เท่านั้น, สูงสุด 256×256 px และ 128 KiB
- ชื่อและ path ต้องตรงกับ `shyne-package.json`
- `icon` ใน `skills/arc_bolt.json` และ `items/arcane_focus.json` อ้าง **asset id** (`arc_bolt`) ไม่ใช่ path หรือ URL

เมื่อต้องการทดสอบ ให้คัดลอกโฟลเดอร์นี้ไปไว้ที่:

```text
<Minecraft instance>/shyne-mods/arcane_power_pack/
```

Server จะตรวจ PNG และส่ง texture ที่ผ่านการตรวจไปยัง Shyne client พร้อม skill/item registry. ไฟล์ SVG เป็น source สำหรับ editor เท่านั้น และไม่ถูกอ่านในเกม.
