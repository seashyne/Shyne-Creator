# Multiplayer test matrix

Shyne provides isolated Loom run profiles for one dedicated server and two clients:

```powershell
.\gradlew.bat runServer
.\gradlew.bat runClient
.\gradlew.bat runClientB
```

The profiles use `run/server`, `run/client-a`, and `run/client-b`. Accept the Minecraft EULA in `run/server/eula.txt` before starting the dedicated server. Each command should run in its own terminal.

The generated development server uses `online-mode=false` so the two isolated development usernames can connect locally. Never copy that setting to a public or production server. Client A and Client B are preconfigured to connect to `127.0.0.1:25565`.

## Required release checks

| Scenario | Expected result |
|---|---|
| Client A and B use the same Shyne version | Server logs `Protocol ready` for both players. |
| Client completes handshake | Client log lists `gameplay.server_authoritative`, `content.registry_sync`, `avatar.peer_snapshot_v2`, `avatar.snapshot_request_v1`, `player.tab_status_v1`, and `avatar.bone_physics_v1`. |
| Client has a different protocol or mod version | Connection closes with both server and client versions in the message. |
| Client has no Shyne Creator | Connection closes after the five-second handshake timeout. |
| Gameplay pack exists only in `run/server/shyne-mods` | Both clients receive skills, profiles, weapons, models, and textures. Server remains authoritative for world changes. |
| Avatar exists only in Client A's `shyne-mods/avatars` | Client B receives the model, primary PNG texture, animation, visible parts, and approved synchronized variables. |
| Client B joins after Client A activated an avatar | Client B receives the server's retained full avatar snapshot. |
| Client A reconnects | A fresh full snapshot is sent; later snapshots contain state deltas only. |
| Oversized or malformed avatar payload | Server ignores it without crashing or broadcasting it. |
| Rapid avatar updates | Server rate limits snapshots and synchronized variables. |
| Client A moves an ear/tail physics preset | Client B receives pose near 10 Hz and renders a smooth 100 ms interpolation without snapping; Client A never rewinds from its own server echo. |
| Bone/group names contain spaces or Thai text | Snapshot is accepted and the exact canonical path controls the same part on Client B. |
| Lua attempts NaN/Infinity in a transform | Local state is sanitized and the server never broadcasts a non-finite pose. |
| Client presses a skill key while its server profile slot is empty | Server ignores the input; a client-supplied skill id is never executed. |
| Client sends Avatar variables for a different Avatar id | Server ignores the variables instead of broadcasting them. |

## Evidence to capture

Keep these files when testing a release candidate:

- `run/server/logs/latest.log`
- `run/client-a/logs/latest.log`
- `run/client-b/logs/latest.log`
- screenshots from Client B showing Client A's texture and animation

Search the logs for `ShyneNetwork`, `ERROR`, `Exception`, `Rejected`, and `disconnect`.

หลัง Client A/B เข้าโลกแล้ว ตรวจหลักฐานอัตโนมัติด้วย:

```powershell
.\tools\verify_multiplayer_logs.ps1
```

## Cloud Avatar release checks

| Scenario | Expected result |
|---|---|
| Online Minecraft account signs in | Cloud account UUID/name ตรงกับ session ที่ Mojang ยืนยัน โดย Shyne Cloud ไม่ได้รับ Minecraft access token |
| Offline development user signs in | ปฏิเสธอย่างปลอดภัย; gameplay multiplayer ยังทำงานต่อ |
| Owner publishes active Avatar | Backend ตรวจ ZIP policy/SHA-256, D1 บันทึก publication/permissions และ R2 มี Public ZIP หนึ่ง object |
| Another account publishes the same public Avatar id | Backend ตอบ `409 public_id_taken`; Private Backup ที่ใช้ id เดียวกันคนละบัญชียังทำได้ |
| Player downloads a Public Avatar | ต้อง Sign in, Content-Type เป็น Shyne Avatar ZIP และ Client ตรวจ package hash ก่อนติดตั้ง |
| Corrupt Public ZIP or mismatched hash | Client ปฏิเสธก่อนแตกไฟล์และไม่เปิด Lua runtime |
| Interrupted install | Avatar เดิมถูกเก็บเป็น backup และกู้คืนหากสลับโฟลเดอร์ไม่สำเร็จ |
| Public/private | Discover เห็นเฉพาะ public; private metadata/manifest เห็นเฉพาะ owner |
| Owner republishes with changed permissions | package hash ใหม่แสดงหลัง refresh และผู้ใช้ต้องตรวจ permission ของ package รุ่นใหม่ |
| Owner revokes a Public Avatar | ลบ Public ZIP และปฏิเสธ download ใหม่ โดย Private Backup และสำเนาที่ติดตั้งไปแล้วไม่ถูกลบ |
| Two owners have an identical private chunk hash | R2 แยก namespace ตาม owner และบัญชีหนึ่งดาวน์โหลด chunk ของอีกบัญชีไม่ได้ |
| Cloud is unavailable | Local Avatar และ Minecraft server gameplay ยังใช้ได้ |

## Known scope

Protocol ปัจจุบันคือ 14 และเพิ่ม revision ACK สำหรับ snapshot/full model/clear พร้อมส่งเวลา animation เป็น elapsed age เพื่อไม่อิงนาฬิกาของแต่ละเครื่อง Remote Avatar สมัครรับเป็นรายผู้เล่น ดังนั้น Block จะหยุดข้อมูลของผู้เล่นนั้นตั้งแต่ Server ขณะที่ model upload และ pose update ใช้งบ rate limit แยกกัน `physicsPreset` ของ bone เดินทางพร้อม model snapshot, pose จาก physics/Lua ส่งได้สูงสุดประมาณ 10 Hz และ client ปลายทาง interpolate 100 ms

แพ็กเก็ต Avatar JSON มีเพดาน 2 MiB และ client จะ preflight ก่อนส่งโดยไม่ล้าง dirty revision เมื่อใหญ่เกินกำหนด PNG ที่รับจาก peer ต้องมี IHDR ถูกต้อง ขนาดด้านละไม่เกิน 4096 px และงบรวมไม่เกิน 16,777,216 pixels ต่อ model ดังนั้นแพ็กใหญ่มากต้องลด texture หรือใช้ระบบ Cloud manifest/cache แทนการฝัง asset ทั้งหมดใน peer snapshot
