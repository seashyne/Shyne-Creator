# Action Wheel Showcase Avatar

ตัวอย่าง Avatar ครบวงจรสำหรับสาธิตระบบ **8-Slot Radial Action Wheel GUI**, **Custom Sound Player (.ogg)**, **First-Person Custom Arm**, และ **Bone Physics** บน Shyne Creator Standard 2.0

---

## 🌟 ฟีเจอร์ที่สาธิตในตัวอย่างนี้ (Key Features)

### 1. 🎛️ 8-Slot Radial Action Wheel GUI
- **ปุ่มลัด (Keybind)**: กดปุ่ม **`B`** ในเกมเพื่อเปิดเมนูวงล้อ Action Wheel
- **การควบคุม (Controls)**:
  - เลื่อนเมาส์ไปยังทิศทางของช่องเพื่อไฮไลต์ (คำนวณมุมองศา Radial Geometry 8 ทิศ)
  - คลิกซ้ายเพื่อสั่งรัน Action หรือสลับสถานะ Toggle
  - กดปุ่มตัวเลข **`1` - `8`** บนคีย์บอร์ดเพื่อเลือก Action ได้ทันที
  - เลื่อนลูกกลิ้งเมาส์ (Mouse Scroll Wheel) เพื่อสลับหน้า (Page Switching)
- **การปรับแต่งช่อง (Customization)**:
  - กำหนดไอคอนไอเทม Minecraft (`act:setItem("minecraft:dragon_head")`)
  - กำหนดสีและสีตอนชี้ (`act:setColor(r, g, b)`, `act:setHoverColor(r, g, b)`)
  - แสดงสถานะเปิด/ปิดแบบ Toggle (`[ON]` / `[OFF]`) ที่กลางวงล้อ

### 2. 🔊 Custom Sound Player (เล่นเสียง .ogg โดยตรง)
- เล่นไฟล์เสียงจากโฟลเดอร์ของ Avatar ได้ทันทีโดยไม่ต้องสร้าง Resource Pack แยก
- ไฟล์เสียงในตัวอย่าง:
  - `sounds/my_roar.ogg`: เสียงคำรามสำหรับท่า Roar Emote
  - `sounds/chime.ogg`: เสียงกระดิ่งคริสตัล
- สั่งเล่นในสคริปต์ได้ง่ายๆ:
  ```lua
  sounds:playSound("my_roar", 1.2, 1.0)
  ```

### 3. 🦾 First-Person Custom Arm (แขนมุมมองบุคคลที่หนึ่ง)
- เปลี่ยนแขนสตีฟ/อเล็กซ์เดิมในมุมมอง F5/First-Person ให้เป็นแขน 3D จริงจากโมเดล Blockbench
- ควบคุมผ่าน Lua API:
  ```lua
  avatar:setFirstPersonArm(true)
  ```

### 4. 🐰 Procedural Bone Physics (ฟิสิกส์หูและหาง)
- เปิดฟิสิกส์การแกว่งไหวแบบสปริงนุ่มนวลให้กับกระดูกหู (`LeftEar`, `RightEar`) และหาง (`Tail`)
- สามารถเปิด/ปิดได้แบบเรียลไทม์ผ่าน Action Wheel:
  ```lua
  left_ear:setPhysics(true)
  tail:setPhysics(true)
  ```

### 5. 📑 Multi-Page Navigation (ระบบหลายหน้า)
- สลับหน้าเมนู Action Wheel ไปยังหน้า Page 2 (FX & Cosmetics) เพื่อใช้งานคำสั่งเพิ่มเติม

---

## 📁 โครงสร้างไฟล์ (File Structure)

```text
action-wheel-showcase-avatar/
├── avatar.json        # กำหนด ID, ชื่อ, โมเดล, สคริปต์หลัก, และ Permissions (sound, particle, hud_render, camera)
├── model.bbmodel      # โมเดล Blockbench พร้อมกระดูก Head, Ears, Body, Tail, Arms และแอนิเมชัน
├── script.lua         # สคริปต์ Lua ตั้งค่า Action Wheel, ควบคุมเสียง, ฟิสิกส์, และเอฟเฟกต์
├── sounds/            # โฟลเดอร์เสียง Custom Audio
│   ├── my_roar.ogg    # เสียงคำราม
│   └── chime.ogg      # เสียงกระดิ่ง
└── README.md          # เอกสารคู่มือการใช้งาน
```

---

## 🚀 วิธีติดตั้งและทดสอบ (Installation & Usage)

1. คัดลอกโฟลเดอร์ `action-wheel-showcase-avatar` ไปไว้ในโฟลเดอร์อวตารของเกม:
   - Minecraft Client: `.minecraft/shyne-mods/avatars/action-wheel-showcase-avatar/`
2. เข้าเกม Minecraft เปิดเมนู **Avatar Library** (ปุ่ม `K` หรือผ่าน Shyne Menu)
3. เลือก **Action Wheel Showcase** แล้วกดสวมใส่ (Equip)
4. กดปุ่ม **`B`** เพื่อเปิด Action Wheel แล้วสนุกกับการทดลองฟังก์ชันต่างๆ ได้ทันที!
