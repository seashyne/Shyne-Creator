# Custom Skill & Mana HUD Showcase Avatar

ตัวอย่าง Avatar ครบวงจรสำหรับสาธิตระบบ **Custom Skills (ระบบสกิลเฉพาะตัว)**, **Dynamic Keybinds (ปุ่มลัดในเมนู Controls)**, **Responsive Mana HUD (หลอดพลังงานและคูลดาวน์บนหน้าจอ)**, **3D Magic Barrier (โล่เวทมนตร์หมุนรอบตัว)**, และการผสาน **Action Wheel** บน Shyne Creator Standard 2.0

---

## 🌟 ฟีเจอร์เด่นในตัวอย่างนี้ (Key Features)

### 1. ⌨️ Dynamic Avatar Keybinds (ปุ่มกดเฉพาะตัวละคร)
- ลงทะเบียนปุ่มกดเฉพาะตัวละครผ่าน `input.bind()`
- **จุดเด่น**: ปุ่มกดเหล่านี้จะ**ปรากฏในหน้าต่าง `Options > Controls > Key Binds` ของ Minecraft อัตโนมัติ** ในหมวดหมู่ชื่อ Avatar!
- ผู้เล่นสามารถเปลี่ยนปุ่ม (Remap) เป็นปุ่มอื่น หรือปุ่มเมาส์ (Mouse Button 4/5) ได้ตามสะดวก โดยที่ระบบจะจำค่าไว้ถาวรใน `saved_keys.json`
- ปุ่มเริ่มต้นในตัวอย่าง:
  - **`[R]`**: Gale Dash (สกิลพุ่งตัว)
  - **`[V]`**: Dragon Burst (สกิลท่าไม้ตายระเบิดคลื่นพลัง)
  - **`[C]`**: Magic Barrier (สกิลกางโล่บาเรีย 3 วินาที)

### 2. 📊 Responsive 2D Mana & Energy HUD (หลอดพลังบนหน้าจอ)
- ใช้ระบบเรนเดอร์ 2D HUD ดั้งเดิมของ Shyne (`render.screen`, `render.rect`, `render.text`)
- คำนวณตำแหน่งกึ่งกลางหน้าจอแบบ Responsive โดยอัตโนมัติ (ไม่เลื่อนหลุดตำแหน่งแม้เปลี่ยน Resolution จอ หรือปรับ GUI Scale)
- **องค์ประกอบของ HUD**:
  - **กรอบหลัง (Background)**: สี่เหลี่ยมสีดำโปร่งแสงสไตล์ Modern RPG
  - **หลอดพลัง (Mana Fill)**: สีฟ้าครามเรืองแสง (Cyan) ยืดหดตามเปอร์เซ็นต์มานาปัจจุบัน
  - **ตัวเลขแสดงค่า (Mana Text)**: แสดง `MP: 85 / 100` ชัดเจน
  - **สถานะสกิลและคูลดาวน์ (Skill Badges)**: แสดงสถานะพร้อมใช้งานหรือเวลานับถอยหลัง เช่น `[R] Dash | [V] 3.5s | [C] Barrier (2.1s)`
  - **ระบบเตือนเมื่อมานาไม่พอ**: กะพริบข้อความเตือนสีแดง `! NOT ENOUGH MANA !` พร้อมเสียงเอฟเฟกต์

### 3. ✨ สกิลและเอฟเฟกต์ (VFX, SFX & Animations)

| สกิล | ปุ่มกด | ค่าร่าย / คูลดาวน์ | เอฟเฟกต์ภาพ (VFX) | เอฟเฟกต์เสียง (SFX) | ท่าทาง (Animation) |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **Gale Dash** | `[R]` | 20 MP / 1.5s | ละอองพายุ `cloud` ทะยานตามทิศทางสายตา | `enderman.teleport` (pitch 1.4) | `cast_dash` |
| **Dragon Burst** | `[V]` | 50 MP / 6.0s | วงแหวนคลื่นพลัง `dust` สีทอง 360 องศา | `warden.sonic_boom` (pitch 1.1) | `cast_ultimate` |
| **Magic Barrier** | `[C]` | 35 MP / 5.0s | อัญมณีเวทมนตร์ 3D หมุนรอบตัว + ละออง `dust` สีฟ้า | `beacon.activate` (pitch 1.3) | `cast_shield` |

### 4. 🛡️ 3D Magic Shield & Bone Transforms (โล่เวทมนตร์ 3D)
- ในโมเดล Blockbench (`model.bbmodel`) มีกระดูกชิ้นส่วน `MagicShield` ประกอบด้วยแท่งผลึกพลัง 4 ทิศรอบตัว
- ในสภาวะปกติจะถูกซ่อนไว้ (`magic_shield:setVisible(false)`)
- เมื่อกดใช้สกิล `[C]` โล่จะปรากฏขึ้นมา และหมุนรอบตัวผู้เล่น 360 องศาเป็นเวลา 3 วินาที

### 5. 🎛️ ผสานรวม Action Wheel (กดปุ่ม `B`)
- นอกจากปุ่มลัด [R], [V], [C] บนคีย์บอร์ดแล้ว ยังสามารถกดปุ่ม **`B`** เพื่อเปิดวงล้อ Action Wheel แล้วคลิกใช้สกิลด้วยเมาส์ได้เช่นกัน
- มีปุ่ม Toggle ในช่องที่ 8 เพื่อเปิด/ปิดการแสดงผลหน้าต่าง Mana HUD ได้ตามต้องการ

---

## 📁 โครงสร้างไฟล์ (File Structure)

```text
custom-skill-hud-avatar/
├── avatar.json        # กำหนด ID, ชื่อ, โมเดล, สคริปต์หลัก, และ Permissions (sound, particle, hud_render, command)
├── model.bbmodel      # โมเดล Blockbench (Head, Body, MagicShield, Arms) พร้อม Animations (idle, cast_dash, cast_ultimate, cast_shield)
├── script.lua         # สคริปต์ Lua ควบคุมระบบมานา, คูลดาวน์, วาด HUD, ปุ่มลัด, และ Action Wheel
├── README_TH.md       # สรุปภาษาไทยสั้น
└── README.md          # เอกสารอธิบายฉบับสมบูรณ์
```

---

## 🚀 วิธีนำไปทดลองใช้งาน (How to Test)

1. คัดลอกโฟลเดอร์ `custom-skill-hud-avatar` ไปไว้ที่:
   ```text
   .minecraft/shyne-mods/avatars/custom-skill-hud-avatar
   ```
2. เข้าเกม Minecraft ที่ติดตั้ง **Shyne Core** (Fabric หรือ NeoForge)
3. กดปุ่ม **`K`** เพื่อเปิดเมนู Shyne Avatar Manager แล้วกดเลือกใช้ Avatar **"Custom Skill & Mana HUD Showcase"**
4. ลองกดปุ่มทดสอบ:
   - กด **`R`**: ใช้งาน Gale Dash
   - กด **`V`**: ใช้งาน Dragon Burst
   - กด **`C`**: กาง Magic Barrier
   - กด **`B`**: เปิดเมนูวงล้อ Action Wheel
5. เข้าเมนู **`Options > Controls > Key Binds`** แล้วเลื่อนหาหมวดหมู่ Avatar เพื่อทดลองเปลี่ยนปุ่มกดได้ทันที!
