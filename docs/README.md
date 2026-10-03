# Shyne Creator Documentation (ศูนย์รวมเอกสาร Shyne Creator)

ยินดีต้อนรับสู่ศูนย์รวมเอกสารทางการของ **Shyne Creator MultiLoader** (Fabric + NeoForge บน Minecraft 26.3 / Java 25)

โครงสร้างเอกสารถูกจัดระเบียบแยกตามหมวดหมู่เพื่อความสะดวกในการค้นหาและพัฒนา ดังนี้:

---

## 📁 โครงสร้างโฟลเดอร์เอกสาร

```
docs/
├── api/                  # คู่มือ API และ Compatibility Matrix
├── guides/               # คู่มือการเริ่มต้นสำหรับผู้เล่นและ Creator
├── standards/            # ข้อกำหนดมาตรฐานของ Package, Manifest และ SDK
├── architecture/         # สถาปัตยกรรมระบบ, เอนจินเรนเดอร์ และ Performance
└── legal/                # ความปลอดภัย, สิทธิ์ และเครื่องหมายการค้า
```

---

## 📚 สารบัญและลิงก์ด่วน (Quick Navigation)

### 1. 🚀 คู่มือเริ่มต้น (Guides)
- [เริ่มใช้ Shyne ใน 1 นาที (สำหรับผู้เล่น)](guides/PLAYER_QUICKSTART_TH.md) — แนะนำการติดตั้งและการใช้งาน Avatar เบื้องต้น
- [คู่มือสร้าง Avatar แรก (สำหรับ Creator)](guides/CREATOR_QUICKSTART_TH.md) — ขั้นตอนการปั้นโมเดลและส่งออกอวาตาร์
- [Blockbench Animation Standard](guides/BLOCKBENCH_ANIMATION_STANDARD.md) — มาตรฐานการตั้งค่ากระดูก อนิเมชั่น และ easing ใน Blockbench
- [Public Share & Cloud Export](guides/PUBLIC_SHARE.md) — การแชร์และส่งออก Avatar สาธารณะ

### 2. ⚡ เอกสาร API (API References)
- [**API Compatibility Matrix (Figura & Shyne)**](api/API_COMPATIBILITY_MATRIX.md) — ตารางตรวจสอบสถานะความเข้ากันได้ของ API ทุกตัว (สถานะ Support 100%)
- [Shyne Lua API Standard 2.0](api/SHYNE_LUA_API_TH.md) — เอกสารอ้างอิง Lua API ตัวเต็ม (Client, Renderer, Camera, Events, Math, Vectors)
- [Complete Unified API Reference](api/SHYNE_API_COMPLETE_TH.md) — รวมรวม API ทั้งหมดในที่เดียวพร้อมตัวอย่างโค้ด
- [API Contracts & Capabilities](api/API_CONTRACTS_TH.md) — รายละเอียดสัญญาความสามารถและ Permission
- [Gameplay & Server Authority API](api/SHYNE_GAMEPLAY_API_TH.md) — การเขียนสคริปต์สกิล มานา คูลดาวน์ และ projectile ฝั่ง Server
- [Custom Render API & Script Canvas](api/CUSTOM_RENDER_API_TH.md) — การวาด 2D/3D Canvas UI, HUD, Glyph และ Bone binding
- [Native Rig & Secondary Physics API](api/RIG_API_TH.md) — ระบบกระดูก สปริง ฟิสิกส์เส้นผม/หาง และชุดเกราะคอสเมติก
- [Cloud REST API](api/CLOUD_API.md) — รายละเอียด Endpoint และ Protocol สำหรับเชื่อมต่อ Shyne Cloud

### 3. 📐 ข้อกำหนดมาตรฐาน (Standards)
- [Shyne Avatar Standard 2.0](standards/SHYNE_STANDARD_2_TH.md) — มาตรฐาน avatar.json โครงสร้างโมเดล และ Zero-Lua
- [Power & Asset Package 1.0](standards/POWER_ASSET_PACKAGE_TH.md) — ข้อกำหนด Content Pack สำหรับพลัง ไอเท็ม และเสียง
- [Creator SDK & Tooling Specification](standards/CREATOR_SDK_TH.md) — เครื่องมือ CLI Linter และ Template generator

### 4. 🏗️ สถาปัตยกรรมระบบ (Architecture)
- [ภาพรวมสถาปัตยกรรม MultiLoader](architecture/ARCHITECTURE_TH.md) — การแยกเลเยอร์ Common, Fabric, NeoForge และ Cloud
- [Avatar Runtime Lifecycle](architecture/AVATAR_SYSTEM.md) — วงจรชีวิตของอวาตาร์ การโหลด การแคช และการสลับชุด
- [Multiplayer Testing Guide](architecture/MULTIPLAYER_TESTING.md) — แนวทางการทดสอบในสภาพแวดล้อม Multiplayer และเซิร์ฟเวอร์จริง
- [Performance Standard & Quotas](architecture/PERFORMANCE_STANDARD_TH.md) — โควตา GPU, บัジェットเรนเดอร์ และการป้องกัน Memory Leak
- [Avatar Cloud Architecture](architecture/AVATAR_CLOUD.md) — สถาปัตยกรรมการจัดเก็บและกระจายไฟล์อวาตาร์

### 5. ⚖️ นโยบายและกฎหมาย (Legal & Community)
- [Security Policy](legal/SECURITY.md) — นโยบายความปลอดภัยและการรายงานช่องโหว่
- [Trademarks](legal/TRADEMARKS.md) — นโยบายการใช้เครื่องหมายการค้า Shyne
- [Contributing Guide](legal/CONTRIBUTING.md) — แนวทางการร่วมพัฒนาโค้ด
- [Asset Provenance](legal/ASSET_PROVENANCE.md) — ที่มาของทรัพยากรและลิขสิทธิ์
- [Third-Party Notices](legal/THIRD_PARTY_NOTICES.md) — ประกาศลิขสิทธิ์ซอฟต์แวร์ภายนอก (Luaj, Gson, etc.)
