# Shyne Avatar Cloud API v2.2

Base URL: `https://shyne-avatar-cloud.jirayut-wh.workers.dev`

API แยก Private Backup ออกจาก Public Share ชัดเจน ทุก endpoint ที่อ่านหรือแก้ไฟล์ต้องใช้ `Authorization: Bearer <token>` ส่วน metadata ใน Discover อ่านได้โดยไม่ต้อง Sign in

## Authentication

1. `POST /v1/auth/challenges` — body `{ "username": "Player" }`
2. Client เรียก Minecraft session service `joinServer` โดยตรง
3. `POST /v1/auth/verify` — body `{ "challenge_id": "..." }`
4. Backend ตรวจ `hasJoined` แล้วตอบ `{ token, expires_at, account }`
5. `DELETE /v1/auth/session` เพิกถอน token

Minecraft access token จะไม่ถูกส่งไป Shyne Cloud

## สถานะบริการ

- `GET /healthz` — ตรวจว่า Worker ตอบสนอง
- `GET /readyz` — ตรวจความพร้อมของบริการ
- `GET /v1/status` — version, capability, permission contract และข้อจำกัดของบริการ

Backend 2.2 ประกาศ `public_zip_v1` และ `public_permissions_v2` ไม่มี capability ของ `.sc` หรือ lease

## Private storage

- `GET /v1/me` — บัญชีปัจจุบัน
- `GET /v1/me/avatars?q=<text>&limit=30&offset=0` — รายการสำรองของบัญชีปัจจุบัน
- `GET /v1/avatars/<avatar_id>` — manifest ของบัญชีปัจจุบัน
- `PATCH /v1/avatars/<avatar_id>` — เปลี่ยน name หรือ description
- `DELETE /v1/avatars/<avatar_id>` — ลบข้อมูลสำรอง

## Backup protocol

`POST /v1/avatars` พร้อม metadata และ manifest:

```json
{
  "id": "my.avatar",
  "name": "My Avatar",
  "version": "1.0.0",
  "description": "...",
  "manifest": {
    "format": 1,
    "files": [{
      "path": "avatar.json",
      "size": 120,
      "chunks": [{ "hash": "<sha256>", "size": 120 }]
    }]
  }
}
```

จากนั้นส่งก้อนด้วย `PUT /v1/uploads/<upload_id>/chunks/<sha256>` และจบด้วย `POST /v1/uploads/<upload_id>/complete`

ข้อจำกัด Private Backup: 512 KiB ต่อก้อน, 64 MiB ต่อ Avatar, 256 ไฟล์ และต้องมี `avatar.json`

## Restore protocol

1. อ่าน manifest จาก `GET /v1/avatars/<avatar_id>` ด้วย session ของเจ้าของ
2. ตรวจ cache ด้วย SHA-256
3. รับก้อนที่ขาดจาก `GET /v1/chunks/<sha256>?avatar=<avatar_id>` ด้วย session เดิม
4. ตรวจ size และ SHA-256 ทุกก้อน
5. ประกอบใน temporary folder, validate แล้วจึงสลับเข้าโฟลเดอร์ใช้งาน

Worker ตรวจว่าบัญชีเป็นเจ้าของ Avatar และ hash อยู่ใน manifest ปัจจุบันก่อนอ่าน R2 ทุกครั้ง Chunk ถูกแยก namespace ตามเจ้าของเพื่อไม่ให้ hash เดียวกันข้ามบัญชี

## Public Share ZIP

- `GET /v1/discover?q=<text>&limit=30&offset=0` — ค้นหา metadata ของ Public Avatar
- `GET /v1/avatars` — alias สำหรับรายการ Discover
- `GET /v1/shares/<share_id>` — อ่าน metadata, owner, license, version, package hash, ขนาด และ permission
- `PUT /v1/avatars/<avatar_id>/publication` — Publish ZIP ของ Avatar ใน Private Backup
- `DELETE /v1/avatars/<avatar_id>/publication` — Revoke และลบ Public ZIP
- `GET /v1/shares/<share_id>/package` — ดาวน์โหลด Public ZIP หลัง Sign in

ตัวอย่าง header สำหรับ Publish:

```http
Content-Type: application/vnd.shyne.avatar+zip
X-Shyne-Permissions: particle,sound,hud_render
X-Shyne-License: CC-BY-4.0
```

Backend รับ Public ZIP สูงสุด 16 MiB ตรวจ ZIP policy และ SHA-256 แล้วตอบ `package_format: "zip-v1"` พร้อม `package_hash` การดาวน์โหลดตอบ `application/vnd.shyne.avatar+zip` และ `X-Shyne-Package-Hash`; Client ต้องเทียบ hash ก่อนติดตั้ง

License ที่รองรับคือ `PERSONAL`, `CC0`, `CC-BY-4.0`, `CC-BY-NC-4.0` และ `CUSTOM`

Permission contract `public_permissions_v2` รองรับ `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render` และ `world_render` รายชื่อจริงอ่านได้จาก `public_avatar_permissions` ใน `GET /v1/status`

Public Share 2.2 ไม่มี `.sc v1`, `.sc v2`, endpoint `/lease`, device key หรือ data key การ Revoke หยุดการดาวน์โหลดครั้งใหม่และลบ object บน Cloud แต่ไม่ลบสำเนาที่ดาวน์โหลดไปแล้วจากเครื่องผู้เล่น

## Error response

ข้อผิดพลาดตอบเป็น JSON พร้อม HTTP status ที่ตรงกับสาเหตุ:

```json
{
  "error": "Public Avatar package is unavailable or was revoked",
  "code": "package_not_found"
}
```
