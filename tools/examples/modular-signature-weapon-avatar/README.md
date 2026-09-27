# Modular Signature Weapon & Flight Avatar

ตัวอย่าง Avatar ระดับสูงที่รวบรวมฟีเจอร์ใหม่ครบวงจรบน **Shyne Creator Standard 2.0**:
- **โครงสร้างแบบแยกไฟล์โมดูล (Modular Lua with `require`)**
- **อาวุธประจำตัว 3D Signature Weapon** (สลับตำแหน่งฝักดาบและมืออัตโนมัติ)
- **ระบบฟิสิกส์การบิน (Flight Dash), กระโดดสูง (Super Jump), และลอยตัว (Hover/Anti-Gravity)**
- **มาตรวัดความเร็วแบบเรียลไทม์ (Speed Metrics in m/s)**
- **ตรวจจับการตายและการเกิดใหม่ (Death Detection)**
- **เครื่องมือดีบัคเติมมานา (Debug Tools)**

---

## 📁 โครงสร้างโฟลเดอร์แบบโมดูล (Modular Structure)

```text
modular-signature-weapon-avatar/
├── avatar.json                  # คอนฟิกหลัก & ขอ Permissions (sound, particle, hud_render, command)
├── model.bbmodel                # โมเดล 3D (Body, Head, Arms, SheathedBlade, SignatureBlade)
├── script.lua                   # 🚀 จุดเริ่มต้นหลัก (Main Entrypoint)
├── config.lua                   # ⚙️ การตั้งค่ามานา, ความเร็ว, และสีธีม
├── skills/
│   ├── jump.lua                 # 🦘 Super Jump (กระโดดสูง) & Hover (ลอยตัวกลางอากาศ)
│   ├── flight.lua               # 🦅 Flight Dash (พุ่งบินตามแนวสายตา)
│   └── meditation.lua           # 🧘 Meditation (กดค้างชาร์จสมาธิเพิ่มมานา)
├── weapons/
│   └── signature_blade.lua      # ⚔️ Signature Weapon (ชักดาบ, ท่าฟัน, คลื่นดาบ, ซิงก์กับไอเทมในมือ)
├── ui/
│   └── hud.lua                  # 📊 HUD (หลอดมานา, ความเร็ว m/s, เลือด, สถานะอาวุธ)
└── debug/
    └── debug_tools.lua          # 🛠️ เครื่องมือดีบัค (เติมมานา, สลับอมตะ, ตรวจจับการตาย)
```

---

## 🎮 ปุ่มควบคุม (Controls)

| ปุ่มกด | สกิล / ความสามารถ | รายละเอียด |
| :---: | :--- | :--- |
| **`[X]`** | **Super Jump** | ดีดตัวพุ่งขึ้นฟ้าสูง 8-10 บล็อกทันที |
| **`[Z]`** | **Toggle Hover** | เปิด/ปิดโหมดลอยตัวเคว้งกลางอากาศ (ต้านแรงโน้มถ่วง Anti-Gravity) |
| **`[R]`** | **Flight Dash** | พุ่งทะยานไปข้างหน้าในอากาศ 3 มิติตามแนวสายตาที่หันมอง |
| **`[G]`** | **Draw/Sheath Blade** | ชักอาวุธประจำตัวออกจากฝักที่หลังมาถือในมือ (หรือเก็บกลับ) |
| **`[F]`** | **Blade Wave Attack** | ปล่อยคลื่นดาบฟาดฟันเป็นรูปพระจันทร์เสี้ยว พุ่งทะลวงศัตรูข้างหน้า |
| **`[M]`** (กดค้าง) | **Meditate** | นั่งสมาธิรวบรวมพลัง ฟื้นฟูมานาอย่างรวดเร็ว (60 MP/s) |
| **`[H]`** | **Debug: Refill Mana** | เติมมานาเต็ม 100 ทันทีสำหรับการทดสอบ |
| **`[B]`** | **Action Wheel** | เปิดเมนูวงกลม 8 ช่องเพื่อคลิกสั่งใช้งานได้ด้วยเมาส์ |

---

## 📦 รองรับไฟล์ `.zip` โดยตรง

Shyne Core รองรับการบีบอัด Avatar เป็นไฟล์ `.zip` ได้ทันที:
1. บีบอัดโฟลเดอร์นี้เป็น `modular-signature-weapon-avatar.zip`
2. นำไฟล์ `.zip` ไปวางใน `.minecraft/shyne-mods/avatars/`
3. Shyne Core จะแตกไฟล์และโหลดเข้าเมนู Avatar Manager (ปุ่ม `K`) ให้โดยอัตโนมัติ!
