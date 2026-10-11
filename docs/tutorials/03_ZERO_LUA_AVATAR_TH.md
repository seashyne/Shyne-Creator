# ✨ บทเรียนที่ 3: สร้าง Avatar แบบ Zero-Lua (ไม่ต้องเขียนโค้ดแม้แต่บรรทัดเดียว)

หนึ่งในจุดเด่นที่สุดของ **Shyne Creator** คือปรัชญา **Model-First & Zero-Lua**!
คุณสามารถสร้าง Avatar 3D เต็มรูปแบบที่มีทั้ง แอนิเมชันเดิน/วิ่ง/กะพริบตา, ระบบปากขยับตามเสียงพูด (Lip-sync), และการซ่อนตัวละครเดิม ได้โดยการเขียนคอนฟิกไฟล์ `avatar.json` เพียงไฟล์เดียว

---

## 📌 สารบัญบทเรียน
1. [โครงสร้างไฟล์ของ Zero-Lua Avatar](#1-โครงสร้างไฟล์ของ-zero-lua-avatar)
2. [โครงสร้างไฟล์ avatar.json ฉบับสมบูรณ์](#2-โครงสร้างไฟล์-avatarjson-ฉบับสมบูรณ์)
3. [การเปิดใช้งาน Auto Animations](#3-การเปิดใช้งาน-auto-animations)
4. [การเปิดใช้งานระบบตรวจจับเสียงพูด (Mouth Speech Tracking)](#4-การเปิดใช้งานระบบตรวจจับเสียงพูด-mouth-speech-tracking)
5. [การซ่อนตัวละครเดิม (Vanilla Model Masking) เพื่อไม่ให้โมเดลซ้อนทับกัน](#5-การซ่อนตัวละครเดิม-vanilla-model-masking)
6. [การใช้คำสั่ง One-Click Importer แปลงจาก Figura](#6-การใช้คำสั่ง-one-click-importer-แปลงจาก-figura)

---

## 1. โครงสร้างไฟล์ของ Zero-Lua Avatar

ในโฟลเดอร์ Avatar ของคุณ (เช่น `.minecraft/shyne-mods/avatars/cute_fox/`) ต้องมีไฟล์อย่างน้อยดังนี้:

```text
cute_fox/
├── avatar.json      (ไฟล์คอนฟิกหลัก)
└── model.bbmodel    (ไฟล์โมเดล 3D ที่สร้างจาก Blockbench)
```

---

## 2. โครงสร้างไฟล์ avatar.json ฉบับสมบูรณ์

สร้างไฟล์ข้อความชื่อ `avatar.json` แล้วบันทึกโค้ด JSON ต่อไปนี้:

```json
{
  "name": "Cute Fox Girl",
  "author": "YourName",
  "version": "1.0.0",
  "standard": "shyne",
  "model": "model.bbmodel",
  "auto_animations": true,
  "mouth_speech": {
    "enabled": true,
    "part": "mouth_open",
    "open_scale_y": 1.2,
    "closed_scale_y": 0.0
  },
  "vanilla_model": {
    "head": false,
    "body": false,
    "left_arm": false,
    "right_arm": false,
    "left_leg": false,
    "right_leg": false,
    "cape": true
  }
}
```

### คำอธิบายแต่ละฟิลด์:
* `"standard"`: กำหนดเป็น `"shyne"` (หรือปล่อยว่างไว้ก็ได้)
* `"model"`: ระบุชื่อไฟล์โมเดล Blockbench ของคุณ
* `"auto_animations"`: ตั้งเป็น `true` เพื่อให้ระบบ State Machine เล่นแอนิเมชันเดิน, วิ่ง, กะพริบตาให้อัตโนมัติ

---

## 3. การเปิดใช้งาน Auto Animations

เมื่อคุณตั้ง `"auto_animations": true`:
* ถ้าใน `model.bbmodel` มีอนิเมชันชื่อ `idle`, `walk`, `run`, `sneak`, `jump`, `swim` ระบบจะเปลี่ยนท่าตามความเร็วและการกระทำของผู้เล่นอัตโนมัติ
* ตัวละครจะมีการคำนวณการเบลนด์ระหว่างท่าเดินกับท่ายืนอย่างนุ่มนวล (Crossfade Blending)
* หากมีอนิเมชันชื่อ `blink` ระบบจะสุ่มกะพริบตาทุกๆ 3 ถึง 5 วินาที ทำให้โมเดลดูมีชีวิตชีวาเป็นธรรมชาติ

---

## 4. การเปิดใช้งานระบบตรวจจับเสียงพูด (Mouth Speech Tracking)

เมื่อใส่บล็อก `"mouth_speech"`:
```json
"mouth_speech": {
  "enabled": true,
  "part": "mouth_open",
  "open_scale_y": 1.0,
  "closed_scale_y": 0.0
}
```
* **เมื่อไม่มีเสียงพูด:** ชิ้นส่วนกระดูก `mouth_open` จะถูกย่อขนาดแกน Y เหลือ `0.0` (ปิดสนิทหรือซ่อนไว้)
* **เมื่อคุณพูดผ่านไมโครโฟน หรือส่งข้อความในแชท:** ชิ้นส่วน `mouth_open` จะขยายแกน Y ขึ้นมาตามความดังของเสียงโดยอัตโนมัติ

---

## 5. การซ่อนตัวละครเดิม (Vanilla Model Masking)

หาก Avatar ของคุณเป็นโมเดลที่ครอบคลุมร่างกายทั้งหมด คุณควรซ่อนชิ้นส่วนตัวละคร Minecraft วานิลลาเดิม เพื่อไม่ให้แขนหรือหัวของ Steve/Alex โผล่ทะลุออกมาซ้อนทับ:

```json
"vanilla_model": {
  "head": false,
  "body": false,
  "left_arm": false,
  "right_arm": false,
  "left_leg": false,
  "right_leg": false
}
```
* ค่า `false` หมายถึง **ซ่อนชิ้นส่วนนั้น**
* ค่า `true` หมายถึง **แสดงชิ้นส่วนนั้นไว้ตามเดิม** (เหมาะสำหรับโมเดลที่เป็นแค่ พร็อพเสริม เช่น โมเดลที่มีแค่ หู และ หาง เท่านั้น)

---

## 6. การใช้คำสั่ง One-Click Importer แปลงจาก Figura

หากคุณมีโฟลเดอร์โมเดลเดิมจากม็อด Figura และต้องการเปลี่ยนมาใช้บน Shyne Creator:
1. เข้าเกม Minecraft
2. พิมพ์คำสั่ง:
   ```text
   /shyne avatar import <ชื่อโฟลเดอร์>
   ```
3. ม็อดจะทำการ:
   * แปลงโครงสร้างโมเดลและไฟล์คอนฟิกให้เข้ากับ Shyne Standard อัตโนมัติ
   * สร้างไฟล์ `ATTRIBUTION.md` เพื่อบันทึกเครดิตผู้สร้างโมเดลเดิมไว้อย่างถูกต้อง
   * แสดง Avatar ในคลังพร้อมให้เลือกเล่นได้ทันที!
