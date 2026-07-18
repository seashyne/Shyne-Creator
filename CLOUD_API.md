# Shyne Private Avatar Cloud API v1.1

Base URL: `https://shyne-avatar-cloud.jirayut-wh.workers.dev`

API นี้เป็นพื้นที่เก็บ Avatar ส่วนตัว ไม่ใช่ API สำหรับเผยแพร่หรือดาวน์โหลด Avatar ของผู้อื่น ทุก endpoint ที่อ่านหรือแก้ข้อมูล Avatar ต้องใช้ `Authorization: Bearer <token>` ของเจ้าของ

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
- `GET /v1/avatars` — ปิดถาวรและตอบ `410 public_library_removed`

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
