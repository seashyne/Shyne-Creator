# Shyne Public ZIP Share

Public Share แยกจาก Private Cloud Backup โฟลเดอร์พัฒนาและไฟล์ที่ Restore ยังคงเป็น Avatar folder ตามปกติ ส่วนสำเนาที่ Publish จะเป็น ZIP มาตรฐานซึ่ง Shyne Creator สร้างและ Backend ตรวจสอบก่อนเผยแพร่

## สัญญา Public ZIP 2.2

- ส่งผ่าน HTTPS และต้องมี Minecraft sign-in ก่อนดาวน์โหลด package
- Backend จำกัดขนาด ตรวจ ZIP directory, local header, path traversal, symlink, จำนวนไฟล์ และขนาดที่ประกาศ
- Backend คำนวณ SHA-256 หลังรับ ZIP และเก็บ hash ไว้กับ metadata
- Client คำนวณ SHA-256 ซ้ำและต้องตรงกับ metadata ก่อนแตกไฟล์
- Client แตกไฟล์ใน temporary runtime directory โดยจำกัดจำนวนไฟล์และขนาดรวมอีกชั้น
- Permission ใช้ manifest ของ package และการอนุมัติผูกกับ package hash เมื่อ package เปลี่ยนต้องตรวจสิทธิ์ใหม่
- `particle` และ `sound` เป็นความสามารถความเสี่ยงต่ำ ส่วน `camera`, `microphone`, `command`, `hud_render` และ `world_render` ต้องให้ผู้ใช้ตัดสินใจก่อนเปิดใช้
- Render task ยังอยู่ภายใต้งบจำนวน task/line และการ cull ของ Shyne runtime

Public Share 2.2 ไม่ใช้ container `.sc v1` หรือ `.sc v2`, ไม่เข้ารหัสด้วย data key และไม่มี lease หรือ device key แบบกำหนดเอง ไฟล์ที่ผู้เล่นดาวน์โหลดจึงเป็น ZIP ที่อ่านได้ตามปกติ ผู้สร้างควร Publish เฉพาะ asset และ source ที่ตนมีสิทธิ์แจกให้ผู้รับ

## Publish และ Revoke

- `PUT /v1/avatars/{avatarId}/publication` — อัปโหลด ZIP พร้อม license และ permission manifest
- `DELETE /v1/avatars/{avatarId}/publication` — ลบ publication และหยุดการดาวน์โหลดครั้งใหม่
- `GET /v1/discover` — ดู metadata ของรายการที่ Publish
- `GET /v1/shares/{shareId}` — ดู metadata และ package hash
- `GET /v1/shares/{shareId}/package` — ดาวน์โหลด ZIP หลัง Sign in

การ Revoke ไม่กระทบ Private Backup แต่ไม่สามารถลบสำเนาที่ดาวน์โหลดหรือติดตั้งไปแล้วจากเครื่องผู้เล่นได้ ระบบจึงไม่โฆษณา revocation แบบบังคับหยุด runtime ระยะไกล

## สิ่งที่ระบบป้องกันได้

- ZIP เสียหายหรือ hash ไม่ตรง
- path traversal, absolute path, symlink และ entry ที่เกิน policy
- package ที่ไม่มี `avatar.json`
- capability ที่ไม่ได้ประกาศหรือผู้ใช้ไม่อนุมัติ
- การดาวน์โหลดใหม่หลัง publication ถูก Revoke

## สิ่งที่ระบบไม่รับประกัน

- การป้องกันผู้รับคัดลอกหรือแก้ไฟล์หลังดาวน์โหลด
- DRM หรือการลบไฟล์จากเครื่องผู้เล่นระยะไกล
- ความปลอดภัยของ Lua ที่ขอ permission อันตรายโดยอัตโนมัติ ผู้ใช้ยังต้องตรวจและอนุมัติเอง

รายละเอียด endpoint และรูปแบบ header อยู่ใน [CLOUD_API.md](CLOUD_API.md)
