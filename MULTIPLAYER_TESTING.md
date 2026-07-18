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
| Client completes handshake | Client log lists `gameplay.server_authoritative`, `content.registry_sync`, and `avatar.peer_snapshot_v2`. |
| Client has a different protocol or mod version | Connection closes with both server and client versions in the message. |
| Client has no Shyne Creator | Connection closes after the five-second handshake timeout. |
| Gameplay pack exists only in `run/server/shyne-mods` | Both clients receive skills, profiles, weapons, models, and textures. Server remains authoritative for world changes. |
| Avatar exists only in Client A's `shyne-mods/avatars` | Client B receives the model, primary PNG texture, animation, visible parts, and approved synchronized variables. |
| Client B joins after Client A activated an avatar | Client B receives the server's retained full avatar snapshot. |
| Client A reconnects | A fresh full snapshot is sent; later snapshots contain state deltas only. |
| Oversized or malformed avatar payload | Server ignores it without crashing or broadcasting it. |
| Rapid avatar updates | Server rate limits snapshots and synchronized variables. |
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
| Owner publishes active Avatar | D1 บันทึก ownership/version/permissions และ R2 มี chunks ตาม SHA-256 |
| Another account reuses the same Avatar id | Backend ตอบ `403 not_owner` |
| Download the same Avatar twice | ครั้งที่สองอ่าน `.shyne-cache/chunks` และไม่ดาวน์โหลดก้อนเดิม |
| Corrupt one cached chunk | Client ลบทิ้ง ดาวน์โหลดใหม่ ตรวจ hash แล้วจึงติดตั้ง |
| Interrupted install | Avatar เดิมถูกเก็บเป็น backup และกู้คืนหากสลับโฟลเดอร์ไม่สำเร็จ |
| Public/unlisted/private | Discover เห็นเฉพาะ public; unlisted เปิดด้วย id; private เห็นเฉพาะ owner |
| Owner changes permissions | Cloud Library แสดงค่าใหม่หลัง refresh; account อื่นแก้ไม่ได้ |
| Cloud is unavailable | Local Avatar และ Minecraft server gameplay ยังใช้ได้ |

## Known scope

Protocol 6 ส่ง texture หลายไฟล์พร้อม hash ใน full snapshot จำกัดไฟล์ละ 8 MiB และรวม 64 MiB ต่อ Avatar แพ็กขนาดใหญ่มากยังควรใช้ระบบ manifest/cache แบบแบ่ง chunk ในรุ่นถัดไป เพื่อลดการส่งข้อมูลซ้ำระหว่างเข้าเซิร์ฟเวอร์
