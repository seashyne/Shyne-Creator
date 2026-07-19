# Shyne Avatar Cloud API v2.1

Base URL: `https://shyne-avatar-cloud.jirayut-wh.workers.dev`

API แยก Private Backup ออกจาก Public Share ชัดเจน ทุก endpoint ที่อ่านหรือแก้ไฟล์ต้องใช้ `Authorization: Bearer <token>` ส่วน metadata ใน Discover อ่านได้โดยไม่รับไฟล์ต้นฉบับหรือกุญแจถอดรหัส

## Authentication

1. `POST /v1/auth/challenges` — body `{ "username": "Player" }`
2. Client เรียก Minecraft session service `joinServer` โดยตรง
3. `POST /v1/auth/verify` — body `{ "challenge_id": "..." }`
4. Backend ตรวจ `hasJoined` แล้วตอบ `{ token, expires_at, account }`
5. `DELETE /v1/auth/session` เพิกถอน token

Minecraft access token จะไม่ถูกส่งไป Shyne Cloud

## Private storage

- `GET /v1/status` — สถานะบริการ
- `GET /v1/me/avatars?q=<text>&limit=30&offset=0` — รายการของบัญชีปัจจุบัน
- `GET /v1/avatars/<avatar_id>` — manifest ของบัญชีปัจจุบัน
- `PATCH /v1/avatars/<avatar_id>` — เปลี่ยน name หรือ description
- `DELETE /v1/avatars/<avatar_id>` — ลบข้อมูลสำรอง
- `GET /v1/avatars` — รายการ Public Avatar สำหรับ Discover

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

ข้อจำกัด: 512 KiB ต่อก้อน, 64 MiB ต่อ Avatar, 256 ไฟล์ และต้องมี `avatar.json`

## Restore protocol

1. อ่าน manifest จาก `GET /v1/avatars/<avatar_id>` ด้วย session ของเจ้าของ
2. ตรวจ cache ด้วย SHA-256
3. รับก้อนที่ขาดจาก `GET /v1/chunks/<sha256>?avatar=<avatar_id>` ด้วย session เดิม
4. ตรวจ size และ SHA-256 ทุกก้อน
5. ประกอบใน temporary folder, validate แล้วจึงสลับเข้าโฟลเดอร์ใช้งาน

Worker ตรวจว่าบัญชีเป็นเจ้าของ Avatar และ hash อยู่ใน manifest ปัจจุบันก่อนอ่าน R2 ทุกครั้ง

## Public Share

- `GET /v1/discover` — ค้นหา metadata ของ Public Avatar
- `GET /v1/shares/<share_id>` — อ่าน metadata, ผู้สร้าง, license, version และ permission
- `PUT /v1/avatars/<avatar_id>/publication` — Publish ZIP ที่ผ่าน validator เป็น `.sc v2`
- `DELETE /v1/avatars/<avatar_id>/publication` — Revoke และหยุดออก lease ใหม่
- `GET /v1/shares/<share_id>/package` — รับแพ็กเกจ `.sc` ที่เข้ารหัสและเซ็นกำกับ
- `POST /v1/shares/<share_id>/lease` — รับ lease 15 นาทีและ data key ที่ห่อด้วย X25519 สำหรับเครื่องนั้น
- `GET /v1/shares/<share_id>/versions` — ประวัติเวอร์ชันและสถานะ revoke
- `POST /v1/shares/<share_id>/reports` — Report Public Avatar
- `PUT`/`DELETE /v1/creators/<creator_id>/block` — Block หรือปลด Block ผู้สร้าง

Permission contract รุ่น `public_permissions_v2` รองรับ `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render` และ `world_render` รายชื่อจริงอ่านได้จาก `public_avatar_permissions` ใน `GET /v1/status` เพื่อไม่ให้ client กับ backend ใช้ whitelist คนละชุด

Public Share ไม่เปิดเผย ZIP ต้นฉบับหรือกุญแจดิบ ตัว client ตรวจลายเซ็น Ed25519, package hash, lease, device identity และ permission ที่ผู้ใช้อนุมัติก่อนเริ่ม Lua runtime
